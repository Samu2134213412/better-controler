package com.bettercontroller.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A logic circuit: nodes plus connections between their ports. Pure Java without Minecraft
 * classes, so it is used unchanged on client and server and can be unit tested.
 *
 * <p>Connection rules:
 * <ul>
 *   <li>A connection goes from an output port to an input port of a different node.</li>
 *   <li>An output may feed any number of inputs; an input accepts at most one connection.
 *       {@link #connect} replaces an existing connection on that input, while {@link #validate}
 *       rejects data that contains two.</li>
 *   <li>Connections that would create a cycle are rejected, so evaluation is always well defined.</li>
 * </ul>
 */
public final class LogicGraph {
    public enum ConnectResult {
        OK, UNKNOWN_NODE, INVALID_PORT, SELF_CONNECTION, DUPLICATE, CYCLE, TOO_MANY_CONNECTIONS
    }

    private final UUID id;
    private String name;
    private final TreeMap<Integer, LogicNode> nodes = new TreeMap<>();
    private final List<Connection> connections = new ArrayList<>();

    public LogicGraph(UUID id, String name) {
        this.id = id;
        setName(name);
    }

    public static LogicGraph create(String name) {
        return new LogicGraph(UUID.randomUUID(), name);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            trimmed = "Unnamed";
        }
        this.name = trimmed.length() > GraphLimits.MAX_NAME_LENGTH
                ? trimmed.substring(0, GraphLimits.MAX_NAME_LENGTH)
                : trimmed;
    }

    /** Nodes in ascending id order. */
    public Collection<LogicNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public LogicNode node(int nodeId) {
        return nodes.get(nodeId);
    }

    public List<Connection> connections() {
        return Collections.unmodifiableList(connections);
    }

    /** Adds a node with the next free id. @return the node, or null if the node limit is reached */
    public LogicNode addNode(NodeType type, int x, int y) {
        if (nodes.size() >= GraphLimits.MAX_NODES) {
            return null;
        }
        int nodeId = nodes.isEmpty() ? GraphLimits.MIN_NODE_ID : nodes.lastKey() + 1;
        if (!GraphLimits.isValidNodeId(nodeId)) {
            nodeId = GraphLimits.MIN_NODE_ID;
            while (nodes.containsKey(nodeId)) {
                nodeId++;
            }
        }
        LogicNode node = new LogicNode(nodeId, type, x, y);
        nodes.put(nodeId, node);
        return node;
    }

    /** Adds a node with a given id, used when decoding stored data. Run {@link #validate} afterwards. */
    void putNodeUnchecked(LogicNode node) {
        nodes.put(node.id(), node);
    }

    /** Adds a connection as stored, used when decoding stored data. Run {@link #validate} afterwards. */
    void putConnectionUnchecked(Connection connection) {
        connections.add(connection);
    }

    public void removeNode(int nodeId) {
        if (nodes.remove(nodeId) != null) {
            connections.removeIf(c -> c.fromNode() == nodeId || c.toNode() == nodeId);
        }
    }

    public ConnectResult connect(Connection connection) {
        ConnectResult structural = checkEndpoints(connection);
        if (structural != ConnectResult.OK) {
            return structural;
        }
        if (connections.contains(connection)) {
            return ConnectResult.DUPLICATE;
        }
        Connection replaced = inputConnection(connection.toNode(), connection.toPort());
        if (replaced == null && connections.size() >= GraphLimits.MAX_CONNECTIONS) {
            return ConnectResult.TOO_MANY_CONNECTIONS;
        }
        if (reaches(connection.toNode(), connection.fromNode())) {
            return ConnectResult.CYCLE;
        }
        if (replaced != null) {
            connections.remove(replaced);
        }
        connections.add(connection);
        return ConnectResult.OK;
    }

    /** Removes the connection feeding the given input port. @return true if one was removed */
    public boolean disconnectInput(int nodeId, int port) {
        Connection existing = inputConnection(nodeId, port);
        return existing != null && connections.remove(existing);
    }

    public Connection inputConnection(int nodeId, int port) {
        for (Connection c : connections) {
            if (c.toNode() == nodeId && c.toPort() == port) {
                return c;
            }
        }
        return null;
    }

    /** Key codes used by keyboard input nodes, in ascending order. */
    public Set<Integer> usedKeys() {
        Set<Integer> keys = new TreeSet<>();
        for (LogicNode node : nodes.values()) {
            if (node.type() == NodeType.KEYBOARD_INPUT) {
                keys.add(node.key());
            }
        }
        return keys;
    }

    /**
     * Checks every rule and limit. Data from files, the network or older saves must pass this
     * before it is used.
     */
    public void validate() throws GraphValidationException {
        if (nodes.size() > GraphLimits.MAX_NODES) {
            throw new GraphValidationException("Too many nodes (max " + GraphLimits.MAX_NODES + ")");
        }
        if (connections.size() > GraphLimits.MAX_CONNECTIONS) {
            throw new GraphValidationException("Too many connections (max " + GraphLimits.MAX_CONNECTIONS + ")");
        }
        for (Map.Entry<Integer, LogicNode> entry : nodes.entrySet()) {
            LogicNode node = entry.getValue();
            if (entry.getKey() != node.id() || !GraphLimits.isValidNodeId(node.id())) {
                throw new GraphValidationException("Invalid node id " + node.id());
            }
            if (!KeyCodes.isAssignable(node.key())) {
                throw new GraphValidationException("Invalid key code " + node.key() + " on node " + node.id());
            }
            if (!GraphLimits.isValidFrequency(node.frequency())) {
                throw new GraphValidationException("Invalid frequency on node " + node.id());
            }
            if (!GraphLimits.isValidItemId(node.linkItem(0)) || !GraphLimits.isValidItemId(node.linkItem(1))) {
                throw new GraphValidationException("Invalid link item on node " + node.id());
            }
            if (node.strength() < GraphLimits.MIN_STRENGTH || node.strength() > GraphLimits.MAX_STRENGTH) {
                throw new GraphValidationException("Invalid strength on node " + node.id());
            }
        }
        Set<Connection> seen = new HashSet<>();
        Set<Long> usedInputs = new HashSet<>();
        for (Connection c : connections) {
            ConnectResult result = checkEndpoints(c);
            if (result != ConnectResult.OK) {
                throw new GraphValidationException("Invalid connection " + describe(c) + ": " + result);
            }
            if (!seen.add(c)) {
                throw new GraphValidationException("Duplicate connection " + describe(c));
            }
            if (!usedInputs.add(((long) c.toNode() << 8) | c.toPort())) {
                throw new GraphValidationException("Input " + c.toPort() + " of node " + c.toNode()
                        + " has more than one connection");
            }
        }
        if (topologicalOrder() == null) {
            throw new GraphValidationException("Connections contain a cycle");
        }
    }

    /**
     * Node ids ordered so that every node comes after all nodes feeding it; ties are broken by
     * ascending id, which makes evaluation deterministic. @return null if the graph has a cycle
     */
    public List<Integer> topologicalOrder() {
        Map<Integer, Integer> inDegree = new TreeMap<>();
        Map<Integer, List<Integer>> outgoing = new TreeMap<>();
        for (int nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
            outgoing.put(nodeId, new ArrayList<>());
        }
        for (Connection c : connections) {
            if (!nodes.containsKey(c.fromNode()) || !nodes.containsKey(c.toNode())) {
                continue;
            }
            inDegree.merge(c.toNode(), 1, Integer::sum);
            outgoing.get(c.fromNode()).add(c.toNode());
        }
        TreeSet<Integer> ready = new TreeSet<>();
        inDegree.forEach((nodeId, degree) -> {
            if (degree == 0) {
                ready.add(nodeId);
            }
        });
        List<Integer> order = new ArrayList<>(nodes.size());
        while (!ready.isEmpty()) {
            int nodeId = ready.pollFirst();
            order.add(nodeId);
            for (int target : outgoing.get(nodeId)) {
                if (inDegree.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }
        return order.size() == nodes.size() ? order : null;
    }

    public LogicGraph copy() {
        return copyWithId(id);
    }

    /** A deep copy under another id, e.g. for importing a file as a new graph. */
    public LogicGraph copyWithId(UUID newId) {
        LogicGraph copy = new LogicGraph(newId, name);
        for (LogicNode node : nodes.values()) {
            copy.nodes.put(node.id(), node.copy());
        }
        copy.connections.addAll(connections);
        return copy;
    }

    private ConnectResult checkEndpoints(Connection c) {
        LogicNode from = nodes.get(c.fromNode());
        LogicNode to = nodes.get(c.toNode());
        if (from == null || to == null) {
            return ConnectResult.UNKNOWN_NODE;
        }
        if (c.fromNode() == c.toNode()) {
            return ConnectResult.SELF_CONNECTION;
        }
        if (c.fromPort() < 0 || c.fromPort() >= from.type().outputs()
                || c.toPort() < 0 || c.toPort() >= to.type().inputs()) {
            return ConnectResult.INVALID_PORT;
        }
        return ConnectResult.OK;
    }

    /** True if {@code target} can be reached from {@code start} by following connections. */
    private boolean reaches(int start, int target) {
        Deque<Integer> stack = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int current = stack.pop();
            if (current == target) {
                return true;
            }
            if (visited.add(current)) {
                for (Connection c : connections) {
                    if (c.fromNode() == current) {
                        stack.push(c.toNode());
                    }
                }
            }
        }
        return false;
    }

    private static String describe(Connection c) {
        return c.fromNode() + ":" + c.fromPort() + " -> " + c.toNode() + ":" + c.toPort();
    }
}
