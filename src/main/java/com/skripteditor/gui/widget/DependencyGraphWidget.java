package com.skripteditor.gui.widget;

import com.skripteditor.network.SkriptPackets;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interactive dependency graph visualization using force-directed layout.
 * Shows script files as nodes and function cross-references as directed edges.
 *
 * Controls:
 * - Click a node to select it and highlight its connections
 * - Double-click a node to open that script in the editor
 * - Drag a node to reposition it (pins it in place)
 * - Drag the background to pan the view
 * - Scroll wheel to zoom in/out
 * - Right-click a pinned node to unpin it
 */
public class DependencyGraphWidget {

    // --- Colors (Catppuccin Mocha) ---
    private static final int BG_COLOR         = 0xFF1E1E2E;
    private static final int GRID_COLOR       = 0xFF232334;
    private static final int NODE_DEFAULT     = 0xFF89B4FA; // Blue - leaf scripts
    private static final int NODE_PROVIDER    = 0xFFA6E3A1; // Green - defines functions others use
    private static final int NODE_CONSUMER    = 0xFFFAB387; // Orange - calls external functions
    private static final int NODE_BOTH        = 0xFFCBA6F7; // Purple - both provides and consumes
    private static final int NODE_ISOLATED    = 0xFF6C7086; // Gray - no connections
    private static final int NODE_SELECTED    = 0xFFF9E2AF; // Yellow highlight
    private static final int NODE_BORDER      = 0xFF313244;
    private static final int NODE_TEXT        = 0xFFCDD6F4;
    private static final int EDGE_DEFAULT     = 0x806C7086; // Semi-transparent gray
    private static final int EDGE_HIGHLIGHT   = 0xFFCBA6F7; // Purple when connected to selected
    private static final int EDGE_ARROW       = 0xFFBAC2DE;
    private static final int TOOLTIP_BG       = 0xE0181825;
    private static final int TOOLTIP_BORDER   = 0xFF313244;
    private static final int TOOLTIP_TEXT     = 0xFFCDD6F4;
    private static final int FUNC_TEXT        = 0xFF94E2D5; // Teal for function names

    // --- Layout constants ---
    private static final double REPULSION = 8000.0;
    private static final double ATTRACTION = 0.003;
    private static final double REST_LENGTH = 180.0;
    private static final double DAMPING = 0.85;
    private static final double CENTER_GRAVITY = 0.008;
    private static final double MAX_VELOCITY = 15.0;
    private static final double STABLE_THRESHOLD = 0.5;
    private static final int NODE_PADDING_X = 8;
    private static final int NODE_PADDING_Y = 4;
    private static final int NODE_HEIGHT = 18;
    private static final int ARROW_SIZE = 7;

    // --- Widget bounds ---
    private int x, y, width, height;

    // --- Camera ---
    private double camX = 0, camY = 0;
    private double zoom = 1.0;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.0;

    // --- Graph data ---
    private final List<NodeState> nodes = new ArrayList<>();
    private final List<EdgeState> edges = new ArrayList<>();
    private final List<List<Integer>> adjacency = new ArrayList<>(); // adjacency[i] = list of edge indices touching node i
    private final java.util.Set<Integer> selectedConnectedNodes = new java.util.HashSet<>();
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private boolean layoutStable = false;
    private int simulationTicks = 0;

    // --- Interaction state ---
    private boolean draggingNode = false;
    private boolean draggingCanvas = false;
    private int draggedNodeIndex = -1;
    private double dragStartX, dragStartY;
    private double dragCamStartX, dragCamStartY;
    private long lastClickTime = 0;
    private int lastClickIndex = -1;

    // --- Callbacks ---
    private Consumer<String> onOpenFile;

    /** Internal state for each graph node. */
    private static class NodeState {
        String scriptPath;
        String displayName;
        List<String> definedFunctions;
        double x, y;
        double vx, vy;
        int renderWidth;
        boolean pinned;
        NodeRole role;

        enum NodeRole { ISOLATED, PROVIDER, CONSUMER, BOTH }
    }

    /** Internal state for each edge. */
    private static class EdgeState {
        int fromIndex;
        int toIndex;
        String functionName;
    }

