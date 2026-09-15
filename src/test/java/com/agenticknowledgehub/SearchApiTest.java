package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("dev")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"akh.source.tenant=search-api-test"})
@org.springframework.context.annotation.Import(TestIdentity.class)
class SearchApiTest extends PostgresTestSupport {
  @LocalServerPort int port;
  private final JsonMapper json = new JsonMapper();

  @Test
  void returnsAuthorizedEvidenceAndIgnoresSpoofedIdentity() throws Exception {
    assertEquals(
        200,
        post(
                "/v1/documents/text",
                """
        {"external_id":"search-api-allowed","title":"Guide","text":"deploymentsky authorized","allowed_groups":["support"]}
        """)
            .statusCode());
    assertEquals(
        200,
        post(
                "/v1/documents/text",
                """
        {"external_id":"search-api-denied","title":"Restricted","text":"deploymentsky restricted","allowed_groups":["admin"]}
        """)
            .statusCode());
    var response =
        post(
            "/v1/search",
            """
        {"query":"deploymentsky","limit":5,"tenant_id":"other","groups":["admin"]}
        """);
    assertEquals(200, response.statusCode());
    var results = json.readTree(response.body()).get("results");
    assertEquals(1, results.size());
    assertEquals("search-api-allowed", results.get(0).get("external_id").asString());
    assertTrue(results.get(0).has("citation"));
    assertTrue(results.get(0).has("chunk_id"));
    assertFalse(response.body().contains("restricted"));
  }

  @Test
  void validatesQueryAndBoundedLimitWithoutEchoingInput() throws Exception {
    for (String body :
        new String[] {
          "{}",
          "{\"query\":\" \"}",
          "{\"query\":\"q\",\"limit\":0}",
          "{\"query\":\"q\",\"limit\":21}",
          "{\"query\":\"" + "x".repeat(501) + "\"}",
          "{"
        }) {
      assertEquals(422, post("/v1/search", body).statusCode());
    }
    assertEquals(200, post("/v1/search", "{\"query\":\"nomatchxyz\"}").statusCode());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .header(
                  "Authorization",
                  "Bearer "
                      + TestIdentity.sign(
                          TestIdentity.claims("search-api-test")
                              .claim(
                                  "akh",
                                  java.util.Map.of(
                                      "tenant",
                                      "search-api-test",
                                      "groups",
                                      java.util.List.of("support"),
                                      "permissions",
                                      java.util.List.of("knowledge:read", "knowledge:write"),
                                      "write_sources",
                                      java.util.List.of("manual-text"),
                                      "assignable_groups",
                                      java.util.List.of("support", "admin")))
                              .build(),
                          TestIdentity.KEY))
              .header("Content-Type", "application/json")
              .header("X-Tenant-ID", "spoofed")
              .header("X-Groups", "admin")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
