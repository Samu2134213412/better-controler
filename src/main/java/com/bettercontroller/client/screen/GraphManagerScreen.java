package com.bettercontroller.client.screen;

import com.bettercontroller.client.ClientGraphState;
import com.bettercontroller.logic.GraphValidationException;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.network.GraphPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lists the graphs saved in this world (own graphs; operators see all) and the .bcgraph files in
 * .minecraft/bettercontroller/graphs for import.
 */
public final class GraphManagerScreen extends Screen implements StatusSink {
    private final Screen parent;
    private final ScrollingList list = new ScrollingList();
    private boolean filesMode;
    private List<String> files = List.of();
    /** Graph id that was clicked once on Delete; a second click confirms. */
    private UUID pendingDelete;

    private String status = "";
    private int statusColor = 0xFFB0B0B0;

    private Button modeButton;
    private Button openButton;
    private Button deleteButton;

    public GraphManagerScreen(Screen parent) {
        super(Component.translatable("screen.bettercontroller.graphs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(300, width - 32);
        int left = (width - listWidth) / 2;
        list.setBounds(left, 40, listWidth, height - 40 - 62);

        int y = height - 54;
        int w = (listWidth - 6) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.bettercontroller.new"), b -> newGraph())
                .bounds(left, y, w, 20).build());
        openButton = addRenderableWidget(Button.builder(Component.empty(), b -> openSelected())
                .bounds(left + w + 3, y, w, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("gui.bettercontroller.delete"), b -> deleteSelected())
                .bounds(left + 2 * (w + 3), y, w, 20).build());
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleMode())
                .bounds(left, y + 24, w * 2 + 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 2 * (w + 3), y + 24, w, 20).build());
        updateButtons();
        if (!filesMode) {
            PacketDistributor.sendToServer(new GraphPayloads.RequestGraphList());
        }
    }

    /** Called when the server sends a new graph list. */
    public void onGraphListChanged() {
        if (!filesMode && list.selected() >= ClientGraphState.graphs().size()) {
            list.setSelected(-1);
        }
        updateButtons();
    }

    private void updateButtons() {
        openButton.setMessage(Component.translatable(filesMode ? "gui.bettercontroller.import" : "gui.bettercontroller.open"));
        modeButton.setMessage(Component.translatable(filesMode ? "gui.bettercontroller.show_world" : "gui.bettercontroller.show_files"));
        openButton.active = list.selected() >= 0;
        deleteButton.visible = !filesMode;
        deleteButton.active = list.selected() >= 0;
        deleteButton.setMessage(Component.translatable(pendingDelete != null
                ? "gui.bettercontroller.delete_confirm" : "gui.bettercontroller.delete"));
    }

    private void toggleMode() {
        filesMode = !filesMode;
        list.setSelected(-1);
        pendingDelete = null;
        if (filesMode) {
            try {
                files = ClientGraphState.fileStore().list();
                showStatus(Component.translatable("gui.bettercontroller.files_hint",
                        "bettercontroller/graphs").getString(), false);
            } catch (IOException e) {
                files = List.of();
                showStatus(e.getMessage(), true);
            }
        } else {
            PacketDistributor.sendToServer(new GraphPayloads.RequestGraphList());
        }
        updateButtons();
    }

    private void newGraph() {
        LogicGraph graph = LogicGraph.create("New Graph");
        ClientGraphState.setEditingGraph(graph, true);
        minecraft.setScreen(new LogicEditorScreen(graph));
    }

    private void openSelected() {
        int index = list.selected();
        if (index < 0) {
            return;
        }
        if (filesMode) {
            if (index >= files.size()) {
                return;
            }
            try {
                // A new id makes the import a separate graph; it never overwrites one in the world.
                LogicGraph imported = ClientGraphState.fileStore().read(files.get(index)).copyWithId(UUID.randomUUID());
                ClientGraphState.setEditingGraph(imported, true);
                LogicEditorScreen editor = new LogicEditorScreen(imported);
                minecraft.setScreen(editor);
                editor.showStatus(Component.translatable("gui.bettercontroller.imported").getString(), false);
            } catch (GraphValidationException e) {
                showStatus(Component.translatable("gui.bettercontroller.import_failed", e.getMessage()).getString(), true);
            } catch (IOException e) {
                showStatus(Component.translatable("gui.bettercontroller.import_failed", e.getMessage()).getString(), true);
            }
        } else if (index < ClientGraphState.graphs().size()) {
            PacketDistributor.sendToServer(new GraphPayloads.OpenGraph(ClientGraphState.graphs().get(index).id()));
        }
    }

    private void deleteSelected() {
        int index = list.selected();
        if (filesMode || index < 0 || index >= ClientGraphState.graphs().size()) {
            return;
        }
        UUID id = ClientGraphState.graphs().get(index).id();
        if (!id.equals(pendingDelete)) {
            pendingDelete = id;
            updateButtons();
            return;
        }
        pendingDelete = null;
        PacketDistributor.sendToServer(new GraphPayloads.DeleteGraph(id));
        if (ClientGraphState.editingGraph().id().equals(id)) {
            ClientGraphState.setEditingGraph(null, false);
        }
        list.setSelected(-1);
        updateButtons();
    }

    private List<ScrollingList.Row> rows() {
        List<ScrollingList.Row> rows = new ArrayList<>();
        if (filesMode) {
            for (String file : files) {
                rows.add(new ScrollingList.Row(file, "", 0xFFFFFFFF));
            }
        } else {
            for (GraphPayloads.GraphSummary graph : ClientGraphState.graphs()) {
                String detail = graph.nodeCount() + " nodes" + (graph.own() ? "" : "  -  " + graph.ownerName());
                rows.add(new ScrollingList.Row(graph.name(), detail, graph.own() ? 0xFFFFFFFF : 0xFFFFC66B));
            }
        }
        return rows;
    }

    private int rowCount() {
        return filesMode ? files.size() : ClientGraphState.graphs().size();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, filesMode ? Component.translatable("gui.bettercontroller.show_files") : title,
                width / 2, 14, 0xFFFFFFFF);
        List<ScrollingList.Row> rows = rows();
        list.render(g, font, rows, mouseX, mouseY);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.bettercontroller.empty"), width / 2, 56, 0xFF8A8A8A);
        }
        g.drawCenteredString(font, status, width / 2, 27, statusColor);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.contains(mouseX, mouseY)) {
            list.scroll(scrollY, rowCount());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = list.rowAt(mouseX, mouseY, rowCount());
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0) {
            if (index == list.selected()) {
                openSelected();
            } else {
                list.setSelected(index);
                pendingDelete = null;
                updateButtons();
            }
            return true;
        }
        return false;
    }

    @Override
    public void showStatus(String message, boolean error) {
        status = message;
        statusColor = error ? 0xFFFF6B6B : 0xFFB0B0B0;
    }

    @Override
    public void onClose() {
        // Back to the editor; if the edited graph was deleted, start a fresh one.
        minecraft.setScreen(parent instanceof LogicEditorScreen && ClientGraphState.hasEditingGraph()
                ? parent
                : new LogicEditorScreen(ClientGraphState.editingGraph()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
