package com.bettercontroller;

import com.bettercontroller.item.ModItems;
import com.bettercontroller.network.GraphNetwork;
import com.bettercontroller.server.ModCommands;
import com.bettercontroller.server.ServerGraphService;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(BetterController.MOD_ID)
public final class BetterController {
    public static final String MOD_ID = "bettercontroller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterController(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        modBus.addListener(GraphNetwork::register);
        modBus.addListener(BetterController::addToCreativeTab);

        NeoForge.EVENT_BUS.register(ServerGraphService.class);
        NeoForge.EVENT_BUS.addListener(ModCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CONTROLLER);
        }
    }
}
