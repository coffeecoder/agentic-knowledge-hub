package com.agenticknowledgehub;

import com.agenticknowledgehub.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class TestDocuments {
  private TestDocuments() {}

  static SourceDocument text(String text) {
    return document(SourceType.TEXT, "text/plain", text.getBytes(StandardCharsets.UTF_8));
  }

  static SourceDocument document(SourceType type, String mime, byte[] bytes) {
    return new SourceDocument(
        "runbook",
        "Gateway Runbook",
        type,
        "v1",
        "https://example.test/runbook",
        bytes,
        mime,
        List.of("support"),
        Map.of());
  }
}
