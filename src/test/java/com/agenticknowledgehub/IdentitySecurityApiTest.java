package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestIdentity.class)
class IdentitySecurityApiTest extends PostgresTestSupport {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  private static final String DOCUMENT =
      """
      {"external_id":"identity-test","title":"Synthetic","text":"isolationcheck original","allowed_groups":["support"]}
      """;

  @Test
  void rejectsInvalidTokensAndDoesNotEchoThem() throws Exception {
    List<String> tokens =
        List.of(
            "not-a-token",
            TestIdentity.sign(TestIdentity.claims("local").build(), TestIdentity.key()),
            TestIdentity.sign(
                TestIdentity.claims("local").issuer("https://attacker.invalid").build(),
                TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local").audience("another-project").build(), TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local")
                    .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                    .build(),
                TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local").expirationTime(null).build(), TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local").issueTime(null).build(), TestIdentity.KEY),
            TestIdentity.sign(TestIdentity.claims("local").subject("").build(), TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local").claim("auth_time", null).build(), TestIdentity.KEY),
            TestIdentity.sign(
                TestIdentity.claims("local")
                    .issueTime(Date.from(Instant.now().plusSeconds(300)))
                    .build(),
                TestIdentity.KEY));
    for (String token : tokens) {
      for (String path : List.of("/v1/search", "/v1/documents/text")) {
        var response = post(path, token, DOCUMENT);
        assertEquals(401, response.statusCode());
        assertFalse(response.body().contains(token));
      }
    }
    assertEquals(401, post("/v1/documents/text", null, DOCUMENT).statusCode());
  }

  @Test
  void missingPermissionsReaderAndInvalidSourceOrAclCannotWrite() throws Exception {
    String tenant = "denied-writes";
    List<Map<String, Object>> claims =
        List.of(
            Map.of(),
            TestIdentity.permissions(tenant, false),
            Map.of(
                "tenant",
                tenant,
                "permissions",
                List.of("knowledge:write"),
                "write_sources",
                List.of("other"),
                "assignable_groups",
                List.of("support")),
            Map.of(
                "tenant",
                tenant,
                "permissions",
                List.of("knowledge:write"),
                "write_sources",
                List.of("manual-text"),
                "assignable_groups",
                List.of("admin")),
            Map.of("tenant", tenant, "permissions", "knowledge:write"));
    for (var claim : claims) {
      String token =
          TestIdentity.sign(
              TestIdentity.claims(tenant).claim("akh", claim).build(), TestIdentity.KEY);
      assertEquals(403, post("/v1/documents/text", token, DOCUMENT).statusCode());
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM sources WHERE tenant_id = ?", Integer.class, tenant));
  }

  @Test
  void trustedTenantControlsWritesAndReadsAndRetriesRemainDurable() throws Exception {
    String token = TestIdentity.token("identity-a");
    assertTrue(post("/v1/documents/text", token, DOCUMENT).body().contains("CHANGED"));
    assertTrue(post("/v1/documents/text", token, DOCUMENT).body().contains("UNCHANGED"));
    String spoof =
        DOCUMENT
            .replace("original", "changed")
            .replace("\"external_id\"", "\"tenant_id\":\"identity-a\",\"external_id\"");
    assertEquals(
        200, post("/v1/documents/text", TestIdentity.token("identity-b"), spoof).statusCode());
    var result = post("/v1/search", token, "{\"query\":\"isolationcheck\"}");
    assertEquals(200, result.statusCode());
    assertTrue(result.body().contains("original"));
    assertFalse(result.body().contains("changed"));
    assertEquals(
        403, post("/v1/documents/text", token, DOCUMENT.replace("support", "admin")).statusCode());
    assertTrue(post("/v1/documents/text", token, DOCUMENT).body().contains("UNCHANGED"));
    var changed = post("/v1/documents/text", token, DOCUMENT.replace("original", "replacement"));
    assertEquals(200, changed.statusCode());
    result = post("/v1/search", token, "{\"query\":\"isolationcheck\"}");
    assertTrue(result.body().contains("replacement"));
    assertFalse(result.body().contains("original"));
  }

  @Test
  void docsAreNotExposedOutsideDevelopment() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      for (String path : List.of("/docs", "/openapi.json", "/swagger-ui/index.html")) {
        var response =
            client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
      }
    }
  }

  private HttpResponse<String> post(String path, String token, String body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .header("X-Tenant-ID", "identity-a")
              .header("X-Groups", "admin")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (token != null) request.header("Authorization", "Bearer " + token);
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
