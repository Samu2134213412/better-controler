package com.bettercontroller.client.screen;

import com.bettercontroller.logic.NodeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Scrollable list of node types. Choosing one passes it to the editor and returns there. */
public final class BlockLibraryScreen extends Screen {
    private final Screen parent;
    private final Consumer<NodeType> onChoose;
    private final ScrollingList list = new ScrollingList();
    private final List<NodeType> types = List.of(NodeType.values());

    public BlockLibraryScreen(Screen parent, Consumer<NodeType> onChoose) {
        super(Component.translatable("screen.bettercontroller.library"));
        this.parent = parent;
        this.onChoose = onChoose;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(240, width - 32);
        int listTop = 32;
        int listHeight = height - listTop - 40;
        list.setBounds((width - listWidth) / 2, listTop, listWidth, listHeight);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Plain dark background without the vanilla blur, which made the GUI look unsharp.
        g.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        list.render(g, font, rows(), mouseX, mouseY);
    }

    private List<ScrollingList.Row> rows() {
        List<ScrollingList.Row> rows = new ArrayList<>();
        for (NodeType type : types) {
            rows.add(new ScrollingList.Row(
                    Component.translatable(type.translationKey()).getString(),
                    Component.translatable(type.translationKey() + ".desc").getString(),
                    0xFFFFFFFF));
        }
        return rows;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.contains(mouseX, mouseY)) {
            list.scroll(scrollY, types.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = list.rowAt(mouseX, mouseY, types.size());
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0) {
            minecraft.setScreen(parent);
            onChoose.accept(types.get(index));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && hasShiftDown()) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