    public DependencyGraphWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setOnOpenFile(Consumer<String> callback) {
        this.onOpenFile = callback;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================

    /** Load graph data from a server response. */
    public void loadGraph(List<SkriptPackets.GraphNode> graphNodes, List<SkriptPackets.GraphEdge> graphEdges) {
        nodes.clear();
        edges.clear();
        adjacency.clear();
        selectedConnectedNodes.clear();
        selectedIndex = -1;
        hoveredIndex = -1;
        layoutStable = false;
        simulationTicks = 0;

        // Create node states arranged in a circle
        double angleStep = graphNodes.isEmpty() ? 0 : (2 * Math.PI / graphNodes.size());
        double radius = Math.max(150, graphNodes.size() * 30);

        for (int i = 0; i < graphNodes.size(); i++) {
            SkriptPackets.GraphNode gn = graphNodes.get(i);
            NodeState ns = new NodeState();
            ns.scriptPath = gn.scriptPath();
            ns.displayName = extractFileName(gn.scriptPath());
            ns.definedFunctions = gn.definedFunctions();
            ns.x = radius * Math.cos(angleStep * i);
            ns.y = radius * Math.sin(angleStep * i);
            ns.vx = 0;
            ns.vy = 0;
            ns.pinned = false;
            ns.role = NodeState.NodeRole.ISOLATED;
            nodes.add(ns);
        }

        // Create edge states
        for (SkriptPackets.GraphEdge ge : graphEdges) {
            if (ge.fromIndex() >= 0 && ge.fromIndex() < nodes.size()
                    && ge.toIndex() >= 0 && ge.toIndex() < nodes.size()
                    && ge.fromIndex() != ge.toIndex()) {
                EdgeState es = new EdgeState();
                es.fromIndex = ge.fromIndex();
                es.toIndex = ge.toIndex();
                es.functionName = ge.functionName();
                edges.add(es);
            }
        }

        // Build adjacency list for fast neighbor lookups
        for (int i = 0; i < nodes.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        for (int ei = 0; ei < edges.size(); ei++) {
            EdgeState es = edges.get(ei);
            adjacency.get(es.fromIndex).add(ei);
            adjacency.get(es.toIndex).add(ei);
        }

        // Determine node roles based on connections
        boolean[] provides = new boolean[nodes.size()];
        boolean[] consumes = new boolean[nodes.size()];
        for (EdgeState edge : edges) {
            consumes[edge.fromIndex] = true;
            provides[edge.toIndex] = true;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (provides[i] && consumes[i]) {
                nodes.get(i).role = NodeState.NodeRole.BOTH;
            } else if (provides[i]) {
                nodes.get(i).role = NodeState.NodeRole.PROVIDER;
            } else if (consumes[i]) {
                nodes.get(i).role = NodeState.NodeRole.CONSUMER;
            }
        }

        // Center camera on the graph
        zoomToFit();
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    public boolean hasData() {
        return !nodes.isEmpty();
    }

    // =========================================================================
    // FORCE-DIRECTED LAYOUT SIMULATION
    // =========================================================================

    /** Run one tick of the physics simulation. Call each frame. */
    public void tick() {
        if (layoutStable || nodes.size() <= 1) return;
        simulationTicks++;

        double totalKinetic = 0;

        // Calculate forces
        for (int i = 0; i < nodes.size(); i++) {
            NodeState a = nodes.get(i);
            if (a.pinned) continue;

            double fx = 0, fy = 0;

            // Repulsion from all other nodes
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                NodeState b = nodes.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                double force = REPULSION / (dist * dist);
                fx += (dx / dist) * force;
                fy += (dy / dist) * force;
            }

            // Attraction along edges (using adjacency list for O(degree) instead of O(E))
            for (int ei : adjacency.get(i)) {
                EdgeState edge = edges.get(ei);
                int otherIdx = (edge.fromIndex == i) ? edge.toIndex : edge.fromIndex;

                NodeState other = nodes.get(otherIdx);
                double dx = other.x - a.x;
                double dy = other.y - a.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                double force = ATTRACTION * (dist - REST_LENGTH);
                fx += (dx / dist) * force;
                fy += (dy / dist) * force;
            }

            // Gravity toward center
            fx -= CENTER_GRAVITY * a.x;
            fy -= CENTER_GRAVITY * a.y;

            // Apply forces
            a.vx = (a.vx + fx) * DAMPING;
            a.vy = (a.vy + fy) * DAMPING;

            // Clamp velocity
            double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
            if (speed > MAX_VELOCITY) {
                a.vx = (a.vx / speed) * MAX_VELOCITY;
                a.vy = (a.vy / speed) * MAX_VELOCITY;
            }

            totalKinetic += speed;
        }

        // Update positions
        for (NodeState node : nodes) {
            if (!node.pinned) {
                node.x += node.vx;
                node.y += node.vy;
            }
        }

        // Check stability (stop simulating when motion is negligible)
        if (simulationTicks > 100 && totalKinetic < STABLE_THRESHOLD * nodes.size()) {
            layoutStable = true;
        }
    }

    /** Reset the layout and restart simulation. */
    public void resetLayout() {
        double angleStep = nodes.isEmpty() ? 0 : (2 * Math.PI / nodes.size());
        double radius = Math.max(150, nodes.size() * 30);
        for (int i = 0; i < nodes.size(); i++) {
            NodeState ns = nodes.get(i);
            ns.x = radius * Math.cos(angleStep * i);
            ns.y = radius * Math.sin(angleStep * i);
            ns.vx = 0;
            ns.vy = 0;
            ns.pinned = false;
        }
        layoutStable = false;
        simulationTicks = 0;
    }

    /** Center and zoom to fit all nodes on screen. */
    public void zoomToFit() {
        if (nodes.isEmpty()) {
            camX = 0;
            camY = 0;
            zoom = 1.0;
            return;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (NodeState n : nodes) {
            minX = Math.min(minX, n.x - 60);
            maxX = Math.max(maxX, n.x + 60);
            minY = Math.min(minY, n.y - 20);
            maxY = Math.max(maxY, n.y + 20);
        }

        double graphW = maxX - minX;
        double graphH = maxY - minY;
        if (graphW < 1) graphW = 1;
        if (graphH < 1) graphH = 1;

        double zoomX = (width - 40) / graphW;
        double zoomY = (height - 40) / graphH;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(zoomX, zoomY)));

        camX = (minX + maxX) / 2.0;
        camY = (minY + maxY) / 2.0;
    }

    // =========================================================================
    // COORDINATE TRANSFORMS
    // =========================================================================

    /** World X -> screen X */
    private int worldToScreenX(double wx) {
        return (int) ((wx - camX) * zoom + x + width / 2.0);
    }

    /** World Y -> screen Y */
    private int worldToScreenY(double wy) {
        return (int) ((wy - camY) * zoom + y + height / 2.0);
    }

    /** Screen X -> world X */
    private double screenToWorldX(double sx) {
        return (sx - x - width / 2.0) / zoom + camX;
    }

    /** Screen Y -> world Y */
    private double screenToWorldY(double sy) {
        return (sy - y - height / 2.0) / zoom + camY;
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        // Run simulation
        tick();

        // Precompute node render widths
        for (NodeState node : nodes) {
            node.renderWidth = textRenderer.getWidth(node.displayName) + NODE_PADDING_X * 2;
        }

        // Update hovered node
        hoveredIndex = getNodeAt(mouseX, mouseY);

        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);

        // Grid dots (subtle visual reference)
        renderGrid(context);

        context.enableScissor(x, y, x + width, y + height);

        // Draw edges first (behind nodes)
        for (EdgeState edge : edges) {
            boolean highlight = selectedIndex >= 0
                    && (edge.fromIndex == selectedIndex || edge.toIndex == selectedIndex);
            renderEdge(context, edge, highlight);
        }

        // Draw nodes
        for (int i = 0; i < nodes.size(); i++) {
            renderNode(context, textRenderer, i);
        }

        context.disableScissor();

        // Tooltip on hover
        if (hoveredIndex >= 0) {
            renderTooltip(context, textRenderer, hoveredIndex, mouseX, mouseY);
        }

        // Legend
        renderLegend(context, textRenderer);
    }

