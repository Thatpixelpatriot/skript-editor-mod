package com.skripteditor.editor;

import com.skripteditor.SkriptEditorClient;
import com.skripteditor.config.EditorConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages auto-save drafts for crash recovery.
 * Periodically writes unsaved tab contents to local files in the drafts directory.
 * On editor open, checks for recoverable drafts and offers restoration.
 *
 * Draft files are named by sanitizing the script path (e.g. "scripts_myscript.sk.draft").
 * Each draft file contains the unsaved content. A companion ".meta" file stores the original path.
 */
public final class DraftManager {

    private static final DraftManager INSTANCE = new DraftManager();
    private static final String DRAFT_EXT = ".draft";
    private static final String META_EXT = ".meta";

    private long lastSaveTime = 0;

    private DraftManager() {}

    public static DraftManager getInstance() {
        return INSTANCE;
    }

    /**
     * Called periodically (e.g. every frame or every few seconds).
     * Saves drafts for any modified tabs if enough time has elapsed.
     */
    public void tick(List<EditorTab> openTabs) {
        if (!EditorConfig.getInstance().isAutoSaveDrafts()) return;

        long now = System.currentTimeMillis();
        long intervalMs = EditorConfig.getInstance().getAutoSaveIntervalSec() * 1000L;
        if (now - lastSaveTime < intervalMs) return;
        lastSaveTime = now;

        saveDrafts(openTabs);
    }

    /** Saves drafts for all modified tabs. */
    public void saveDrafts(List<EditorTab> tabs) {
        Path draftsDir = EditorConfig.getInstance().getDraftsDir();
        try {
            Files.createDirectories(draftsDir);
        } catch (IOException e) {
            return; // Can't create dir, skip silently
        }

        for (EditorTab tab : tabs) {
            if (tab.isModified()) {
                String safeName = sanitizePath(tab.getFilePath());
                try {
                    Files.writeString(draftsDir.resolve(safeName + DRAFT_EXT),
                            tab.getCurrentContent(), StandardCharsets.UTF_8);
                    Files.writeString(draftsDir.resolve(safeName + META_EXT),
                            tab.getFilePath(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    SkriptEditorClient.LOGGER.debug("Failed to save draft for {}: {}",
                            tab.getFilePath(), e.getMessage());
                }
            }
        }
    }

    /** Clears the draft for a specific file (called after successful save). */
    public void clearDraft(String filePath) {
        Path draftsDir = EditorConfig.getInstance().getDraftsDir();
        String safeName = sanitizePath(filePath);
        try {
            Files.deleteIfExists(draftsDir.resolve(safeName + DRAFT_EXT));
            Files.deleteIfExists(draftsDir.resolve(safeName + META_EXT));
        } catch (IOException e) {
            // Ignore cleanup failures
        }
    }

    /** Clears all drafts (called when editor closes cleanly with no unsaved changes). */
    public void clearAllDrafts() {
        Path draftsDir = EditorConfig.getInstance().getDraftsDir();
        if (!Files.exists(draftsDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(draftsDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.endsWith(DRAFT_EXT) || name.endsWith(META_EXT)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /** A recoverable draft entry. */
    public record RecoverableDraft(String filePath, String content) {}

    /** Returns any drafts that can be recovered (from a prior crash). */
    public List<RecoverableDraft> getRecoverableDrafts() {
        List<RecoverableDraft> drafts = new ArrayList<>();
        Path draftsDir = EditorConfig.getInstance().getDraftsDir();
        if (!Files.exists(draftsDir)) return drafts;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(draftsDir, "*" + DRAFT_EXT)) {
            for (Path draftFile : stream) {
                String baseName = draftFile.getFileName().toString();
                baseName = baseName.substring(0, baseName.length() - DRAFT_EXT.length());
                Path metaFile = draftsDir.resolve(baseName + META_EXT);

                if (Files.exists(metaFile)) {
                    try {
                        String filePath = Files.readString(metaFile, StandardCharsets.UTF_8).trim();
                        String content = Files.readString(draftFile, StandardCharsets.UTF_8);
                        drafts.add(new RecoverableDraft(filePath, content));
                    } catch (IOException e) {
                        // Skip corrupt drafts
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        return drafts;
    }

    /** Converts a file path to a safe filename for the drafts directory. */
    private String sanitizePath(String path) {
        return path.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
