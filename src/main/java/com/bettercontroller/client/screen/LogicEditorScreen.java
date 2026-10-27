package com.bettercontroller.client.screen;

import com.bettercontroller.client.ClientGraphState;
import com.bettercontroller.logic.Connection;
import com.bettercontroller.logic.GraphEvaluator;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.GraphLimits;
import com.bettercontroller.logic.KeyCodes;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;
import com.bettercontroller.network.GraphPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The node editor. Nodes are stored in canvas units; zoom and pan only change how canvas units map
 * to screen pixels, never the stored positions. Everything is drawn with plain GUI fills and text
 * at the normal GUI scale (no render-to-texture and no pose scaling), so text stays sharp at every
 * zoom level, and buttons are unaffected by the canvas zoom.
 */
public final class LogicEditorScreen extends Screen implements StatusSink {
    private static final int TOP_BAR = 26;
    private static final int STATUS_BAR = 14;
    private static final int PANEL_WIDTH = 132;
    private static final int PANEL_HEIGHT = 74;

    private static final int NODE_WIDTH = 76;
    private static final int NODE_HEADER = 12;
    private static final int PORT_ROW = 12;
    private static final int PORT_SIZE = 6;
    private static final int GRID = 20;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2.0f;
    /** Below this zoom, node text would not fit and is hidden. */
    private static final float TEXT_ZOOM = 0.75f;

    private static final int COLOR_CANVAS = 0xFF1B1D22;
    private static final int COLOR_GRID = 0xFF24272E;
    private static final int COLOR_NODE = 0xFF2E323B;
    private static final int COLOR_HEADER = 0xFF44506A;
    private static final int COLOR_HEADER_ON = 0xFF2F8F4E;
    private static final int COLOR_SELECTED = 0xFFFFD34D;
    private static final int COLOR_PORT = 0xFFB8BEC9;
    private static final int COLOR_WIRE = 0xFF7A3030;
    private static final int COLOR_WIRE_ON = 0xFFFF4A3D;

    private final LogicGraph graph;

    private float zoom = 1.0f;
    private float panX = 40;
    private float panY = 40;

    private int selectedNode = -1;
    private int draggingNode = -1;
    private double dragOffsetX;
    private double dragOffsetY;
    /** Source of a wire being dragged, or null. */
    private int[] wireFrom;
    private boolean panning;
    private boolean capturingKey;

    private String status = "";
    private int statusColor = 0xFFB0B0B0;

    private EditBox nameBox;
    private EditBox frequencyBox;
    private Button changeKeyButton;

    public LogicEditorScreen(LogicGraph graph) {
        super(Component.translatable("screen.bettercontroller.editor"));
        this.graph = graph;
    }

    // ---- layout ----

    @Override
    protected void init() {
        int x = 4;
        x = addButton(x, "gui.bettercontroller.nodes", 60, this::openLibrary);
        x = addButton(x, "gui.bettercontroller.graphs", 56, () -> minecraft.setScreen(new GraphManagerScreen(this)));
        x = addButton(x, "gui.bettercontroller.save", 44, this::save);
        x = addButton(x, "gui.bettercontroller.export", 50, this::export);
        x = addButton(x, "gui.bettercontroller.reset_view", 64, this::resetView);

        int nameWidth = Math.max(60, Math.min(160, width - x - 6));
        nameBox = new EditBox(font, width - nameWidth - 4, 4, nameWidth, 18, Component.translatable("gui.bettercontroller.name"));
        nameBox.setMaxLength(GraphLimits.MAX_NAME_LENGTH);
        nameBox.setValue(graph.name());
        nameBox.setResponder(value -> {
            if (!value.strip().equals(graph.name())) {
                graph.setName(value);
                markChanged();
            }
        });
        addRenderableWidget(nameBox);

        int panelX = width - PANEL_WIDTH - 4;
        int panelY = TOP_BAR + 4;
        changeKeyButton = addRenderableWidget(Button.builder(Component.translatable("gui.bettercontroller.change_key"),
                b -> startKeyCapture()).bounds(panelX + 6, panelY + 40, PANEL_WIDTH - 12, 20).build());
        frequencyBox = new EditBox(font, panelX + 6, panelY + 40, PANEL_WIDTH - 12, 18,
                Component.translatable("gui.bettercontroller.frequency"));
        frequencyBox.setMaxLength(6);
        frequencyBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
        frequencyBox.setResponder(this::onFrequencyEdited);
        addRenderableWidget(frequencyBox);
        updatePanel();
    }

