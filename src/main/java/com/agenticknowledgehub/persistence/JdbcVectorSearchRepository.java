package com.agenticknowledgehub.persistence;

import com.agenticknowledgehub.embeddings.*;
import com.agenticknowledgehub.model.Citation;
import com.agenticknowledgehub.retrieval.*;
import com.agenticknowledgehub.security.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class JdbcVectorSearchRepository implements VectorSearchRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public JdbcVectorSearchRepository(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // Materialization deliberately prevents approximate HNSW top-k selection before ACL filtering.
  // It provides an exact, auditable baseline for the current small dataset.
  static final String SQL =
      """
      WITH eligible AS MATERIALIZED (
        SELECT c.chunk_id, c.document_id, d.external_id, c.content, c.citation, c.embedding
        FROM chunks c JOIN documents d ON d.document_id = c.document_id
        JOIN sources s ON s.source_id = d.source_id
        WHERE s.tenant_id = ? AND s.status = 'ACTIVE' AND NOT d.is_deleted AND c.is_active
          AND d.allowed_groups && ARRAY(SELECT jsonb_array_elements_text(?::jsonb))
          AND c.embedding IS NOT NULL AND c.embedding_space = ? AND d.embedding_space = ?
          AND c.embedding_dimensions = 768
      )
      SELECT chunk_id, document_id, external_id, content, citation::text,
             1 - (embedding <=> ?::vector) AS score
      FROM eligible ORDER BY score DESC, chunk_id ASC LIMIT ?
      """;

  @Override
  public List<SearchHit> search(
      CallerContext caller, EmbeddingVector query, EmbeddingSpace space, int limit) {
    return jdbc.query(
        connection -> {
          var statement = connection.prepareStatement(SQL);
          statement.setQueryTimeout(5);
          statement.setString(1, caller.tenantId());
          statement.setString(2, json.writeValueAsString(caller.groups()));
          statement.setString(3, space.id());
          statement.setString(4, space.id());
          statement.setString(5, query.sqlValue());
          statement.setInt(6, limit);
          return statement;
        },
        (row, index) -> {
          try {
            return new SearchHit(
                row.getString("chunk_id"),
                row.getObject("document_id", UUID.class),
                row.getString("external_id"),
                row.getString("content"),
                row.getDouble("score"),
                json.readValue(row.getString("citation"), Citation.class));
          } catch (tools.jackson.core.JacksonException exception) {
            throw new org.springframework.dao.DataRetrievalFailureException(
                "Stored citation unavailable");
          }
        });
  }
}
