package com.skripteditor.gui;

import com.skripteditor.gui.widget.DependencyGraphWidget;
import com.skripteditor.gui.widget.ToastManager;
import com.skripteditor.network.SkriptPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Standalone screen for viewing the script dependency graph.
 * Opened from the editor via F7. Shows an interactive force-directed
 * graph of all .sk files and their cross-file function references.
 *
 * Toolbar along the top provides: Back to Editor, Reset Layout,
 * Zoom to Fit, Refresh, and status info.
 *
 * Double-clicking a node opens that script back in the editor.
 */
public class DependencyGraphScreen extends Screen {

    private static final int TOOLBAR_HEIGHT = 22;
    private static final int BG_COLOR = 0xFF11111B;
    private static final int TOOLBAR_BG = 0xFF181825;
    private static final int TOOLBAR_TEXT = 0xFFCDD6F4;
    private static final int TOOLBAR_DIM = 0xFF6C7086;
    private static final int BUTTON_BG = 0xFF313244;
    private static final int BUTTON_HOVER = 0xFF45475A;
    private static final int BUTTON_TEXT = 0xFFCDD6F4;
    private static final int ACCENT = 0xFF89B4FA;

    private DependencyGraphWidget graphWidget;
    private ToastManager toastManager;
    private final Screen parentScreen;

    // Toolbar button definitions
    private static final String[] BUTTON_LABELS = {"< Editor", "Reset Layout", "Zoom to Fit", "Refresh"};
    private int[] buttonXPositions;
    private int[] buttonWidths;
    private int hoveredButton = -1;

    // Loading state
    private boolean loading = false;
    private String statusMessage = "Press Refresh or F5 to load dependency data";

    public DependencyGraphScreen(Screen parent) {
        super(Text.translatable("skripteditor.depgraph.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        toastManager = new ToastManager();
        graphWidget = new DependencyGraphWidget(0, TOOLBAR_HEIGHT, width, height - TOOLBAR_HEIGHT);
        graphWidget.setOnOpenFile(this::openFileInEditor);

        // Calculate toolbar button positions
        buttonXPositions = new int[BUTTON_LABELS.length];
        buttonWidths = new int[BUTTON_LABELS.length];
        int bx = 4;
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            buttonWidths[i] = textRenderer.getWidth(BUTTON_LABELS[i]) + 12;
            buttonXPositions[i] = bx;
            bx += buttonWidths[i] + 4;
        }

        // Auto-request graph data if we have a connection
        requestGraphData();
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(0, 0, width, height, BG_COLOR);

        // Graph
        graphWidget.render(context, textRenderer, mouseX, mouseY);

        // Loading overlay
        if (loading) {
            renderLoadingOverlay(context);
        } else if (!graphWidget.hasData()) {
            renderEmptyState(context);
        }

        // Toolbar (on top)
        renderToolbar(context, mouseX, mouseY);

        // Toasts
        toastManager.render(context, textRenderer, width, TOOLBAR_HEIGHT + 4);
    }

    private void renderToolbar(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, TOOLBAR_HEIGHT, TOOLBAR_BG);
        context.fill(0, TOOLBAR_HEIGHT - 1, width, TOOLBAR_HEIGHT, 0xFF313244);

        // Update hover state
        hoveredButton = -1;
        if (mouseY < TOOLBAR_HEIGHT && mouseY >= 0) {
            for (int i = 0; i < BUTTON_LABELS.length; i++) {
                if (mouseX >= buttonXPositions[i] && mouseX < buttonXPositions[i] + buttonWidths[i]) {
                    hoveredButton = i;
                    break;
                }
            }
        }

        // Draw buttons
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            int bx = buttonXPositions[i];
            int bw = buttonWidths[i];
            int bg = (i == hoveredButton) ? BUTTON_HOVER : BUTTON_BG;
            int textColor = (i == 0) ? ACCENT : BUTTON_TEXT; // "< Editor" in accent color

            context.fill(bx, 2, bx + bw, TOOLBAR_HEIGHT - 3, bg);
            context.drawText(textRenderer, BUTTON_LABELS[i], bx + 6, 6, textColor, false);
        }

