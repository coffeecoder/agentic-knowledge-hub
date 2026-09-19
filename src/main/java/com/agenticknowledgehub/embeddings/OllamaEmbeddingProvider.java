package com.agenticknowledgehub.embeddings;

import java.net.URI;
import java.util.Map;

/** Local model adapter. Endpoint is operator configuration, never supplied by an API caller. */
public final class OllamaEmbeddingProvider implements EmbeddingProvider {
  private final EmbeddingHttpClient http;
  private final URI endpoint;
  private final EmbeddingSpace space;
  private final String documentPrefix;
  private final String queryPrefix;

  public OllamaEmbeddingProvider(
      EmbeddingHttpClient http,
      URI endpoint,
      String model,
      String revision,
      String documentPrefix,
      String queryPrefix) {
    if (endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null
        || endpoint.getFragment() != null
        || !("https".equals(endpoint.getScheme())
            || ("http".equals(endpoint.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]")
                    .contains(endpoint.getHost())))) {
      throw new IllegalArgumentException(
          "Ollama requires HTTPS or loopback HTTP without URL credentials");
    }
    this.http = http;
    this.endpoint = endpoint;
    this.documentPrefix = documentPrefix;
    this.queryPrefix = queryPrefix;
    this.space =
        new EmbeddingSpace(
            "ollama",
            model,
            revision
                + ":"
                + documentPrefix.length()
                + ":"
                + documentPrefix
                + ":"
                + queryPrefix.length()
                + ":"
                + queryPrefix);
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
                "model",
                space.model(),
                "input",
                (task == Task.DOCUMENT ? documentPrefix : queryPrefix) + text,
                "truncate",
                false),
            Map::of);
    var vectors = result.get("embeddings");
    if (vectors == null || !vectors.isArray() || vectors.size() != 1)
      throw new EmbeddingUnavailableException();
    return EmbeddingHttpClient.vector(vectors.get(0));
  }
}
