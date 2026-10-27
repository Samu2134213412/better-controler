package com.bettercontroller.server;

import com.bettercontroller.logic.GraphLimits;
import com.bettercontroller.signal.FrequencyRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

/**
 * {@code /bettercontroller signal <frequency>} and {@code /bettercontroller signals}: read-only
 * views of the wireless signal state, mainly for testing circuits until receivers exist.
 */
public final class ModCommands {
    private static final int MAX_LISTED = 50;

    private ModCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bettercontroller")
                .then(Commands.literal("signal")
                        .then(Commands.argument("frequency",
                                        IntegerArgumentType.integer(GraphLimits.MIN_FREQUENCY, GraphLimits.MAX_FREQUENCY))
                                .executes(context -> {
                                    int frequency = IntegerArgumentType.getInteger(context, "frequency");
                                    FrequencyRegistry signals = ServerGraphService.signals();
                                    boolean active = signals.isActive(frequency);
                                    context.getSource().sendSuccess(() -> Component.literal("Frequency " + frequency + ": "
                                            + (active ? "ON" : "OFF") + " (" + signals.sourceCount(frequency) + " outputs)"), false);
                                    return active ? 1 : 0;
                                })))
                .then(Commands.literal("signals")
                        .executes(context -> {
                            Set<Integer> active = ServerGraphService.signals().activeFrequencies();
                            String list = active.isEmpty() ? "none" : String.join(", ",
                                    active.stream().limit(MAX_LISTED).map(String::valueOf).toList())
                                    + (active.size() > MAX_LISTED ? ", ..." : "");
                            context.getSource().sendSuccess(() -> Component.literal("Active frequencies: " + list), false);
                            return active.size();
                        })));
    }
}
