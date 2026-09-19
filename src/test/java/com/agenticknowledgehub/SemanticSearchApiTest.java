package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"akh.embedding.provider=local-hash", "akh.embedding.max-chunks=1"})
@Import(TestIdentity.class)
class SemanticSearchApiTest extends PostgresTestSupport {
  @LocalServerPort int port;
  private final JsonMapper json = new JsonMapper();

  @Test
  void localModePersistsVectorsAndReturnsCitationsThroughAuthenticatedApi() throws Exception {
    String document =
        """
        {"external_id":"semantic-api","title":"Guide","text":"Cloud Run hosts containers","allowed_groups":["support"]}
        """;
    assertEquals(200, post("/v1/documents/text", document).statusCode());
    var response =
        post("/v1/search", "{\"query\":\"Cloud Run\",\"mode\":\"semantic\",\"limit\":1}");
    assertEquals(200, response.statusCode());
    var hit = json.readTree(response.body()).get("results").get(0);
    assertEquals("semantic-api", hit.get("external_id").asString());
    assertTrue(hit.has("citation"));
    assertTrue(hit.get("score").asDouble() > 0);
    assertTrue(post("/v1/documents/text", document).body().contains("UNCHANGED"));
    assertEquals(200, post("/v1/search", "{\"query\":\"containers\"}").statusCode());
    assertEquals(
        422, post("/v1/search", "{\"query\":\"Cloud Run\",\"mode\":\"typo\"}").statusCode());
    assertEquals(
        403, post("/v1/documents/text", document.replace("support", "admin")).statusCode());
  }

  @Test
  void inputLimitsFailWithoutEchoingText() throws Exception {
    String body =
        "{\"external_id\":\"large\",\"title\":\"Large\",\"text\":\""
            + "sensitive ".repeat(600)
            + "\",\"allowed_groups\":[\"support\"]}";
    var result = post("/v1/documents/text", body);
    assertEquals(422, result.statusCode());
    assertFalse(result.body().contains("sensitive"));
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + TestIdentity.token("semantic-api"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
