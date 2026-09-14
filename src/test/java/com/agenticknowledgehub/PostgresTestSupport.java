package com.agenticknowledgehub;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Shared disposable database; tests never connect to the developer's Compose volume. */
abstract class PostgresTestSupport {
  static final GenericContainer<?> POSTGRES =
      new GenericContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
          .withEnv("POSTGRES_USER", "akh_test")
          .withEnv("POSTGRES_PASSWORD", "synthetic-test-password")
          .withEnv("POSTGRES_DB", "akh_test")
          .withExposedPorts(5432)
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                  .withStartupTimeout(Duration.ofSeconds(90)));

  static {
    POSTGRES.start();
  }

  static String url(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + database;
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> url("akh_test"));
    registry.add("spring.datasource.username", () -> "akh_test");
    registry.add("spring.datasource.password", () -> "synthetic-test-password");
  }
}
