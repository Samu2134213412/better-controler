package com.bettercontroller.client;

import com.bettercontroller.item.ModItems;
import com.bettercontroller.network.GraphPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * HUD while the controller is held: a hint how to activate it, or, while active, the graph keys
 * (lit while held) and the outputs as the server reports them. The output list is the server's
 * state, so it also shows whether key presses actually arrived.
 */
public final class ControllerHud {
    private static final int MAX_OUTPUT_LINES = 8;

    private ControllerHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !ModItems.isHoldingController(mc.player)) {
            return;
        }
        Font font = mc.font;
        if (!ControllerInput.isActive()) {
            Component hint = Component.translatable("hud.bettercontroller.hint");
            int width = font.width(hint);
            int x = (g.guiWidth() - width) / 2;
            int y = g.guiHeight() - 72;
            g.fill(x - 3, y - 2, x + width + 3, y + 10, 0x90000000);
            g.drawString(font, hint, x, y, 0xFFE0E0E0, false);
            return;
        }

        int x = 6;
        int y = 6;
        g.drawString(font, Component.translatable("hud.bettercontroller.active"), x, y, 0xFF6BE06B, true);
        y += 12;

        List<Integer> keys = List.copyOf(ClientGraphState.boundKeys());
        if (keys.isEmpty()) {
            g.drawString(font, Component.translatable("hud.bettercontroller.no_keys"), x, y, 0xFFFFC66B, true);
            y += 12;
        } else {
            int kx = x;
            for (int key : keys) {
                String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
                int w = font.width(name) + 8;
                if (kx + w > g.guiWidth() / 2) {
                    kx = x;
                    y += 14;
                }
                boolean down = ControllerInput.pressedKeys().contains(key);
                g.fill(kx, y, kx + w, y + 12, down ? 0xE02F8F4E : 0xA0303030);
                g.drawString(font, name, kx + 4, y + 2, down ? 0xFFFFFFFF : 0xFFB0B0B0, false);
                kx += w + 3;
            }
            y += 16;
        }

        List<GraphPayloads.OutputInfo> outputs = ClientGraphState.outputs();
        for (int i = 0; i < Math.min(outputs.size(), MAX_OUTPUT_LINES); i++) {
            GraphPayloads.OutputInfo out = outputs.get(i);
            g.fill(x, y, x + 8, y + 8, out.on() ? 0xFFFF4A3D : 0xFF5A2020);
            int tx = x + 12;
            for (String itemId : new String[]{out.linkItem1(), out.linkItem2()}) {
                ItemStack stack = stack(itemId);
                if (!stack.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(tx, y - 2, 0);
                    g.pose().scale(0.75f, 0.75f, 1);
                    g.renderItem(stack, 0, 0);
                    g.pose().popPose();
                    tx += 13;
                }
            }
            Component text = Component.translatable(out.on() ? "hud.bettercontroller.output_on" : "hud.bettercontroller.output_off",
                    out.graphName(), out.frequency(), out.strength());
            g.drawString(font, text, tx + 2, y, out.on() ? 0xFFFFFFFF : 0xFF9A9A9A, true);
            y += 12;
        }
        if (outputs.size() > MAX_OUTPUT_LINES) {
            g.drawString(font, "+" + (outputs.size() - MAX_OUTPUT_LINES), x, y, 0xFF9A9A9A, true);
        }
    }

    private static ItemStack stack(String itemId) {
        if (itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
