package com.bettercontroller.gametest;

import com.bettercontroller.BetterController;
import com.bettercontroller.integration.CreateLinkBridge;
import com.bettercontroller.logic.Connection;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;
import com.bettercontroller.server.WorldGraphData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
}
