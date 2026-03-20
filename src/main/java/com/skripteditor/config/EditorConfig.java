package com.skripteditor.config;

import com.skripteditor.SkriptEditorClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-side configuration for the Skript Editor mod.
 * Stored as a .properties file in the Fabric config directory.
 *
 * Config file: config/skripteditor.properties
 */
public final class EditorConfig {

    private static final EditorConfig INSTANCE = new EditorConfig();

    private static final String CONFIG_FILE = "skripteditor.properties";

    // --- Defaults ---
    private static final int DEFAULT_TAB_WIDTH = 4;
    private static final boolean DEFAULT_AUTO_INDENT = true;
    private static final boolean DEFAULT_SHOW_LINE_NUMBERS = true;
    private static final int DEFAULT_FONT_SCALE = 100; // percentage
    private static final boolean DEFAULT_AUTO_SAVE_DRAFTS = true;
    private static final int DEFAULT_AUTO_SAVE_INTERVAL_SEC = 30;
    private static final int DEFAULT_MAX_UNDO_HISTORY = 200;

    // --- Config values ---
    private int tabWidth = DEFAULT_TAB_WIDTH;
    private boolean autoIndent = DEFAULT_AUTO_INDENT;
    private boolean showLineNumbers = DEFAULT_SHOW_LINE_NUMBERS;
    private int fontScale = DEFAULT_FONT_SCALE;
    private boolean autoSaveDrafts = DEFAULT_AUTO_SAVE_DRAFTS;
    private int autoSaveIntervalSec = DEFAULT_AUTO_SAVE_INTERVAL_SEC;
    private int maxUndoHistory = DEFAULT_MAX_UNDO_HISTORY;

    private EditorConfig() {}

    public static EditorConfig getInstance() {
        return INSTANCE;
    }

    /** Loads config from disk. Call during mod init. */
    public void load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            save(); // Create default config
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            SkriptEditorClient.LOGGER.warn("Failed to load config, using defaults: {}", e.getMessage());
            return;
        }

        tabWidth = getInt(props, "tab_width", DEFAULT_TAB_WIDTH, 1, 8);
        autoIndent = getBool(props, "auto_indent", DEFAULT_AUTO_INDENT);
        showLineNumbers = getBool(props, "show_line_numbers", DEFAULT_SHOW_LINE_NUMBERS);
        fontScale = getInt(props, "font_scale", DEFAULT_FONT_SCALE, 50, 200);
        autoSaveDrafts = getBool(props, "auto_save_drafts", DEFAULT_AUTO_SAVE_DRAFTS);
        autoSaveIntervalSec = getInt(props, "auto_save_interval_sec", DEFAULT_AUTO_SAVE_INTERVAL_SEC, 5, 300);
        maxUndoHistory = getInt(props, "max_undo_history", DEFAULT_MAX_UNDO_HISTORY, 10, 1000);

        SkriptEditorClient.LOGGER.info("Config loaded from {}", configPath);
    }

    /** Saves current config to disk. */
    public void save() {
        Path configPath = getConfigPath();
        Properties props = new Properties();
        props.setProperty("tab_width", String.valueOf(tabWidth));
        props.setProperty("auto_indent", String.valueOf(autoIndent));
        props.setProperty("show_line_numbers", String.valueOf(showLineNumbers));
        props.setProperty("font_scale", String.valueOf(fontScale));
        props.setProperty("auto_save_drafts", String.valueOf(autoSaveDrafts));
        props.setProperty("auto_save_interval_sec", String.valueOf(autoSaveIntervalSec));
        props.setProperty("max_undo_history", String.valueOf(maxUndoHistory));

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Skript Editor Client Configuration");
            }
        } catch (IOException e) {
            SkriptEditorClient.LOGGER.warn("Failed to save config: {}", e.getMessage());
        }
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    private int getInt(Properties props, String key, int defaultVal, int min, int max) {
        try {
            int val = Integer.parseInt(props.getProperty(key, String.valueOf(defaultVal)));
            return Math.max(min, Math.min(max, val));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private boolean getBool(Properties props, String key, boolean defaultVal) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultVal)));
    }

    // --- Accessors ---
    public int getTabWidth() { return tabWidth; }
    public boolean isAutoIndent() { return autoIndent; }
    public boolean isShowLineNumbers() { return showLineNumbers; }
    public int getFontScale() { return fontScale; }
    public boolean isAutoSaveDrafts() { return autoSaveDrafts; }
    public int getAutoSaveIntervalSec() { return autoSaveIntervalSec; }
    public int getMaxUndoHistory() { return maxUndoHistory; }

    /** Returns the path to the drafts directory for auto-save recovery. */
    public Path getDraftsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("skripteditor-drafts");
    }
}
