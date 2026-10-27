package com.bettercontroller.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicGraphTest {
    @Test
    void rejectsInvalidConnections() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode input = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode and = graph.addNode(NodeType.AND, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 0, 0);

        assertEquals(LogicGraph.ConnectResult.UNKNOWN_NODE, graph.connect(new Connection(input.id(), 0, 999, 0)));
        assertEquals(LogicGraph.ConnectResult.INVALID_PORT, graph.connect(new Connection(input.id(), 1, and.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.INVALID_PORT, graph.connect(new Connection(input.id(), 0, and.id(), 2)));
        assertEquals(LogicGraph.ConnectResult.INVALID_PORT, graph.connect(new Connection(out.id(), 0, and.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.SELF_CONNECTION, graph.connect(new Connection(and.id(), 0, and.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(input.id(), 0, and.id(), 0)));
        assertEquals(LogicGraph.ConnectResult.DUPLICATE, graph.connect(new Connection(input.id(), 0, and.id(), 0)));
        assertEquals(1, graph.connections().size());
    }

    @Test
    void newConnectionReplacesExistingOnSameInput() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode a = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode b = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode not = graph.addNode(NodeType.NOT, 0, 0);
        graph.connect(new Connection(a.id(), 0, not.id(), 0));
        assertEquals(LogicGraph.ConnectResult.OK, graph.connect(new Connection(b.id(), 0, not.id(), 0)));
        assertEquals(1, graph.connections().size());
        assertEquals(b.id(), graph.inputConnection(not.id(), 0).fromNode());
    }

    @Test
    void rejectsCycles() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode n1 = graph.addNode(NodeType.NOT, 0, 0);
        LogicNode n2 = graph.addNode(NodeType.NOT, 0, 0);
        LogicNode n3 = graph.addNode(NodeType.NOT, 0, 0);
        graph.connect(new Connection(n1.id(), 0, n2.id(), 0));
        graph.connect(new Connection(n2.id(), 0, n3.id(), 0));
        assertEquals(LogicGraph.ConnectResult.CYCLE, graph.connect(new Connection(n3.id(), 0, n1.id(), 0)));
        assertEquals(2, graph.connections().size());
    }

    @Test
    void removingNodeRemovesItsConnections() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode a = graph.addNode(NodeType.KEYBOARD_INPUT, 0, 0);
        LogicNode not = graph.addNode(NodeType.NOT, 0, 0);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 0, 0);
        graph.connect(new Connection(a.id(), 0, not.id(), 0));
        graph.connect(new Connection(not.id(), 0, out.id(), 0));
        graph.removeNode(not.id());
        assertTrue(graph.connections().isEmpty());
        assertNull(graph.node(not.id()));
    }

    @Test
    void nodeIdsAreUniqueAndLimited() {
        LogicGraph graph = LogicGraph.create("test");
        LogicNode first = graph.addNode(NodeType.AND, 0, 0);
        LogicNode second = graph.addNode(NodeType.AND, 0, 0);
        assertNotEquals(first.id(), second.id());
        for (int i = 2; i < GraphLimits.MAX_NODES; i++) {
            graph.addNode(NodeType.AND, 0, 0);
        }
        assertEquals(GraphLimits.MAX_NODES, graph.nodes().size());
        assertNull(graph.addNode(NodeType.AND, 0, 0));
        for (LogicNode node : graph.nodes()) {
            assertTrue(GraphLimits.isValidNodeId(node.id()));
        }
    }

    @Test
    void namesAreTrimmedAndLimited() {
        assertEquals("Unnamed", LogicGraph.create("   ").name());
        assertEquals(GraphLimits.MAX_NAME_LENGTH, LogicGraph.create("x".repeat(100)).name().length());
    }

    @Test
    void coordinatesAreClamped() {
        LogicNode node = new LogicNode(1, NodeType.AND, Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(GraphLimits.MAX_COORDINATE, node.x());
        assertEquals(-GraphLimits.MAX_COORDINATE, node.y());
    }
}