        // Status info on the right side
        String status = buildStatusString();
        int statusWidth = textRenderer.getWidth(status);
        context.drawText(textRenderer, status, width - statusWidth - 8, 6, TOOLBAR_DIM, false);
    }

    private String buildStatusString() {
        if (loading) return "Loading...";
        if (!graphWidget.hasData()) return statusMessage;

        StringBuilder sb = new StringBuilder();
        sb.append(graphWidget.getNodeCount()).append(" scripts | ");
        sb.append(graphWidget.getEdgeCount()).append(" connections");
        if (!graphWidget.isStable()) sb.append(" | Simulating...");

        String sel = graphWidget.getSelectedNodePath();
        if (sel != null) {
            sb.append(" | Selected: ").append(sel);
        }
        return sb.toString();
    }

    private void renderLoadingOverlay(DrawContext context) {
        int cx = width / 2;
        int cy = height / 2;
        String loadingText = "Analyzing script dependencies...";
        int tw = textRenderer.getWidth(loadingText);
        context.fill(cx - tw / 2 - 10, cy - 14, cx + tw / 2 + 10, cy + 10, 0xC0181825);
        context.drawText(textRenderer, loadingText, cx - tw / 2, cy - 8, ACCENT, true);
    }

    private void renderEmptyState(DrawContext context) {
        int cx = width / 2;
        int cy = height / 2;

        String title = "Dependency Graph";
        int titleW = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, cx - titleW / 2, cy - 24, ACCENT, true);

        String hint = statusMessage;
        int hintW = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint, cx - hintW / 2, cy - 4, TOOLBAR_DIM, false);

        String controls = "Pan: drag | Zoom: scroll | Select: click | Open: double-click";
        int ctrlW = textRenderer.getWidth(controls);
        context.drawText(textRenderer, controls, cx - ctrlW / 2, cy + 14, 0xFF45475A, false);
    }

    // =========================================================================
    // INPUT
    // =========================================================================

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToEditor();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F5) {
            requestGraphData();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F) {
            graphWidget.zoomToFit();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            graphWidget.resetLayout();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Toolbar clicks
        if (mouseY < TOOLBAR_HEIGHT && button == 0) {
            if (hoveredButton >= 0) {
                handleToolbarButton(hoveredButton);
                return true;
            }
        }

        // Forward to graph widget
        if (graphWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (graphWidget.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        graphWidget.mouseReleased(click.x(), click.y(), click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (graphWidget.mouseScrolled(mouseX, mouseY, vAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // =========================================================================
    // TOOLBAR ACTIONS
    // =========================================================================

    private void handleToolbarButton(int index) {
        switch (index) {
            case 0 -> returnToEditor();
            case 1 -> graphWidget.resetLayout();
            case 2 -> graphWidget.zoomToFit();
            case 3 -> requestGraphData();
        }
    }

    private void returnToEditor() {
        client.setScreen(parentScreen);
    }

    private void openFileInEditor(String path) {
        // Return to editor and request to open this file
        if (parentScreen instanceof SkriptEditorScreen editor) {
            client.setScreen(editor);
            editor.onFileContentRequested(path);
        } else {
            returnToEditor();
        }
    }

    // =========================================================================
    // NETWORK
    // =========================================================================

    private void requestGraphData() {
        loading = true;
        statusMessage = "Requesting dependency data from server...";
        try {
            ClientPlayNetworking.send(new SkriptPackets.DependencyGraphRequest());
        } catch (Exception e) {
            loading = false;
            statusMessage = "Not connected to server bridge";
            toastManager.error("Failed to request graph: " + e.getMessage());
        }
    }

    /** Called by PacketHandler when the server sends back graph data. */
    public void onGraphReceived(SkriptPackets.DependencyGraphResponse response) {
        loading = false;
        if (response.success()) {
            graphWidget.loadGraph(response.nodes(), response.edges());
            if (response.nodes().isEmpty()) {
                statusMessage = "No scripts found on server";
                toastManager.info("No scripts found");
            } else {
                toastManager.success("Loaded " + response.nodes().size() + " scripts, "
                        + response.edges().size() + " connections");
            }
        } else {
            statusMessage = "Error: " + response.error();
            toastManager.error(response.error());
        }
    }
}
