package com.skripteditor.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

/**
 * Search/find bar widget that appears at the top of the editor area.
 * Supports case-sensitive search and find next/previous navigation.
 */
public class SearchBarWidget {

    private static final int HEIGHT = 22;
    private static final int PADDING = 4;
    private static final int INPUT_HEIGHT = 14;

    // Colors
    private static final int BG_COLOR       = 0xFF1E1E2E;
    private static final int INPUT_BG       = 0xFF313244;
    private static final int INPUT_BORDER   = 0xFF45475A;
    private static final int INPUT_FOCUSED   = 0xFF89B4FA;
    private static final int TEXT_COLOR     = 0xFFCDD6F4;
    private static final int PLACEHOLDER   = 0xFF6C7086;
    private static final int MATCH_INFO     = 0xFF6C7086;
    private static final int BUTTON_BG      = 0xFF313244;
    private static final int BUTTON_HOVER   = 0xFF45475A;
    private static final int BUTTON_TEXT    = 0xFFCDD6F4;

    private int x, y, width;
    private boolean visible = false;
    private boolean focused = false;
    private String query = "";
    private int cursorPos = 0;
    private boolean caseSensitive = false;
    private int matchCount = 0;
    private int currentMatch = 0;

    // Callbacks: (query, caseSensitive)
    private BiConsumer<String, Boolean> onSearch;
    private Runnable onFindNext;
    private Runnable onFindPrevious;
    private Runnable onClose;

    public SearchBarWidget(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void setOnSearch(BiConsumer<String, Boolean> callback) { this.onSearch = callback; }
    public void setOnFindNext(Runnable callback) { this.onFindNext = callback; }
    public void setOnFindPrevious(Runnable callback) { this.onFindPrevious = callback; }
    public void setOnClose(Runnable callback) { this.onClose = callback; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return visible ? HEIGHT : 0; }
    public boolean isVisible() { return visible; }
    public boolean isFocused() { return focused; }
    public String getQuery() { return query; }
    public boolean isCaseSensitive() { return caseSensitive; }

    public void show() {
        this.visible = true;
        this.focused = true;
    }

    public void hide() {
        this.visible = false;
        this.focused = false;
        this.query = "";
        this.cursorPos = 0;
    }

    public void setFocused(boolean focused) { this.focused = focused; }
    public void setMatchInfo(int current, int total) {
        this.currentMatch = current;
        this.matchCount = total;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!visible) return;

        // Background
        context.fill(x, y, x + width, y + HEIGHT, BG_COLOR);
        context.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, 0xFF313244);

        int inputX = x + PADDING;
        int inputY = y + (HEIGHT - INPUT_HEIGHT) / 2;
        int inputWidth = width - 180;

        // Input field
        int borderColor = focused ? INPUT_FOCUSED : INPUT_BORDER;
        context.fill(inputX, inputY, inputX + inputWidth, inputY + INPUT_HEIGHT, INPUT_BG);
        context.fill(inputX, inputY, inputX + inputWidth, inputY + 1, borderColor);
        context.fill(inputX, inputY + INPUT_HEIGHT - 1, inputX + inputWidth, inputY + INPUT_HEIGHT, borderColor);
        context.fill(inputX, inputY, inputX + 1, inputY + INPUT_HEIGHT, borderColor);
        context.fill(inputX + inputWidth - 1, inputY, inputX + inputWidth, inputY + INPUT_HEIGHT, borderColor);

        int textY = inputY + (INPUT_HEIGHT - textRenderer.fontHeight) / 2;

        // Query text or placeholder
        if (query.isEmpty()) {
            context.drawText(textRenderer, "Find...", inputX + 3, textY, PLACEHOLDER, false);
        } else {
            context.drawText(textRenderer, query, inputX + 3, textY, TEXT_COLOR, false);
        }

        // Cursor
        if (focused) {
            long time = System.currentTimeMillis();
            if ((time / 530) % 2 == 0) {
                int cursorX = inputX + 3 + textRenderer.getWidth(query.substring(0, cursorPos));
                context.fill(cursorX, inputY + 2, cursorX + 1, inputY + INPUT_HEIGHT - 2, TEXT_COLOR);
            }
        }

