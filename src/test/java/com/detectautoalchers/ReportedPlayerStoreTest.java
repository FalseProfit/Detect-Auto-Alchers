package com.detectautoalchers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReportedPlayerStoreTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsReportedPlayersFromCsv() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        Files.write(path, List.of(
            "normalized_name,display_name,date_reported",
            "alch bot,Alch Bot,2026-04-26T14:35:22Z"
        ), StandardCharsets.UTF_8);

        ReportedPlayerStore store = new ReportedPlayerStore(path);
        store.load();

        assertTrue(store.contains("Alch Bot"));
        assertEquals(1, store.getNormalizedNames().size());
        ReportedPlayer reportedPlayer = store.getReportedPlayers().iterator().next();
        assertEquals("alch bot", reportedPlayer.getNormalizedName());
        assertEquals("Alch Bot", reportedPlayer.getDisplayName());
        assertEquals(Instant.parse("2026-04-26T14:35:22Z"), reportedPlayer.getDateReported());
    }

    @Test
    public void recordsAndEscapesCsvValues() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("quote, bot", "Quote, \"Bot\"", Instant.parse("2026-04-26T14:35:22Z"));
        store.load();

        assertTrue(store.contains("quote, bot"));
        String csv = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"quote, bot\",\"Quote, \"\"Bot\"\"\",2026-04-26T14:35:22Z"));
    }

    @Test
    public void duplicateReportsUpdateExistingRow() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("dupe bot", "Dupe Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.record("Dupe Bot", "Dupe Bot", Instant.parse("2026-04-27T14:35:22Z"));

        assertEquals(1, store.getNormalizedNames().size());
        ReportedPlayer reportedPlayer = store.getReportedPlayers().iterator().next();
        assertEquals(Instant.parse("2026-04-27T14:35:22Z"), reportedPlayer.getDateReported());
    }

    @Test
    public void clearEmptiesMemoryAndFile() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("clear bot", "Clear Bot", Instant.parse("2026-04-26T14:35:22Z"));
        assertTrue(store.contains("clear bot"));

        store.clear();

        assertFalse(store.contains("clear bot"));
        assertEquals("normalized_name,display_name,date_reported", Files.readString(path, StandardCharsets.UTF_8).trim());
    }

    @Test
    public void importsReportedPlayersByMerging() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        Path importPath = temporaryFolder.newFolder().toPath().resolve("import.csv");
        Files.write(importPath, List.of(
            "normalized_name,display_name,date_reported",
            "import bot,Import Bot,2026-04-27T14:35:22Z"
        ), StandardCharsets.UTF_8);
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("local bot", "Local Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.importFrom(importPath, ImportMode.MERGE);

        assertTrue(store.contains("Local Bot"));
        assertTrue(store.contains("Import Bot"));
        assertEquals(2, store.getNormalizedNames().size());
    }

    @Test
    public void importsReportedPlayersByReplacing() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        Path importPath = temporaryFolder.newFolder().toPath().resolve("import.csv");
        Files.write(importPath, List.of(
            "normalized_name,display_name,date_reported",
            "import bot,Import Bot,2026-04-27T14:35:22Z"
        ), StandardCharsets.UTF_8);
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("local bot", "Local Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.importFrom(importPath, ImportMode.REPLACE);

        assertFalse(store.contains("Local Bot"));
        assertTrue(store.contains("Import Bot"));
        assertEquals(1, store.getNormalizedNames().size());
    }

    @Test
    public void exportsReportedPlayers() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        Path exportPath = temporaryFolder.newFolder().toPath().resolve("export.csv");
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("export bot", "Export Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.exportTo(exportPath);

        String csv = Files.readString(exportPath, StandardCharsets.UTF_8);
        assertTrue(csv.contains("export bot,Export Bot,2026-04-26T14:35:22Z"));
    }

    @Test
    public void removesSingleEntry() throws Exception
    {
        Path path = temporaryFolder.newFolder().toPath().resolve("reported-players.csv");
        ReportedPlayerStore store = new ReportedPlayerStore(path);

        store.record("keep bot", "Keep Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.record("remove bot", "Remove Bot", Instant.parse("2026-04-26T14:35:22Z"));
        store.remove("Remove Bot");

        assertTrue(store.contains("Keep Bot"));
        assertFalse(store.contains("Remove Bot"));
    }
}
