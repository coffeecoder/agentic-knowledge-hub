package com.agenticknowledgehub.config;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.IngestionService;
import com.agenticknowledgehub.parsers.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IngestionConfiguration {
  @Bean
  ParserRegistry parserRegistry() {
    return new ParserRegistry(List.of(new PdfParser(), new DocxParser(), new TextParser()));
  }

  @Bean
  SemanticChunker semanticChunker(
      @Value("${akh.chunking.max-words}") int maxWords,
      @Value("${akh.chunking.overlap-words}") int overlapWords) {
    return new SemanticChunker(maxWords, overlapWords);
  }

  @Bean
  IngestionService ingestionService(ParserRegistry parsers, SemanticChunker chunker) {
    return new IngestionService(parsers, chunker);
  }
}
