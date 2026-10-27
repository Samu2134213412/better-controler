package com.bettercontroller.logic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Evaluates a graph in topological order. Unconnected inputs read as false. A graph with a
 * cycle (only possible with unvalidated data) evaluates to all outputs off.
 */
public final class GraphEvaluator {
    private GraphEvaluator() {
    }

    /**
     * @param keyDown whether a key code is currently held down
     * @return the state of every node by id: the output value for inputs and gates, the received
     *         value for redstone outputs
     */
    public static Map<Integer, Boolean> evaluate(LogicGraph graph, IntPredicate keyDown) {
        Map<Integer, Boolean> states = new TreeMap<>();
        List<Integer> order = graph.topologicalOrder();
        if (order == null) {
            for (LogicNode node : graph.nodes()) {
                states.put(node.id(), false);
            }
            return states;
        }
        for (int nodeId : order) {
            LogicNode node = graph.node(nodeId);
            boolean value;
            if (node.type() == NodeType.KEYBOARD_INPUT) {
                value = keyDown.test(node.key());
            } else {
                boolean[] inputs = new boolean[node.type().inputs()];
                for (int port = 0; port < inputs.length; port++) {
                    Connection c = graph.inputConnection(nodeId, port);
                    inputs[port] = c != null && states.getOrDefault(c.fromNode(), false);
                }
                value = node.type().evaluate(inputs);
            }
            states.put(nodeId, value);
        }
        return states;
    }

    public static Map<Integer, Boolean> evaluate(LogicGraph graph, Set<Integer> pressedKeys) {
        return evaluate(graph, pressedKeys::contains);
    }
}
