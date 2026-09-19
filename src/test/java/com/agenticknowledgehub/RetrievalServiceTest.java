package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.retrieval.*;
import com.agenticknowledgehub.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class RetrievalServiceTest {
  @Test
  void emptyGroupsNeverCallEmbeddingProviderAndDisabledSemanticModeFailsExplicitly() {
    var provider =
        org.mockito.Mockito.mock(com.agenticknowledgehub.embeddings.EmbeddingProvider.class);
    var service =
        new RetrievalService(
            (caller, query, limit) -> List.of(),
            (caller, vector, space, limit) -> List.of(),
            provider);
    assertTrue(
        service.search(new CallerContext("p", "t", List.of()), "query", 5, "semantic").isEmpty());
    org.mockito.Mockito.verifyNoInteractions(provider);
    assertThrows(
        com.agenticknowledgehub.embeddings.EmbeddingUnavailableException.class,
        () ->
            new RetrievalService((caller, query, limit) -> List.of())
                .search(new CallerContext("p", "t", List.of("support")), "q", 5, "semantic"));
  }

  @Test
  void deniesMissingIdentityAndSkipsDatabaseForEmptyGroups() {
    var service =
        new RetrievalService(
            (caller, query, limit) -> {
              fail("Must not query");
              return List.of();
            });
    assertThrows(SearchAccessDeniedException.class, () -> service.search(null, "query", 5));
    assertTrue(service.search(new CallerContext("p", "t", List.of()), "query", 5).isEmpty());
  }

  @Test
  void sanitizesDatabaseFailures() {
    var service =
        new RetrievalService(
            (caller, query, limit) -> {
              throw new DataAccessResourceFailureException("sensitive SQL");
            });
    var failure =
        assertThrows(
            SearchUnavailableException.class,
            () -> service.search(new CallerContext("p", "t", List.of("g")), "q", 5));
    assertNull(failure.getCause());
    assertEquals("Search unavailable", failure.getMessage());
  }
}
