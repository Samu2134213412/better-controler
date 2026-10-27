package com.bettercontroller.client;

import com.bettercontroller.item.ModItems;
import com.bettercontroller.network.GraphPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Active mode of the controller, modelled on Create's Linked Controller: right-click toggles it.
 * While active, the keys used by the player's saved graphs are read every tick and sent to the
 * server on change, and Minecraft's own key bindings on those keys are suppressed (so a graph using
 * W does not also walk). Opening any screen, putting the controller away or spectator mode ends it.
 */
public final class ControllerInput {
    private static boolean active;
    private static Set<Integer> pressed = Set.of();

    private ControllerInput() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Keys of the player's graphs that are currently held (only meaningful while active). */
    public static Set<Integer> pressedKeys() {
        return pressed;
    }

    public static void toggle(Minecraft mc) {
        if (active) {
            deactivate(mc);
            return;
        }
        active = true;
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.LEVER_CLICK, 0.4f, 0.8f);
            Component message = ClientGraphState.boundKeys().isEmpty()
                    ? Component.translatable("message.bettercontroller.active_no_keys")
                    : Component.translatable("message.bettercontroller.active");
            mc.player.displayClientMessage(message, true);
        }
    }

    private static void deactivate(Minecraft mc) {
        if (!active) {
            return;
        }
        active = false;
        releaseSuppressedKeys(mc);
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.LEVER_CLICK, 0.4f, 0.6f);
            mc.player.displayClientMessage(Component.translatable("message.bettercontroller.inactive"), true);
        }
    }

    /** Called at the start of every client tick, before the player reads its movement keys. */
    static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            active = false;
            pressed = Set.of();
            return;
        }
        if (active && (mc.screen != null || mc.player.isSpectator() || !ModItems.isHoldingController(mc.player))) {
            deactivate(mc);
        }
        sendIfChanged(mc);
        if (active) {
            suppressVanillaKeys(mc);
        }
    }

    /**
     * Called from the keyboard callback, so a press is sent at once instead of at the next tick
     * (up to 50 ms later). The tick above still catches anything missed, e.g. focus loss.
     */
    static void onKey(Minecraft mc, int key) {
        if (active && mc.player != null && mc.screen == null && ClientGraphState.boundKeys().contains(key)) {
            sendIfChanged(mc);
        }
    }

    private static void sendIfChanged(Minecraft mc) {
        Set<Integer> now = new TreeSet<>();
        if (active) {
            long window = mc.getWindow().getWindow();
            for (int key : ClientGraphState.boundKeys()) {
                if (now.size() < GraphPayloads.MAX_HELD_KEYS && InputConstants.isKeyDown(window, key)) {
                    now.add(key);
                }
            }
        }
        if (!now.equals(pressed) && mc.getConnection() != null) {
            pressed = Set.copyOf(now);
            PacketDistributor.sendToServer(new GraphPayloads.HeldKeys(new ArrayList<>(now)));
        }
    }

    static void reset() {
        active = false;
        pressed = Set.of();
    }

    /** Keeps Minecraft's key bindings that share a key with a graph input from firing. */
    private static void suppressVanillaKeys(Minecraft mc) {
        Set<Integer> bound = ClientGraphState.boundKeys();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == BetterControllerClient.OPEN_EDITOR || !usesBoundKey(mapping, bound)) {
                continue;
            }
            while (mapping.consumeClick()) {
                // drop queued presses
            }
            mapping.setDown(false);
        }
    }

    /** After deactivating, give keys that are still physically held back to Minecraft. */
    private static void releaseSuppressedKeys(Minecraft mc) {
        Set<Integer> bound = ClientGraphState.boundKeys();
        long window = mc.getWindow().getWindow();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (usesBoundKey(mapping, bound)) {
                mapping.setDown(InputConstants.isKeyDown(window, mapping.getKey().getValue()));
            }
        }
    }

    private static boolean usesBoundKey(KeyMapping mapping, Set<Integer> bound) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && bound.contains(key.getValue());
    }
}