        // Match count info
        int infoX = inputX + inputWidth + PADDING;
        if (!query.isEmpty()) {
            String info = matchCount > 0 ? (currentMatch + "/" + matchCount) : "No results";
            context.drawText(textRenderer, info, infoX, textY, MATCH_INFO, false);
            infoX += textRenderer.getWidth(info) + PADDING;
        }

        // Buttons: Previous, Next, Case, Close
        infoX = renderButton(context, textRenderer, "<", infoX, inputY, mouseX, mouseY);
        infoX = renderButton(context, textRenderer, ">", infoX + 2, inputY, mouseX, mouseY);
        infoX = renderButton(context, textRenderer, caseSensitive ? "Aa" : "aa",
                infoX + 2, inputY, mouseX, mouseY);
        renderButton(context, textRenderer, "X", infoX + 4, inputY, mouseX, mouseY);
    }

    private int renderButton(DrawContext context, TextRenderer textRenderer, String label,
                              int bx, int by, int mouseX, int mouseY) {
        int bw = textRenderer.getWidth(label) + 6;
        boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + INPUT_HEIGHT;
        context.fill(bx, by, bx + bw, by + INPUT_HEIGHT, hovered ? BUTTON_HOVER : BUTTON_BG);
        int textY = by + (INPUT_HEIGHT - textRenderer.fontHeight) / 2;
        context.drawText(textRenderer, label, bx + 3, textY, BUTTON_TEXT, false);
        return bx + bw;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (mouseY < y || mouseY >= y + HEIGHT || mouseX < x || mouseX >= x + width) {
            focused = false;
            return false;
        }

        int inputX = x + PADDING;
        int inputY = y + (HEIGHT - INPUT_HEIGHT) / 2;
        int inputWidth = width - 180;

        // Check if clicked in input
        if (mouseX >= inputX && mouseX < inputX + inputWidth
                && mouseY >= inputY && mouseY < inputY + INPUT_HEIGHT) {
            focused = true;
            return true;
        }

        // Check buttons
        TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int infoX = inputX + inputWidth + PADDING;
        if (!query.isEmpty()) {
            String info = matchCount > 0 ? (currentMatch + "/" + matchCount) : "No results";
            infoX += textRenderer.getWidth(info) + PADDING;
        }

        // Previous button
        int bw = textRenderer.getWidth("<") + 6;
        if (mouseX >= infoX && mouseX < infoX + bw) {
            if (onFindPrevious != null) onFindPrevious.run();
            return true;
        }
        infoX += bw + 2;

        // Next button
        bw = textRenderer.getWidth(">") + 6;
        if (mouseX >= infoX && mouseX < infoX + bw) {
            if (onFindNext != null) onFindNext.run();
            return true;
        }
        infoX += bw + 2;

        // Case sensitivity toggle
        String caseLabel = caseSensitive ? "Aa" : "aa";
        bw = textRenderer.getWidth(caseLabel) + 6;
        if (mouseX >= infoX && mouseX < infoX + bw) {
            caseSensitive = !caseSensitive;
            triggerSearch();
            return true;
        }
        infoX += bw + 4;

        // Close button
        bw = textRenderer.getWidth("X") + 6;
        if (mouseX >= infoX && mouseX < infoX + bw) {
            if (onClose != null) onClose.run();
            return true;
        }

        focused = true;
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !focused) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (onClose != null) onClose.run();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift) {
                if (onFindPrevious != null) onFindPrevious.run();
            } else {
                if (onFindNext != null) onFindNext.run();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0) {
                query = query.substring(0, cursorPos - 1) + query.substring(cursorPos);
                cursorPos--;
                triggerSearch();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < query.length()) {
                query = query.substring(0, cursorPos) + query.substring(cursorPos + 1);
                triggerSearch();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            cursorPos = Math.max(0, cursorPos - 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            cursorPos = Math.min(query.length(), cursorPos + 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            cursorPos = query.length();
            return true;
        }

        // Ctrl+A select all (in search field)
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            cursorPos = query.length();
            return true;
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!visible || !focused) return false;
        if (chr < 32) return false; // Control characters

        query = query.substring(0, cursorPos) + chr + query.substring(cursorPos);
        cursorPos++;
        triggerSearch();
        return true;
    }

    private void triggerSearch() {
        if (onSearch != null) {
            onSearch.accept(query, caseSensitive);
        }
    }
}
