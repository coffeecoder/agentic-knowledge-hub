package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.embeddings.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EmbeddingAdaptersTest {
  HttpServer server;
  URI endpoint;
  final JsonMapper json = new JsonMapper();
  AtomicInteger calls = new AtomicInteger();
  AtomicReference<JsonNode> request = new AtomicReference<>();
  AtomicReference<String> authorization = new AtomicReference<>();
  int status = 200;
  boolean firstRetryable;
  long delayMillis;
  String body;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/embed",
        exchange -> {
          request.set(
              json.readTree(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          int count = calls.incrementAndGet();
          if (delayMillis > 0) {
            try {
              Thread.sleep(delayMillis);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(firstRetryable && count == 1 ? 503 : status, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    server.start();
    endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/embed");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void timeoutFailsWithSanitizedException() {
    body = "{}";
    delayMillis = 500;
    var provider =
        new VertexEmbeddingProvider(
            new EmbeddingHttpClient(json, Duration.ofMillis(100), 1),
            endpoint,
            Map::of,
            "model",
            "v1");
    var error =
        assertThrows(
            EmbeddingUnavailableException.class,
            () -> provider.embed("synthetic", EmbeddingProvider.Task.QUERY));
    assertNull(error.getCause());
  }

  @Test
  void vertexUsesTaskAndDimensionAndRetriesTransientFailureWithoutLeakingBodies() {
    body =
        json.writeValueAsString(
            Map.of(
                "predictions",
                List.of(
                    Map.of(
                        "embeddings",
                        Map.of("values", values(), "statistics", Map.of("truncated", false))))));
    firstRetryable = true;
    var vertex = vertex(2);
    assertEquals(
        1, vertex.embed("synthetic evidence", EmbeddingProvider.Task.DOCUMENT).values()[0], 1e-8);
    assertEquals(2, calls.get());
    assertEquals(
        "RETRIEVAL_DOCUMENT", request.get().get("instances").get(0).get("task_type").asString());
    assertFalse(request.get().get("parameters").get("autoTruncate").asBoolean());
    assertEquals(768, request.get().get("parameters").get("outputDimensionality").asInt());
    assertEquals("Bearer synthetic-token", authorization.get());
    vertex.embed("synthetic query", EmbeddingProvider.Task.QUERY);
    assertEquals(
        "RETRIEVAL_QUERY", request.get().get("instances").get(0).get("task_type").asString());
  }

  @Test
  void rejectsBadDimensionsTruncationAndMalformedResponses() {
    for (String invalid :
        List.of(
            "not-json-sensitive",
            "{}",
            "{\"predictions\":[{\"embeddings\":{\"values\":[1,2]}}]}")) {
      body = invalid;
      var error =
          assertThrows(
              EmbeddingUnavailableException.class,
              () -> vertex(1).embed("text", EmbeddingProvider.Task.DOCUMENT));
      assertNull(error.getCause());
      assertFalse(error.getMessage().contains("sensitive"));
    }
    body = "{\"predictions\":[{\"embeddings\":{\"statistics\":{\"truncated\":true}}}]}";
    assertThrows(
        EmbeddingInputException.class,
        () -> vertex(1).embed("text", EmbeddingProvider.Task.DOCUMENT));
  }

  @Test
  void permanentErrorsAreNotRetriedAndRetryExhaustionIsBounded() {
    body = "sensitive upstream response";
    status = 403;
    assertThrows(
        EmbeddingUnavailableException.class,
        () -> vertex(3).embed("text", EmbeddingProvider.Task.DOCUMENT));
    assertEquals(1, calls.get());
    calls.set(0);
    status = 400;
    assertThrows(
        EmbeddingInputException.class,
        () -> vertex(3).embed("text", EmbeddingProvider.Task.DOCUMENT));
    assertEquals(1, calls.get());
    calls.set(0);
    status = 429;
    assertThrows(
        EmbeddingUnavailableException.class,
        () -> vertex(2).embed("text", EmbeddingProvider.Task.DOCUMENT));
    assertEquals(2, calls.get());
  }

  @Test
  void ollamaUsesModelPrefixesAndDoesNotSendGoogleCredentials() {
    body = json.writeValueAsString(Map.of("embeddings", List.of(values())));
    var provider =
        new OllamaEmbeddingProvider(
            http(1),
            endpoint,
            "nomic-embed-text:v1.5",
            "v1",
            "search_document: ",
            "search_query: ");
    provider.embed("evidence", EmbeddingProvider.Task.DOCUMENT);
    assertEquals("search_document: evidence", request.get().get("input").asString());
    assertFalse(request.get().get("truncate").asBoolean());
    assertNull(authorization.get());
    provider.embed("question", EmbeddingProvider.Task.QUERY);
    assertEquals("search_query: question", request.get().get("input").asString());
    assertThrows(
        EmbeddingInputException.class,
        () -> provider.embed("x".repeat(32001), EmbeddingProvider.Task.DOCUMENT));
    assertEquals(2, calls.get());
  }

  private EmbeddingHttpClient http(int attempts) {
    return new EmbeddingHttpClient(json, Duration.ofSeconds(1), attempts);
  }

  private VertexEmbeddingProvider vertex(int attempts) {
    return new VertexEmbeddingProvider(
        http(attempts),
        endpoint,
        () -> Map.of("Authorization", "Bearer synthetic-token"),
        "gemini-embedding-001",
        "v1");
  }

  private double[] values() {
    double[] result = new double[768];
    result[0] = 1;
    return result;
  }
}
