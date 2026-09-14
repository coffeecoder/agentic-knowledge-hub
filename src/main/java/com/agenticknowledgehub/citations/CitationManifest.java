package com.agenticknowledgehub.citations;

import com.agenticknowledgehub.model.KnowledgeChunk;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Checks citation IDs against application-owned evidence, not factual entailment. */
public final class CitationManifest {
  private static final Pattern REFERENCE = Pattern.compile("\\[(C\\d+)\\]");

  public record CitationRecord(String citationId, KnowledgeChunk chunk) {}

  public record Validation(boolean valid, List<String> unknownIds) {
    public Validation {
      unknownIds = List.copyOf(unknownIds);
    }
  }

  private final List<CitationRecord> records;
  private final Set<String> ids;

  public CitationManifest(List<KnowledgeChunk> chunks) {
    List<CitationRecord> entries = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      entries.add(new CitationRecord("C" + (i + 1), chunks.get(i)));
    }
    records = List.copyOf(entries);
    ids = records.stream().map(CitationRecord::citationId).collect(Collectors.toUnmodifiableSet());
  }

  public List<CitationRecord> records() {
    return records;
  }

  public Validation validateAnswer(String answer) {
    var unknown =
        REFERENCE
            .matcher(answer)
            .results()
            .map(match -> match.group(1))
            .distinct()
            .filter(id -> !ids.contains(id))
            .toList();
    return new Validation(unknown.isEmpty(), unknown);
  }

  /** Returned text is untrusted source data and must never grant tool permissions. */
  public String promptContext() {
    return records.stream()
        .map(record -> "[" + record.citationId() + "] " + record.chunk().content())
        .collect(Collectors.joining("\n\n"));
  }
}
