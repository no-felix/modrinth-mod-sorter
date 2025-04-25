package de.nofelix.ModManager;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;

/**
 * Handles mod list processing and enrichment with Modrinth API data.
 * Part of the Modrinth Mod Sorter application.
 * Version 1.2
 */
public class ModListManager {
    private final ModrinthApiClient apiClient = new ModrinthApiClient();

    /**
     * Enriches a list of mod entries with Modrinth API data.
     * @param rows Raw CSV data (first row is header)
     * @return List of enriched ModInfo objects
     */
    public List<ModInfo> enrichModList(List<String[]> rows) throws Exception {
        List<CompletableFuture<ModInfo>> futures = new ArrayList<>();
        boolean isHeader = true;
        for (var cols : rows) {
            if (isHeader) { isHeader = false; continue; }
            var filename = cols.length > 0 ? cols[0] : "";
            var slug = cols.length > 1 ? cols[1] : "";
            futures.add(apiClient.fetchModInfoAsync(filename, slug));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<ModInfo> result = new ArrayList<>();
        for (var future : futures) {
            result.add(future.get());
        }
        return result;
    }

    /**
     * Prepares server and client mod directories based on compatibility flags.
     * @param mods List of mods to process
     * @param config Application configuration
     */
    public void prepareServerAndClientMods(List<ModInfo> mods, Properties config) throws IOException {
        String modsDir = PathUtils.normalize(config.getProperty("mods.dir"));
        String serverOutputDir = PathUtils.normalize(config.getProperty("server.output.dir"));
        String clientOutputDir = PathUtils.normalize(config.getProperty("client.output.dir"));
        String serverBackupDir = PathUtils.normalize(config.getProperty("server.backup.dir"));
        String clientBackupDir = PathUtils.normalize(config.getProperty("client.backup.dir"));

        ModFileManager.backupAndPrepareDirectory(serverOutputDir, serverBackupDir);
        ModFileManager.backupAndPrepareDirectory(clientOutputDir, clientBackupDir);

        Set<String> serverAllowed = new HashSet<>();
        Set<String> clientAllowed = new HashSet<>();
        for (ModInfo mod : mods) {
            String filename = mod.filename();
            String serverSide = mod.serverSide().toLowerCase();
            String clientSide = mod.clientSide().toLowerCase();
            if ("required".equals(serverSide) || "optional".equals(serverSide)) {
                serverAllowed.add(filename);
            }
            if ("required".equals(clientSide) || "optional".equals(clientSide)) {
                clientAllowed.add(filename);
            }
            if ("not_found".equals(serverSide) || "not_found".equals(clientSide)) {
                serverAllowed.add(filename);
                clientAllowed.add(filename);
            }
        }
        ModFileManager.copyModFiles(serverAllowed, modsDir, serverOutputDir);
        ModFileManager.copyModFiles(clientAllowed, modsDir, clientOutputDir);
    }
}