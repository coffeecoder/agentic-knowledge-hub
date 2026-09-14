package com.agenticknowledgehub.persistence;

import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.*;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class JdbcDocumentRepository implements DocumentRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public JdbcDocumentRepository(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public UUID lockSource(SourceScope scope, SourceType type) {
    var proposed =
        stableUuid(
            json.writeValueAsString(
                new String[] {scope.tenantId(), type.name(), scope.sourceName()}));
    jdbc.update(
        """
        INSERT INTO sources(source_id, tenant_id, source_type, name) VALUES (?, ?, ?, ?)
        ON CONFLICT (tenant_id, source_type, name) DO NOTHING
        """,
        proposed,
        scope.tenantId(),
        type.name(),
        scope.sourceName());
    // This lock exists even on the first ingestion. READ COMMITTED sees the preceding writer's
    // commit.
    return jdbc.queryForObject(
        """
        SELECT source_id FROM sources WHERE tenant_id = ? AND source_type = ? AND name = ? FOR UPDATE
        """,
        UUID.class,
        scope.tenantId(),
        type.name(),
        scope.sourceName());
  }

  @Override
  public boolean isUnchanged(UUID sourceId, PreparedDocument prepared) {
    var doc = prepared.document();
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM documents WHERE source_id = ? AND external_id = ?
          AND content_hash = ? AND processing_version = ? AND title = ? AND mime_type = ?
          AND source_version = ? AND source_url IS NOT DISTINCT FROM ?
          AND to_jsonb(allowed_groups) = ?::jsonb AND metadata = ?::jsonb AND NOT is_deleted)
        """,
            Boolean.class,
            sourceId,
            doc.externalId(),
            prepared.contentHash(),
            prepared.processingVersion(),
            doc.title(),
            doc.mimeType(),
            doc.sourceVersion(),
            doc.sourceUrl(),
            json.writeValueAsString(groups(doc)),
            json.writeValueAsString(doc.metadata())));
  }

  @Override
  public UUID saveDocument(UUID sourceId, PreparedDocument prepared) {
    var doc = prepared.document();
    var proposed =
        stableUuid(json.writeValueAsString(new String[] {sourceId.toString(), doc.externalId()}));
    return jdbc.queryForObject(
        """
        INSERT INTO documents(document_id, source_id, external_id, title, mime_type, source_version,
          content_hash, source_url, allowed_groups, metadata, processing_version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), ?::jsonb, ?)
        ON CONFLICT (source_id, external_id) DO UPDATE SET title = EXCLUDED.title,
          mime_type = EXCLUDED.mime_type, source_version = EXCLUDED.source_version,
          content_hash = EXCLUDED.content_hash, source_url = EXCLUDED.source_url,
          allowed_groups = EXCLUDED.allowed_groups, metadata = EXCLUDED.metadata,
          processing_version = EXCLUDED.processing_version, is_deleted = FALSE, updated_at = now()
        RETURNING document_id
        """,
        UUID.class,
        proposed,
        sourceId,
        doc.externalId(),
        doc.title(),
        doc.mimeType(),
        doc.sourceVersion(),
        prepared.contentHash(),
        doc.sourceUrl(),
        json.writeValueAsString(groups(doc)),
        json.writeValueAsString(doc.metadata()),
        prepared.processingVersion());
  }

  @Override
  public void replaceChunks(UUID documentId, PreparedDocument prepared) {
    jdbc.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    jdbc.batchUpdate(
        """
        INSERT INTO chunks(chunk_id, document_id, ordinal, content, content_hash,
          heading_path, page_number, line_start, line_end, word_count, citation)
        VALUES (?, ?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(?::jsonb)), ?, ?, ?, ?, ?::jsonb)
        """,
        prepared.chunks(),
        100,
        (statement, chunk) -> {
          var citation = chunk.citation();
          statement.setString(1, chunk.chunkId());
          statement.setObject(2, documentId);
          statement.setInt(3, chunk.ordinal());
          statement.setString(4, chunk.content());
          statement.setString(5, chunk.contentHash());
          statement.setString(6, json.writeValueAsString(citation.headingPath()));
          statement.setObject(7, citation.pageNumber(), Types.INTEGER);
          statement.setObject(8, citation.lineStart(), Types.INTEGER);
          statement.setObject(9, citation.lineEnd(), Types.INTEGER);
          statement.setInt(10, chunk.wordCount());
          statement.setString(11, json.writeValueAsString(citation));
        });
  }

  private static java.util.List<String> groups(SourceDocument doc) {
    return doc.allowedGroups().stream().distinct().sorted().toList();
  }

  private static UUID stableUuid(String identity) {
    return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
  }
}
