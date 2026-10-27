package com.bettercontroller.logic;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicGateTest {
    private static final int KEY_A = 65;
    private static final int KEY_B = 66;

    @Test
    void truthTables() {
        boolean[][] cases = {{false, false}, {false, true}, {true, false}, {true, true}};
        boolean[] and = {false, false, false, true};
        boolean[] or = {false, true, true, true};
        boolean[] nand = {true, true, true, false};
        boolean[] nor = {true, false, false, false};
        boolean[] xor = {false, true, true, false};
        for (int i = 0; i < cases.length; i++) {
            assertEquals(and[i], NodeType.AND.evaluate(cases[i]));
            assertEquals(or[i], NodeType.OR.evaluate(cases[i]));
            assertEquals(nand[i], NodeType.NAND.evaluate(cases[i]));
            assertEquals(nor[i], NodeType.NOR.evaluate(cases[i]));
            assertEquals(xor[i], NodeType.XOR.evaluate(cases[i]));
        }
        assertTrue(NodeType.NOT.evaluate(new boolean[]{false}));
        assertFalse(NodeType.NOT.evaluate(new boolean[]{true}));
    }

    @Test
    void evaluatesKeyboardCircuit() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode a = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        a.setKey(KEY_A);
        LogicNode b = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 50);
        LogicNode xor = graph.addNode(NodeType.XOR, 100, 0);
        LogicNode not = graph.addNode(NodeType.NOT, 200, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 300, 0);
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(a.id(), 0, xor.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(b.id(), 0, xor.id(), 1)));
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(xor.id(), 0, not.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(not.id(), 0, out.id(), 0)));

        assertTrue(GraphEvaluator.evaluate(graph, Set.of()).get(out.id()));
        assertFalse(GraphEvaluator.evaluate(graph, Set.of(KEY_A)).get(out.id()));
        assertFalse(GraphEvaluator.evaluate(graph, Set.of(KEY_B)).get(out.id()));
        assertTrue(GraphEvaluator.evaluate(graph, Set.of(KEY_A, KEY_B)).get(out.id()));
    }

    @Test
    void unconnectedInputsAreOff() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode or = graph.addNode(NodeType.OR, 0, 0);
        LogicNode nor = graph.addNode(NodeType.NOR, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 0, 0);
        Map<Integer, Boolean> states = GraphEvaluator.evaluate(graph, Set.of());
        assertFalse(states.get(or.id()));
        assertTrue(states.get(nor.id()));
        assertFalse(states.get(out.id()));
    }

    @Test
    void linkSettingsAreValidated() {
        LogicNode node = new LogicNode(1, NodeType.REDSTONE_OUTPUT, 0, 0);
        assertFalse(node.hasLinkFrequency());
        assertTrue(node.setLinkItem(1, "minecraft:lever"));
        assertTrue(node.hasLinkFrequency());
        assertFalse(node.setLinkItem(2, "minecraft:lever"));
        assertFalse(node.setLinkItem(0, "lever"));
        assertFalse(node.setLinkItem(0, "minecraft:Lever"));
        assertFalse(node.setLinkItem(0, "minecraft:a:b"));
        assertFalse(node.setLinkItem(0, null));
        assertTrue(node.setLinkItem(1, ""));
        assertFalse(node.hasLinkFrequency());
        assertEquals(15, node.strength());
        assertFalse(node.setStrength(0));
        assertFalse(node.setStrength(16));
        assertTrue(node.setStrength(1));
    }

    @Test
    void keyboardNodesDefaultToBAndRejectInvalidKeys() {
        LogicNode node = new LogicNode(1, NodeType.KEYBOARD_INPUT, 0, 0);
        assertEquals(KEY_B, node.key());
        assertFalse(node.setKey(KeyCodes.ESCAPE));
        assertFalse(node.setKey(-1));
        assertFalse(node.setKey(0));
        assertFalse(node.setKey(1000));
        assertEquals(KEY_B, node.key());
        assertTrue(node.setKey(32));   // space
        assertTrue(node.setKey(290));  // F1
        assertTrue(node.setKey(265));  // arrow up
        assertTrue(node.setKey(48));   // 0
    }
}
