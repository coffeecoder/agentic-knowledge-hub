package com.agenticknowledgehub.embeddings;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Lazy ADC loading keeps disabled and local modes independent of Google credentials. */
public final class GoogleAdcHeaders implements Supplier<Map<String, String>> {
  private final String project;
  private GoogleCredentials credentials;

  public GoogleAdcHeaders(String project) {
    this.project = project;
  }

  @Override
  public synchronized Map<String, String> get() {
    try {
      if (credentials == null)
        credentials =
            GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
      credentials.refreshIfExpired();
      return Map.of(
          "Authorization",
          "Bearer " + credentials.getAccessToken().getTokenValue(),
          "x-goog-user-project",
          project);
    } catch (IOException | RuntimeException exception) {
      throw new EmbeddingUnavailableException();
    }
  }
}