    private void renderGrid(DrawContext context) {
        int gridSpacing = (int) (50 * zoom);
        if (gridSpacing < 10) return;

        int offsetX = (int) ((-camX * zoom + width / 2.0) % gridSpacing);
        int offsetY = (int) ((-camY * zoom + height / 2.0) % gridSpacing);

        for (int gx = x + offsetX; gx < x + width; gx += gridSpacing) {
            for (int gy = y + offsetY; gy < y + height; gy += gridSpacing) {
                context.fill(gx, gy, gx + 1, gy + 1, GRID_COLOR);
            }
        }
    }

    private void renderEdge(DrawContext context, EdgeState edge, boolean highlight) {
        NodeState from = nodes.get(edge.fromIndex);
        NodeState to = nodes.get(edge.toIndex);

        int sx1 = worldToScreenX(from.x);
        int sy1 = worldToScreenY(from.y);
        int sx2 = worldToScreenX(to.x);
        int sy2 = worldToScreenY(to.y);

        // Clip edge endpoints to node borders
        double hw2 = (to.renderWidth / 2.0) * zoom;
        double hh2 = (NODE_HEIGHT / 2.0) * zoom;
        double[] border = getNodeBorderPoint(sx2, sy2, hw2, hh2, sx1, sy1);
        int ex = (int) border[0];
        int ey = (int) border[1];

        int color = highlight ? EDGE_HIGHLIGHT : EDGE_DEFAULT;
        int lineWidth = highlight ? 2 : 1;

        drawLine(context, sx1, sy1, ex, ey, color, lineWidth);

        // Arrowhead at the border point
        if (highlight || zoom > 0.4) {
            drawArrowhead(context, sx1, sy1, ex, ey, highlight ? EDGE_HIGHLIGHT : EDGE_ARROW);
        }
    }

