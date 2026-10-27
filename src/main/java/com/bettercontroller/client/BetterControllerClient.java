package com.bettercontroller.client;

import com.bettercontroller.BetterController;
import com.bettercontroller.client.screen.LogicEditorScreen;
import com.bettercontroller.item.ModItems;
import com.bettercontroller.network.GraphPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Client-only entry point: key mapping for the editor and reporting of held keys. */
@Mod(value = BetterController.MOD_ID, dist = Dist.CLIENT)
public final class BetterControllerClient {
    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.bettercontroller.open_editor",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.bettercontroller");

    public BetterControllerClient(IEventBus modBus) {
        modBus.addListener(BetterControllerClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onLoggingOut);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGraphState.reset();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_EDITOR.consumeClick()) {
            openEditor(mc);
        }
        reportHeldKeys(mc);
    }

    /** Opens the editor if the player holds a controller and no other screen is open. */
    public static void openEditor(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!ModItems.isHoldingController(mc.player)) {
            mc.player.displayClientMessage(Component.translatable("message.bettercontroller.need_controller"), true);
            return;
        }
        mc.setScreen(new LogicEditorScreen(ClientGraphState.editingGraph()));
    }

    /** Sends the held bound keys to the server whenever they change. No GUI may be open. */
    private static void reportHeldKeys(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        Set<Integer> held = new TreeSet<>();
        if (mc.screen == null && ModItems.isHoldingController(mc.player)) {
            long window = mc.getWindow().getWindow();
            for (int key : ClientGraphState.boundKeys()) {
                if (held.size() < GraphPayloads.MAX_HELD_KEYS && InputConstants.isKeyDown(window, key)) {
                    held.add(key);
                }
            }
        }
        if (!held.equals(ClientGraphState.lastSentKeys())) {
            ClientGraphState.setLastSentKeys(held);
            List<Integer> keys = new ArrayList<>(held);
            PacketDistributor.sendToServer(new GraphPayloads.HeldKeys(keys));
        }
    }
}
