package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Simulates a runtime outage; production still requires successful startup migrations. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable",
      "spring.datasource.hikari.connection-timeout=250",
      "spring.datasource.hikari.initialization-fail-timeout=-1"
    })
class DatabaseUnavailableApiTest {
  @LocalServerPort int port;

  @Test
  void livenessStaysUpButNonemptyIngestionReturnsSanitized503() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var health =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health/live"))
                  .timeout(Duration.ofSeconds(5))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, health.statusCode());
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/documents/text"))
                  .timeout(Duration.ofSeconds(5))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"external_id\":\"outage\",\"title\":\"Doc\",\"text\":\"synthetic-sensitive-payload\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(503, response.statusCode());
      assertTrue(response.body().contains("Document persistence unavailable"));
      assertFalse(response.body().contains("sensitive"));
      assertFalse(response.body().contains("jdbc"));
    }
  }
}