    /** Calculate where a line from (fromX,fromY) hits the border of a rectangle centered at (cx,cy). */
    private double[] getNodeBorderPoint(double cx, double cy, double hw, double hh, double fromX, double fromY) {
        double dx = fromX - cx;
        double dy = fromY - cy;
        if (dx == 0 && dy == 0) return new double[]{cx, cy};

        double scaleX = (dx != 0) ? hw / Math.abs(dx) : Double.MAX_VALUE;
        double scaleY = (dy != 0) ? hh / Math.abs(dy) : Double.MAX_VALUE;
        double scale = Math.min(scaleX, scaleY);
        return new double[]{cx + dx * scale, cy + dy * scale};
    }

    private void renderNode(DrawContext context, TextRenderer textRenderer, int index) {
        NodeState node = nodes.get(index);
        int sx = worldToScreenX(node.x);
        int sy = worldToScreenY(node.y);
        int halfW = (int) (node.renderWidth / 2.0 * zoom);
        int halfH = (int) (NODE_HEIGHT / 2.0 * zoom);

        int left = sx - halfW;
        int top = sy - halfH;
        int right = sx + halfW;
        int bottom = sy + halfH;

        // Node fill color based on role
        int fillColor = switch (node.role) {
            case PROVIDER -> NODE_PROVIDER;
            case CONSUMER -> NODE_CONSUMER;
            case BOTH -> NODE_BOTH;
            case ISOLATED -> NODE_ISOLATED;
        };

        // Border and selection highlight
        int borderColor = NODE_BORDER;
        if (index == selectedIndex) {
            borderColor = NODE_SELECTED;
            fillColor = blendColor(fillColor, NODE_SELECTED, 0.3);
        } else if (index == hoveredIndex) {
            fillColor = blendColor(fillColor, 0xFFFFFFFF, 0.15);
        }

        // Highlight if connected to selected node (O(1) lookup)
        if (selectedIndex >= 0 && selectedIndex != index && selectedConnectedNodes.contains(index)) {
            borderColor = EDGE_HIGHLIGHT;
        }

        // Draw node rectangle
        context.fill(left - 1, top - 1, right + 1, bottom + 1, borderColor);
        context.fill(left, top, right, bottom, fillColor);

        // Pin indicator
        if (node.pinned) {
            context.fill(right - 4, top, right, top + 4, NODE_SELECTED);
        }

        // Label (only draw if zoom is large enough for text to be readable)
        if (zoom > 0.35) {
            int textX = sx - textRenderer.getWidth(node.displayName) / 2;
            int textY = sy - 4;
            context.drawText(textRenderer, node.displayName, textX, textY, NODE_TEXT, true);
        }
    }