    private int addButton(int x, String key, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run()).bounds(x, 4, width, 18).build());
        return x + width + 3;
    }

    /** Shows the settings widget that fits the selected node. */
    private void updatePanel() {
        LogicNode node = graph.node(selectedNode);
        boolean keyboard = node != null && node.type() == NodeType.KEYBOARD_INPUT;
        boolean output = node != null && node.type() == NodeType.REDSTONE_OUTPUT;
        changeKeyButton.visible = keyboard;
        frequencyBox.visible = output;
        if (output) {
            String text = String.valueOf(node.frequency());
            if (!text.equals(frequencyBox.getValue())) {
                frequencyBox.setValue(text);
            }
        } else if (frequencyBox.isFocused()) {
            setFocused(null);
        }
        if (!keyboard) {
            capturingKey = false;
        }
    }

    private boolean panelVisible() {
        LogicNode node = graph.node(selectedNode);
        return node != null && (node.type() == NodeType.KEYBOARD_INPUT || node.type() == NodeType.REDSTONE_OUTPUT);
    }

    private boolean inPanel(double mx, double my) {
        int panelX = width - PANEL_WIDTH - 4;
        int panelY = TOP_BAR + 4;
        return panelVisible() && mx >= panelX && mx < panelX + PANEL_WIDTH && my >= panelY && my < panelY + PANEL_HEIGHT;
    }

    private boolean inCanvas(double mx, double my) {
        return my >= TOP_BAR && my < height - STATUS_BAR && !inPanel(mx, my);
    }

    // ---- coordinate transforms ----

    private float toScreenX(float canvasX) {
        return canvasX * zoom + panX;
    }

    private float toScreenY(float canvasY) {
        return canvasY * zoom + panY + TOP_BAR;
    }

    private float toCanvasX(double screenX) {
        return (float) ((screenX - panX) / zoom);
    }

    private float toCanvasY(double screenY) {
        return (float) ((screenY - TOP_BAR - panY) / zoom);
    }

    private static int nodeHeight(NodeType type) {
        return NODE_HEADER + Math.max(1, type.inputs()) * PORT_ROW + 14;
    }

    /** Canvas y of the centre of an input port. */
    private static int inputPortY(LogicNode node, int port) {
        return node.y() + NODE_HEADER + PORT_ROW / 2 + port * PORT_ROW;
    }

    private static int outputPortY(LogicNode node) {
        return node.y() + NODE_HEADER + (nodeHeight(node.type()) - NODE_HEADER) / 2;
    }

    // ---- rendering ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Replaces the vanilla blurred background; the canvas is drawn here so widgets stay on top.
        g.fill(0, 0, width, height, COLOR_CANVAS);
        g.enableScissor(0, TOP_BAR, width, height - STATUS_BAR);
        renderGrid(g);
        Map<Integer, Boolean> states = capturingKey ? Map.of() : GraphEvaluator.evaluate(graph, this::isKeyDown);
        for (Connection c : graph.connections()) {
            LogicNode from = graph.node(c.fromNode());
            LogicNode to = graph.node(c.toNode());
            renderWire(g, toScreenX(from.x() + NODE_WIDTH), toScreenY(outputPortY(from)),
                    toScreenX(to.x()), toScreenY(inputPortY(to, c.toPort())),
                    states.getOrDefault(c.fromNode(), false) ? COLOR_WIRE_ON : COLOR_WIRE);
        }
        if (wireFrom != null) {
            LogicNode from = graph.node(wireFrom[0]);
            if (from != null) {
                renderWire(g, toScreenX(from.x() + NODE_WIDTH), toScreenY(outputPortY(from)), mouseX, mouseY, COLOR_WIRE_ON);
            }
        }
        for (LogicNode node : graph.nodes()) {
            renderNode(g, node, states.getOrDefault(node.id(), false));
        }
        g.disableScissor();

        g.fill(0, 0, width, TOP_BAR, 0xFF2A2D35);
        g.fill(0, TOP_BAR - 1, width, TOP_BAR, 0xFF0E0F12);
        g.fill(0, height - STATUS_BAR, width, height, 0xFF2A2D35);
        if (panelVisible()) {
            renderPanel(g);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        String info = graph.nodes().size() + "/" + GraphLimits.MAX_NODES + " nodes  "
                + Math.round(zoom * 100) + "%" + (ClientGraphState.hasUnsavedChanges() ? "  *unsaved*" : "");
        int infoWidth = font.width(info);
        g.drawString(font, info, width - infoWidth - 4, height - STATUS_BAR + 3, 0xFF8A8F99, false);
        g.drawString(font, font.plainSubstrByWidth(status, width - infoWidth - 16), 4, height - STATUS_BAR + 3, statusColor, false);
    }

    private void renderGrid(GuiGraphics g) {
        float step = GRID * zoom;
        float startX = panX % step;
        float startY = panY % step;
        for (float x = startX; x < width; x += step) {
            g.fill(Math.round(x), TOP_BAR, Math.round(x) + 1, height - STATUS_BAR, COLOR_GRID);
        }
        for (float y = startY; y < height; y += step) {
            int sy = Math.round(y) + TOP_BAR;
            g.fill(0, sy, width, sy + 1, COLOR_GRID);
        }
    }

    private void renderNode(GuiGraphics g, LogicNode node, boolean active) {
        int x1 = Math.round(toScreenX(node.x()));
        int y1 = Math.round(toScreenY(node.y()));
        int x2 = Math.round(toScreenX(node.x() + NODE_WIDTH));
        int y2 = Math.round(toScreenY(node.y() + nodeHeight(node.type())));
        int header = Math.round(toScreenY(node.y() + NODE_HEADER));
        if (x2 < 0 || x1 > width || y2 < TOP_BAR || y1 > height) {
            return;
        }
        if (node.id() == selectedNode) {
            g.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, COLOR_SELECTED);
        }
        g.fill(x1, y1, x2, y2, COLOR_NODE);
        g.fill(x1, y1, x2, header, active ? COLOR_HEADER_ON : COLOR_HEADER);

        int half = Math.max(2, Math.round(PORT_SIZE * zoom / 2));
        for (int port = 0; port < node.type().inputs(); port++) {
            int py = Math.round(toScreenY(inputPortY(node, port)));
            g.fill(x1 - half, py - half, x1 + half, py + half, COLOR_PORT);
        }
        if (node.type().outputs() > 0) {
            int py = Math.round(toScreenY(outputPortY(node)));
            g.fill(x2 - half, py - half, x2 + half, py + half, active ? COLOR_WIRE_ON : COLOR_PORT);
        }

        if (zoom >= TEXT_ZOOM) {
            int textWidth = x2 - x1 - 6;
            String name = Component.translatable(node.type().translationKey()).getString();
            g.drawString(font, font.plainSubstrByWidth(name, textWidth), x1 + 3, y1 + Math.max(1, (header - y1 - 8) / 2), 0xFFFFFFFF, false);
            String detail = switch (node.type()) {
                case KEYBOARD_INPUT -> keyName(node.key());
                case REDSTONE_OUTPUT -> "f " + node.frequency() + (active ? "  ON" : "  OFF");
                default -> active ? "ON" : "OFF";
            };
            g.drawString(font, font.plainSubstrByWidth(detail, textWidth - 6), x1 + 8, y2 - 11, 0xFFB0B6C2, false);
        }
    }

    /** Draws a cubic Bezier wire as a chain of small squares. */
    private void renderWire(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        float bend = Math.max(30 * zoom, Math.abs(x2 - x1) / 2);
        float c1x = x1 + bend;
        float c2x = x2 - bend;
        int steps = Mth.clamp((int) (Math.abs(x2 - x1) + Math.abs(y2 - y1)) / 2, 8, 400);
        int size = zoom >= 1.5f ? 2 : 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1 - t;
            float x = u * u * u * x1 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x2;
            float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            int px = Math.round(x);
            int py = Math.round(y);
            g.fill(px - size, py - size, px + size, py + size, color);
        }
    }

    private void renderPanel(GuiGraphics g) {
        LogicNode node = graph.node(selectedNode);
        int x = width - PANEL_WIDTH - 4;
        int y = TOP_BAR + 4;
        g.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + PANEL_HEIGHT + 1, 0xFF0E0F12);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xF02A2D35);
        g.drawString(font, Component.translatable(node.type().translationKey()), x + 6, y + 6, 0xFFFFFFFF, false);
        if (node.type() == NodeType.KEYBOARD_INPUT) {
            Component line = capturingKey
                    ? Component.translatable("gui.bettercontroller.press_key")
                    : Component.translatable("gui.bettercontroller.key", keyName(node.key()));
            g.drawString(font, line, x + 6, y + 22, capturingKey ? COLOR_SELECTED : 0xFFB0B6C2, false);
        } else {
            g.drawString(font, Component.translatable("gui.bettercontroller.frequency_range",
                    GraphLimits.MIN_FREQUENCY, GraphLimits.MAX_FREQUENCY), x + 6, y + 22, 0xFFB0B6C2, false);
        }
    }

    private boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key);
    }

    private static String keyName(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    // ---- hit testing ----

    private LogicNode nodeAt(double mx, double my) {
        List<LogicNode> nodes = new ArrayList<>(graph.nodes());
        // Last drawn is on top, so search backwards.
        for (int i = nodes.size() - 1; i >= 0; i--) {
            LogicNode node = nodes.get(i);
            if (mx >= toScreenX(node.x()) && mx <= toScreenX(node.x() + NODE_WIDTH)
                    && my >= toScreenY(node.y()) && my <= toScreenY(node.y() + nodeHeight(node.type()))) {
                return node;
            }
        }
        return null;
    }

    /** @return {nodeId, port} of an input port near the cursor, or null */
    private int[] inputPortAt(double mx, double my) {
        float radius = Math.max(5, PORT_SIZE * zoom);
        for (LogicNode node : graph.nodes()) {
            for (int port = 0; port < node.type().inputs(); port++) {
                if (Math.abs(mx - toScreenX(node.x())) <= radius && Math.abs(my - toScreenY(inputPortY(node, port))) <= radius) {
                    return new int[]{node.id(), port};
                }
            }
        }
        return null;
    }

    private int[] outputPortAt(double mx, double my) {
        float radius = Math.max(5, PORT_SIZE * zoom);
        for (LogicNode node : graph.nodes()) {
            if (node.type().outputs() > 0
                    && Math.abs(mx - toScreenX(node.x() + NODE_WIDTH)) <= radius
                    && Math.abs(my - toScreenY(outputPortY(node))) <= radius) {
                return new int[]{node.id(), 0};
            }
        }
        return null;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (capturingKey) {
            capturingKey = false;
            showStatus("", false);
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!inCanvas(mx, my)) {
            return false;
        }
        setFocused(null);

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            panning = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int[] output = outputPortAt(mx, my);
            if (output != null) {
                wireFrom = output;
                return true;
            }
            int[] input = inputPortAt(mx, my);
            if (input != null) {
                // Grabbing a connected input detaches the wire and lets the player move it.
                Connection existing = graph.inputConnection(input[0], input[1]);
                if (existing != null) {
                    graph.disconnectInput(input[0], input[1]);
                    markChanged();
                    wireFrom = new int[]{existing.fromNode(), existing.fromPort()};
                }
                return true;
            }
        }
        LogicNode node = nodeAt(mx, my);
        if (node == null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectNode(-1);
                panning = true;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            deleteNode(node.id());
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectNode(node.id());
            draggingNode = node.id();
            dragOffsetX = toCanvasX(mx) - node.x();
            dragOffsetY = toCanvasY(my) - node.y();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        if (draggingNode >= 0) {
            LogicNode node = graph.node(draggingNode);
            if (node != null) {
                int x = Math.round((float) (toCanvasX(mx) - dragOffsetX));
                int y = Math.round((float) (toCanvasY(my) - dragOffsetY));
                if (x != node.x() || y != node.y()) {
                    node.setPosition(x, y);
                    markChanged();
                }
            }
            return true;
        }
        if (wireFrom != null) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (wireFrom != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int[] input = inputPortAt(mx, my);
            if (input != null) {
                LogicGraph.ConnectResult result = graph.connect(new Connection(wireFrom[0], wireFrom[1], input[0], input[1]));
                if (result == LogicGraph.ConnectResult.OK) {
                    markChanged();
                } else {
                    showStatus(Component.translatable("gui.bettercontroller.connect." + result.name().toLowerCase(Locale.ROOT)).getString(), true);
                }
            }
            wireFrom = null;
            return true;
        }
        boolean handled = panning || draggingNode >= 0;
        panning = false;
        draggingNode = -1;
        return handled || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!inCanvas(mx, my) || scrollY == 0) {
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }
        float canvasX = toCanvasX(mx);
        float canvasY = toCanvasY(my);
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.1f : 1 / 1.1f), MIN_ZOOM, MAX_ZOOM);
        // Keep the canvas point under the cursor in place.
        panX = (float) (mx - canvasX * zoom);
        panY = (float) (my - TOP_BAR - canvasY * zoom);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturingKey) {
            capturingKey = false;
            if (keyCode == KeyCodes.ESCAPE) {
                showStatus(Component.translatable("gui.bettercontroller.key_cancelled").getString(), false);
            } else {
                LogicNode node = graph.node(selectedNode);
                if (node != null && node.setKey(keyCode)) {
                    markChanged();
                    showStatus(Component.translatable("gui.bettercontroller.key_set", keyName(keyCode)).getString(), false);
                } else {
                    showStatus(Component.translatable("gui.bettercontroller.key_invalid").getString(), true);
                }
            }
            return true;
        }
        boolean typing = getFocused() instanceof EditBox box && box.isFocused();
        if (!typing) {
            if (keyCode == GLFW.GLFW_KEY_SPACE && hasShiftDown()) {
                openLibrary();
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && selectedNode >= 0) {
                deleteNode(selectedNode);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- actions ----

    private void selectNode(int nodeId) {
        selectedNode = nodeId;
        updatePanel();
    }

    private void deleteNode(int nodeId) {
        graph.removeNode(nodeId);
        if (selectedNode == nodeId) {
            selectNode(-1);
        }
        markChanged();
    }

    private void startKeyCapture() {
        capturingKey = true;
        setFocused(null);
        showStatus(Component.translatable("gui.bettercontroller.press_key").getString(), false);
    }

    private void onFrequencyEdited(String value) {
        LogicNode node = graph.node(selectedNode);
        if (node == null || node.type() != NodeType.REDSTONE_OUTPUT || value.isEmpty()) {
            return;
        }
        int frequency;
        try {
            frequency = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return;
        }
        if (frequency != node.frequency() && node.setFrequency(frequency)) {
            markChanged();
        }
    }

    private void openLibrary() {
        minecraft.setScreen(new BlockLibraryScreen(this, this::addNode));
    }

    private void addNode(NodeType type) {
        // Place the node in the middle of the visible canvas, slightly offset for each new node.
        int offset = (graph.nodes().size() % 8) * 10;
        int x = Math.round(toCanvasX(width / 2.0)) - NODE_WIDTH / 2 + offset;
        int y = Math.round(toCanvasY((TOP_BAR + height - STATUS_BAR) / 2.0)) - 20 + offset;
        LogicNode node = graph.addNode(type, x, y);
        if (node == null) {
            showStatus(Component.translatable("gui.bettercontroller.node_limit", GraphLimits.MAX_NODES).getString(), true);
            return;
        }
        selectNode(node.id());
        markChanged();
    }

    private void save() {
        PacketDistributor.sendToServer(new GraphPayloads.SaveGraph(GraphJsonCodec.encode(graph, false)));
        showStatus(Component.translatable("gui.bettercontroller.saving").getString(), false);
    }

    private void export() {
        try {
            String file = ClientGraphState.fileStore().write(graph);
            showStatus(Component.translatable("gui.bettercontroller.exported", "bettercontroller/graphs/" + file).getString(), false);
        } catch (IOException e) {
            showStatus(Component.translatable("gui.bettercontroller.export_failed", e.getMessage()).getString(), true);
        }
    }

    private void resetView() {
        zoom = 1.0f;
        panX = 40;
        panY = 40;
    }

    private void markChanged() {
        ClientGraphState.setUnsaved(true);
    }

    @Override
    public void showStatus(String message, boolean error) {
        status = message;
        statusColor = error ? 0xFFFF6B6B : 0xFFB0B0B0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
