package com.skripteditor.gui;

import com.skripteditor.editor.DraftManager;
import com.skripteditor.editor.EditorTab;
import com.skripteditor.gui.widget.*;
import com.skripteditor.network.ConnectionState;
import com.skripteditor.network.SkriptPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Skript Editor screen. Orchestrates all sub-widgets:
 * - File tree sidebar (left)
 * - Tab bar (top)
 * - Code editor (center)
 * - Search bar (below tabs, when active)
 * - Status bar (bottom)
 * - Toast notifications (top-right overlay)
 * - Confirmation dialogs (modal overlay)
 *
 * Also serves as the bridge between network responses and the UI.
 */
public class SkriptEditorScreen extends Screen {

    // Layout dimensions
    private static final int SIDEBAR_WIDTH = 160;
    private static final int MIN_SIDEBAR_WIDTH = 100;
    private static final int MAX_SIDEBAR_WIDTH = 300;

    // Sub-widgets
    private FileTreeWidget fileTree;
    private TabBarWidget tabBar;
    private CodeEditorWidget codeEditor;
    private SearchBarWidget searchBar;
    private StatusBarWidget statusBar;
    private ToastManager toastManager;
    private ConfirmDialog confirmDialog;

    // Editor state
    private final List<EditorTab> openTabs = new ArrayList<>();
    private int activeTabIndex = -1;
    private int sidebarWidth = SIDEBAR_WIDTH;

    // Reload console
    private final List<String> reloadLogs = new ArrayList<>();
    private boolean showReloadConsole = false;
    private static final int CONSOLE_HEIGHT = 80;
    private static final int MAX_RELOAD_LOGS = 200;

    public SkriptEditorScreen() {
        super(Text.translatable("skripteditor.title"));
    }

    @Override
    protected void init() {
        super.init();

        toastManager = new ToastManager();
        confirmDialog = new ConfirmDialog();
        confirmDialog.setScreenSize(width, height);

        // File tree sidebar
        fileTree = new FileTreeWidget(0, 0, sidebarWidth, height);
        fileTree.setOnFileSelected(this::openFile);
        fileTree.setOnDirectoryExpanded(this::requestDirectoryListing);
        fileTree.setOnFileRightClicked(this::showFileContextMenu);

        // Tab bar
        int editorAreaX = sidebarWidth;
        int editorAreaWidth = width - sidebarWidth;
        tabBar = new TabBarWidget(editorAreaX, 0, editorAreaWidth);
        tabBar.setOnTabSelected(this::switchToTab);
        tabBar.setOnTabCloseRequested(this::requestCloseTab);

        // Search bar (initially hidden)
        int searchY = tabBar.getHeight();
        searchBar = new SearchBarWidget(editorAreaX, searchY, editorAreaWidth);
        searchBar.setOnSearch(this::performSearch);
        searchBar.setOnFindNext(this::findNext);
        searchBar.setOnFindPrevious(this::findPrevious);
        searchBar.setOnClose(this::closeSearch);

        // Status bar
        int statusBarHeight = 16;
        statusBar = new StatusBarWidget(0, height - statusBarHeight, width);

        // Code editor
        int editorY = tabBar.getHeight() + searchBar.getHeight();
        int editorHeight = height - editorY - statusBarHeight;
        if (showReloadConsole) editorHeight -= CONSOLE_HEIGHT;
        codeEditor = new CodeEditorWidget(editorAreaX, editorY, editorAreaWidth, editorHeight);
        codeEditor.setOnContentChanged(this::onEditorContentChanged);

        // Send handshake to verify server bridge compatibility
        ConnectionState.getInstance().beginHandshake();
        trySend(new SkriptPackets.HandshakeRequest(
                SkriptPackets.PROTOCOL_VERSION, "1.0.0"));

        // Check for recoverable drafts from a prior crash
        List<DraftManager.RecoverableDraft> drafts = DraftManager.getInstance().getRecoverableDrafts();
        if (!drafts.isEmpty()) {
            confirmDialog.show("Recover Drafts",
                    drafts.size() + " unsaved draft(s) found from a previous session. Recover?",
                    confirmed -> {
                        if (confirmed) {
                            for (DraftManager.RecoverableDraft draft : drafts) {
                                EditorTab tab = new EditorTab(draft.filePath(), draft.content());
                                tab.markModifiedFromDraft();
                                openTabs.add(tab);
                            }
                            if (!openTabs.isEmpty()) switchToTab(0);
                            toastManager.success("Recovered " + drafts.size() + " draft(s)");
                        }
                        DraftManager.getInstance().clearAllDrafts();
                    });
        }

        // Request initial file listing from server
        requestDirectoryListing("");
    }

