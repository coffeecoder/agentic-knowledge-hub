package com.agenticknowledgehub.persistence;

import com.agenticknowledgehub.model.Citation;
import com.agenticknowledgehub.retrieval.*;
import com.agenticknowledgehub.security.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class JdbcChunkSearchRepository implements ChunkSearchRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public JdbcChunkSearchRepository(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // All eligibility predicates are in SQL before ORDER BY / LIMIT. Never fetch unrestricted top-k.
  static final String SQL =
      """
      SELECT c.chunk_id, c.document_id, d.external_id, c.content, c.citation::text,
             ts_rank_cd(to_tsvector('english', c.content), q.query) AS score
      FROM chunks c JOIN documents d ON d.document_id = c.document_id
      JOIN sources s ON s.source_id = d.source_id
      CROSS JOIN (SELECT websearch_to_tsquery('english', ?) AS query) q
      WHERE s.tenant_id = ? AND s.status = 'ACTIVE' AND NOT d.is_deleted AND c.is_active
        AND d.allowed_groups && ARRAY(SELECT jsonb_array_elements_text(?::jsonb))
        AND to_tsvector('english', c.content) @@ q.query
      ORDER BY score DESC, c.chunk_id ASC
      LIMIT ?
      """;

  @Override
  public List<SearchHit> search(CallerContext caller, String query, int limit) {
    return jdbc.query(
        connection -> {
          var statement = connection.prepareStatement(SQL);
          statement.setQueryTimeout(5);
          statement.setString(1, query);
          statement.setString(2, caller.tenantId());
          statement.setString(3, json.writeValueAsString(caller.groups()));
          statement.setInt(4, limit);
          return statement;
        },
        (row, index) ->
            new SearchHit(
                row.getString("chunk_id"),
                row.getObject("document_id", UUID.class),
                row.getString("external_id"),
                row.getString("content"),
                row.getDouble("score"),
                readCitation(row.getString("citation"))));
  }

  private Citation readCitation(String stored) {
    try {
      return json.readValue(stored, Citation.class);
    } catch (tools.jackson.core.JacksonException exception) {
      throw new org.springframework.dao.DataRetrievalFailureException(
          "Stored citation unavailable");
    }
  }
}
