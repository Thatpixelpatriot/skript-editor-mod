package com.skripteditor.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter for Skript (.sk) files.
 * Tokenizes each line into colored segments for rendering.
 *
 * Color scheme uses a Catppuccin Mocha-inspired dark palette.
 */
public class SkriptSyntaxHighlighter {

    // --- Color constants (ARGB) ---
    public static final int COLOR_DEFAULT     = 0xFFCDD6F4; // Light text
    public static final int COLOR_COMMENT     = 0xFF6C7086; // Muted gray
    public static final int COLOR_STRING      = 0xFFF9E2AF; // Warm yellow
    public static final int COLOR_KEYWORD     = 0xFFCBA6F7; // Purple
    public static final int COLOR_VARIABLE    = 0xFFF5C2E7; // Pink
    public static final int COLOR_COMMAND     = 0xFFFAB387; // Orange
    public static final int COLOR_EVENT       = 0xFF89DCEB; // Cyan
    public static final int COLOR_FUNCTION    = 0xFF89B4FA; // Blue
    public static final int COLOR_NUMBER      = 0xFF94E2D5; // Teal
    public static final int COLOR_OPTION      = 0xFFF5C2E7; // Pink (same as variable)
    public static final int COLOR_BOOLEAN     = 0xFFFAB387; // Orange
    public static final int COLOR_TYPE        = 0xFF89DCEB; // Cyan
    public static final int COLOR_SECTION_KEY = 0xFFF9E2AF; // Yellow for trigger/options/etc.

    /** A single colored segment of text within a line. */
    public record Segment(String text, int color) {}

    // Skript keywords that appear at the start of effects/conditions
    private static final Set<String> KEYWORDS = Set.of(
        "if", "else", "else if", "loop", "while", "set", "add", "remove",
        "delete", "send", "broadcast", "execute", "make", "spawn", "kill",
        "teleport", "cancel", "stop", "return", "continue", "exit",
        "wait", "give", "take", "clear", "equip", "apply", "log",
        "charge", "damage", "heal", "feed", "ban", "unban", "kick",
        "op", "deop", "enchant", "push", "pull", "strike"
    );

    // Skript section keywords
    private static final Set<String> SECTION_KEYS = Set.of(
        "command", "trigger", "on", "every", "options", "variables",
        "aliases", "function", "effect", "condition", "expression",
        "permission", "permission message", "description", "usage",
        "cooldown", "executable by"
    );

