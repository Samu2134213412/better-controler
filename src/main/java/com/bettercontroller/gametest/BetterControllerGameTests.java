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
import java.util.List;
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
    public static void controllerRecipeIsLoaded(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(BetterController.id("controller"));
        helper.assertTrue(recipe.isPresent(), "controller recipe not loaded");
        ItemStack result = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.CONTROLLER.get()), "recipe does not make a controller");
        helper.assertTrue(server.getAdvancements().get(BetterController.id("recipes/redstone/controller")) != null,
                "recipe book unlock advancement not loaded");
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
                CreateLinkBridge.testTransmitter(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                        "minecraft:redstone", "", 15).close();
            } catch (Exception e) {
                helper.fail("Create link self test failed: " + e);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The whole server path: a player holding the controller reports key B, the graph turns its
     * output on and the frequency becomes active; releasing the key turns it off again.
     *
     * <p>Runs only without Create: Create broadcasts packets to all players every few ticks, which
     * the mock player's connection cannot take. The Create part is covered by
     * {@link #createReceiverGetsSignal}.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heldKeyDrivesOutputs(GameTestHelper helper) {
        if (ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        int frequency = 777_001;
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONTROLLER.get()));

        LogicGraph graph = LogicGraph.create("Key Test");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 100, 0);
        out.setFrequency(frequency);
        graph.connect(new Connection(key.id(), 0, out.id(), 0));
        WorldGraphData data = WorldGraphData.get(helper.getLevel().getServer());
        data.put(graph, player.getUUID(), "mock");

        helper.startSequence()
                // The server reacts within the packet handler, not one tick later.
                .thenExecuteAfter(1, () -> {
                    ServerGraphService.handleHeldKeys(player, new GraphPayloads.HeldKeys(List.of(KeyCodes.DEFAULT_KEY)));
                    helper.assertTrue(ServerGraphService.signals().isActive(frequency), "frequency not active right after key press");
                })
                .thenExecute(() -> {
                    ServerGraphService.handleHeldKeys(player, new GraphPayloads.HeldKeys(List.of()));
                    helper.assertFalse(ServerGraphService.signals().isActive(frequency), "frequency still active right after release");
                })
                // Without the controller in hand, key reports are ignored.
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    ServerGraphService.handleHeldKeys(player, new GraphPayloads.HeldKeys(List.of(KeyCodes.DEFAULT_KEY)));
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertFalse(ServerGraphService.signals().isActive(frequency),
                        "key accepted without controller"))
                .thenExecute(() -> {
                    data.remove(graph.id());
                    player.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * With Create installed: our transmitter powers a real Create Redstone Link receiver that has
     * the same two frequency items, and turning it to strength 0 unpowers it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void createReceiverGetsSignal(GameTestHelper helper) {
        if (!CreateLinkBridge.isAvailable()) {
            helper.succeed();
            return;
        }
        BlockPos receiverPos = new BlockPos(1, 2, 1);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        Block link = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:redstone_link"));
        BlockState transmitterState = withValue(link.defaultBlockState(), "facing", "up");
        // Like a player-placed link: it starts as a transmitter (which creates its link behaviour on
        // the first tick) and is then switched to receiving, which keeps the frequency items.
        helper.setBlock(receiverPos, transmitterState);
        CreateLinkBridge.TestTransmitter[] transmitter = new CreateLinkBridge.TestTransmitter[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(linkBehaviour(helper, receiverPos) != null,
                        "Create Redstone Link not initialized yet"))
                .thenExecute(() -> {
                    setCreateFrequency(helper, linkBehaviour(helper, receiverPos));
                    helper.setBlock(receiverPos, withValue(transmitterState, "receiver", "true"));
                })
                .thenWaitUntil(() -> helper.assertTrue(isListening(linkBehaviour(helper, receiverPos)),
                        "Create Redstone Link did not switch to receiving"))
                .thenExecute(() -> {
                    try {
                        transmitter[0] = CreateLinkBridge.testTransmitter(helper.getLevel(),
                                helper.absolutePos(new BlockPos(0, 1, 0)), "minecraft:redstone", "minecraft:lapis_lazuli", 15);
                    } catch (ReflectiveOperationException e) {
                        helper.fail("Could not create transmitter: " + e);
                    }
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(receiverPos, BlockStateProperties.POWERED, true))
                .thenExecute(() -> {
                    try {
                        transmitter[0].setStrength(0);
                    } catch (ReflectiveOperationException e) {
                        helper.fail("Could not update transmitter: " + e);
                    }
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(receiverPos, BlockStateProperties.POWERED, false))
                .thenExecute(() -> transmitter[0].close())
                .thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, String name, String value) {
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            throw new IllegalStateException("Block has no property " + name);
        }
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    /** The LinkBehaviour of the Create Redstone Link at a relative position, or null. */
    private static Object linkBehaviour(GameTestHelper helper, BlockPos relativePos) {
        try {
            ClassLoader loader = BetterControllerGameTests.class.getClassLoader();
            Class<?> behaviourClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour", true, loader);
            Class<?> typeClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType", true, loader);
            Class<?> linkClass = Class.forName("com.simibubi.create.content.redstone.link.LinkBehaviour", true, loader);
            Object type = linkClass.getField("TYPE").get(null);
            return behaviourClass.getMethod("get", BlockGetter.class, BlockPos.class, typeClass)
                    .invoke(null, helper.getLevel(), helper.absolutePos(relativePos), type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Create API not found", e);
        }
    }

    private static boolean isListening(Object link) {
        try {
            return link != null && (boolean) link.getClass().getMethod("isListening").invoke(link);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Create API not found", e);
        }
    }

    /** Puts redstone and lapis into the two frequency slots of a Create Redstone Link. */
    private static void setCreateFrequency(GameTestHelper helper, Object link) {
        try {
            Method setFrequency = link.getClass().getMethod("setFrequency", boolean.class, ItemStack.class);
            setFrequency.invoke(link, true, new ItemStack(Items.REDSTONE));
            setFrequency.invoke(link, false, new ItemStack(Items.LAPIS_LAZULI));
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not configure Create Redstone Link: " + e);
        }
    }
}
