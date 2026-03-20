package com.skripteditor.gui.widget;

import com.skripteditor.editor.EditorTab;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Horizontal tab bar showing all open editor tabs.
 * Supports click-to-switch, middle-click-to-close, and visual indicators
 * for modified (unsaved) files.
 */
public class TabBarWidget {

    private static final int TAB_HEIGHT = 18;
    private static final int TAB_PADDING = 8;
    private static final int CLOSE_BTN_SIZE = 7;
    private static final int MAX_TAB_TEXT_WIDTH = 100;

    // Colors
    private static final int BG_COLOR       = 0xFF11111B;
    private static final int ACTIVE_BG      = 0xFF1E1E2E;
    private static final int INACTIVE_BG    = 0xFF181825;
    private static final int HOVER_BG       = 0xFF252536;
    private static final int TEXT_COLOR      = 0xFFCDD6F4;
    private static final int INACTIVE_TEXT   = 0xFF6C7086;
    private static final int MODIFIED_DOT    = 0xFFF9E2AF;
    private static final int CLOSE_COLOR     = 0xFF6C7086;
    private static final int CLOSE_HOVER     = 0xFFF38BA8;
    private static final int BORDER_BOTTOM   = 0xFF313244;

    private int x, y, width;
    private int activeIndex = -1;
    private int scrollOffset = 0;

    private IntConsumer onTabSelected;
    private IntConsumer onTabCloseRequested;

    public TabBarWidget(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void setOnTabSelected(IntConsumer callback) { this.onTabSelected = callback; }
    public void setOnTabCloseRequested(IntConsumer callback) { this.onTabCloseRequested = callback; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return TAB_HEIGHT; }

    public void setActiveIndex(int index) { this.activeIndex = index; }

    public void render(DrawContext context, TextRenderer textRenderer, List<EditorTab> tabs,
                       int mouseX, int mouseY) {
        // Background bar
        context.fill(x, y, x + width, y + TAB_HEIGHT, BG_COLOR);
        context.fill(x, y + TAB_HEIGHT - 1, x + width, y + TAB_HEIGHT, BORDER_BOTTOM);

        if (tabs.isEmpty()) {
            context.drawText(textRenderer, "No files open", x + 8, y + 5, INACTIVE_TEXT, false);
            return;
        }

        context.enableScissor(x, y, x + width, y + TAB_HEIGHT);

        int tabX = x - scrollOffset + 2;
        for (int i = 0; i < tabs.size(); i++) {
            EditorTab tab = tabs.get(i);
            String label = tab.getFileName();
            int textW = Math.min(textRenderer.getWidth(label), MAX_TAB_TEXT_WIDTH);
            int tabWidth = textW + TAB_PADDING * 2 + CLOSE_BTN_SIZE + 8;

            boolean isActive = i == activeIndex;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= y && mouseY < y + TAB_HEIGHT;

            // Tab background
            int bgColor = isActive ? ACTIVE_BG : (isHovered ? HOVER_BG : INACTIVE_BG);
            context.fill(tabX, y, tabX + tabWidth, y + TAB_HEIGHT - 1, bgColor);

            // Active tab top accent
            if (isActive) {
                context.fill(tabX, y, tabX + tabWidth, y + 2, 0xFF89B4FA);
            }

            // Tab separator
            context.fill(tabX + tabWidth, y + 3, tabX + tabWidth + 1, y + TAB_HEIGHT - 4, 0xFF313244);

            // Modified indicator (dot before filename)
            int textStartX = tabX + TAB_PADDING;
            if (tab.isModified()) {
                context.fill(textStartX, y + 7, textStartX + 4, y + 11, MODIFIED_DOT);
                textStartX += 6;
            }

            // Tab label
            int textColor = isActive ? TEXT_COLOR : INACTIVE_TEXT;
            context.drawText(textRenderer, label, textStartX, y + 5, textColor, false);

            // Close button (x)
            int closeX = tabX + tabWidth - CLOSE_BTN_SIZE - TAB_PADDING + 2;
            int closeY = y + (TAB_HEIGHT - CLOSE_BTN_SIZE) / 2;
            boolean closeHovered = mouseX >= closeX - 2 && mouseX < closeX + CLOSE_BTN_SIZE + 2
                    && mouseY >= closeY - 2 && mouseY < closeY + CLOSE_BTN_SIZE + 2
                    && isHovered;
            int closeColor = closeHovered ? CLOSE_HOVER : CLOSE_COLOR;
            context.drawText(textRenderer, "x", closeX, closeY, closeColor, false);

            tabX += tabWidth + 1;
        }

        context.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, List<EditorTab> tabs) {
        if (mouseY < y || mouseY >= y + TAB_HEIGHT || mouseX < x || mouseX >= x + width) {
            return false;
        }

        int tabX = x - scrollOffset + 2;
        TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;

        for (int i = 0; i < tabs.size(); i++) {
            EditorTab tab = tabs.get(i);
            String label = tab.getFileName();
            int textW = Math.min(textRenderer.getWidth(label), MAX_TAB_TEXT_WIDTH);
            int tabWidth = textW + TAB_PADDING * 2 + CLOSE_BTN_SIZE + 8;

            if (mouseX >= tabX && mouseX < tabX + tabWidth) {
                // Check close button
                int closeX = tabX + tabWidth - CLOSE_BTN_SIZE - TAB_PADDING + 2;
                if (mouseX >= closeX - 2 && mouseX < closeX + CLOSE_BTN_SIZE + 4) {
                    if (onTabCloseRequested != null) onTabCloseRequested.accept(i);
                    return true;
                }

                // Middle click to close
                if (button == 2) {
                    if (onTabCloseRequested != null) onTabCloseRequested.accept(i);
                    return true;
                }

                // Select tab
                if (onTabSelected != null) onTabSelected.accept(i);
                return true;
            }
            tabX += tabWidth + 1;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, List<EditorTab> tabs) {
        if (mouseY < y || mouseY >= y + TAB_HEIGHT || mouseX < x || mouseX >= x + width) {
            return false;
        }
        scrollOffset = Math.max(0, scrollOffset - (int) (amount * 30));
        return true;
    }
}
