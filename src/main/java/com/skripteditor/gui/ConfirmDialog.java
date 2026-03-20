package com.skripteditor.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Modal confirmation dialog with a message and Yes/No buttons.
 * Renders on top of the editor screen with a dimmed overlay.
 */
public class ConfirmDialog {

    private static final int DIALOG_WIDTH = 260;
    private static final int DIALOG_HEIGHT = 80;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PADDING = 10;

    // Colors
    private static final int OVERLAY_COLOR = 0x80000000;
    private static final int BG_COLOR      = 0xFF1E1E2E;
    private static final int BORDER_COLOR  = 0xFF45475A;
    private static final int TITLE_COLOR   = 0xFFCDD6F4;
    private static final int TEXT_COLOR    = 0xFFBAC2DE;
    private static final int BTN_CONFIRM_BG  = 0xFF264032;
    private static final int BTN_CONFIRM_HOVER = 0xFF305040;
    private static final int BTN_CANCEL_BG   = 0xFF402626;
    private static final int BTN_CANCEL_HOVER = 0xFF503030;
    private static final int BTN_TEXT      = 0xFFCDD6F4;

    private boolean visible = false;
    private String title = "";
    private String message = "";
    private Consumer<Boolean> callback;
    private int screenWidth, screenHeight;

    /** Shows the dialog with a message. Callback receives true for confirm, false for cancel. */
    public void show(String title, String message, Consumer<Boolean> callback) {
        this.title = title;
        this.message = message;
        this.callback = callback;
        this.visible = true;
    }

    public boolean isVisible() { return visible; }

    public void hide() {
        this.visible = false;
        this.callback = null;
    }

    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!visible) return;

        int dialogX = (screenWidth - DIALOG_WIDTH) / 2;
        int dialogY = (screenHeight - DIALOG_HEIGHT) / 2;

        // Dim overlay
        context.fill(0, 0, screenWidth, screenHeight, OVERLAY_COLOR);

        // Dialog background
        context.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, BG_COLOR);
        // Border
        context.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 1, BORDER_COLOR);
        context.fill(dialogX, dialogY + DIALOG_HEIGHT - 1, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, BORDER_COLOR);
        context.fill(dialogX, dialogY, dialogX + 1, dialogY + DIALOG_HEIGHT, BORDER_COLOR);
        context.fill(dialogX + DIALOG_WIDTH - 1, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, BORDER_COLOR);

        // Title
        context.drawText(textRenderer, title, dialogX + PADDING, dialogY + PADDING, TITLE_COLOR, false);

        // Message
        context.drawText(textRenderer, message, dialogX + PADDING, dialogY + PADDING + 14, TEXT_COLOR, false);

        // Buttons
        int btnY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - PADDING;
        int confirmX = dialogX + DIALOG_WIDTH / 2 - BUTTON_WIDTH - 5;
        int cancelX = dialogX + DIALOG_WIDTH / 2 + 5;

        // Confirm button
        boolean confirmHover = mouseX >= confirmX && mouseX < confirmX + BUTTON_WIDTH
                && mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT;
        context.fill(confirmX, btnY, confirmX + BUTTON_WIDTH, btnY + BUTTON_HEIGHT,
                confirmHover ? BTN_CONFIRM_HOVER : BTN_CONFIRM_BG);
        String yesText = "Yes";
        int yesX = confirmX + (BUTTON_WIDTH - textRenderer.getWidth(yesText)) / 2;
        context.drawText(textRenderer, yesText, yesX, btnY + 5, BTN_TEXT, false);

        // Cancel button
        boolean cancelHover = mouseX >= cancelX && mouseX < cancelX + BUTTON_WIDTH
                && mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT;
        context.fill(cancelX, btnY, cancelX + BUTTON_WIDTH, btnY + BUTTON_HEIGHT,
                cancelHover ? BTN_CANCEL_HOVER : BTN_CANCEL_BG);
        String noText = "No";
        int noX = cancelX + (BUTTON_WIDTH - textRenderer.getWidth(noText)) / 2;
        context.drawText(textRenderer, noText, noX, btnY + 5, BTN_TEXT, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int dialogX = (screenWidth - DIALOG_WIDTH) / 2;
        int dialogY = (screenHeight - DIALOG_HEIGHT) / 2;
        int btnY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - PADDING;
        int confirmX = dialogX + DIALOG_WIDTH / 2 - BUTTON_WIDTH - 5;
        int cancelX = dialogX + DIALOG_WIDTH / 2 + 5;

        if (mouseY >= btnY && mouseY < btnY + BUTTON_HEIGHT) {
            if (mouseX >= confirmX && mouseX < confirmX + BUTTON_WIDTH) {
                if (callback != null) callback.accept(true);
                hide();
                return true;
            }
            if (mouseX >= cancelX && mouseX < cancelX + BUTTON_WIDTH) {
                if (callback != null) callback.accept(false);
                hide();
                return true;
            }
        }

        return true; // Consume all clicks while dialog is showing
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (callback != null) callback.accept(true);
            hide();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (callback != null) callback.accept(false);
            hide();
            return true;
        }
        return true; // Consume all keys while showing
    }
}
