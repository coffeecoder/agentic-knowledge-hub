package com.agenticknowledgehub.embeddings;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class VertexEmbeddingProvider implements EmbeddingProvider {
  private final EmbeddingHttpClient http;
  private final URI endpoint;
  private final Supplier<Map<String, String>> headers;
  private final EmbeddingSpace space;

  public VertexEmbeddingProvider(
      EmbeddingHttpClient http,
      URI endpoint,
      Supplier<Map<String, String>> headers,
      String model,
      String revision) {
    this.http = http;
    this.endpoint = endpoint;
    this.headers = headers;
    this.space = new EmbeddingSpace("vertex", model, revision + ":retrieval-document/query-v1");
  }

  @Override
  public EmbeddingSpace space() {
    return space;
  }

  @Override
  public EmbeddingVector embed(String text, Task task) {
    EmbeddingProvider.validateInput(text);
    var result =
        http.post(
            endpoint,
            Map.of(
                "instances",
                    List.of(
                        Map.of(
                            "content",
                            text,
                            "task_type",
                            task == Task.DOCUMENT ? "RETRIEVAL_DOCUMENT" : "RETRIEVAL_QUERY")),
                "parameters",
                    Map.of(
                        "autoTruncate", false, "outputDimensionality", EmbeddingSpace.DIMENSIONS)),
            headers);
    var predictions = result.get("predictions");
    if (predictions == null || !predictions.isArray() || predictions.size() != 1)
      throw new EmbeddingUnavailableException();
    var embedding = predictions.get(0).get("embeddings");
    if (embedding == null) throw new EmbeddingUnavailableException();
    var stats = embedding.get("statistics");
    if (stats != null && stats.has("truncated") && stats.get("truncated").asBoolean())
      throw new EmbeddingInputException();
    return EmbeddingHttpClient.vector(embedding.get("values"));
  }
}
