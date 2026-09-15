package com.agenticknowledgehub.config;

import com.agenticknowledgehub.persistence.JdbcChunkSearchRepository;
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
  RetrievalService retrievalService(ChunkSearchRepository repository) {
    return new RetrievalService(repository);
  }
}
