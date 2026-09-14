package com.agenticknowledgehub.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Compares the existing public schema to V1 in a rollback-only scratch schema before adoption. */
public final class LegacySchemaVerifier {
  private LegacySchemaVerifier() {}

  public static void verify(DataSource dataSource) {
    String scratch = "akh_verify_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (var statement = connection.createStatement()) {
          // Both extensions must already exist in the legacy database. Never install while
          // verifying.
          var result =
              statement.executeQuery(
                  "SELECT count(*) FROM pg_extension WHERE extname IN ('vector', 'pgcrypto')");
          result.next();
          if (result.getInt(1) != 2) {
            throw new IllegalStateException("Legacy extensions do not match V1");
          }
          statement.execute("CREATE SCHEMA " + scratch);
          statement.execute("SET LOCAL search_path TO " + scratch + ", public");
        }
        // IF NOT EXISTS extension declarations are no-ops: their existence was checked above.
        ScriptUtils.executeSqlScript(
            connection, new ClassPathResource("db/migration/V1__initial_schema.sql"));
        try (var statement = connection.createStatement()) {
          statement.execute("SET LOCAL search_path TO public");
        }
        if (!snapshot(connection, "public").equals(snapshot(connection, scratch))) {
          throw new IllegalStateException(
              "Legacy schema differs from V1; reconcile before baselining");
        }
      } finally {
        connection.rollback(); // Includes the scratch schema and all its objects, even on mismatch.
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Unable to verify legacy schema (SQLSTATE " + exception.getSQLState() + ")");
    }
  }

  private static List<String> snapshot(Connection connection, String schema) throws SQLException {
    List<String> snapshot = new ArrayList<>();
    String[] queries = {
      """
      SELECT 'relation:' || c.relname || ':' || c.relkind::text || ':' || c.relrowsecurity
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = ? AND c.relkind IN ('r','p','v','m','S','f') ORDER BY c.relname
      """,
      """
      SELECT 'column:' || c.relname || ':' || a.attname || ':' || format_type(a.atttypid, a.atttypmod)
        || ':' || a.attnotnull || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
      FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
      WHERE n.nspname = ? AND c.relkind IN ('r','p') AND a.attnum > 0 AND NOT a.attisdropped
      ORDER BY c.relname, a.attnum
      """,
      """
      SELECT 'constraint:' || c.relname || ':' || k.conname || ':' || pg_get_constraintdef(k.oid)
      FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? ORDER BY c.relname, k.conname
      """,
      """
      SELECT 'index:' || indexname || ':' || indexdef FROM pg_indexes WHERE schemaname = ? ORDER BY indexname
      """,
      """
      SELECT 'trigger:' || pg_get_triggerdef(t.oid) FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = ? AND NOT t.tgisinternal ORDER BY c.relname, t.tgname
      """
    };
    for (String sql : queries) {
      try (var statement = connection.prepareStatement(sql)) {
        statement.setString(1, schema);
        try (var rows = statement.executeQuery()) {
          while (rows.next()) {
            snapshot.add(rows.getString(1).replace(schema + ".", ""));
          }
        }
      }
    }
    return snapshot;
  }
}
