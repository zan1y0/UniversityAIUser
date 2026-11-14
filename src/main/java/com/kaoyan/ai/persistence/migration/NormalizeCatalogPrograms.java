package com.kaoyan.ai.persistence.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve the imported directory and put only Program fields in the queryable aggregate. */
public abstract class NormalizeCatalogPrograms extends BaseJavaMigration {
  private static final List<String> PROGRAM_FIELDS =
      List.of("id", "schoolName", "programName", "major", "region", "year", "difficulty",
          "scoreLine", "enrollment", "admissionRatio", "sources", "snapshotId");

  private record Change(String id, String stored, String original, String normalized) {}

  @Override
  public void migrate(Context context) throws Exception {
    Connection db = context.getConnection();
    boolean h2 = db.getMetaData().getDatabaseProductName().equals("H2");
    ObjectMapper json = new ObjectMapper();
    List<Change> changes = new ArrayList<>();
    try (var statement = db.prepareStatement("SELECT id, payload FROM programs WHERE owner_id = 'public'");
        var rows = statement.executeQuery()) {
      while (rows.next()) {
        String stored = rows.getString("payload");
        if (!stored.contains("\"systemTier\":")) continue;
        // The older import SQL doubled backslashes for MySQL. H2 stored them literally.
        String original = h2 ? stored.replace("\\\\", "\\") : stored;
        JsonNode program = json.readTree(original);
        if (program.path("year").asInt() != 2026 || !program.has("systemTier")) continue;

        String id = rows.getString("id");
        ObjectNode normalized = json.createObjectNode();
        for (String field : PROGRAM_FIELDS) {
          if (!program.has(field)) throw new SQLException("Missing Program field " + field + " for " + id);
          normalized.set(field, program.get(field));
        }
        normalized.put("difficulty", switch (program.path("difficulty").asText()) {
          case "top", "high", "hard" -> "hard";
          case "medium" -> "medium";
          case "low", "easy" -> "easy";
          default -> throw new SQLException("Unknown Program difficulty for " + id);
        });
        changes.add(new Change(id, stored, original, normalized.toString()));
      }
    }

    try (var ddl = db.createStatement()) {
      ddl.execute("""
          CREATE TABLE IF NOT EXISTS program_catalog_archive (
            program_id VARCHAR(100) PRIMARY KEY,
            original_payload LONGTEXT NOT NULL
          )
          """);
    }
    // MySQL commits DDL implicitly; keep the archive inserts and program updates in one transaction.
    boolean ownTransaction = db.getAutoCommit();
    if (ownTransaction) db.setAutoCommit(false);
    try (var archive = db.prepareStatement(
            "INSERT INTO program_catalog_archive (program_id, original_payload) VALUES (?, ?)");
        var update = db.prepareStatement(
            "UPDATE programs SET payload = ? WHERE id = ? AND owner_id = 'public' AND payload = ?")) {
      for (Change change : changes) {
        archive.setString(1, change.id());
        archive.setString(2, change.original());
        archive.executeUpdate();
        update.setString(1, change.normalized());
        update.setString(2, change.id());
        update.setString(3, change.stored());
        if (update.executeUpdate() != 1) throw new SQLException("Program changed during migration: " + change.id());
      }
      if (ownTransaction) db.commit();
    } catch (SQLException ex) {
      if (ownTransaction) db.rollback();
      throw ex;
    } finally {
      if (ownTransaction) db.setAutoCommit(true);
    }
  }
}
