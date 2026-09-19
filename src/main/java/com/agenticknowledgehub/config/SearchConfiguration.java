package com.agenticknowledgehub.config;

import com.agenticknowledgehub.embeddings.EmbeddingProvider;
import com.agenticknowledgehub.persistence.JdbcChunkSearchRepository;
import com.agenticknowledgehub.persistence.JdbcVectorSearchRepository;
import com.agenticknowledgehub.retrieval.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class SearchConfiguration {
  @Bean
  ChunkSearchRepository chunkSearchRepository(JdbcTemplate jdbc, JsonMapper json) {
    return new JdbcChunkSearchRepository(jdbc, json);
  }

  @Bean
  VectorSearchRepository vectorSearchRepository(JdbcTemplate jdbc, JsonMapper json) {
    return new JdbcVectorSearchRepository(jdbc, json);
  }

  @Bean
  RetrievalService retrievalService(
      ChunkSearchRepository repository,
      VectorSearchRepository vectors,
      EmbeddingProvider embeddings) {
    return new RetrievalService(repository, vectors, embeddings);
  }
}