    private void updateLayout() {
        int editorAreaX = sidebarWidth;
        int editorAreaWidth = width - sidebarWidth;

        fileTree.setSize(sidebarWidth, height);
        tabBar.setPosition(editorAreaX, 0);
        tabBar.setWidth(editorAreaWidth);

        int searchY = tabBar.getHeight();
        searchBar.setPosition(editorAreaX, searchY);
        searchBar.setWidth(editorAreaWidth);

        statusBar.setPosition(0, height - statusBar.getHeight());
        statusBar.setWidth(width);

        int editorY = tabBar.getHeight() + searchBar.getHeight();
        int editorHeight = height - editorY - statusBar.getHeight();
        if (showReloadConsole) editorHeight -= CONSOLE_HEIGHT;
        codeEditor.setPosition(editorAreaX, editorY);
        codeEditor.setSize(editorAreaWidth, editorHeight);

        confirmDialog.setScreenSize(width, height);
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Tick auto-save drafts
        DraftManager.getInstance().tick(openTabs);

        // Dark background fill
        context.fill(0, 0, width, height, 0xFF11111B);

        // Render sub-widgets
        fileTree.render(context, textRenderer, mouseX, mouseY);
        tabBar.render(context, textRenderer, openTabs, mouseX, mouseY);
        searchBar.render(context, textRenderer, mouseX, mouseY);

        if (activeTabIndex >= 0) {
            codeEditor.render(context, textRenderer, mouseX, mouseY);
        } else {
            renderWelcome(context, mouseX, mouseY);
        }

        if (showReloadConsole) {
            renderReloadConsole(context);
        }

        EditorTab activeTab = activeTabIndex >= 0 ? openTabs.get(activeTabIndex) : null;
        statusBar.render(context, textRenderer, activeTab);
        toastManager.render(context, textRenderer, width, tabBar.getHeight() + 4);

        // Dialogs render last (on top)
        confirmDialog.render(context, textRenderer, mouseX, mouseY);
    }

    private void renderWelcome(DrawContext context, int mouseX, int mouseY) {
        int cx = sidebarWidth + (width - sidebarWidth) / 2;
        int cy = height / 2;

        String title = "Skript Editor";
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, cx - titleWidth / 2, cy - 20, 0xFF89B4FA, true);

