package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.config.EmbeddingConfiguration;
import com.agenticknowledgehub.embeddings.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class EmbeddingProviderTest {
  @Test
  void normalizesAndDefensivelyCopiesAndRejectsInvalidVectors() {
    double[] values = new double[768];
    values[0] = 3;
    values[1] = 4;
    var vector = new EmbeddingVector(values);
    values[0] = 0;
    assertEquals(0.6, vector.values()[0], 1e-8);
    var copy = vector.values();
    copy[0] = 0;
    assertEquals(0.6, vector.values()[0], 1e-8);
    assertTrue(vector.toString().contains("redacted"));
    assertThrows(EmbeddingUnavailableException.class, () -> new EmbeddingVector(new double[767]));
    assertThrows(EmbeddingUnavailableException.class, () -> new EmbeddingVector(new double[768]));
    values[0] = Double.NaN;
    assertThrows(EmbeddingUnavailableException.class, () -> new EmbeddingVector(values));
    values[0] = Double.POSITIVE_INFINITY;
    assertThrows(EmbeddingUnavailableException.class, () -> new EmbeddingVector(values));
  }

  @Test
  void localHashIsDeterministicAndSpacesSeparateProviderModelAndRevision() {
    var local = new LocalHashEmbeddingProvider();
    assertArrayEquals(
        local.embed("cloud run", EmbeddingProvider.Task.DOCUMENT).values(),
        local.embed("cloud run", EmbeddingProvider.Task.QUERY).values());
    assertFalse(
        Arrays.equals(
            local.embed("cloud run", EmbeddingProvider.Task.DOCUMENT).values(),
            local.embed("postgresql transaction", EmbeddingProvider.Task.DOCUMENT).values()));
    var space = new EmbeddingSpace("a", "model", "v1");
    assertNotEquals(space.id(), new EmbeddingSpace("b", "model", "v1").id());
    assertNotEquals(space.id(), new EmbeddingSpace("a", "other", "v1").id());
    assertNotEquals(space.id(), new EmbeddingSpace("a", "model", "v2").id());
  }

  @Test
  void configurationDefaultsToNoCloudAndRejectsTyposAndUnsafeEndpoints() throws Exception {
    var method =
        EmbeddingConfiguration.class.getDeclaredMethod(
            "embeddingProvider", org.springframework.core.env.Environment.class, JsonMapper.class);
    method.setAccessible(true);
    var config = new EmbeddingConfiguration();
    var json = new JsonMapper();
    assertInstanceOf(
        DisabledEmbeddingProvider.class, method.invoke(config, new MockEnvironment(), json));
    assertInstanceOf(
        LocalHashEmbeddingProvider.class,
        method.invoke(
            config,
            new MockEnvironment().withProperty("akh.embedding.provider", "local-hash"),
            json));
    assertInstanceOf(
        OllamaEmbeddingProvider.class,
        method.invoke(
            config, new MockEnvironment().withProperty("akh.embedding.provider", "ollama"), json));
    assertThrows(
        java.lang.reflect.InvocationTargetException.class,
        () ->
            method.invoke(
                config,
                new MockEnvironment().withProperty("akh.embedding.provider", "typo"),
                json));
    var http = new EmbeddingHttpClient(json, java.time.Duration.ofSeconds(1), 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OllamaEmbeddingProvider(
                http,
                java.net.URI.create("http://remote.invalid/api/embed"),
                "model",
                "v1",
                "",
                ""));
  }
}
