package com.bettercontroller.client;

import com.bettercontroller.BetterController;
import com.bettercontroller.client.screen.LogicEditorScreen;
import com.bettercontroller.item.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entry point: Shift+C and Shift+right-click open the editor, right-click toggles the
 * controller (see {@link ControllerInput}), and the HUD shows its state.
 */
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
        modBus.addListener(BetterControllerClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onKey);
        NeoForge.EVENT_BUS.addListener(BetterControllerClient::onLoggingOut);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BetterController.id("controller_hud"), ControllerHud::render);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ControllerInput.reset();
        ClientGraphState.reset();
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        ControllerInput.tick(Minecraft.getInstance());
    }

    private static void onKey(InputEvent.Key event) {
        ControllerInput.onKey(Minecraft.getInstance(), event.getKey());
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_EDITOR.consumeClick()) {
            openEditor(mc);
        }
    }

    /** Right-click with the controller: toggle it; with Shift: open the editor. Client side only. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(ModItems.CONTROLLER.get())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) {
            return;
        }
        if (mc.player.isShiftKeyDown()) {
            openEditor(mc);
        } else {
            ControllerInput.toggle(mc);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
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
}
