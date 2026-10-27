package com.bettercontroller.logic;

import com.bettercontroller.signal.FrequencyRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrequencyRegistryTest {
    private static final int KEY = KeyCodes.DEFAULT_KEY;

    /** Keyboard input wired straight to an output on the given frequency. */
    private static LogicGraph buttonGraph(int frequency) {
        LogicGraph graph = LogicGraph.create("button");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 100, 0);
        out.setFrequency(frequency);
        graph.connect(new Connection(key.id(), 0, out.id(), 0));
        return graph;
    }

    private static Set<Integer> update(FrequencyRegistry registry, LogicGraph graph, boolean pressed) {
        return registry.updateGraph(graph, GraphEvaluator.evaluate(graph, pressed ? Set.of(KEY) : Set.of()));
    }

    @Test
    void outputsDriveTheirOwnFrequency() {
        FrequencyRegistry registry = new FrequencyRegistry();
        LogicGraph graph = LogicGraph.create("two outputs");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode not = graph.addNode(NodeType.NOT, 0, 0);
        LogicNode outA = graph.addNode(NodeType.REDSTONE_OUTPUT, 0, 0);
        LogicNode outB = graph.addNode(NodeType.REDSTONE_OUTPUT, 0, 0);
        outA.setFrequency(10);
        outB.setFrequency(20);
        graph.connect(new Connection(key.id(), 0, outA.id(), 0));
        graph.connect(new Connection(key.id(), 0, not.id(), 0));
        graph.connect(new Connection(not.id(), 0, outB.id(), 0));

        assertEquals(Set.of(20), update(registry, graph, false));
        assertFalse(registry.isActive(10));
        assertTrue(registry.isActive(20));
        assertEquals(Set.of(10, 20), update(registry, graph, true));
        assertTrue(registry.isActive(10));
        assertFalse(registry.isActive(20));
        assertEquals(Set.of(), update(registry, graph, true));
    }

    @Test
    void sharedFrequencyIsOrOfAllSources() {
        FrequencyRegistry registry = new FrequencyRegistry();
        LogicGraph first = buttonGraph(5);
        LogicGraph second = buttonGraph(5);
        update(registry, first, true);
        update(registry, second, false);
        assertTrue(registry.isActive(5));
        assertEquals(2, registry.sourceCount(5));
        update(registry, second, true);
        update(registry, first, false);
        assertTrue(registry.isActive(5));
        update(registry, second, false);
        assertFalse(registry.isActive(5));
    }

    @Test
    void removedGraphStopsSignalling() {
        FrequencyRegistry registry = new FrequencyRegistry();
        LogicGraph graph = buttonGraph(999_999);
        update(registry, graph, true);
        assertTrue(registry.isActive(999_999));
        assertEquals(Set.of(999_999), registry.removeGraph(graph.id()));
        assertFalse(registry.isActive(999_999));
        assertEquals(0, registry.sourceCount(999_999));
    }

    @Test
    void changingFrequencyOrRemovingNodeDropsOldSource() {
        FrequencyRegistry registry = new FrequencyRegistry();
        LogicGraph graph = buttonGraph(1);
        update(registry, graph, true);
        LogicNode out = graph.nodes().stream().filter(n -> n.type() == NodeType.REDSTONE_OUTPUT).findFirst().orElseThrow();
        out.setFrequency(2);
        assertEquals(Set.of(1, 2), update(registry, graph, true));
        assertFalse(registry.isActive(1));
        assertTrue(registry.isActive(2));
        graph.removeNode(out.id());
        assertEquals(Set.of(2), registry.updateGraph(graph, Map.of()));
        assertEquals(Set.of(), registry.activeFrequencies());
    }

    @Test
    void frequencyRangeIsValidated() {
        LogicNode node = new LogicNode(1, NodeType.REDSTONE_OUTPUT, 0, 0);
        assertTrue(node.setFrequency(0));
        assertTrue(node.setFrequency(999_999));
        assertFalse(node.setFrequency(1_000_000));
        assertFalse(node.setFrequency(-1));
        assertEquals(999_999, node.frequency());
    }
}
