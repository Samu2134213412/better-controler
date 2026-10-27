package com.bettercontroller.integration;

import com.bettercontroller.BetterController;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;
import com.bettercontroller.server.WorldGraphData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sends redstone output nodes into Create's Redstone Link network, so Create Redstone Links (and
 * links of addons such as Create: Aeronautics that use the same network) receive them.
 *
 * <p>Works like Create's Linked Controller: every output with at least one frequency item is a
 * virtual transmitter at its graph owner's position, in the owner's dimension. It transmits only
 * while the owner is online, and receivers must be within Create's link range (256 blocks by default).
 * The strength is the node's strength while the output is on and 0 while it is off.
 *
 * <p>Create is an optional dependency and is only accessed through reflection, so this mod needs no
 * Create classes to compile or run. If anything in Create's API does not match, the bridge logs one
 * warning and switches itself off instead of crashing.
 */
public final class CreateLinkBridge {
    private static final String CREATE_ID = "create";

    private static boolean initialized;
    private static boolean available;
    private static Object networkHandler;
    private static Class<?> linkableClass;
    private static Method addToNetwork;
    private static Method removeFromNetwork;
    private static Method updateNetworkOf;
    private static Method frequencyOf;
    private static Method coupleCreate;

    private record SourceKey(UUID graphId, int nodeId) {
    }

    private static final Map<SourceKey, VirtualLink> LINKS = new HashMap<>();

    private CreateLinkBridge() {
    }

