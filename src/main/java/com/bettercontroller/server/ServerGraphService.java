package com.bettercontroller.server;

import com.bettercontroller.BetterController;
import com.bettercontroller.integration.CreateLinkBridge;
import com.bettercontroller.item.ModItems;
import com.bettercontroller.logic.GraphEvaluator;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.GraphLimits;
import com.bettercontroller.logic.GraphValidationException;
import com.bettercontroller.logic.GraphWire;
import com.bettercontroller.logic.KeyCodes;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;
import com.bettercontroller.network.GraphPayloads;
import com.bettercontroller.signal.FrequencyRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Server side of the mod: handles graph packets, checks permissions, runs the graphs and keeps the
 * frequency signals up to date. All state is per server, i.e. per world, and is reset when the
 * server stops.
 *
 * <p>Graphs run continuously. Keyboard inputs of a graph read the keys its owner holds, and only
 * while the owner is online and holds a controller; otherwise they read as released. Outputs drive
 * the mod's own frequencies and, if Create is installed, Create link frequencies
 * (see {@link CreateLinkBridge}).
 */
public final class ServerGraphService {
    /** Operators (permission level 2) may open, edit and delete every graph. */
    private static final int OP_LEVEL = 2;
    /** Minimum ticks between two save/delete requests of one player. */
    private static final int ACTION_COOLDOWN_TICKS = 5;

    private static final FrequencyRegistry SIGNALS = new FrequencyRegistry();
    private static final Map<UUID, Set<Integer>> HELD_KEYS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ACTION_TICK = new HashMap<>();
    /** Owners whose graphs must be re-evaluated on the next tick. */
    private static final Set<UUID> DIRTY_OWNERS = new HashSet<>();
    /** Node states of every graph from its last evaluation. */
    private static final Map<UUID, Map<Integer, Boolean>> LAST_STATES = new HashMap<>();
    /** Output list last sent to each online owner, to send only changes. */
    private static final Map<UUID, List<GraphPayloads.OutputInfo>> LAST_SENT_OUTPUTS = new HashMap<>();

    private ServerGraphService() {
    }

    public static FrequencyRegistry signals() {
        return SIGNALS;
    }

