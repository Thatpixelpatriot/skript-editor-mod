package com.skripteditor.gui.widget;

import com.skripteditor.editor.EditorTab;
import com.skripteditor.editor.SkriptSyntaxHighlighter;
import com.skripteditor.editor.UndoManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main code editor widget. Handles multi-line text editing, rendering with
 * syntax highlighting, cursor movement, selection, scrolling, copy/paste, and undo/redo.
 *
 * Designed specifically for editing Skript (.sk) files with tab-based indentation,
 * but also supports .txt and .yml files.
 */
public class CodeEditorWidget {

    // Layout
    private int x, y, width, height;
    private static final int LINE_HEIGHT = 11;
    private static final int GUTTER_PADDING = 4;
    private static final int TEXT_PADDING = 6;
    private static final int TAB_DISPLAY_WIDTH = 4; // Tab renders as 4 spaces

    // Colors (Catppuccin Mocha-inspired dark theme)
    private static final int BG_COLOR          = 0xFF1E1E2E;
    private static final int GUTTER_BG         = 0xFF181825;
    private static final int GUTTER_TEXT       = 0xFF6C7086;
    private static final int CURRENT_LINE_BG   = 0xFF252536;
    private static final int SELECTION_COLOR    = 0x603A3A5C;
    private static final int CURSOR_COLOR      = 0xFFCDD6F4;
    private static final int SEARCH_HIGHLIGHT  = 0x40F9E2AF;

    // Editor state
    private final List<StringBuilder> lines = new ArrayList<>();
    private int cursorLine = 0;
    private int cursorCol = 0;
    private int selStartLine = -1, selStartCol = -1;
    private int selEndLine = -1, selEndCol = -1;
    private boolean selecting = false;
    private int scrollLine = 0;
    private int scrollX = 0;
    private boolean focused = true;
    private int gutterWidth = 40;

    // Search state
    private String searchQuery = "";
    private boolean searchCaseSensitive = false;
    private final List<int[]> searchMatches = new ArrayList<>(); // [line, startCol, endCol]
    private int currentSearchMatch = -1;

    // External references
    private EditorTab activeTab;
    private final SkriptSyntaxHighlighter highlighter = new SkriptSyntaxHighlighter();
    private Runnable onContentChanged;

