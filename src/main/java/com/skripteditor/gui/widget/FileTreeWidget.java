package com.skripteditor.gui.widget;

import com.skripteditor.network.SkriptPackets;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.*;
import java.util.function.Consumer;

/**
 * Sidebar widget displaying the file/folder tree for the Skript scripts directory.
 * Supports expanding/collapsing folders, selection, and scrolling.
 */
public class FileTreeWidget {

    // Layout constants
    private static final int ITEM_HEIGHT = 14;
    private static final int INDENT_SIZE = 10;
    private static final int ICON_WIDTH = 10;
    private static final int PADDING = 4;

    // Colors
    private static final int BG_COLOR         = 0xFF181825;
    private static final int SELECTED_BG      = 0xFF313244;
    private static final int HOVER_BG         = 0xFF252536;
    private static final int TEXT_COLOR        = 0xFFCDD6F4;
    private static final int FOLDER_COLOR     = 0xFFF9E2AF;
    private static final int FILE_SK_COLOR    = 0xFF89B4FA;
    private static final int FILE_OTHER_COLOR = 0xFF6C7086;
    private static final int BORDER_COLOR     = 0xFF313244;

    /** A node in the file tree. */
    public static class TreeNode {
        public final String name;
        public final String path; // Relative path within scripts dir
        public final boolean isDirectory;
        public boolean expanded = false;
        public List<TreeNode> children = new ArrayList<>();
        public int depth = 0;

