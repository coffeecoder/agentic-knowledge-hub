package com.agenticknowledgehub.chunking;

import com.agenticknowledgehub.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Deterministic word windows within parser sections; no model calls or embeddings. */
public final class SemanticChunker {
  private static final Pattern WORD = Pattern.compile("\\S+", Pattern.UNICODE_CHARACTER_CLASS);
  private final int maxWords;
  private final int overlapWords;

  public SemanticChunker(int maxWords, int overlapWords) {
    if (maxWords < 20) {
      throw new IllegalArgumentException("maxWords must be at least 20");
    }
    if (overlapWords < 0 || overlapWords >= maxWords) {
      throw new IllegalArgumentException(
          "overlapWords must be non-negative and smaller than maxWords");
    }
    this.maxWords = maxWords;
    this.overlapWords = overlapWords;
  }

  public List<KnowledgeChunk> chunk(SourceDocument document, List<ParsedSection> sections) {
    List<KnowledgeChunk> chunks = new ArrayList<>();
    for (var section : sections) {
      List<String> words =
          WORD.matcher(section.text()).results().map(match -> match.group()).toList();
      for (int start = 0; start < words.size(); start += maxWords - overlapWords) {
        var part = words.subList(start, Math.min(start + maxWords, words.size()));
        String content = String.join(" ", part);
        String digest = ContentHash.sha256(content);
        int ordinal = chunks.size();
        String stableKey =
            document.externalId()
                + ":"
                + document.sourceVersion()
                + ":"
                + ordinal
                + ":"
                + digest.substring(0, 16);
        var citation =
            new Citation(
                document.sourceType(),
                document.title(),
                document.sourceUrl(),
                document.sourceVersion(),
                section.headingPath(),
                section.pageNumber(),
                section.lineStart(),
                section.lineEnd());
        chunks.add(
            new KnowledgeChunk(
                ContentHash.sha256(stableKey),
                document.externalId(),
                ordinal,
                content,
                digest,
                part.size(),
                citation,
                document.allowedGroups()));
        if (start + maxWords >= words.size()) {
          break;
        }
      }
    }
    return List.copyOf(chunks);
  }
}
