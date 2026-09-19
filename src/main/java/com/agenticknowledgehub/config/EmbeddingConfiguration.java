package com.agenticknowledgehub.config;

import com.agenticknowledgehub.embeddings.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {
  @Bean
  EmbeddingProvider embeddingProvider(Environment env, JsonMapper json) {
    String provider = env.getProperty("akh.embedding.provider", "none");
    if (provider.equals("none")) return new DisabledEmbeddingProvider();
    if (provider.equals("local-hash")) return new LocalHashEmbeddingProvider();
    var http =
        new EmbeddingHttpClient(
            json,
            Duration.ofSeconds(env.getProperty("akh.embedding.timeout-seconds", Integer.class, 15)),
            env.getProperty("akh.embedding.attempts", Integer.class, 2));
    String revision = env.getProperty("akh.embedding.revision", "v1");
    return switch (provider) {
      case "ollama" ->
          new OllamaEmbeddingProvider(
              http,
              URI.create(
                  env.getProperty("akh.embedding.ollama.url", "http://localhost:11434/api/embed")),
              env.getProperty("akh.embedding.ollama.model", "nomic-embed-text:v1.5"),
              revision,
              env.getProperty("akh.embedding.ollama.document-prefix", "search_document: "),
              env.getProperty("akh.embedding.ollama.query-prefix", "search_query: "));
      case "vertex" -> {
        String project = env.getProperty("akh.embedding.vertex.project", "");
        String location = env.getProperty("akh.embedding.vertex.location", "us-central1");
        String model = env.getProperty("akh.embedding.vertex.model", "gemini-embedding-001");
        if (!project.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]")
            || !location.matches("[a-z]+-[a-z]+[0-9]+")
            || !model.matches("[a-zA-Z0-9_-]+")) {
          throw new IllegalArgumentException(
              "Invalid Vertex project, regional location or model configuration");
        }
        URI endpoint =
            URI.create(
                "https://"
                    + location
                    + "-aiplatform.googleapis.com/v1/projects/"
                    + project
                    + "/locations/"
                    + location
                    + "/publishers/google/models/"
                    + model
                    + ":predict");
        yield new VertexEmbeddingProvider(
            http, endpoint, new GoogleAdcHeaders(project), model, revision);
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown embedding provider; choose none, local-hash, ollama or vertex");
    };
  }
}
