package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiTest {
  @LocalServerPort private int port;
  private final JsonMapper json = new JsonMapper();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Test
  void livenessWorksWithoutDatabase() throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(uri("/health/live")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals("UP", json.readTree(response.body()).get("status").asString());
    assertEquals("0.1.0", json.readTree(response.body()).get("version").asString());
  }

  @Test
  void preservesSnakeCaseContractAndStatelessRepeatBehavior() throws Exception {
    String body =
        """
            {"external_id":"doc","title":"Doc","text":"some useful knowledge"}
            """;
    var first = post(body);
    assertEquals(200, first.statusCode());
    JsonNode result = json.readTree(first.body());
    assertEquals("CHANGED", result.get("status").asString());
    assertEquals(64, result.get("content_hash").asString().length());
    assertEquals(1, result.get("chunk_count").asInt());
    assertEquals(1, result.get("chunk_ids").size());
    assertFalse(result.has("contentHash"));
    assertEquals(result, json.readTree(post(body).body()));
  }

  @Test
  void emptyTextKeepsEmptyStatus() throws Exception {
    var response = post("{\"external_id\":\"doc\",\"title\":\"Doc\",\"text\":\"\"}");
    assertEquals(200, response.statusCode());
    assertEquals("EMPTY", json.readTree(response.body()).get("status").asString());
    assertEquals(0, json.readTree(response.body()).get("chunk_count").asInt());
  }

  @Test
  void malformedOrMissingFieldsReturnSanitized422() throws Exception {
    for (String body :
        new String[] {
          "{",
          "{\"text\":\"synthetic-sensitive-payload\"}",
          "{\"external_id\":\"doc\",\"title\":\"Doc\",\"text\":null}"
        }) {
      var response = post(body);
      assertEquals(422, response.statusCode());
      assertFalse(response.body().contains("synthetic-sensitive-payload"));
      assertEquals(
          "Invalid document request", json.readTree(response.body()).get("detail").asString());
    }
  }

  @Test
  void openApiAndInteractiveDocsRemainAvailable() throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(uri("/openapi.json")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    var paths = json.readTree(response.body()).get("paths");
    assertTrue(paths.has("/health/live"));
    assertTrue(paths.has("/v1/documents/text"));
    var docs =
        client.send(
            HttpRequest.newBuilder(uri("/docs")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertTrue(docs.statusCode() == 302 || docs.statusCode() == 200);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private HttpResponse<String> post(String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri("/v1/documents/text"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
