package com.bettercontroller.gametest;

import com.bettercontroller.BetterController;
import com.bettercontroller.integration.CreateLinkBridge;
import com.bettercontroller.logic.Connection;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;
import com.bettercontroller.item.ModItems;
import com.bettercontroller.logic.KeyCodes;
import com.bettercontroller.network.GraphPayloads;
import com.bettercontroller.server.ServerGraphService;
import com.bettercontroller.server.WorldGraphData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Checks that need a running server. CI runs them with {@code gradlew runGameTestServer}, once
 * without and once with Create installed.
 */
@GameTestHolder(BetterController.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BetterControllerGameTests {
    private BetterControllerGameTests() {
    }

    @GameTest(template = "empty")
    public static void worldDataSurvivesSaveAndLoad(GameTestHelper helper) {
        LogicGraph graph = LogicGraph.create("Game Test");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 100, 0);
        out.setFrequency(4242);
        out.setLinkItem(0, "minecraft:redstone");
        out.setStrength(9);
        graph.connect(new Connection(key.id(), 0, out.id(), 0));

        var registries = helper.getLevel().registryAccess();
        WorldGraphData data = new WorldGraphData();
        data.put(graph, UUID.randomUUID(), "tester");
        CompoundTag tag = data.save(new CompoundTag(), registries);
        WorldGraphData loaded = WorldGraphData.load(tag, registries);
        WorldGraphData.Entry entry = loaded.get(graph.id());
        helper.assertTrue(entry != null, "graph missing after load");
        helper.assertTrue(GraphJsonCodec.encode(graph, false).equals(GraphJsonCodec.encode(entry.graph(), false)),
                "graph changed by save/load");
        helper.assertTrue("tester".equals(entry.ownerName()), "owner name lost");

        // The real per-world storage of the test server must be reachable too.
        helper.assertTrue(WorldGraphData.get(helper.getLevel().getServer()) != null, "no world graph data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemIdsAreCheckedAgainstRegistry(GameTestHelper helper) {
        helper.assertTrue(CreateLinkBridge.isKnownItem(""), "empty slot rejected");
        helper.assertTrue(CreateLinkBridge.isKnownItem("minecraft:redstone"), "redstone rejected");
        helper.assertFalse(CreateLinkBridge.isKnownItem("minecraft:air"), "air accepted");
        helper.assertFalse(CreateLinkBridge.isKnownItem("nomod:nothing"), "unknown item accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createBridgeMatchesInstalledCreate(GameTestHelper helper) {
        boolean createLoaded = ModList.get().isLoaded("create");
        helper.assertTrue(CreateLinkBridge.isAvailable() == createLoaded,
                "bridge available=" + CreateLinkBridge.isAvailable() + " but create loaded=" + createLoaded);
        if (createLoaded) {
            try {
                CreateLinkBridge.selfTest(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            } catch (Exception e) {
                helper.fail("Create link self test failed: " + e);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The whole server path: a player holding the controller reports key B, the graph turns its
     * output on, the frequency becomes active and - with Create installed - a real Create Redstone
     * Link receiver with the same two items gets powered. Releasing the key turns everything off.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heldKeyDrivesOutputs(GameTestHelper helper) {
        boolean create = CreateLinkBridge.isAvailable();
        BlockPos receiverPos = new BlockPos(1, 2, 1);
        int frequency = 777_001;

        ServerPlayer player = mockPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONTROLLER.get()));
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 0));
        player.moveTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);

        LogicGraph graph = LogicGraph.create("Key Test");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 100, 0);
        out.setFrequency(frequency);
        out.setLinkItem(0, "minecraft:redstone");
        out.setLinkItem(1, "minecraft:lapis_lazuli");
        graph.connect(new Connection(key.id(), 0, out.id(), 0));
        WorldGraphData data = WorldGraphData.get(helper.getLevel().getServer());
        data.put(graph, player.getUUID(), "mock");

        if (create) {
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            Block link = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:redstone_link"));
            BlockState state = withValue(withValue(link.defaultBlockState(), "facing", "up"), "receiver", "true");
            helper.setBlock(receiverPos, state);
        }

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (create) {
                        setCreateFrequency(helper, helper.absolutePos(receiverPos));
                    }
                    ServerGraphService.handleHeldKeys(player, new GraphPayloads.HeldKeys(List.of(KeyCodes.DEFAULT_KEY)));
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(ServerGraphService.signals().isActive(frequency), "frequency not active after key press");
                    if (create) {
                        helper.assertBlockProperty(receiverPos, BlockStateProperties.POWERED, true);
                    }
                })
                .thenExecute(() -> ServerGraphService.handleHeldKeys(player, new GraphPayloads.HeldKeys(List.of())))
                .thenWaitUntil(() -> {
                    helper.assertFalse(ServerGraphService.signals().isActive(frequency), "frequency still active after release");
                    if (create) {
                        helper.assertBlockProperty(receiverPos, BlockStateProperties.POWERED, false);
                    }
                })
                .thenExecute(() -> {
                    data.remove(graph.id());
                    player.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * A mock player in the player list. Other mods' login handlers may throw because the mock
     * connection cannot receive their packets (Create does); the player is already in the player
     * list when the login event fires, so it is looked up there in that case.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        var players = helper.getLevel().getServer().getPlayerList();
        Set<UUID> before = new HashSet<>();
        players.getPlayers().forEach(p -> before.add(p.getUUID()));
        try {
            return helper.makeMockServerPlayerInLevel();
        } catch (RuntimeException e) {
            return players.getPlayers().stream()
                    .filter(p -> !before.contains(p.getUUID()))
                    .findFirst()
                    .orElseThrow(() -> e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, String name, String value) {
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            throw new IllegalStateException("Block has no property " + name);
        }
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    /** Puts redstone and lapis into the two frequency slots of a Create Redstone Link. */
    private static void setCreateFrequency(GameTestHelper helper, BlockPos absolutePos) {
        try {
            ClassLoader loader = BetterControllerGameTests.class.getClassLoader();
            Class<?> behaviourClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour", true, loader);
            Class<?> typeClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType", true, loader);
            Class<?> linkClass = Class.forName("com.simibubi.create.content.redstone.link.LinkBehaviour", true, loader);
            Object type = linkClass.getField("TYPE").get(null);
            Object link = behaviourClass.getMethod("get", BlockGetter.class, BlockPos.class, typeClass)
                    .invoke(null, helper.getLevel(), absolutePos, type);
            if (link == null) {
                helper.fail("Create Redstone Link has no link behaviour");
                return;
            }
            Method setFrequency = linkClass.getMethod("setFrequency", boolean.class, ItemStack.class);
            setFrequency.invoke(link, true, new ItemStack(Items.REDSTONE));
            setFrequency.invoke(link, false, new ItemStack(Items.LAPIS_LAZULI));
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not configure Create Redstone Link: " + e);
        }
    }
}
