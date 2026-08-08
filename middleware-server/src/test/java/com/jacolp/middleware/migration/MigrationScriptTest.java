package com.jacolp.middleware.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationScriptTest {

    private static final Path MIGRATION_DIRECTORY = locateMigrationDirectory();

    @Test
    void audioSizeUpgradeShouldBeGuardedWhenBootstrapAlreadyContainsColumn()
            throws IOException {
        String bootstrap = readMigration("20260519_audio_tasks.sql");
        String refactorMigration = readMigration("20260730_refactor_mq.sql");

        assertThat(bootstrap).containsPattern(
                "`audio_size`\\s+bigint\\s+DEFAULT NULL");
        assertThat(refactorMigration)
                .contains("FROM information_schema.columns")
                .contains("table_schema = DATABASE()")
                .contains("table_name = 'audio_tasks'")
                .contains("column_name = 'audio_size'")
                .contains("@audio_size_column_exists = 0")
                .contains("PREPARE audio_size_upgrade FROM @audio_size_upgrade_sql")
                .contains("EXECUTE audio_size_upgrade")
                .contains("DEALLOCATE PREPARE audio_size_upgrade");
        assertThat(refactorMigration.indexOf("SET @audio_size_column_exists"))
                .isLessThan(refactorMigration.indexOf("ADD COLUMN `audio_size`"));
    }

    private static String readMigration(String fileName) throws IOException {
        Path migration = MIGRATION_DIRECTORY.resolve(fileName);
        assertThat(migration)
                .as("migration must be resolved from the Maven reactor root")
                .isRegularFile();
        return Files.readString(migration);
    }

    private static Path locateMigrationDirectory() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(Path.of(
                    "static", "database", "migrations"));
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("static/database/migrations directory not found");
    }
}