        public TreeNode(String name, String path, boolean isDirectory, int depth) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.depth = depth;
        }
    }

    private int x, y, width, height;
    private final List<TreeNode> rootNodes = new ArrayList<>();
    private final Map<String, TreeNode> nodesByPath = new HashMap<>();
    private String selectedPath = null;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    // Cached flattened visible nodes list - invalidated on structure changes
    private List<TreeNode> cachedVisibleNodes = null;

    private Consumer<String> onFileSelected;       // Callback when a file is clicked
    private Consumer<String> onDirectoryExpanded;   // Callback when a folder needs loading
    private Consumer<String> onFileRightClicked;    // Callback for context actions

    public FileTreeWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // --- Callbacks ---
    public void setOnFileSelected(Consumer<String> callback) { this.onFileSelected = callback; }
    public void setOnDirectoryExpanded(Consumer<String> callback) { this.onDirectoryExpanded = callback; }
    public void setOnFileRightClicked(Consumer<String> callback) { this.onFileRightClicked = callback; }

    // --- Position/size ---
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public int getX() { return x; }
    public int getWidth() { return width; }

    /**
     * Updates the tree with a new file listing for the given directory path.
     * Called when a FileListResponse is received from the server.
     */
    public void updateListing(String parentPath, List<SkriptPackets.FileEntry> entries) {
        if (parentPath.isEmpty() || parentPath.equals("/") || parentPath.equals(".")) {
            // Root listing
            rootNodes.clear();
            nodesByPath.clear();
            for (SkriptPackets.FileEntry entry : entries) {
                TreeNode node = new TreeNode(entry.name(), entry.name(), entry.isDirectory(), 0);
                rootNodes.add(node);
                nodesByPath.put(node.path, node);
            }
            sortNodes(rootNodes);
        } else {
            // Sub-directory listing - find parent and update children
            TreeNode parent = nodesByPath.get(parentPath);
            if (parent != null) {
                parent.children.clear();
                for (SkriptPackets.FileEntry entry : entries) {
                    String childPath = parentPath + "/" + entry.name();
                    TreeNode child = new TreeNode(entry.name(), childPath, entry.isDirectory(), parent.depth + 1);
                    parent.children.add(child);
                    nodesByPath.put(childPath, child);
                }
                sortNodes(parent.children);
                parent.expanded = true;
            }
        }
        invalidateVisibleCache();
    }

    /** Sorts nodes: directories first, then alphabetically by name. */
    private void sortNodes(List<TreeNode> nodes) {
        nodes.sort((a, b) -> {
            if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    /** Returns a flattened list of all visible (expanded) nodes for rendering. Cached. */
    private List<TreeNode> getVisibleNodes() {
        if (cachedVisibleNodes == null) {
            cachedVisibleNodes = new ArrayList<>();
            for (TreeNode root : rootNodes) {
                addVisibleRecursive(root, cachedVisibleNodes);
            }
        }
        return cachedVisibleNodes;
    }

    private void invalidateVisibleCache() {
        cachedVisibleNodes = null;
    }

    private void addVisibleRecursive(TreeNode node, List<TreeNode> list) {
        list.add(node);
        if (node.isDirectory && node.expanded) {
            for (TreeNode child : node.children) {
                addVisibleRecursive(child, list);
            }
        }
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);
        // Right border
        context.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Header
        context.fill(x, y, x + width, y + ITEM_HEIGHT + 2, 0xFF11111B);
        context.drawText(textRenderer, "Scripts", x + PADDING, y + 3, FOLDER_COLOR, false);

        int contentY = y + ITEM_HEIGHT + 3;
        int contentHeight = height - ITEM_HEIGHT - 3;

        context.enableScissor(x, contentY, x + width - 1, contentY + contentHeight);

        List<TreeNode> visible = getVisibleNodes();
        int maxScroll = Math.max(0, visible.size() * ITEM_HEIGHT - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        hoveredIndex = -1;

        for (int i = 0; i < visible.size(); i++) {
            int itemY = contentY + i * ITEM_HEIGHT - scrollOffset;
            if (itemY + ITEM_HEIGHT < contentY || itemY > contentY + contentHeight) continue;

            TreeNode node = visible.get(i);
            int itemX = x + PADDING + node.depth * INDENT_SIZE;

            // Hover detection
            boolean hovered = mouseX >= x && mouseX < x + width - 1
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) hoveredIndex = i;

            // Selection/hover background
            if (node.path.equals(selectedPath)) {
                context.fill(x, itemY, x + width - 1, itemY + ITEM_HEIGHT, SELECTED_BG);
            } else if (hovered) {
                context.fill(x, itemY, x + width - 1, itemY + ITEM_HEIGHT, HOVER_BG);
            }

            // Directory expand/collapse indicator
            if (node.isDirectory) {
                String arrow = node.expanded ? "v" : ">";
                context.drawText(textRenderer, arrow, itemX, itemY + 3, FOLDER_COLOR, false);
                context.drawText(textRenderer, node.name, itemX + ICON_WIDTH, itemY + 3, FOLDER_COLOR, false);
            } else {
                // File icon - color based on extension
                int fileColor = node.name.endsWith(".sk") ? FILE_SK_COLOR : FILE_OTHER_COLOR;
                context.drawText(textRenderer, node.name, itemX + ICON_WIDTH, itemY + 3, fileColor, false);
            }
        }

        context.disableScissor();

        // Scrollbar if needed
        if (visible.size() * ITEM_HEIGHT > contentHeight) {
            renderScrollbar(context, contentY, contentHeight, visible.size() * ITEM_HEIGHT);
        }
    }

    private void renderScrollbar(DrawContext context, int contentY, int contentHeight, int totalHeight) {
        int scrollbarX = x + width - 4;
        float ratio = (float) contentHeight / totalHeight;
        int barHeight = Math.max(10, (int) (contentHeight * ratio));
        int maxScroll = totalHeight - contentHeight;
        int barY = contentY + (maxScroll > 0 ? (int) ((float) scrollOffset / maxScroll * (contentHeight - barHeight)) : 0);
        context.fill(scrollbarX, barY, scrollbarX + 3, barY + barHeight, 0xFF45475A);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        List<TreeNode> visible = getVisibleNodes();
        int contentY = y + ITEM_HEIGHT + 3;

        for (int i = 0; i < visible.size(); i++) {
            int itemY = contentY + i * ITEM_HEIGHT - scrollOffset;
            if (mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                TreeNode node = visible.get(i);

                if (button == 1 && onFileRightClicked != null) {
                    // Right click
                    onFileRightClicked.accept(node.path);
                    return true;
                }

                if (node.isDirectory) {
                    if (node.expanded) {
                        node.expanded = false;
                        invalidateVisibleCache();
                    } else {
                        // Request directory contents from server if not loaded
                        if (node.children.isEmpty() && onDirectoryExpanded != null) {
                            onDirectoryExpanded.accept(node.path);
                        } else {
                            node.expanded = true;
                            invalidateVisibleCache();
                        }
                    }
                } else {
                    selectedPath = node.path;
                    if (onFileSelected != null) {
                        onFileSelected.accept(node.path);
                    }
                }
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scrollOffset = Math.max(0, scrollOffset - (int) (amount * ITEM_HEIGHT * 3));
        return true;
    }

    public void setSelectedPath(String path) { this.selectedPath = path; }
    public String getSelectedPath() { return selectedPath; }

    /** Clears the entire tree. */
    public void clear() {
        rootNodes.clear();
        nodesByPath.clear();
        selectedPath = null;
        scrollOffset = 0;
        invalidateVisibleCache();
    }
}
