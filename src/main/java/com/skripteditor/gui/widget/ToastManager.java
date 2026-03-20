package com.skripteditor.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages and renders toast notifications (brief messages) in the editor UI.
 * Toasts appear in the top-right corner and fade out after a duration.
 */
public class ToastManager {

    /** The three severity levels for toast messages. */
    public enum Level { INFO, SUCCESS, ERROR }

    private static final int TOAST_DURATION_MS = 4000;
    private static final int FADE_DURATION_MS = 500;
    private static final int TOAST_PADDING = 6;
    private static final int TOAST_MARGIN = 4;
    private static final int TOAST_MAX_WIDTH = 250;
    private static final int MAX_VISIBLE = 5;

    // Colors (ARGB)
    private static final int BG_INFO    = 0xE0313244;
    private static final int BG_SUCCESS = 0xE0264032;
    private static final int BG_ERROR   = 0xE0402626;
    private static final int TEXT_INFO    = 0xFF89B4FA;
    private static final int TEXT_SUCCESS = 0xFFA6E3A1;
    private static final int TEXT_ERROR   = 0xFFF38BA8;
    private static final int BORDER_INFO    = 0xFF89B4FA;
    private static final int BORDER_SUCCESS = 0xFFA6E3A1;
    private static final int BORDER_ERROR   = 0xFFF38BA8;

    /** A single toast notification. */
    private static class Toast {
        final String message;
        final Level level;
        final long createdAt;

        Toast(String message, Level level) {
            this.message = message;
            this.level = level;
            this.createdAt = System.currentTimeMillis();
        }

        /** Returns opacity from 0.0 to 1.0. */
        float getOpacity() {
            long age = System.currentTimeMillis() - createdAt;
            if (age > TOAST_DURATION_MS) {
                long fadeAge = age - TOAST_DURATION_MS;
                return Math.max(0f, 1f - (float) fadeAge / FADE_DURATION_MS);
            }
            // Fade in quickly
            return Math.min(1f, age / 150f);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TOAST_DURATION_MS + FADE_DURATION_MS;
        }
    }

    private final List<Toast> toasts = new ArrayList<>();

    /** Shows a new toast notification. */
    public void show(String message, Level level) {
        toasts.add(new Toast(message, level));
        // Trim oldest if too many
        while (toasts.size() > MAX_VISIBLE) {
            toasts.remove(0);
        }
    }

    public void info(String message)    { show(message, Level.INFO); }
    public void success(String message) { show(message, Level.SUCCESS); }
    public void error(String message)   { show(message, Level.ERROR); }

    /**
     * Renders all active toasts in the top-right area of the given screen region.
     * @param screenWidth  total width of the screen
     * @param topY         y offset from the top where toasts should start
     */
    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int topY) {
        // Remove expired toasts
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired()) {
                it.remove();
            }
        }

        int y = topY + TOAST_MARGIN;
        for (Toast toast : toasts) {
            float opacity = toast.getOpacity();
            if (opacity <= 0) continue;

            int textWidth = Math.min(textRenderer.getWidth(toast.message), TOAST_MAX_WIDTH);
            int boxWidth = textWidth + TOAST_PADDING * 2;
            int boxHeight = textRenderer.fontHeight + TOAST_PADDING * 2;
            int x = screenWidth - boxWidth - TOAST_MARGIN - 4;

            int alpha = (int) (opacity * 224); // Max alpha 0xE0
            int bgColor = applyAlpha(getBackground(toast.level), alpha);
            int borderColor = applyAlpha(getBorder(toast.level), (int) (opacity * 255));
            int textColor = applyAlpha(getTextColor(toast.level), (int) (opacity * 255));

            // Background
            context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);
            // Left accent border
            context.fill(x, y, x + 2, y + boxHeight, borderColor);
            // Text
            context.drawText(textRenderer, toast.message, x + TOAST_PADDING + 2,
                    y + TOAST_PADDING, textColor, false);

            y += boxHeight + TOAST_MARGIN;
        }
    }

    private int getBackground(Level level) {
        return switch (level) {
            case INFO -> BG_INFO;
            case SUCCESS -> BG_SUCCESS;
            case ERROR -> BG_ERROR;
        };
    }

    private int getBorder(Level level) {
        return switch (level) {
            case INFO -> BORDER_INFO;
            case SUCCESS -> BORDER_SUCCESS;
            case ERROR -> BORDER_ERROR;
        };
    }

    private int getTextColor(Level level) {
        return switch (level) {
            case INFO -> TEXT_INFO;
            case SUCCESS -> TEXT_SUCCESS;
            case ERROR -> TEXT_ERROR;
        };
    }

    /** Replaces the alpha channel of an ARGB color. */
    private int applyAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Returns true if there are any visible toasts. */
    public boolean hasVisibleToasts() {
        for (Toast t : toasts) {
            if (!t.isExpired()) return true;
        }
        return false;
    }
}
