package com.skripteditor.gui.widget;

import com.skripteditor.editor.EditorTab;
import com.skripteditor.network.ConnectionState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Bottom status bar showing file path, cursor position, save state,
 * and server response messages.
 */
public class StatusBarWidget {

    private static final int HEIGHT = 16;
    private static final int PADDING = 6;

    // Colors
    private static final int BG_COLOR    = 0xFF181825;
    private static final int TEXT_COLOR  = 0xFFBAC2DE;
    private static final int ACCENT_COLOR = 0xFF89B4FA;
    private static final int MODIFIED_COLOR = 0xFFF9E2AF;
    private static final int ERROR_COLOR = 0xFFF38BA8;
    private static final int SUCCESS_COLOR = 0xFFA6E3A1;
    private static final int CONNECTED_COLOR = 0xFFA6E3A1;
    private static final int DISCONNECTED_COLOR = 0xFFF38BA8;

    private int x, y, width;
    private String serverMessage = "";
    private long serverMessageTime = 0;
    private boolean serverMessageIsError = false;

    public StatusBarWidget(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return HEIGHT; }

    /** Sets a temporary server response message to display. */
    public void setServerMessage(String message, boolean isError) {
        this.serverMessage = message;
        this.serverMessageTime = System.currentTimeMillis();
        this.serverMessageIsError = isError;
    }

    public void render(DrawContext context, TextRenderer textRenderer, EditorTab activeTab) {
        // Background
        context.fill(x, y, x + width, y + HEIGHT, BG_COLOR);
        // Top border
        context.fill(x, y, x + width, y + 1, 0xFF313244);

        int textY = y + (HEIGHT - textRenderer.fontHeight) / 2 + 1;

        // Connection status (far right, always visible)
        ConnectionState conn = ConnectionState.getInstance();
        String connText = conn.getStatusText();
        int connColor = conn.isConnected() ? CONNECTED_COLOR : DISCONNECTED_COLOR;
        int connWidth = textRenderer.getWidth(connText);
        context.drawText(textRenderer, connText, x + width - connWidth - PADDING, textY, connColor, false);

        if (activeTab == null) {
            context.drawText(textRenderer, "No file open", x + PADDING, textY, TEXT_COLOR, false);
            return;
        }

        // File path (left side)
        String pathDisplay = activeTab.getFilePath();
        context.drawText(textRenderer, pathDisplay, x + PADDING, textY, ACCENT_COLOR, false);

        // Modified indicator
        int pathWidth = textRenderer.getWidth(pathDisplay);
        if (activeTab.isModified()) {
            context.drawText(textRenderer, " [Modified]", x + PADDING + pathWidth, textY,
                    MODIFIED_COLOR, false);
        }

        // Cursor position (right side, before connection status)
        String cursorInfo = String.format("Ln %d, Col %d",
                activeTab.getCursorLine() + 1, activeTab.getCursorCol() + 1);
        int cursorInfoWidth = textRenderer.getWidth(cursorInfo);
        int cursorInfoX = x + width - connWidth - cursorInfoWidth - PADDING * 3;
        context.drawText(textRenderer, cursorInfo, cursorInfoX, textY, TEXT_COLOR, false);

        // Server message (center, fades after 5 seconds)
        if (!serverMessage.isEmpty()) {
            long age = System.currentTimeMillis() - serverMessageTime;
            if (age < 5000) {
                float opacity = age > 4000 ? 1f - (age - 4000) / 1000f : 1f;
                int alpha = (int) (opacity * 255);
                int msgColor = serverMessageIsError ? ERROR_COLOR : SUCCESS_COLOR;
                msgColor = (alpha << 24) | (msgColor & 0x00FFFFFF);

                int msgWidth = textRenderer.getWidth(serverMessage);
                int msgX = x + (width - msgWidth) / 2;
                context.drawText(textRenderer, serverMessage, msgX, textY, msgColor, false);
            }
        }

        // Save indicator
        if (activeTab.isPendingSave()) {
            String saving = "Saving...";
            int savingWidth = textRenderer.getWidth(saving);
            context.drawText(textRenderer, saving, x + width - cursorInfoWidth - savingWidth - PADDING * 3,
                    textY, MODIFIED_COLOR, false);
        }
    }
}
