package com.agenticknowledgehub.config;

import com.agenticknowledgehub.persistence.LegacySchemaVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfiguration {
  @Bean
  public FlywayMigrationStrategy migrationStrategy(
      @Value("${akh.database.adopt-legacy:false}") boolean adoptLegacy) {
    return flyway -> {
      if (adoptLegacy && flyway.info().applied().length == 0) {
        LegacySchemaVerifier.verify(flyway.getConfiguration().getDataSource());
        flyway.baseline(); // Explicitly requested adoption; baseline version defaults to 1.
      }
      flyway.migrate();
    };
  }
}
