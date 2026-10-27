package com.bettercontroller.client;

import com.bettercontroller.client.screen.GraphManagerScreen;
import com.bettercontroller.client.screen.LogicEditorScreen;
import com.bettercontroller.client.screen.StatusSink;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.GraphValidationException;
import com.bettercontroller.logic.GraphWire;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.network.GraphPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Handles server-to-client packets. Only loaded on the client. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleGraphList(GraphPayloads.GraphList payload) {
        ClientGraphState.setGraphs(payload.graphs());
        if (Minecraft.getInstance().screen instanceof GraphManagerScreen screen) {
            screen.onGraphListChanged();
        }
    }

    public static void handleGraphData(GraphPayloads.GraphData payload) {
        LogicGraph graph;
        try {
            graph = GraphJsonCodec.decode(GraphWire.decompress(payload.data()));
        } catch (GraphValidationException e) {
            handleStatus(new GraphPayloads.Status("Received invalid graph: " + e.getMessage(), true, false));
            return;
        }
        ClientGraphState.setEditingGraph(graph, false);
        Minecraft mc = Minecraft.getInstance();
        // Only replace a screen of this mod; never pop the editor up over another GUI or the game.
        if (mc.screen instanceof GraphManagerScreen || mc.screen instanceof LogicEditorScreen) {
            mc.setScreen(new LogicEditorScreen(graph));
        }
    }

    public static void handleBoundKeys(GraphPayloads.BoundKeys payload) {
        ClientGraphState.setBoundKeys(payload.keys());
    }

    public static void handleStatus(GraphPayloads.Status payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.saved()) {
            ClientGraphState.setUnsaved(false);
        }
        if (mc.screen instanceof StatusSink sink) {
            sink.showStatus(payload.message(), payload.error());
        } else if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(payload.message()), true);
        }
    }
}