    private void renderTooltip(DrawContext context, TextRenderer textRenderer, int index, int mouseX, int mouseY) {
        NodeState node = nodes.get(index);

        List<String> lines = new ArrayList<>();
        lines.add(node.scriptPath);
        lines.add("Role: " + switch (node.role) {
            case ISOLATED -> "Isolated (no cross-file refs)";
            case PROVIDER -> "Provider (defines shared funcs)";
            case CONSUMER -> "Consumer (calls external funcs)";
            case BOTH -> "Both (provides & consumes)";
        });

        if (!node.definedFunctions.isEmpty()) {
            lines.add("");
            lines.add("Defined functions:");
            for (String func : node.definedFunctions) {
                lines.add("  " + func + "()");
            }
        }

        // Count connections
        int inbound = 0, outbound = 0;
        List<String> inFuncs = new ArrayList<>();
        List<String> outFuncs = new ArrayList<>();
        for (EdgeState e : edges) {
            if (e.toIndex == index) {
                inbound++;
                inFuncs.add(nodes.get(e.fromIndex).displayName + " -> " + e.functionName + "()");
            }
            if (e.fromIndex == index) {
                outbound++;
                outFuncs.add(e.functionName + "() -> " + nodes.get(e.toIndex).displayName);
            }
        }
        if (inbound > 0) {
            lines.add("");
            lines.add("Inbound (" + inbound + "):");
            for (String s : inFuncs) lines.add("  " + s);
        }
        if (outbound > 0) {
            lines.add("");
            lines.add("Outbound (" + outbound + "):");
            for (String s : outFuncs) lines.add("  " + s);
        }

        if (node.pinned) {
            lines.add("");
            lines.add("Right-click to unpin");
        }

        // Calculate tooltip dimensions
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, textRenderer.getWidth(line));
        }
        int tooltipW = maxLineW + 12;
        int tooltipH = lines.size() * 10 + 8;

        // Position tooltip (avoid going off screen)
        int tx = mouseX + 12;
        int ty = mouseY - 4;
        if (tx + tooltipW > x + width) tx = mouseX - tooltipW - 4;
        if (ty + tooltipH > y + height) ty = y + height - tooltipH;
        if (ty < y) ty = y;

        // Draw tooltip
        context.fill(tx - 1, ty - 1, tx + tooltipW + 1, ty + tooltipH + 1, TOOLTIP_BORDER);
        context.fill(tx, ty, tx + tooltipW, ty + tooltipH, TOOLTIP_BG);

        int lineY = ty + 4;
        for (String line : lines) {
            int color = line.contains("()") ? FUNC_TEXT : TOOLTIP_TEXT;
            if (line.startsWith("Role:")) color = 0xFFBAC2DE;
            context.drawText(textRenderer, line, tx + 6, lineY, color, false);
            lineY += 10;
        }
    }

    private void renderLegend(DrawContext context, TextRenderer textRenderer) {
        int lx = x + 8;
        int ly = y + height - 68;
        int dotSize = 8;
        int lineSpacing = 12;

        context.fill(lx - 4, ly - 4, lx + 120, ly + lineSpacing * 5 + 2, 0xC0181825);

        drawLegendEntry(context, textRenderer, lx, ly, dotSize, NODE_PROVIDER, "Provider");
        drawLegendEntry(context, textRenderer, lx, ly + lineSpacing, dotSize, NODE_CONSUMER, "Consumer");
        drawLegendEntry(context, textRenderer, lx, ly + lineSpacing * 2, dotSize, NODE_BOTH, "Both");
        drawLegendEntry(context, textRenderer, lx, ly + lineSpacing * 3, dotSize, NODE_ISOLATED, "Isolated");
        drawLegendEntry(context, textRenderer, lx, ly + lineSpacing * 4, dotSize, NODE_SELECTED, "Selected");
    }

    private void drawLegendEntry(DrawContext ctx, TextRenderer tr, int x, int y, int size, int color, String label) {
        ctx.fill(x, y + 1, x + size, y + 1 + size, color);
        ctx.drawText(tr, label, x + size + 4, y + 1, TOOLTIP_TEXT, false);
    }

    // =========================================================================
    // LINE & ARROW DRAWING UTILITIES
    // =========================================================================

    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
        // Use Bresenham's for thin lines, thick fill for wider
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int half = thickness / 2;

        int px = x1, py = y1;
        int steps = 0;
        int maxSteps = dx + dy + 1;

        while (steps++ < maxSteps) {
            context.fill(px - half, py - half, px - half + thickness, py - half + thickness, color);
            if (px == x2 && py == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; px += sx; }
            if (e2 < dx) { err += dx; py += sy; }
        }
    }

    private void drawArrowhead(DrawContext context, int fromX, int fromY, int toX, int toY, int color) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        double a1 = angle + Math.PI * 0.82;
        double a2 = angle - Math.PI * 0.82;
        int size = (int) (ARROW_SIZE * Math.max(0.6, zoom));

        int ax1 = toX + (int) (size * Math.cos(a1));
        int ay1 = toY + (int) (size * Math.sin(a1));
        int ax2 = toX + (int) (size * Math.cos(a2));
        int ay2 = toY + (int) (size * Math.sin(a2));

        drawLine(context, toX, toY, ax1, ay1, color, 1);
        drawLine(context, toX, toY, ax2, ay2, color, 1);
        drawLine(context, ax1, ay1, ax2, ay2, color, 1);
    }

    // =========================================================================
    // COLOR UTILITIES
    // =========================================================================

    private int blendColor(int base, int blend, double factor) {
        int ba = (base >> 24) & 0xFF, br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int la = (blend >> 24) & 0xFF, lr = (blend >> 16) & 0xFF, lg = (blend >> 8) & 0xFF, lb = blend & 0xFF;
        int ra = (int) (ba + (la - ba) * factor);
        int rr = (int) (br + (lr - br) * factor);
        int rg = (int) (bg + (lg - bg) * factor);
        int rb = (int) (bb + (lb - bb) * factor);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // =========================================================================
    // INPUT HANDLING
    // =========================================================================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInBounds(mouseX, mouseY)) return false;

        int nodeIndex = getNodeAt((int) mouseX, (int) mouseY);

        if (button == 0) { // Left click
            long now = System.currentTimeMillis();

            // Double-click detection
            if (nodeIndex >= 0 && nodeIndex == lastClickIndex && now - lastClickTime < 400) {
                // Double-click: open this file in the editor
                if (onOpenFile != null) {
                    onOpenFile.accept(nodes.get(nodeIndex).scriptPath);
                }
                lastClickTime = 0;
                return true;
            }

            lastClickTime = now;
            lastClickIndex = nodeIndex;

            if (nodeIndex >= 0) {
                // Start dragging this node
                setSelectedIndex(nodeIndex);
                draggingNode = true;
                draggedNodeIndex = nodeIndex;
                dragStartX = screenToWorldX(mouseX);
                dragStartY = screenToWorldY(mouseY);
            } else {
                // Deselect and start panning
                setSelectedIndex(-1);
                draggingCanvas = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragCamStartX = camX;
                dragCamStartY = camY;
            }
            return true;
        }

        if (button == 1 && nodeIndex >= 0) { // Right click: unpin
            nodes.get(nodeIndex).pinned = false;
            layoutStable = false;
            simulationTicks = Math.max(0, simulationTicks - 50);
            return true;
        }

        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingNode && draggedNodeIndex >= 0) {
            NodeState node = nodes.get(draggedNodeIndex);
            node.x = screenToWorldX(mouseX);
            node.y = screenToWorldY(mouseY);
            node.vx = 0;
            node.vy = 0;
            node.pinned = true;
            // Keep simulation alive while dragging
            layoutStable = false;
            return true;
        }

        if (draggingCanvas) {
            camX = dragCamStartX - (mouseX - dragStartX) / zoom;
            camY = dragCamStartY - (mouseY - dragStartY) / zoom;
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingNode = false;
        draggingCanvas = false;
        draggedNodeIndex = -1;
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isInBounds(mouseX, mouseY)) return false;

        // Zoom toward/away from mouse position
        double worldMouseX = screenToWorldX(mouseX);
        double worldMouseY = screenToWorldY(mouseY);

        zoom *= (amount > 0) ? 1.15 : (1.0 / 1.15);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

        // Adjust camera so the world point under the mouse stays fixed
        camX = worldMouseX - (mouseX - x - width / 2.0) / zoom;
        camY = worldMouseY - (mouseY - y - height / 2.0) / zoom;

        return true;
    }

    /** Find which node (if any) is at the given screen coordinates. */
    private int getNodeAt(int screenX, int screenY) {
        // Check in reverse order so topmost nodes are picked first
        for (int i = nodes.size() - 1; i >= 0; i--) {
            NodeState node = nodes.get(i);
            int sx = worldToScreenX(node.x);
            int sy = worldToScreenY(node.y);
            int halfW = (int) (node.renderWidth / 2.0 * zoom);
            int halfH = (int) (NODE_HEIGHT / 2.0 * zoom);

            if (screenX >= sx - halfW && screenX <= sx + halfW
                    && screenY >= sy - halfH && screenY <= sy + halfH) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInBounds(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public int getNodeCount() { return nodes.size(); }
    public int getEdgeCount() { return edges.size(); }
    public boolean isStable() { return layoutStable; }

    public String getSelectedNodePath() {
        return selectedIndex >= 0 ? nodes.get(selectedIndex).scriptPath : null;
    }

    /** Updates the selected node and precomputes the set of connected node indices. */
    private void setSelectedIndex(int index) {
        selectedIndex = index;
        selectedConnectedNodes.clear();
        if (index >= 0 && index < adjacency.size()) {
            for (int ei : adjacency.get(index)) {
                EdgeState e = edges.get(ei);
                selectedConnectedNodes.add(e.fromIndex == index ? e.toIndex : e.fromIndex);
            }
        }
    }
}
