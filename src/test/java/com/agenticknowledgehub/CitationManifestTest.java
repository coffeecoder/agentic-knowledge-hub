package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.citations.CitationManifest;
import com.agenticknowledgehub.model.ParsedSection;
import java.util.List;
import org.junit.jupiter.api.Test;

class CitationManifestTest {
  @Test
  void validatesOnlyApplicationOwnedCitationIds() {
    var chunks =
        new SemanticChunker(20, 3)
            .chunk(
                TestDocuments.text("unused"),
                List.of(
                    new ParsedSection(
                        "Cloud Run hosts the query API", List.of(), null, null, null)));
    var manifest = new CitationManifest(chunks);
    var result = manifest.validateAnswer("Cloud Run [C1], not GKE [C9], again [C9].");
    assertFalse(result.valid());
    assertEquals(List.of("C9"), result.unknownIds());
    assertTrue(manifest.validateAnswer("Cloud Run [C1]").valid());
    assertEquals("[C1] Cloud Run hosts the query API", manifest.promptContext());
    // ID validation alone neither requires a citation nor proves that a claim is grounded.
    assertTrue(manifest.validateAnswer("No citation").valid());
  }
}
