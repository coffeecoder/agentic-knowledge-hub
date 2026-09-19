package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.config.DatabaseMigrationConfiguration;
import com.agenticknowledgehub.persistence.LegacySchemaVerifier;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MigrationIntegrationTest extends PostgresTestSupport {
  @Test
  void freshDatabaseMigratesAndRerunsCleanly() throws Exception {
    var ds = database();
    var flyway = flyway(ds);
    flyway.migrate();
    assertEquals("3", flyway.info().current().getVersion().toString());
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }

  @Test
  void explicitlyAdoptsLegacySchemaAndPreservesRows() throws Exception {
    var ds = legacy();
    var jdbc = new JdbcTemplate(ds);
    jdbc.update(
        "INSERT INTO sources(source_id,source_type,name,tenant_id) VALUES ('00000000-0000-0000-0000-000000000001','TEXT','manual','local')");
    jdbc.update(
        "INSERT INTO documents(source_id,external_id,title,mime_type,source_version,content_hash) VALUES ('00000000-0000-0000-0000-000000000001','doc','Doc','text/plain','v1',repeat('a',64))");
    jdbc.update(
        "INSERT INTO chunks(chunk_id,document_id,ordinal,content,content_hash,token_count,citation) SELECT 'legacy-chunk',document_id,0,'synthetic evidence',repeat('b',64),2,'{}'::jsonb FROM documents");
    new DatabaseMigrationConfiguration().migrationStrategy(true).migrate(flyway(ds));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM documents", Integer.class));
    assertEquals(
        "synthetic evidence", jdbc.queryForObject("SELECT content FROM chunks", String.class));
    assertNull(jdbc.queryForObject("SELECT word_count FROM chunks", Integer.class));
    assertEquals(
        "legacy", jdbc.queryForObject("SELECT processing_version FROM documents", String.class));
    assertEquals(0, scratchCount(jdbc));
  }

  @Test
  void refusesAutomaticAdoptionAndSchemaDrift() throws Exception {
    var ds = legacy();
    assertThrows(FlywayException.class, () -> flyway(ds).migrate());
    new JdbcTemplate(ds).execute("ALTER TABLE documents ADD COLUMN unexpected TEXT");
    assertThrows(IllegalStateException.class, () -> LegacySchemaVerifier.verify(ds));
    assertEquals(0, scratchCount(new JdbcTemplate(ds)));
  }

  @Test
  void duplicateVersionsAbortV2WithoutDeletingData() throws Exception {
    var ds = legacy();
    var jdbc = new JdbcTemplate(ds);
    jdbc.update(
        "INSERT INTO sources(source_id,source_type,name,tenant_id) VALUES ('00000000-0000-0000-0000-000000000001','TEXT','manual','local')");
    for (String version : new String[] {"v1", "v2"}) {
      jdbc.update(
          "INSERT INTO documents(source_id,external_id,title,mime_type,source_version,content_hash) VALUES ('00000000-0000-0000-0000-000000000001','doc','Doc','text/plain',?,repeat('a',64))",
          version);
    }
    assertThrows(
        FlywayException.class,
        () -> new DatabaseMigrationConfiguration().migrationStrategy(true).migrate(flyway(ds)));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM documents", Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name='documents' AND column_name='processing_version'",
            Integer.class));
  }

  private int scratchCount(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM pg_namespace WHERE nspname LIKE 'akh_verify_%'", Integer.class);
  }

  private DataSource legacy() throws Exception {
    var ds = database();
    try (var connection = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V1__initial_schema.sql"));
    }
    return ds;
  }

  private DataSource database() throws Exception {
    String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
            DriverManager.getConnection(url("akh_test"), "akh_test", "synthetic-test-password");
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    return new DriverManagerDataSource(url(name), "akh_test", "synthetic-test-password");
  }

  private Flyway flyway(DataSource ds) {
    return Flyway.configure().dataSource(ds).cleanDisabled(true).load();
  }
}
