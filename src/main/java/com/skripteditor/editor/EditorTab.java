package com.skripteditor.editor;

/**
 * Represents a single open file tab in the editor.
 * Tracks the file's content state, cursor position, scroll offset,
 * and undo history independently per tab.
 */
public class EditorTab {

    private final String filePath;       // Relative path within the scripts directory
    private String fileName;             // Display name (just the filename)
    private String originalContent;      // Content as last loaded/saved from server
    private String currentContent;       // Current editor content (may differ if modified)
    private int cursorLine = 0;
    private int cursorCol = 0;
    private int scrollLine = 0;         // First visible line (vertical scroll)
    private int scrollX = 0;            // Horizontal scroll offset in pixels
    private final UndoManager undoManager;
    private boolean pendingSave = false; // True while a save request is in flight

    public EditorTab(String filePath, String content) {
        this.filePath = filePath;
        this.fileName = extractFileName(filePath);
        this.originalContent = content;
        this.currentContent = content;
        this.undoManager = new UndoManager();
        this.undoManager.markSaved(content);
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "untitled.sk";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    // --- Accessors ---

    public String getFilePath() { return filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String name) { this.fileName = name; }

    public String getOriginalContent() { return originalContent; }
    public String getCurrentContent() { return currentContent; }

    public void setCurrentContent(String content) {
        this.currentContent = content;
    }

    /** Called after a successful save to update the baseline. */
    public void markSaved() {
        this.originalContent = this.currentContent;
        this.undoManager.markSaved(this.currentContent);
        this.pendingSave = false;
    }

    /**
     * Marks this tab as modified from a recovered draft.
     * The content hasn't been saved to the server yet, so we force modified state.
     */
    public void markModifiedFromDraft() {
        this.originalContent = null; // Ensures isModified() returns true
    }

    /** Returns true if the content has been modified since last save. */
    public boolean isModified() {
        if (originalContent == null) return true;
        return undoManager.isModifiedSince(currentContent);
    }

    // --- Cursor ---

    public int getCursorLine() { return cursorLine; }
    public int getCursorCol() { return cursorCol; }

    public void setCursorLine(int line) { this.cursorLine = line; }
    public void setCursorCol(int col) { this.cursorCol = col; }

    public void setCursor(int line, int col) {
        this.cursorLine = line;
        this.cursorCol = col;
    }

    // --- Scroll ---

    public int getScrollLine() { return scrollLine; }
    public int getScrollX() { return scrollX; }

    public void setScrollLine(int line) { this.scrollLine = Math.max(0, line); }
    public void setScrollX(int x) { this.scrollX = Math.max(0, x); }

    // --- Undo ---

    public UndoManager getUndoManager() { return undoManager; }

    // --- Save state ---

    public boolean isPendingSave() { return pendingSave; }
    public void setPendingSave(boolean pending) { this.pendingSave = pending; }
}
