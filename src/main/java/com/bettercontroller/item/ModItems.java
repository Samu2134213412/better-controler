package com.bettercontroller.item;

import com.bettercontroller.BetterController;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BetterController.MOD_ID);

    public static final DeferredItem<Item> CONTROLLER =
            ITEMS.registerSimpleItem("controller", new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    /** True if the player holds the controller in the main or off hand. */
    public static boolean isHoldingController(Player player) {
        return player.getMainHandItem().is(CONTROLLER.get()) || player.getOffhandItem().is(CONTROLLER.get());
    }
}
