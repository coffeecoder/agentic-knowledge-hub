package com.agenticknowledgehub.config;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.SourceScope;
import com.agenticknowledgehub.parsers.*;
import com.agenticknowledgehub.persistence.JdbcDocumentRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class IngestionConfiguration {
  @Bean
  ParserRegistry parserRegistry() {
    return new ParserRegistry(List.of(new PdfParser(), new DocxParser(), new TextParser()));
  }

  @Bean
  SemanticChunker semanticChunker(
      @Value("${akh.chunking.max-words}") int maxWords,
      @Value("${akh.chunking.overlap-words}") int overlapWords,
      @Value("${akh.processing-revision}") String revision) {
    return new SemanticChunker(maxWords, overlapWords, revision);
  }

  @Bean
  IngestionService ingestionService(
      ParserRegistry parsers,
      SemanticChunker chunker,
      TransactionalDocumentWriter writer,
      SourceScope scope,
      com.agenticknowledgehub.embeddings.EmbeddingProvider embeddings,
      @Value("${akh.embedding.max-chunks:16}") int maxChunks) {
    return new IngestionService(parsers, chunker, writer, scope, embeddings, maxChunks);
  }

  @Bean
  SourceScope sourceScope(
      @Value("${akh.source.tenant}") String tenant, @Value("${akh.source.name}") String name) {
    return new SourceScope(tenant, name);
  }

  @Bean
  DocumentRepository documentRepository(JdbcTemplate jdbc, JsonMapper json) {
    return new JdbcDocumentRepository(jdbc, json);
  }

  @Bean
  TransactionalDocumentWriter documentWriter(DocumentRepository repository) {
    return new TransactionalDocumentWriter(repository);
  }
}