        String hint = "Select a file from the sidebar to begin editing";
        int hintWidth = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint, cx - hintWidth / 2, cy, 0xFF6C7086, false);

        String shortcutHint = "Ctrl+S Save | Ctrl+F Find | Ctrl+W Close Tab | F5 Reload";
        int shWidth = textRenderer.getWidth(shortcutHint);
        context.drawText(textRenderer, shortcutHint, cx - shWidth / 2, cy + 16, 0xFF45475A, false);
    }

    private void renderReloadConsole(DrawContext context) {
        int consoleX = sidebarWidth;
        int consoleW = width - sidebarWidth;
        int consoleY = height - statusBar.getHeight() - CONSOLE_HEIGHT;

        // Background
        context.fill(consoleX, consoleY, consoleX + consoleW, consoleY + CONSOLE_HEIGHT, 0xFF181825);
        // Top border
        context.fill(consoleX, consoleY, consoleX + consoleW, consoleY + 1, 0xFF313244);

        // Title bar
        context.drawText(textRenderer, "Reload Console", consoleX + 6, consoleY + 4, 0xFF89B4FA, false);

        // Log lines
        int lineY = consoleY + 16;
        int maxLines = (CONSOLE_HEIGHT - 18) / 10;
        int startIdx = Math.max(0, reloadLogs.size() - maxLines);
        int maxTextWidth = consoleW - 12;
        int ellipsisWidth = textRenderer.getWidth("...");
        for (int i = startIdx; i < reloadLogs.size() && lineY < consoleY + CONSOLE_HEIGHT - 2; i++) {
            String line = reloadLogs.get(i);
            String lower = line.toLowerCase();
            int color = 0xFFA6ADC8; // default
            if (lower.contains("error")) color = 0xFFF38BA8;
            else if (lower.contains("warn")) color = 0xFFFAB387;
            else if (lower.contains("success") || lower.contains("loaded")) color = 0xFFA6E3A1;

            // Truncate long lines using binary search instead of character-by-character loop
            if (textRenderer.getWidth(line) > maxTextWidth) {
                int lo = 1, hi = line.length();
                while (lo < hi) {
                    int mid = (lo + hi + 1) / 2;
                    if (textRenderer.getWidth(line.substring(0, mid)) + ellipsisWidth <= maxTextWidth) {
                        lo = mid;
                    } else {
                        hi = mid - 1;
                    }
                }
                line = line.substring(0, lo) + "...";
            }
            context.drawText(textRenderer, line, consoleX + 6, lineY, color, false);
            lineY += 10;
        }
    }

    // =========================================================================
    // INPUT HANDLING
    // =========================================================================

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        // Dialog takes priority
        if (confirmDialog.isVisible()) {
            return confirmDialog.keyPressed(keyCode);
        }

        boolean ctrl = input.hasCtrlOrCmd();
        boolean shift = input.hasShift();

        // Global keyboard shortcuts
        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_S -> {
                    if (shift) saveAll(); else saveCurrentFile();
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    toggleSearchBar();
                    return true;
                }
                case GLFW.GLFW_KEY_W -> {
                    if (activeTabIndex >= 0) requestCloseTab(activeTabIndex);
                    return true;
                }
                case GLFW.GLFW_KEY_TAB -> {
                    // Ctrl+Tab / Ctrl+Shift+Tab to switch tabs
                    if (!openTabs.isEmpty()) {
                        int next = shift
                                ? (activeTabIndex - 1 + openTabs.size()) % openTabs.size()
                                : (activeTabIndex + 1) % openTabs.size();
                        switchToTab(next);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_N -> {
                    promptCreateFile();
                    return true;
                }
            }
        }

        // F5 = reload current script, Shift+F5 = reload all
        if (keyCode == GLFW.GLFW_KEY_F5) {
            if (shift) reloadAllScripts(); else reloadCurrentScript();
            return true;
        }

        // F6 = toggle reload console
        if (keyCode == GLFW.GLFW_KEY_F6) {
            showReloadConsole = !showReloadConsole;
            updateLayout();
            return true;
        }

        // F7 = open dependency graph
        if (keyCode == GLFW.GLFW_KEY_F7) {
            openDependencyGraph();
            return true;
        }

        // Escape handling
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchBar.isVisible()) {
                closeSearch();
                return true;
            }
            // Check for unsaved changes before closing
            if (hasUnsavedChanges()) {
                confirmDialog.show("Unsaved Changes",
                        "You have unsaved changes. Close anyway?",
                        confirmed -> { if (confirmed) close(); });
                return true;
            }
            close();
            return true;
        }

        // Forward to search bar if focused
        if (searchBar.isFocused() && searchBar.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Forward to code editor
        if (codeEditor.isFocused() && codeEditor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();

        if (confirmDialog.isVisible()) return true;

        if (searchBar.isFocused() && searchBar.charTyped(chr, modifiers)) {
            return true;
        }

        if (codeEditor.isFocused() && codeEditor.charTyped(chr, modifiers)) {
            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (confirmDialog.isVisible()) {
            return confirmDialog.mouseClicked(mouseX, mouseY, button);
        }

        // Check which widget was clicked to set focus
        if (searchBar.mouseClicked(mouseX, mouseY, button)) {
            codeEditor.setFocused(false);
            return true;
        }

        if (tabBar.mouseClicked(mouseX, mouseY, button, openTabs)) {
            return true;
        }

        if (fileTree.mouseClicked(mouseX, mouseY, button)) {
            codeEditor.setFocused(false);
            searchBar.setFocused(false);
            return true;
        }

        if (codeEditor.mouseClicked(mouseX, mouseY, button)) {
            searchBar.setFocused(false);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (codeEditor.mouseDragged(mouseX, mouseY, button, offsetX, offsetY)) {
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        codeEditor.mouseReleased(click.x(), click.y(), click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (confirmDialog.isVisible()) return true;

        if (fileTree.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (tabBar.mouseScrolled(mouseX, mouseY, verticalAmount, openTabs)) {
            return true;
        }
        if (codeEditor.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false; // Don't pause the game while editing
    }

    // =========================================================================
    // TAB MANAGEMENT
    // =========================================================================

    private void openFile(String path) {
        // Check if already open
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).getFilePath().equals(path)) {
                switchToTab(i);
                return;
            }
        }

        if (!validateExtension(path)) return;

        // Request file contents from server
        trySend(new SkriptPackets.FileOpenRequest(path));
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= openTabs.size()) return;

        // Save current editor state
        codeEditor.syncToTab();

        activeTabIndex = index;
        tabBar.setActiveIndex(index);
        codeEditor.bindTab(openTabs.get(index));
        fileTree.setSelectedPath(openTabs.get(index).getFilePath());
    }

    private void requestCloseTab(int index) {
        if (index < 0 || index >= openTabs.size()) return;
        EditorTab tab = openTabs.get(index);

        if (tab.isModified()) {
            confirmDialog.show("Unsaved Changes",
                    "'" + tab.getFileName() + "' has unsaved changes. Close?",
                    confirmed -> { if (confirmed) closeTab(index); });
        } else {
            closeTab(index);
        }
    }

    private void closeTab(int index) {
        if (index < 0 || index >= openTabs.size()) return;
        openTabs.remove(index);

        if (openTabs.isEmpty()) {
            activeTabIndex = -1;
            codeEditor.bindTab(null);
        } else if (index <= activeTabIndex) {
            activeTabIndex = Math.max(0, activeTabIndex - 1);
            switchToTab(activeTabIndex);
        }
        tabBar.setActiveIndex(activeTabIndex);
    }

    // =========================================================================
    // FILE OPERATIONS
    // =========================================================================

    private void saveCurrentFile() {
        if (activeTabIndex < 0) return;
        EditorTab tab = openTabs.get(activeTabIndex);
        codeEditor.syncToTab();

        if (!validateFileSize(tab.getCurrentContent())) return;

        tab.setPendingSave(true);
        if (!trySend(new SkriptPackets.FileSaveRequest(tab.getFilePath(), tab.getCurrentContent()))) {
            tab.setPendingSave(false);
        }
    }

    private void saveAll() {
        codeEditor.syncToTab();
        for (EditorTab tab : openTabs) {
            if (tab.isModified()) {
                if (!validateFileSize(tab.getCurrentContent())) {
                    toastManager.error("Skipped " + tab.getFileName() + " — too large");
                    continue;
                }
                tab.setPendingSave(true);
                if (!trySend(new SkriptPackets.FileSaveRequest(tab.getFilePath(), tab.getCurrentContent()))) {
                    tab.setPendingSave(false);
                }
            }
        }
        toastManager.info("Saving all modified files...");
    }

    private void reloadCurrentScript() {
        if (activeTabIndex < 0) return;
        EditorTab tab = openTabs.get(activeTabIndex);
        if (trySend(new SkriptPackets.ReloadRequest(tab.getFilePath()))) {
            toastManager.info("Reloading " + tab.getFileName() + "...");
        }
    }

    private void reloadAllScripts() {
        if (trySend(new SkriptPackets.ReloadRequest(""))) {
            toastManager.info("Reloading all scripts...");
        }
    }

    private void requestDirectoryListing(String path) {
        trySend(new SkriptPackets.FileListRequest(path));
    }

    private void promptCreateFile() {
        // For simplicity, we use a basic approach - in a full implementation
        // you might want a text input dialog here
        String basePath = fileTree.getSelectedPath();
        if (basePath == null) basePath = "";

        // Default new file name
        String newPath = basePath.isEmpty() ? "new_script.sk" : basePath + "/new_script.sk";
        if (!validateExtension(newPath)) return;
        trySend(new SkriptPackets.FileCreateRequest(newPath, false));
    }

    private void showFileContextMenu(String path) {
        // Show delete confirmation for right-clicked file
        confirmDialog.show("Delete File",
                "Delete '" + path + "'? This cannot be undone.",
                confirmed -> {
                    if (confirmed) {
                        trySend(new SkriptPackets.FileDeleteRequest(path));
                    }
                });
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    private void toggleSearchBar() {
        if (searchBar.isVisible()) {
            closeSearch();
        } else {
            searchBar.show();
            updateLayout();
            // Pre-fill with selected text
            String selected = codeEditor.getSelectedText();
            if (!selected.isEmpty() && !selected.contains("\n")) {
                // Would need a setter on SearchBarWidget - simplified here
            }
        }
    }

    private void performSearch(String query, boolean caseSensitive) {
        int count = codeEditor.search(query, caseSensitive);
        searchBar.setMatchInfo(codeEditor.getCurrentSearchMatch(), count);
    }

    private void findNext() {
        int current = codeEditor.findNext();
        searchBar.setMatchInfo(current, codeEditor.getSearchMatchCount());
    }

    private void findPrevious() {
        int current = codeEditor.findPrevious();
        searchBar.setMatchInfo(current, codeEditor.getSearchMatchCount());
    }

    private void closeSearch() {
        searchBar.hide();
        codeEditor.clearSearch();
        codeEditor.setFocused(true);
        updateLayout();
    }

    // =========================================================================
    // EDITOR CALLBACKS
    // =========================================================================

    private void onEditorContentChanged() {
        if (activeTabIndex >= 0) {
            codeEditor.syncToTab();
        }
    }

    private boolean hasUnsavedChanges() {
        for (EditorTab tab : openTabs) {
            if (tab.isModified()) return true;
        }
        return false;
    }

    @Override
    public void close() {
        // Save drafts for any unsaved tabs before closing, or clear if all clean
        if (hasUnsavedChanges()) {
            DraftManager.getInstance().saveDrafts(openTabs);
        } else {
            DraftManager.getInstance().clearAllDrafts();
        }
        super.close();
    }

    // =========================================================================
    // NETWORK RESPONSE HANDLERS
    // Called from PacketHandler on the render/client thread.
    // =========================================================================

    /** Called when a directory listing is received from the server bridge. */
    public void onFileListReceived(String path, List<SkriptPackets.FileEntry> entries) {
        fileTree.updateListing(path, entries);
    }

    /** Called when file contents are received after an open request. */
    public void onFileContentReceived(String path, String content) {
        // Create a new tab with the content
        EditorTab tab = new EditorTab(path, content);
        openTabs.add(tab);
        switchToTab(openTabs.size() - 1);
        toastManager.success("Opened " + tab.getFileName());
    }

    /** Called when a file save completes successfully. */
    public void onFileSaved(String path) {
        for (EditorTab tab : openTabs) {
            if (tab.getFilePath().equals(path)) {
                tab.markSaved();
                break;
            }
        }
        DraftManager.getInstance().clearDraft(path);
        statusBar.setServerMessage("Saved successfully", false);
        toastManager.success("Saved " + path);
    }

    /** Called when a new file is created on the server. */
    public void onFileCreated(String path) {
        toastManager.success("Created " + path);
        // Refresh the parent directory listing
        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash > 0 ? path.substring(0, lastSlash) : "";
        requestDirectoryListing(parentPath);
        // Auto-open the new file
        openFile(path);
    }

    /** Called when a file is renamed on the server. */
    public void onFileRenamed(String oldPath, String newPath) {
        // Update any open tab that references the old path
        for (EditorTab tab : openTabs) {
            if (tab.getFilePath().equals(oldPath)) {
                // EditorTab path is final, so we'd need to replace the tab
                // For simplicity, close the old and open the new
                int idx = openTabs.indexOf(tab);
                closeTab(idx);
                openFile(newPath);
                break;
            }
        }
        toastManager.success("Renamed to " + newPath);
        // Refresh file tree
        requestDirectoryListing("");
    }

    /** Called when a file is deleted on the server. */
    public void onFileDeleted(String path) {
        // Close the tab if it's open
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).getFilePath().equals(path)) {
                closeTab(i);
                break;
            }
        }
        toastManager.success("Deleted " + path);
        // Refresh file tree
        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash > 0 ? path.substring(0, lastSlash) : "";
        requestDirectoryListing(parentPath);
    }

    /** Called when a script reload completes. */
    public void onReloadResponse(String path, boolean success, String message, List<String> logs) {
        if (success) {
            toastManager.success(message.isEmpty() ? "Reload successful" : message);
            statusBar.setServerMessage("Reload OK", false);
        } else {
            toastManager.error(message.isEmpty() ? "Reload failed" : message);
            statusBar.setServerMessage("Reload FAILED", true);
        }

        // Store reload logs for the console (capped to prevent unbounded growth)
        reloadLogs.clear();
        if (!logs.isEmpty()) {
            int start = Math.max(0, logs.size() - MAX_RELOAD_LOGS);
            reloadLogs.addAll(logs.subList(start, logs.size()));
            showReloadConsole = true;
            updateLayout();
        }
    }

    /** Called when any file operation fails. */
    public void onOperationError(String errorMessage) {
        toastManager.error(errorMessage);
        statusBar.setServerMessage(errorMessage, true);
    }

    /** Called when the server responds to our handshake. */
    public void onHandshakeComplete() {
        ConnectionState conn = ConnectionState.getInstance();
        if (!conn.isProtocolCompatible()) {
            toastManager.error("Server bridge version mismatch! Some features may not work.");
        } else {
            toastManager.success("Connected to server bridge " + conn.getStatusText());
        }
    }

    // =========================================================================
    // NETWORK UTILITY
    // =========================================================================

    /** Safely sends a packet, checking connection state first. Returns true on success. */
    private <T extends net.minecraft.network.packet.CustomPayload> boolean trySend(T payload) {
        try {
            ClientPlayNetworking.send(payload);
            return true;
        } catch (Exception e) {
            toastManager.error("Not connected to server bridge");
            return false;
        }
    }

    /** Validates a file path's extension against the allowed list. Returns true if allowed. */
    private boolean validateExtension(String path) {
        if (!ConnectionState.getInstance().isExtensionAllowed(path)) {
            toastManager.error("File type not allowed: " + path);
            return false;
        }
        return true;
    }

    /** Validates file content size against the server limit. Returns true if allowed. */
    private boolean validateFileSize(String content) {
        int sizeBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        ConnectionState conn = ConnectionState.getInstance();
        if (!conn.isFileSizeAllowed(sizeBytes)) {
            toastManager.error("File too large (" + (sizeBytes / 1024) + " KB). Max: " + conn.getMaxFileSizeDisplay());
            return false;
        }
        return true;
    }

    // =========================================================================
    // DEPENDENCY GRAPH
    // =========================================================================

    /** Opens the dependency graph screen. */
    private void openDependencyGraph() {
        client.setScreen(new DependencyGraphScreen(this));
    }

    /** Called by PacketHandler when graph data arrives while this screen is open. */
    public void onGraphReceived(SkriptPackets.DependencyGraphResponse response) {
        // If graph data arrives while we're in the editor, switch to graph screen
        DependencyGraphScreen graphScreen = new DependencyGraphScreen(this);
        client.setScreen(graphScreen);
        graphScreen.onGraphReceived(response);
    }

    /**
     * Called from DependencyGraphScreen when the user double-clicks a node
     * to open that script file in the editor.
     */
    public void onFileContentRequested(String path) {
        openFile(path);
    }
}