    // ---- lifecycle ----

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        resetState();
        for (WorldGraphData.Entry entry : WorldGraphData.get(event.getServer()).all()) {
            evaluate(entry);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        resetState();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendBoundKeys(player);
            // Evaluating the owner's graphs also sends the current output states to the client.
            DIRTY_OWNERS.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_ACTION_TICK.remove(id);
        LAST_SENT_OUTPUTS.remove(id);
        Set<Integer> held = HELD_KEYS.remove(id);
        if (held != null && !held.isEmpty()) {
            DIRTY_OWNERS.add(id);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Releasing the controller releases all keys, even if the client never tells us.
        for (Map.Entry<UUID, Set<Integer>> entry : HELD_KEYS.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ModItems.isHoldingController(player)) {
                entry.getValue().clear();
                DIRTY_OWNERS.add(entry.getKey());
            }
        }
        WorldGraphData data = WorldGraphData.get(server);
        if (!DIRTY_OWNERS.isEmpty()) {
            for (WorldGraphData.Entry entry : data.all()) {
                if (DIRTY_OWNERS.contains(entry.owner())) {
                    evaluate(entry);
                }
            }
            for (UUID owner : DIRTY_OWNERS) {
                ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player != null) {
                    sendOutputsIfChanged(player, data);
                }
            }
            DIRTY_OWNERS.clear();
        }
        // Every tick, because link transmitters follow their owner's position.
        CreateLinkBridge.sync(server, data.all(), LAST_STATES);
    }

    /**
     * Re-evaluates one owner's graphs right away and pushes the result to the frequencies, Create
     * links and the owner's HUD, instead of waiting for the end of the tick. Keeps key presses fast.
     */
    private static void refreshOwnerNow(MinecraftServer server, UUID owner) {
        WorldGraphData data = WorldGraphData.get(server);
        for (WorldGraphData.Entry entry : data.all()) {
            if (entry.owner().equals(owner)) {
                evaluate(entry);
            }
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            sendOutputsIfChanged(player, data);
        }
        CreateLinkBridge.sync(server, data.all(), LAST_STATES);
    }

    // ---- packet handlers (server thread) ----

    public static void handleListRequest(ServerPlayer player, GraphPayloads.RequestGraphList payload) {
        sendGraphList(player);
    }

    public static void handleOpen(ServerPlayer player, GraphPayloads.OpenGraph payload) {
        WorldGraphData.Entry entry = WorldGraphData.get(player.server).get(payload.graphId());
        if (entry == null) {
            status(player, "Graph not found", true);
            sendGraphList(player);
            return;
        }
        if (!mayAccess(player, entry)) {
            status(player, "You may not open this graph", true);
            return;
        }
        send(player, new GraphPayloads.GraphData(GraphWire.compress(GraphJsonCodec.encode(entry.graph(), false))));
    }

    public static void handleSave(ServerPlayer player, GraphPayloads.SaveGraph payload) {
        if (!checkCooldown(player)) {
            return;
        }
        LogicGraph graph;
        try {
            graph = GraphJsonCodec.decode(GraphWire.decompress(payload.data()));
        } catch (GraphValidationException e) {
            status(player, "Not saved: " + e.getMessage(), true);
            return;
        }
        for (LogicNode node : graph.nodes()) {
            if (node.type() == NodeType.REDSTONE_OUTPUT) {
                for (int slot = 0; slot < 2; slot++) {
                    if (!CreateLinkBridge.isKnownItem(node.linkItem(slot))) {
                        status(player, "Not saved: unknown item " + node.linkItem(slot), true);
                        return;
                    }
                }
            }
        }
        WorldGraphData data = WorldGraphData.get(player.server);
        WorldGraphData.Entry existing = data.get(graph.id());
        UUID owner;
        String ownerName;
        if (existing != null) {
            if (!mayAccess(player, existing)) {
                status(player, "Not saved: this graph belongs to " + existing.ownerName(), true);
                return;
            }
            owner = existing.owner();
            ownerName = existing.ownerName();
        } else {
            if (data.countOwnedBy(player.getUUID()) >= GraphLimits.MAX_GRAPHS_PER_PLAYER) {
                status(player, "Not saved: limit of " + GraphLimits.MAX_GRAPHS_PER_PLAYER + " graphs reached", true);
                return;
            }
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
        data.put(graph, owner, ownerName);
        evaluate(data.get(graph.id()));
        send(player, new GraphPayloads.Status("Saved \"" + graph.name() + "\"", false, true));
        sendGraphList(player);
        notifyOwner(player.server, owner);
    }

    public static void handleDelete(ServerPlayer player, GraphPayloads.DeleteGraph payload) {
        if (!checkCooldown(player)) {
            return;
        }
        WorldGraphData data = WorldGraphData.get(player.server);
        WorldGraphData.Entry entry = data.get(payload.graphId());
        if (entry == null) {
            sendGraphList(player);
            return;
        }
        if (!mayAccess(player, entry)) {
            status(player, "You may not delete this graph", true);
            return;
        }
        data.remove(payload.graphId());
        SIGNALS.removeGraph(payload.graphId());
        LAST_STATES.remove(payload.graphId());
        status(player, "Deleted \"" + entry.graph().name() + "\"", false);
        sendGraphList(player);
        notifyOwner(player.server, entry.owner());
    }

    public static void handleHeldKeys(ServerPlayer player, GraphPayloads.HeldKeys payload) {
        Set<Integer> next = new TreeSet<>();
        if (ModItems.isHoldingController(player)) {
            Set<Integer> bound = boundKeys(WorldGraphData.get(player.server), player.getUUID());
            for (int key : payload.keys()) {
                if (KeyCodes.isAssignable(key) && bound.contains(key)) {
                    next.add(key);
                }
            }
        }
        Set<Integer> previous = HELD_KEYS.getOrDefault(player.getUUID(), Set.of());
        if (!previous.equals(next)) {
            HELD_KEYS.put(player.getUUID(), next);
            refreshOwnerNow(player.server, player.getUUID());
        }
    }

    // ---- helpers ----

    private static void evaluate(WorldGraphData.Entry entry) {
        Set<Integer> held = HELD_KEYS.getOrDefault(entry.owner(), Set.of());
        Map<Integer, Boolean> states = GraphEvaluator.evaluate(entry.graph(), held);
        LAST_STATES.put(entry.graph().id(), states);
        SIGNALS.updateGraph(entry.graph(), states);
    }

    private static boolean mayAccess(ServerPlayer player, WorldGraphData.Entry entry) {
        return entry.owner().equals(player.getUUID()) || player.hasPermissions(OP_LEVEL);
    }

    private static boolean checkCooldown(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_ACTION_TICK.get(player.getUUID());
        if (last != null && now - last < ACTION_COOLDOWN_TICKS) {
            status(player, "Too fast, please try again", true);
            return false;
        }
        LAST_ACTION_TICK.put(player.getUUID(), now);
        return true;
    }

    private static void sendGraphList(ServerPlayer player) {
        boolean op = player.hasPermissions(OP_LEVEL);
        List<GraphPayloads.GraphSummary> list = new ArrayList<>();
        for (WorldGraphData.Entry entry : WorldGraphData.get(player.server).all()) {
            boolean own = entry.owner().equals(player.getUUID());
            if (own || op) {
                list.add(new GraphPayloads.GraphSummary(entry.graph().id(), entry.graph().name(),
                        entry.ownerName(), entry.graph().nodes().size(), own));
            }
        }
        list.sort(Comparator.comparing((GraphPayloads.GraphSummary s) -> !s.own())
                .thenComparing(GraphPayloads.GraphSummary::name, String.CASE_INSENSITIVE_ORDER));
        if (list.size() > GraphPayloads.MAX_LISTED_GRAPHS) {
            list = new ArrayList<>(list.subList(0, GraphPayloads.MAX_LISTED_GRAPHS));
        }
        send(player, new GraphPayloads.GraphList(list));
    }

    /** The owner's key bindings may have changed; tell their client which keys to report. */
    private static void notifyOwner(MinecraftServer server, UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            sendBoundKeys(player);
        }
        DIRTY_OWNERS.add(owner);
    }

    private static void sendBoundKeys(ServerPlayer player) {
        Set<Integer> keys = boundKeys(WorldGraphData.get(player.server), player.getUUID());
        send(player, new GraphPayloads.BoundKeys(new ArrayList<>(keys)));
    }

    private static Set<Integer> boundKeys(WorldGraphData data, UUID owner) {
        Set<Integer> keys = new TreeSet<>();
        for (WorldGraphData.Entry entry : data.all()) {
            if (entry.owner().equals(owner)) {
                keys.addAll(entry.graph().usedKeys());
            }
        }
        return keys;
    }

    /** Output nodes of the player's graphs with their current state, for the controller HUD. */
    private static void sendOutputsIfChanged(ServerPlayer player, WorldGraphData data) {
        List<GraphPayloads.OutputInfo> outputs = new ArrayList<>();
        for (WorldGraphData.Entry entry : data.all()) {
            if (!entry.owner().equals(player.getUUID())) {
                continue;
            }
            Map<Integer, Boolean> states = LAST_STATES.getOrDefault(entry.graph().id(), Map.of());
            for (LogicNode node : entry.graph().nodes()) {
                if (node.type() == NodeType.REDSTONE_OUTPUT && outputs.size() < GraphPayloads.MAX_OUTPUT_INFOS) {
                    outputs.add(new GraphPayloads.OutputInfo(entry.graph().name(), node.frequency(),
                            node.linkItem(0), node.linkItem(1), node.strength(), states.getOrDefault(node.id(), false)));
                }
            }
        }
        if (!outputs.equals(LAST_SENT_OUTPUTS.get(player.getUUID()))) {
            LAST_SENT_OUTPUTS.put(player.getUUID(), outputs);
            send(player, new GraphPayloads.OutputStates(outputs));
        }
    }

    /** Sends a packet if the client can receive it (e.g. not a game test's mock player). */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void status(ServerPlayer player, String message, boolean error) {
        send(player, new GraphPayloads.Status(message, error, false));
    }

    private static void resetState() {
        SIGNALS.clear();
        HELD_KEYS.clear();
        LAST_ACTION_TICK.clear();
        DIRTY_OWNERS.clear();
        LAST_STATES.clear();
        LAST_SENT_OUTPUTS.clear();
        CreateLinkBridge.clear();
        BetterController.LOGGER.debug("Better Controller signal state reset");
    }
}