    public static boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            available = ModList.get().isLoaded(CREATE_ID) && initReflection();
        }
        return available;
    }

    private static boolean initReflection() {
        try {
            ClassLoader loader = CreateLinkBridge.class.getClassLoader();
            Class<?> create = Class.forName("com.simibubi.create.Create", true, loader);
            Class<?> handlerClass = Class.forName("com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler", true, loader);
            Class<?> frequencyClass = Class.forName(
                    "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency", true, loader);
            Class<?> coupleClass = Class.forName("net.createmod.catnip.data.Couple", true, loader);
            Class<?> levelAccessor = Class.forName("net.minecraft.world.level.LevelAccessor", true, loader);
            linkableClass = Class.forName("com.simibubi.create.content.redstone.link.IRedstoneLinkable", true, loader);

            networkHandler = create.getField("REDSTONE_LINK_NETWORK_HANDLER").get(null);
            addToNetwork = handlerClass.getMethod("addToNetwork", levelAccessor, linkableClass);
            removeFromNetwork = handlerClass.getMethod("removeFromNetwork", levelAccessor, linkableClass);
            updateNetworkOf = handlerClass.getMethod("updateNetworkOf", levelAccessor, linkableClass);
            frequencyOf = frequencyClass.getMethod("of", ItemStack.class);
            coupleCreate = coupleClass.getMethod("create", Object.class, Object.class);
            BetterController.LOGGER.info("Create found: redstone outputs can transmit on Create link frequencies");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            BetterController.LOGGER.warn("Create is installed but its Redstone Link API was not found; link output disabled", e);
            return false;
        }
    }

    /** True if the item id names an existing item. "" (empty slot) is valid too. */
    public static boolean isKnownItem(String itemId) {
        return itemId.isEmpty() || item(itemId).isPresent();
    }

    /**
     * Brings Create's link network in line with the current output states. Called every server tick;
     * cheap when nothing changed.
     *
     * @param states node states per graph id, from the last evaluation
     */
    public static void sync(MinecraftServer server, Collection<WorldGraphData.Entry> entries,
                            Map<UUID, Map<Integer, Boolean>> states) {
        if (!isAvailable()) {
            return;
        }
        try {
            Set<SourceKey> present = new HashSet<>();
            for (WorldGraphData.Entry entry : entries) {
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.owner());
                if (owner == null) {
                    continue;
                }
                Map<Integer, Boolean> graphStates = states.getOrDefault(entry.graph().id(), Map.of());
                for (LogicNode node : entry.graph().nodes()) {
                    if (node.type() != NodeType.REDSTONE_OUTPUT || !node.hasLinkFrequency()) {
                        continue;
                    }
                    SourceKey key = new SourceKey(entry.graph().id(), node.id());
                    int strength = graphStates.getOrDefault(node.id(), false) ? node.strength() : 0;
                    if (syncLink(key, node, owner, strength)) {
                        present.add(key);
                    }
                }
            }
            LINKS.entrySet().removeIf(e -> {
                if (present.contains(e.getKey())) {
                    return false;
                }
                e.getValue().remove();
                return true;
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            BetterController.LOGGER.warn("Create link output failed and was disabled", e);
            available = false;
            LINKS.clear();
        }
    }

    /** @return false if the node has no usable frequency (e.g. an item from a removed mod) */
    private static boolean syncLink(SourceKey key, LogicNode node, ServerPlayer owner, int strength)
            throws ReflectiveOperationException {
        ServerLevel level = owner.serverLevel();
        BlockPos pos = owner.blockPosition();
        VirtualLink link = LINKS.get(key);
        if (link != null && (link.level != level || !link.item1.equals(node.linkItem(0)) || !link.item2.equals(node.linkItem(1)))) {
            link.remove();
            LINKS.remove(key);
            link = null;
        }
        if (link == null) {
            Object networkKey = networkKey(node.linkItem(0), node.linkItem(1));
            if (networkKey == null) {
                return false;
            }
            link = new VirtualLink(level, pos, networkKey, node.linkItem(0), node.linkItem(1), strength);
            link.add();
            LINKS.put(key, link);
        } else if (!link.pos.equals(pos) || link.strength != strength) {
            link.pos = pos;
            link.strength = strength;
            link.update();
        }
        return true;
    }

    /** A transmitter that is not tied to a graph; lets the game tests drive a real Create receiver. */
    public static final class TestTransmitter {
        private final VirtualLink link;

        private TestTransmitter(VirtualLink link) {
            this.link = link;
        }

        public void setStrength(int strength) throws ReflectiveOperationException {
            link.strength = strength;
            link.update();
        }

        public void close() {
            link.remove();
        }
    }

    public static TestTransmitter testTransmitter(ServerLevel level, BlockPos pos, String item1, String item2, int strength)
            throws ReflectiveOperationException {
        if (!isAvailable()) {
            throw new IllegalStateException("Create link bridge is not available");
        }
        Object key = networkKey(item1, item2);
        if (key == null) {
            throw new IllegalStateException("Could not build a Create frequency");
        }
        VirtualLink link = new VirtualLink(level, pos, key, item1, item2, strength);
        link.add();
        return new TestTransmitter(link);
    }

    /** Server stopped: Create drops its network space with the levels, so only forget our side. */
    public static void clear() {
        LINKS.clear();
    }

    private static Object networkKey(String item1, String item2) throws ReflectiveOperationException {
        Object first = frequency(item1);
        Object second = frequency(item2);
        return first == null || second == null ? null : coupleCreate.invoke(null, first, second);
    }

    private static Object frequency(String itemId) throws ReflectiveOperationException {
        if (itemId.isEmpty()) {
            return frequencyOf.invoke(null, ItemStack.EMPTY);
        }
        Optional<Item> item = item(itemId);
        return item.isPresent() ? frequencyOf.invoke(null, new ItemStack(item.get())) : null;
    }

    private static Optional<Item> item(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR);
    }

    private static Object callHandler(Method method, Object... args) throws ReflectiveOperationException {
        try {
            return method.invoke(networkHandler, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    /** One transmitter in Create's network, implemented as a proxy of Create's IRedstoneLinkable. */
    private static final class VirtualLink implements InvocationHandler {
        final ServerLevel level;
        final Object networkKey;
        final String item1;
        final String item2;
        BlockPos pos;
        int strength;
        boolean alive = true;
        final Object proxy;

        VirtualLink(ServerLevel level, BlockPos pos, Object networkKey, String item1, String item2, int strength) {
            this.level = level;
            this.pos = pos;
            this.networkKey = networkKey;
            this.item1 = item1;
            this.item2 = item2;
            this.strength = strength;
            this.proxy = Proxy.newProxyInstance(linkableClass.getClassLoader(), new Class<?>[]{linkableClass}, this);
        }

        void add() throws ReflectiveOperationException {
            callHandler(addToNetwork, level, proxy);
        }

        void update() throws ReflectiveOperationException {
            callHandler(updateNetworkOf, level, proxy);
        }

        void remove() {
            alive = false;
            strength = 0;
            try {
                callHandler(removeFromNetwork, level, proxy);
            } catch (ReflectiveOperationException | RuntimeException e) {
                BetterController.LOGGER.debug("Could not remove Create link", e);
            }
        }

        @Override
        public Object invoke(Object self, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getTransmittedStrength" -> alive ? strength : 0;
                case "setReceivedStrength" -> null;
                case "isListening" -> false;
                case "isAlive" -> alive;
                case "getNetworkKey" -> networkKey;
                case "getLocation" -> pos;
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> "BetterController link " + item1 + "/" + item2;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
