package com.agenticknowledgehub.embeddings;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared transport for real adapters: bounded attempts, no response-body/credential logging. */
public final class EmbeddingHttpClient {
  private final HttpClient client;
  private final JsonMapper json;
  private final Duration timeout;
  private final int attempts;

  public EmbeddingHttpClient(JsonMapper json, Duration timeout, int attempts) {
    if (timeout.isNegative()
        || timeout.isZero()
        || timeout.compareTo(Duration.ofSeconds(60)) > 0
        || attempts < 1
        || attempts > 3) throw new IllegalArgumentException("Invalid embedding HTTP limits");
    this.json = json;
    this.timeout = timeout;
    this.attempts = attempts;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public JsonNode post(URI uri, Object body, Supplier<Map<String, String>> headers) {
    String payload = json.writeValueAsString(body);
    for (int attempt = 0; attempt < attempts; attempt++) {
      try {
        var request =
            HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        headers.get().forEach(request::header);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 200) {
          if (response.body().length() > 200000) throw new EmbeddingUnavailableException();
          try {
            var result = json.readTree(response.body());
            if (result == null || !result.isObject()) throw new EmbeddingUnavailableException();
            return result;
          } catch (RuntimeException exception) {
            throw new EmbeddingUnavailableException();
          }
        }
        if (status == 400 || status == 413 || status == 422) throw new EmbeddingInputException();
        if (status != 429 && status != 500 && status != 502 && status != 503 && status != 504) {
          throw new EmbeddingUnavailableException();
        }
      } catch (IOException exception) {
        // Retrying an inference can duplicate billing, but cannot mutate stored documents.
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new EmbeddingUnavailableException();
      }
      if (attempt + 1 < attempts) {
        try {
          Thread.sleep(200L * (attempt + 1));
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new EmbeddingUnavailableException();
        }
      }
    }
    throw new EmbeddingUnavailableException();
  }

  public static EmbeddingVector vector(JsonNode node) {
    if (node == null || !node.isArray() || node.size() != EmbeddingSpace.DIMENSIONS) {
      throw new EmbeddingUnavailableException();
    }
    double[] values = new double[node.size()];
    for (int i = 0; i < values.length; i++) {
      if (!node.get(i).isNumber()) throw new EmbeddingUnavailableException();
      values[i] = node.get(i).asDouble();
    }
    return new EmbeddingVector(values);
  }
}