    // Patterns for matching
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("%[^%]*%");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("\\b(true|false|yes|no)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Highlights a single line of Skript code into colored segments.
     * Processes in priority order: comments > strings > variables > expressions > keywords.
     */
    public List<Segment> highlight(String line) {
        List<Segment> segments = new ArrayList<>();
        if (line.isEmpty()) {
            segments.add(new Segment("", COLOR_DEFAULT));
            return segments;
        }

        // Track which characters have been colorized
        int[] colors = new int[line.length()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = COLOR_DEFAULT;
        }

        String trimmed = line.stripLeading();

        // 1. Comments - entire line from # onward (outside strings)
        applyComments(line, colors);

        // 2. Strings - text within quotes
        applyStrings(line, colors);

        // 3. Variables {var} and options {@option}
        applyPattern(line, colors, VARIABLE_PATTERN, COLOR_VARIABLE);

        // 4. Expressions %expr%
        applyPattern(line, colors, EXPRESSION_PATTERN, COLOR_VARIABLE);

        // 5. Section keywords at line start (command, on, trigger, function, etc.)
        applySectionKeys(line, trimmed, colors);

        // 6. Keywords at word boundaries
        applyKeywords(line, colors);

        // 7. Booleans
        applyPattern(line, colors, BOOLEAN_PATTERN, COLOR_BOOLEAN);

        // 8. Numbers
        applyNumbers(line, colors);

        // Build segments from the color array
        return buildSegments(line, colors);
    }

    private void applyComments(String line, int[] colors) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (c == '#' && !inString) {
                for (int j = i; j < colors.length; j++) {
                    colors[j] = COLOR_COMMENT;
                }
                return;
            }
        }
    }

    private void applyStrings(String line, int[] colors) {
        boolean inString = false;
        int stringStart = -1;
        for (int i = 0; i < line.length(); i++) {
            if (colors[i] == COLOR_COMMENT) break; // Don't colorize inside comments

            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringStart = i;
                } else {
                    // Color the entire string including quotes
                    for (int j = stringStart; j <= i; j++) {
                        colors[j] = COLOR_STRING;
                    }
                    inString = false;
                }
            }
        }
        // Unterminated string - color to end of line
        if (inString && stringStart >= 0) {
            for (int j = stringStart; j < colors.length; j++) {
                if (colors[j] != COLOR_COMMENT) {
                    colors[j] = COLOR_STRING;
                }
            }
        }
    }

    private void applyPattern(String line, int[] colors, Pattern pattern, int color) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            boolean inComment = false;
            for (int j = m.start(); j < m.end(); j++) {
                if (colors[j] == COLOR_COMMENT || colors[j] == COLOR_STRING) {
                    inComment = true;
                    break;
                }
            }
            if (!inComment) {
                for (int j = m.start(); j < m.end(); j++) {
                    colors[j] = color;
                }
            }
        }
    }

    private void applySectionKeys(String line, String trimmed, int[] colors) {
        int indent = line.length() - trimmed.length();
        String lower = trimmed.toLowerCase();

        // Check for command definitions
        if (lower.startsWith("command ") || lower.startsWith("command\t")) {
            applyColorRange(colors, indent, indent + 7, COLOR_COMMAND);
            return;
        }

        // Check for function definitions
        if (lower.startsWith("function ")) {
            applyColorRange(colors, indent, indent + 8, COLOR_FUNCTION);
            return;
        }

        // Check for event handlers (on ...)
        if (lower.startsWith("on ")) {
            applyColorRange(colors, indent, indent + 2, COLOR_EVENT);
            // Color the event name up to the colon
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                applyColorRange(colors, indent + 3, indent + colonIdx, COLOR_EVENT);
            }
            return;
        }

        // Check for every (periodic events)
        if (lower.startsWith("every ")) {
            applyColorRange(colors, indent, indent + 5, COLOR_EVENT);
            return;
        }

        // Check for other section keys
        for (String key : SECTION_KEYS) {
            if (lower.startsWith(key + ":") || lower.startsWith(key + " ")) {
                int end = Math.min(indent + key.length(), colors.length);
                applyColorRange(colors, indent, end, COLOR_SECTION_KEY);
                return;
            }
        }
    }

    private void applyKeywords(String line, int[] colors) {
        String lower = line.toLowerCase();
        for (String keyword : KEYWORDS) {
            int idx = 0;
            while ((idx = lower.indexOf(keyword, idx)) >= 0) {
                int end = idx + keyword.length();
                // Check word boundaries
                boolean startOk = idx == 0 || !Character.isLetterOrDigit(line.charAt(idx - 1));
                boolean endOk = end >= line.length() || !Character.isLetterOrDigit(line.charAt(end));

                if (startOk && endOk) {
                    // Only colorize if not already colored by higher-priority rules
                    boolean alreadyColored = false;
                    for (int j = idx; j < end; j++) {
                        if (colors[j] != COLOR_DEFAULT) {
                            alreadyColored = true;
                            break;
                        }
                    }
                    if (!alreadyColored) {
                        applyColorRange(colors, idx, end, COLOR_KEYWORD);
                    }
                }
                idx = end;
            }
        }
    }

    private void applyNumbers(String line, int[] colors) {
        Matcher m = NUMBER_PATTERN.matcher(line);
        while (m.find()) {
            boolean skip = false;
            for (int j = m.start(); j < m.end(); j++) {
                if (colors[j] != COLOR_DEFAULT) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                for (int j = m.start(); j < m.end(); j++) {
                    colors[j] = COLOR_NUMBER;
                }
            }
        }
    }

    private void applyColorRange(int[] colors, int start, int end, int color) {
        for (int i = Math.max(0, start); i < Math.min(end, colors.length); i++) {
            if (colors[i] == COLOR_DEFAULT) {
                colors[i] = color;
            }
        }
    }

    /** Builds a list of contiguous same-color segments from the color array. */
    private List<Segment> buildSegments(String line, int[] colors) {
        List<Segment> segments = new ArrayList<>();
        if (line.isEmpty()) {
            return segments;
        }

        int segStart = 0;
        int currentColor = colors[0];

        for (int i = 1; i < line.length(); i++) {
            if (colors[i] != currentColor) {
                segments.add(new Segment(line.substring(segStart, i), currentColor));
                segStart = i;
                currentColor = colors[i];
            }
        }
        segments.add(new Segment(line.substring(segStart), currentColor));
        return segments;
    }
}
