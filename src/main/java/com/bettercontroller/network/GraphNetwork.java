package com.bettercontroller.network;

import com.bettercontroller.client.ClientPayloadHandler;
import com.bettercontroller.network.GraphPayloads.BoundKeys;
import com.bettercontroller.network.GraphPayloads.DeleteGraph;
import com.bettercontroller.network.GraphPayloads.GraphData;
import com.bettercontroller.network.GraphPayloads.GraphList;
import com.bettercontroller.network.GraphPayloads.HeldKeys;
import com.bettercontroller.network.GraphPayloads.OpenGraph;
import com.bettercontroller.network.GraphPayloads.RequestGraphList;
import com.bettercontroller.network.GraphPayloads.SaveGraph;
import com.bettercontroller.network.GraphPayloads.Status;
import com.bettercontroller.server.ServerGraphService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

/** Registers all packets. Server handlers run on the server thread and only for real players. */
public final class GraphNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private GraphNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(RequestGraphList.TYPE, RequestGraphList.CODEC, server(ServerGraphService::handleListRequest));
        registrar.playToServer(OpenGraph.TYPE, OpenGraph.CODEC, server(ServerGraphService::handleOpen));
        registrar.playToServer(SaveGraph.TYPE, SaveGraph.CODEC, server(ServerGraphService::handleSave));
        registrar.playToServer(DeleteGraph.TYPE, DeleteGraph.CODEC, server(ServerGraphService::handleDelete));
        registrar.playToServer(HeldKeys.TYPE, HeldKeys.CODEC, server(ServerGraphService::handleHeldKeys));

        // The lambdas keep client classes from loading on a dedicated server: they are only
        // resolved when a packet actually arrives on the client.
        registrar.playToClient(GraphList.TYPE, GraphList.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleGraphList(payload)));
        registrar.playToClient(GraphData.TYPE, GraphData.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleGraphData(payload)));
        registrar.playToClient(BoundKeys.TYPE, BoundKeys.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleBoundKeys(payload)));
        registrar.playToClient(Status.TYPE, Status.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleStatus(payload)));
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> server(BiConsumer<ServerPlayer, T> handler) {
        return (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                handler.accept(player, payload);
            }
        });
    }
}