    // Caches
    private boolean textDirty = true;
    private String cachedText = "";
    private final java.util.Map<String, List<SkriptSyntaxHighlighter.Segment>> highlightCache = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, List<SkriptSyntaxHighlighter.Segment>> eldest) {
            return size() > 500;
        }
    };

    public CodeEditorWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        lines.add(new StringBuilder());
    }

    // --- Configuration ---
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isFocused() { return focused; }
    public void setOnContentChanged(Runnable callback) { this.onContentChanged = callback; }

    // --- Tab binding ---

    /** Binds this widget to an EditorTab, loading its content and cursor state. */
    public void bindTab(EditorTab tab) {
        // Save current state to previous tab
        if (activeTab != null) {
            activeTab.setCurrentContent(getText());
            activeTab.setCursor(cursorLine, cursorCol);
            activeTab.setScrollLine(scrollLine);
            activeTab.setScrollX(scrollX);
        }

        this.activeTab = tab;
        if (tab != null) {
            setText(tab.getCurrentContent());
            cursorLine = tab.getCursorLine();
            cursorCol = Math.min(tab.getCursorCol(), getLineLength(tab.getCursorLine()));
            scrollLine = tab.getScrollLine();
            scrollX = tab.getScrollX();
        } else {
            lines.clear();
            lines.add(new StringBuilder());
            cursorLine = 0;
            cursorCol = 0;
            scrollLine = 0;
            scrollX = 0;
        }
        clearSelection();
    }

    /** Saves the current widget state back to the bound tab. */
    public void syncToTab() {
        if (activeTab != null) {
            activeTab.setCurrentContent(getText());
            activeTab.setCursor(cursorLine, cursorCol);
            activeTab.setScrollLine(scrollLine);
            activeTab.setScrollX(scrollX);
        }
    }

    // --- Text access ---

    public String getText() {
        if (textDirty) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(lines.get(i));
            }
            cachedText = sb.toString();
            textDirty = false;
        }
        return cachedText;
    }

    public void setText(String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add(new StringBuilder());
            textDirty = true;
            return;
        }
        for (String line : text.split("\n", -1)) {
            lines.add(new StringBuilder(line));
        }
        if (lines.isEmpty()) lines.add(new StringBuilder());
        textDirty = true;
        clampCursor();
    }

    public int getLineCount() { return lines.size(); }
    public int getCursorLine() { return cursorLine; }
    public int getCursorCol() { return cursorCol; }

    private int getLineLength(int line) {
        if (line < 0 || line >= lines.size()) return 0;
        return lines.get(line).length();
    }

    // --- Rendering ---

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        // Update gutter width based on line count
        int digits = String.valueOf(lines.size()).length();
        gutterWidth = Math.max(30, digits * 6 + GUTTER_PADDING * 2 + 4);

        int editorX = x + gutterWidth;
        int editorWidth = width - gutterWidth;
        int visibleLines = height / LINE_HEIGHT;

        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);

        // Gutter background
        context.fill(x, y, editorX, y + height, GUTTER_BG);
        context.fill(editorX - 1, y, editorX, y + height, 0xFF313244); // Gutter border

        // Scissor to editor bounds
        context.enableScissor(x, y, x + width, y + height);

        // Render visible lines
        for (int i = 0; i <= visibleLines && scrollLine + i < lines.size(); i++) {
            int lineIdx = scrollLine + i;
            int lineY = y + i * LINE_HEIGHT;

            // Current line highlight
            if (lineIdx == cursorLine && focused) {
                context.fill(editorX, lineY, x + width, lineY + LINE_HEIGHT, CURRENT_LINE_BG);
            }

            // Line number
            String lineNum = String.valueOf(lineIdx + 1);
            int numX = editorX - GUTTER_PADDING - textRenderer.getWidth(lineNum) - 2;
            int numColor = lineIdx == cursorLine ? 0xFFCDD6F4 : GUTTER_TEXT;
            context.drawText(textRenderer, lineNum, numX, lineY + 1, numColor, false);

            // Selection highlighting
            renderSelection(context, lineIdx, lineY, editorX, editorWidth, textRenderer);

            // Search match highlighting
            renderSearchHighlights(context, lineIdx, lineY, editorX, textRenderer);

            // Syntax highlighted text
            String lineText = lines.get(lineIdx).toString();
            String displayText = expandTabs(lineText);
            renderHighlightedLine(context, textRenderer, lineText, displayText,
                    editorX + TEXT_PADDING - scrollX, lineY + 1);
        }

        // Cursor rendering (blinking)
        if (focused) {
            long time = System.currentTimeMillis();
            if ((time / 530) % 2 == 0) {
                int cursorScreenLine = cursorLine - scrollLine;
                if (cursorScreenLine >= 0 && cursorScreenLine <= visibleLines) {
                    String lineBeforeCursor = expandTabs(
                            lines.get(cursorLine).substring(0, Math.min(cursorCol, getLineLength(cursorLine))));
                    int cx = editorX + TEXT_PADDING - scrollX + textRenderer.getWidth(lineBeforeCursor);
                    int cy = y + cursorScreenLine * LINE_HEIGHT;
                    context.fill(cx, cy, cx + 1, cy + LINE_HEIGHT, CURSOR_COLOR);
                }
            }
        }

        context.disableScissor();
    }

    /** Renders syntax-highlighted text for a single line. */
    private void renderHighlightedLine(DrawContext context, TextRenderer textRenderer,
                                        String rawLine, String displayLine,
                                        int startX, int lineY) {
        // Use cached highlighting - lines that haven't changed get free lookups
        List<SkriptSyntaxHighlighter.Segment> segments = highlightCache.computeIfAbsent(rawLine, highlighter::highlight);

        int drawX = startX;

        for (SkriptSyntaxHighlighter.Segment seg : segments) {
            String displaySeg = expandTabs(seg.text());
            context.drawText(textRenderer, displaySeg, drawX, lineY, seg.color(), false);
            drawX += textRenderer.getWidth(displaySeg);
        }
    }

    /** Renders selection highlight for a given line. */
    private void renderSelection(DrawContext context, int lineIdx, int lineY,
                                  int editorX, int editorWidth, TextRenderer textRenderer) {
        if (!hasSelection()) return;

        int selMinLine = Math.min(selStartLine, selEndLine);
        int selMaxLine = Math.max(selStartLine, selEndLine);
        int selMinCol, selMaxCol;

        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            selMinCol = selStartCol;
            selMaxCol = selEndCol;
        } else {
            selMinCol = selEndCol;
            selMaxCol = selStartCol;
        }

        if (lineIdx < selMinLine || lineIdx > selMaxLine) return;

        String line = expandTabs(lines.get(lineIdx).toString());
        int lineLen = lines.get(lineIdx).length();

        int startCol, endCol;
        if (lineIdx == selMinLine && lineIdx == selMaxLine) {
            startCol = selMinCol;
            endCol = selMaxCol;
        } else if (lineIdx == selMinLine) {
            startCol = selMinCol;
            endCol = lineLen;
        } else if (lineIdx == selMaxLine) {
            startCol = 0;
            endCol = selMaxCol;
        } else {
            startCol = 0;
            endCol = lineLen;
        }

        String beforeStart = expandTabs(lines.get(lineIdx).substring(0, Math.min(startCol, lineLen)));
        String beforeEnd = expandTabs(lines.get(lineIdx).substring(0, Math.min(endCol, lineLen)));
        int sx = editorX + TEXT_PADDING - scrollX + textRenderer.getWidth(beforeStart);
        int ex = editorX + TEXT_PADDING - scrollX + textRenderer.getWidth(beforeEnd);
        if (lineIdx != selMaxLine) ex += 4; // Extend to show newline is selected

        context.fill(sx, lineY, ex, lineY + LINE_HEIGHT, SELECTION_COLOR);
    }

    /** Renders search match highlights for a line. */
    private void renderSearchHighlights(DrawContext context, int lineIdx, int lineY,
                                         int editorX, TextRenderer textRenderer) {
        for (int[] match : searchMatches) {
            if (match[0] != lineIdx) continue;
            String before = expandTabs(lines.get(lineIdx).substring(0, match[1]));
            String matched = expandTabs(lines.get(lineIdx).substring(match[1], match[2]));
            int sx = editorX + TEXT_PADDING - scrollX + textRenderer.getWidth(before);
            int ex = sx + textRenderer.getWidth(matched);
            context.fill(sx, lineY, ex, lineY + LINE_HEIGHT, SEARCH_HIGHLIGHT);
        }
    }

    // --- Input handling ---

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        // Ctrl shortcuts
        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> { selectAll(); return true; }
                case GLFW.GLFW_KEY_C -> { copyToClipboard(); return true; }
                case GLFW.GLFW_KEY_X -> { cutToClipboard(); return true; }
                case GLFW.GLFW_KEY_V -> { pasteFromClipboard(); return true; }
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) redo(); else undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> { redo(); return true; }
                case GLFW.GLFW_KEY_D -> { duplicateLine(); return true; }
                case GLFW.GLFW_KEY_HOME -> {
                    if (shift) setSelectionEnd(0, 0); else clearSelection();
                    cursorLine = 0; cursorCol = 0;
                    ensureCursorVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    int lastLine = lines.size() - 1;
                    if (shift) setSelectionEnd(lastLine, getLineLength(lastLine)); else clearSelection();
                    cursorLine = lastLine;
                    cursorCol = getLineLength(lastLine);
                    ensureCursorVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> { moveCursorWordLeft(shift); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { moveCursorWordRight(shift); return true; }
            }
        }

        // Navigation keys
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> { moveCursorUp(shift); return true; }
            case GLFW.GLFW_KEY_DOWN -> { moveCursorDown(shift); return true; }
            case GLFW.GLFW_KEY_LEFT -> { moveCursorLeft(shift); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { moveCursorRight(shift); return true; }
            case GLFW.GLFW_KEY_HOME -> { moveCursorHome(shift); return true; }
            case GLFW.GLFW_KEY_END -> { moveCursorEnd(shift); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { moveCursorPageUp(shift); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { moveCursorPageDown(shift); return true; }
        }

        // Editing keys
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { insertNewline(); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> { handleBackspace(ctrl); return true; }
            case GLFW.GLFW_KEY_DELETE -> { handleDelete(ctrl); return true; }
            case GLFW.GLFW_KEY_TAB -> { insertTab(shift); return true; }
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (chr < 32 && chr != '\t') return false;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return false;

        recordUndo();
        deleteSelection();
        lines.get(cursorLine).insert(cursorCol, chr);
        cursorCol++;
        contentChanged();
        ensureCursorVisible();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            focused = false;
            return false;
        }
        focused = true;

        if (button == 0) {
            int editorX = x + gutterWidth + TEXT_PADDING - scrollX;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            int clickedLine = scrollLine + (int) (mouseY - y) / LINE_HEIGHT;
            clickedLine = Math.max(0, Math.min(clickedLine, lines.size() - 1));

            int clickedCol = getColumnAtX(clickedLine, (int) mouseX - editorX, tr);

            boolean shift = (GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(),
                    GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS);

            if (shift) {
                if (!hasSelection()) {
                    selStartLine = cursorLine;
                    selStartCol = cursorCol;
                }
                selEndLine = clickedLine;
                selEndCol = clickedCol;
            } else {
                clearSelection();
                selecting = true;
                selStartLine = clickedLine;
                selStartCol = clickedCol;
            }

            cursorLine = clickedLine;
            cursorCol = clickedCol;
            return true;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!focused || button != 0) return false;

        int editorX = x + gutterWidth + TEXT_PADDING - scrollX;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int dragLine = scrollLine + (int) (mouseY - y) / LINE_HEIGHT;
        dragLine = Math.max(0, Math.min(dragLine, lines.size() - 1));
        int dragCol = getColumnAtX(dragLine, (int) mouseX - editorX, tr);

        selEndLine = dragLine;
        selEndCol = dragCol;
        cursorLine = dragLine;
        cursorCol = dragCol;

        // Auto-scroll when dragging near edges
        if (mouseY < y + LINE_HEIGHT) {
            scrollLine = Math.max(0, scrollLine - 1);
        } else if (mouseY > y + height - LINE_HEIGHT) {
            scrollLine = Math.min(Math.max(0, lines.size() - height / LINE_HEIGHT + 1), scrollLine + 1);
        }

        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            selecting = false;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        scrollLine = Math.max(0, scrollLine - (int) (amount * 3));
        int maxScroll = Math.max(0, lines.size() - height / LINE_HEIGHT + 1);
        scrollLine = Math.min(scrollLine, maxScroll);
        return true;
    }

    // --- Column/position calculation ---

    /** Finds the column index at the given pixel x offset within a line using binary search. */
    private int getColumnAtX(int line, int pixelX, TextRenderer tr) {
        if (line < 0 || line >= lines.size()) return 0;
        String lineText = lines.get(line).toString();
        if (lineText.isEmpty()) return 0;

        int lo = 0, hi = lineText.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            int xPos = tr.getWidth(expandTabs(lineText.substring(0, mid)));
            if (xPos <= pixelX) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        // Check if the next column is actually closer
        if (lo < lineText.length()) {
            int xLo = tr.getWidth(expandTabs(lineText.substring(0, lo)));
            int xHi = tr.getWidth(expandTabs(lineText.substring(0, lo + 1)));
            if (Math.abs(pixelX - xHi) < Math.abs(pixelX - xLo)) {
                return lo + 1;
            }
        }
        return lo;
    }

    /** Expands tab characters to spaces for display width calculation. */
    private String expandTabs(String text) {
        return text.replace("\t", " ".repeat(TAB_DISPLAY_WIDTH));
    }

    // --- Cursor movement ---

    private void moveCursorUp(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();
        if (cursorLine > 0) {
            cursorLine--;
            cursorCol = Math.min(cursorCol, getLineLength(cursorLine));
        }
        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorDown(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = Math.min(cursorCol, getLineLength(cursorLine));
        }
        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorLeft(boolean shift) {
        if (shift) startOrExtendSelection();
        else if (hasSelection()) { moveCursorToSelectionStart(); clearSelection(); ensureCursorVisible(); return; }
        if (cursorCol > 0) {
            cursorCol--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = getLineLength(cursorLine);
        }
        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorRight(boolean shift) {
        if (shift) startOrExtendSelection();
        else if (hasSelection()) { moveCursorToSelectionEnd(); clearSelection(); ensureCursorVisible(); return; }
        if (cursorCol < getLineLength(cursorLine)) {
            cursorCol++;
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
        }
        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorHome(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();

        // Smart home: first go to first non-whitespace, then to col 0
        String line = lines.get(cursorLine).toString();
        int firstNonSpace = 0;
        while (firstNonSpace < line.length() && (line.charAt(firstNonSpace) == ' ' || line.charAt(firstNonSpace) == '\t')) {
            firstNonSpace++;
        }
        cursorCol = (cursorCol == firstNonSpace) ? 0 : firstNonSpace;

        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorEnd(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();
        cursorCol = getLineLength(cursorLine);
        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorPageUp(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();
        int pageSize = height / LINE_HEIGHT;
        cursorLine = Math.max(0, cursorLine - pageSize);
        scrollLine = Math.max(0, scrollLine - pageSize);
        cursorCol = Math.min(cursorCol, getLineLength(cursorLine));
        if (shift) extendSelection();
    }

    private void moveCursorPageDown(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();
        int pageSize = height / LINE_HEIGHT;
        cursorLine = Math.min(lines.size() - 1, cursorLine + pageSize);
        scrollLine = Math.min(Math.max(0, lines.size() - pageSize), scrollLine + pageSize);
        cursorCol = Math.min(cursorCol, getLineLength(cursorLine));
        if (shift) extendSelection();
    }

    private void moveCursorWordLeft(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();

        if (cursorCol == 0 && cursorLine > 0) {
            cursorLine--;
            cursorCol = getLineLength(cursorLine);
        } else {
            String line = lines.get(cursorLine).toString();
            int col = cursorCol;
            // Skip whitespace
            while (col > 0 && !Character.isLetterOrDigit(line.charAt(col - 1))) col--;
            // Skip word
            while (col > 0 && Character.isLetterOrDigit(line.charAt(col - 1))) col--;
            cursorCol = col;
        }

        if (shift) extendSelection();
        ensureCursorVisible();
    }

    private void moveCursorWordRight(boolean shift) {
        if (shift) startOrExtendSelection();
        else clearSelection();

        int lineLen = getLineLength(cursorLine);
        if (cursorCol >= lineLen && cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
        } else {
            String line = lines.get(cursorLine).toString();
            int col = cursorCol;
            // Skip word
            while (col < line.length() && Character.isLetterOrDigit(line.charAt(col))) col++;
            // Skip whitespace
            while (col < line.length() && !Character.isLetterOrDigit(line.charAt(col))) col++;
            cursorCol = col;
        }

        if (shift) extendSelection();
        ensureCursorVisible();
    }

    // --- Selection ---

    private boolean hasSelection() {
        return selStartLine >= 0 && selEndLine >= 0
                && (selStartLine != selEndLine || selStartCol != selEndCol);
    }

    private void startOrExtendSelection() {
        if (!hasSelection()) {
            selStartLine = cursorLine;
            selStartCol = cursorCol;
            selEndLine = cursorLine;
            selEndCol = cursorCol;
        }
    }

    private void extendSelection() {
        selEndLine = cursorLine;
        selEndCol = cursorCol;
    }

    private void setSelectionEnd(int line, int col) {
        if (!hasSelection()) {
            selStartLine = cursorLine;
            selStartCol = cursorCol;
        }
        selEndLine = line;
        selEndCol = col;
    }

    private void clearSelection() {
        selStartLine = -1; selStartCol = -1;
        selEndLine = -1; selEndCol = -1;
        selecting = false;
    }

    private void selectAll() {
        selStartLine = 0;
        selStartCol = 0;
        selEndLine = lines.size() - 1;
        selEndCol = getLineLength(lines.size() - 1);
        cursorLine = selEndLine;
        cursorCol = selEndCol;
    }

    private void moveCursorToSelectionStart() {
        int minLine, minCol;
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            minLine = selStartLine; minCol = selStartCol;
        } else {
            minLine = selEndLine; minCol = selEndCol;
        }
        cursorLine = minLine;
        cursorCol = minCol;
    }

    private void moveCursorToSelectionEnd() {
        int maxLine, maxCol;
        if (selStartLine > selEndLine || (selStartLine == selEndLine && selStartCol > selEndCol)) {
            maxLine = selStartLine; maxCol = selStartCol;
        } else {
            maxLine = selEndLine; maxCol = selEndCol;
        }
        cursorLine = maxLine;
        cursorCol = maxCol;
    }

    /** Returns the selected text, or empty string if no selection. */
    public String getSelectedText() {
        if (!hasSelection()) return "";

        int minLine, minCol, maxLine, maxCol;
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            minLine = selStartLine; minCol = selStartCol;
            maxLine = selEndLine; maxCol = selEndCol;
        } else {
            minLine = selEndLine; minCol = selEndCol;
            maxLine = selStartLine; maxCol = selStartCol;
        }

        if (minLine == maxLine) {
            int end = Math.min(maxCol, getLineLength(minLine));
            int start = Math.min(minCol, end);
            return lines.get(minLine).substring(start, end);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(minLine).substring(Math.min(minCol, getLineLength(minLine))));
        for (int i = minLine + 1; i < maxLine; i++) {
            sb.append('\n').append(lines.get(i));
        }
        sb.append('\n').append(lines.get(maxLine).substring(0, Math.min(maxCol, getLineLength(maxLine))));
        return sb.toString();
    }

    /** Deletes the current selection. */
    private void deleteSelection() {
        if (!hasSelection()) return;

        int minLine, minCol, maxLine, maxCol;
        if (selStartLine < selEndLine || (selStartLine == selEndLine && selStartCol <= selEndCol)) {
            minLine = selStartLine; minCol = selStartCol;
            maxLine = selEndLine; maxCol = selEndCol;
        } else {
            minLine = selEndLine; minCol = selEndCol;
            maxLine = selStartLine; maxCol = selStartCol;
        }

        minCol = Math.min(minCol, getLineLength(minLine));
        maxCol = Math.min(maxCol, getLineLength(maxLine));

        if (minLine == maxLine) {
            lines.get(minLine).delete(minCol, maxCol);
        } else {
            String remaining = lines.get(maxLine).substring(maxCol);
            lines.get(minLine).delete(minCol, lines.get(minLine).length());
            lines.get(minLine).append(remaining);
            // Remove lines between
            for (int i = maxLine; i > minLine; i--) {
                lines.remove(i);
            }
        }

        cursorLine = minLine;
        cursorCol = minCol;
        clearSelection();
    }

    // --- Editing operations ---

    private void insertNewline() {
        recordUndo();
        deleteSelection();

        String currentLine = lines.get(cursorLine).toString();
        String afterCursor = currentLine.substring(cursorCol);
        lines.get(cursorLine).delete(cursorCol, currentLine.length());

        // Auto-indent: copy leading whitespace from current line
        StringBuilder indent = new StringBuilder();
        for (char c : currentLine.toCharArray()) {
            if (c == ' ' || c == '\t') indent.append(c);
            else break;
        }

        // If line ends with ':', add one more tab for Skript block indentation
        String trimmedBefore = lines.get(cursorLine).toString().stripTrailing();
        if (trimmedBefore.endsWith(":")) {
            indent.append('\t');
        }

        StringBuilder newLine = new StringBuilder(indent).append(afterCursor);
        lines.add(cursorLine + 1, newLine);
        cursorLine++;
        cursorCol = indent.length();
        contentChanged();
        ensureCursorVisible();
    }

    private void handleBackspace(boolean ctrlHeld) {
        recordUndo();
        if (hasSelection()) {
            deleteSelection();
            contentChanged();
            return;
        }

        if (ctrlHeld) {
            // Delete word backward
            int origCol = cursorCol;
            String line = lines.get(cursorLine).toString();
            int col = cursorCol;
            while (col > 0 && !Character.isLetterOrDigit(line.charAt(col - 1))) col--;
            while (col > 0 && Character.isLetterOrDigit(line.charAt(col - 1))) col--;
            lines.get(cursorLine).delete(col, origCol);
            cursorCol = col;
        } else if (cursorCol > 0) {
            lines.get(cursorLine).deleteCharAt(cursorCol - 1);
            cursorCol--;
        } else if (cursorLine > 0) {
            // Merge with previous line
            int prevLen = getLineLength(cursorLine - 1);
            lines.get(cursorLine - 1).append(lines.get(cursorLine));
            lines.remove(cursorLine);
            cursorLine--;
            cursorCol = prevLen;
        }
        contentChanged();
        ensureCursorVisible();
    }

    private void handleDelete(boolean ctrlHeld) {
        recordUndo();
        if (hasSelection()) {
            deleteSelection();
            contentChanged();
            return;
        }

        if (ctrlHeld) {
            // Delete word forward
            String line = lines.get(cursorLine).toString();
            int col = cursorCol;
            while (col < line.length() && Character.isLetterOrDigit(line.charAt(col))) col++;
            while (col < line.length() && !Character.isLetterOrDigit(line.charAt(col))) col++;
            lines.get(cursorLine).delete(cursorCol, col);
        } else if (cursorCol < getLineLength(cursorLine)) {
            lines.get(cursorLine).deleteCharAt(cursorCol);
        } else if (cursorLine < lines.size() - 1) {
            // Merge with next line
            lines.get(cursorLine).append(lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        }
        contentChanged();
    }

    private void insertTab(boolean shift) {
        recordUndo();
        if (shift) {
            // Unindent: remove leading tab or spaces from current line
            String line = lines.get(cursorLine).toString();
            if (line.startsWith("\t")) {
                lines.get(cursorLine).deleteCharAt(0);
                cursorCol = Math.max(0, cursorCol - 1);
            } else if (line.startsWith("    ")) {
                lines.get(cursorLine).delete(0, 4);
                cursorCol = Math.max(0, cursorCol - 4);
            }
        } else {
            deleteSelection();
            lines.get(cursorLine).insert(cursorCol, '\t');
            cursorCol++;
        }
        contentChanged();
    }

    /** Inserts text at the cursor position, handling multi-line paste. */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        recordUndo();
        deleteSelection();

        String[] insertLines = text.split("\n", -1);
        if (insertLines.length == 1) {
            lines.get(cursorLine).insert(cursorCol, insertLines[0]);
            cursorCol += insertLines[0].length();
        } else {
            String afterCursor = lines.get(cursorLine).substring(cursorCol);
            lines.get(cursorLine).delete(cursorCol, lines.get(cursorLine).length());
            lines.get(cursorLine).append(insertLines[0]);

            for (int i = 1; i < insertLines.length - 1; i++) {
                lines.add(cursorLine + i, new StringBuilder(insertLines[i]));
            }

            String lastInsertLine = insertLines[insertLines.length - 1];
            lines.add(cursorLine + insertLines.length - 1,
                    new StringBuilder(lastInsertLine).append(afterCursor));
            cursorLine += insertLines.length - 1;
            cursorCol = lastInsertLine.length();
        }
        contentChanged();
        ensureCursorVisible();
    }

    private void duplicateLine() {
        recordUndo();
        String line = lines.get(cursorLine).toString();
        lines.add(cursorLine + 1, new StringBuilder(line));
        cursorLine++;
        contentChanged();
        ensureCursorVisible();
    }

    // --- Clipboard ---

    private void copyToClipboard() {
        String selected = getSelectedText();
        if (!selected.isEmpty()) {
            MinecraftClient.getInstance().keyboard.setClipboard(selected);
        }
    }

    private void cutToClipboard() {
        String selected = getSelectedText();
        if (!selected.isEmpty()) {
            MinecraftClient.getInstance().keyboard.setClipboard(selected);
            recordUndo();
            deleteSelection();
            contentChanged();
        }
    }

    private void pasteFromClipboard() {
        String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
        if (clipboard != null && !clipboard.isEmpty()) {
            // Normalize line endings
            clipboard = clipboard.replace("\r\n", "\n").replace("\r", "\n");
            insertText(clipboard);
        }
    }

    // --- Undo/Redo ---

    private void recordUndo() {
        if (activeTab != null) {
            activeTab.getUndoManager().record(getText(), cursorLine, cursorCol);
        }
    }

    private void undo() {
        if (activeTab == null) return;
        UndoManager.Snapshot snap = activeTab.getUndoManager().undo(getText(), cursorLine, cursorCol);
        if (snap != null) {
            setText(snap.text());
            cursorLine = snap.cursorLine();
            cursorCol = Math.min(snap.cursorCol(), getLineLength(snap.cursorLine()));
            clearSelection();
            contentChanged();
            ensureCursorVisible();
        }
    }

    private void redo() {
        if (activeTab == null) return;
        UndoManager.Snapshot snap = activeTab.getUndoManager().redo(getText(), cursorLine, cursorCol);
        if (snap != null) {
            setText(snap.text());
            cursorLine = snap.cursorLine();
            cursorCol = Math.min(snap.cursorCol(), getLineLength(snap.cursorLine()));
            clearSelection();
            contentChanged();
            ensureCursorVisible();
        }
    }

    // --- Search ---

    /**
     * Performs a search through the document and highlights matches.
     * Returns the number of matches found.
     */
    public int search(String query, boolean caseSensitive) {
        this.searchQuery = query;
        this.searchCaseSensitive = caseSensitive;
        searchMatches.clear();
        currentSearchMatch = -1;

        if (query.isEmpty()) return 0;

        String q = caseSensitive ? query : query.toLowerCase();

        for (int i = 0; i < lines.size(); i++) {
            String line = caseSensitive ? lines.get(i).toString() : lines.get(i).toString().toLowerCase();
            int idx = 0;
            while ((idx = line.indexOf(q, idx)) >= 0) {
                searchMatches.add(new int[]{i, idx, idx + query.length()});
                idx += query.length();
            }
        }

        if (!searchMatches.isEmpty()) {
            // Find closest match to cursor
            currentSearchMatch = 0;
            for (int i = 0; i < searchMatches.size(); i++) {
                int[] m = searchMatches.get(i);
                if (m[0] > cursorLine || (m[0] == cursorLine && m[1] >= cursorCol)) {
                    currentSearchMatch = i;
                    break;
                }
            }
        }

        return searchMatches.size();
    }

    /** Navigates to the next search match. */
    public int findNext() {
        if (searchMatches.isEmpty()) return 0;
        currentSearchMatch = (currentSearchMatch + 1) % searchMatches.size();
        goToMatch(currentSearchMatch);
        return currentSearchMatch + 1;
    }

    /** Navigates to the previous search match. */
    public int findPrevious() {
        if (searchMatches.isEmpty()) return 0;
        currentSearchMatch = (currentSearchMatch - 1 + searchMatches.size()) % searchMatches.size();
        goToMatch(currentSearchMatch);
        return currentSearchMatch + 1;
    }

    private void goToMatch(int index) {
        if (index < 0 || index >= searchMatches.size()) return;
        int[] match = searchMatches.get(index);
        cursorLine = match[0];
        cursorCol = match[1];
        // Select the match
        selStartLine = match[0]; selStartCol = match[1];
        selEndLine = match[0]; selEndCol = match[2];
        ensureCursorVisible();
    }

    public int getSearchMatchCount() { return searchMatches.size(); }
    public int getCurrentSearchMatch() { return searchMatches.isEmpty() ? 0 : currentSearchMatch + 1; }

    /** Clears search highlights. */
    public void clearSearch() {
        searchQuery = "";
        searchMatches.clear();
        currentSearchMatch = -1;
    }

    // --- Utility ---

    private void clampCursor() {
        cursorLine = Math.max(0, Math.min(cursorLine, lines.size() - 1));
        cursorCol = Math.max(0, Math.min(cursorCol, getLineLength(cursorLine)));
    }

    private void ensureCursorVisible() {
        int visibleLines = height / LINE_HEIGHT;
        // Vertical scrolling
        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visibleLines - 1) {
            scrollLine = cursorLine - visibleLines + 2;
        }
        scrollLine = Math.max(0, scrollLine);

        // Horizontal scrolling
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String lineBeforeCursor = expandTabs(
                lines.get(cursorLine).substring(0, Math.min(cursorCol, getLineLength(cursorLine))));
        int cursorPixelX = tr.getWidth(lineBeforeCursor);
        int editorWidth = width - gutterWidth - TEXT_PADDING * 2;

        if (cursorPixelX - scrollX > editorWidth - 20) {
            scrollX = cursorPixelX - editorWidth + 40;
        } else if (cursorPixelX - scrollX < 10) {
            scrollX = Math.max(0, cursorPixelX - 40);
        }
    }

    private void contentChanged() {
        textDirty = true;
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }
}
