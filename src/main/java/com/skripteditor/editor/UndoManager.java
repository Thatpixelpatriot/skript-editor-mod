package com.skripteditor.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages undo/redo history for the code editor.
 * Each snapshot captures the full text state plus cursor position,
 * allowing complete state restoration on undo/redo.
 */
public class UndoManager {

    /** Maximum number of undo states to keep in memory. */
    private static final int MAX_HISTORY = 200;

    /** A snapshot of the editor state at a point in time. */
    public record Snapshot(String text, int cursorLine, int cursorCol) {}

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private Snapshot lastSaved = null;
    private long lastRecordTime = 0;

    /** Minimum milliseconds between automatic snapshot recordings to avoid excessive history. */
    private static final long MIN_RECORD_INTERVAL_MS = 300;

    /**
     * Records a new state snapshot for undo.
     * Clears the redo stack since we've diverged from the redo history.
     * Throttles recording to avoid saving on every keystroke.
     */
    public void record(String text, int cursorLine, int cursorCol) {
        long now = System.currentTimeMillis();
        if (now - lastRecordTime < MIN_RECORD_INTERVAL_MS && !undoStack.isEmpty()) {
            return;
        }
        lastRecordTime = now;

        // Don't record duplicate states
        if (!undoStack.isEmpty()) {
            Snapshot top = undoStack.peek();
            if (top.text().equals(text)) {
                return;
            }
        }

        undoStack.push(new Snapshot(text, cursorLine, cursorCol));
        redoStack.clear();

        // Enforce max history size - O(1) removal of oldest entry
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
    }

    /**
     * Forces a snapshot to be recorded regardless of throttling.
     * Use this before operations like paste or delete-selection.
     */
    public void forceRecord(String text, int cursorLine, int cursorCol) {
        lastRecordTime = 0;
        record(text, cursorLine, cursorCol);
    }

    /**
     * Undoes the last change. Returns the previous snapshot, or null if nothing to undo.
     * The current state should be pushed to the redo stack by the caller after applying.
     */
    public Snapshot undo(String currentText, int cursorLine, int cursorCol) {
        if (undoStack.isEmpty()) {
            return null;
        }
        redoStack.push(new Snapshot(currentText, cursorLine, cursorCol));
        return undoStack.pop();
    }

    /**
     * Redoes the last undone change. Returns the snapshot to restore, or null if nothing to redo.
     */
    public Snapshot redo(String currentText, int cursorLine, int cursorCol) {
        if (redoStack.isEmpty()) {
            return null;
        }
        undoStack.push(new Snapshot(currentText, cursorLine, cursorCol));
        return redoStack.pop();
    }

    /** Marks the current text state as the "saved" point for modification tracking. */
    public void markSaved(String text) {
        this.lastSaved = new Snapshot(text, 0, 0);
    }

    /** Returns true if the current text differs from the last saved state. */
    public boolean isModifiedSince(String currentText) {
        if (lastSaved == null) {
            return true;
        }
        return !lastSaved.text().equals(currentText);
    }

    /** Clears all undo/redo history. */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        lastSaved = null;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
