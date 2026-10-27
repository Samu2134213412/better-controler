package com.bettercontroller.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable grid of all items, used to choose a Create link frequency item without having the item
 * in the inventory. Choosing one passes its id (e.g. {@code minecraft:redstone}) back and returns.
 */
public final class ItemPickerScreen extends Screen {
    private static final int CELL = 18;

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<ItemStack> allItems = new ArrayList<>();
    private List<ItemStack> shown = List.of();
    private EditBox search;
    private int gridX;
    private int gridY;
    private int columns;
    private int visibleRows;
    private int scrollRow;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("screen.bettercontroller.item_picker"));
        this.parent = parent;
        this.onPick = onPick;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                allItems.add(new ItemStack(item));
            }
        }
    }

    @Override
    protected void init() {
        int gridWidth = Math.min(20 * CELL, (width - 32) / CELL * CELL);
        columns = Math.max(1, gridWidth / CELL);
        gridX = (width - columns * CELL) / 2;
        gridY = 50;
        visibleRows = Math.max(1, (height - gridY - 36) / CELL);

        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, gridX, 26, columns * CELL, 18, Component.translatable("gui.bettercontroller.search"));
        search.setHint(Component.translatable("gui.bettercontroller.search"));
        search.setValue(previous);
        search.setResponder(value -> applyFilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            shown = allItems;
        } else {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : allItems) {
                if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(query)) {
                    result.add(stack);
                }
            }
            shown = result;
        }
        scrollRow = 0;
    }

    private int maxScrollRow() {
        int rows = (shown.size() + columns - 1) / columns;
        return Math.max(0, rows - visibleRows);
    }

    /** @return index into {@link #shown} under the cursor, or -1 */
    private int indexAt(double mx, double my) {
        if (mx < gridX || mx >= gridX + columns * CELL || my < gridY || my >= gridY + visibleRows * CELL) {
            return -1;
        }
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY) / CELL) + scrollRow;
        int index = row * columns + col;
        return index < shown.size() ? index : -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        g.fill(gridX - 1, gridY - 1, gridX + columns * CELL + 1, gridY + visibleRows * CELL + 1, 0xFF505050);
        g.fill(gridX, gridY, gridX + columns * CELL, gridY + visibleRows * CELL, 0xFF181818);
        int hovered = indexAt(mouseX, mouseY);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = (row + scrollRow) * columns + col;
                if (index >= shown.size()) {
                    break;
                }
                int x = gridX + col * CELL;
                int y = gridY + row * CELL;
                if (index == hovered) {
                    g.fill(x, y, x + CELL, y + CELL, 0xFF3A5A8A);
                }
                g.renderItem(shown.get(index), x + 1, y + 1);
            }
        }
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.bettercontroller.empty"), width / 2, gridY + 6, 0xFF8A8A8A);
        }
        if (hovered >= 0) {
            g.renderTooltip(font, shown.get(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = indexAt(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index >= 0) {
            String id = BuiltInRegistries.ITEM.getKey(shown.get(index).getItem()).toString();
            minecraft.setScreen(parent);
            onPick.accept(id);
            return true;
        }
        return false;
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
