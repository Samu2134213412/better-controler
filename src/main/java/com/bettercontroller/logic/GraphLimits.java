package com.bettercontroller.logic;

/**
 * Hard limits for graph data. They keep a single graph well below the 64 KiB string
 * limit of NBT and network packets, and bound the cost of evaluating untrusted data.
 */
public final class GraphLimits {
    public static final int MIN_NODE_ID = 1;
    public static final int MAX_NODE_ID = 99_999;
    public static final int MAX_NODES = 128;
    public static final int MAX_CONNECTIONS = 256;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_COORDINATE = 100_000;

    public static final int MIN_FREQUENCY = 0;
    public static final int MAX_FREQUENCY = 999_999;

    /** Maximum size of a .bcgraph file and of a graph JSON string sent over the network. */
    public static final int MAX_JSON_CHARS = 48 * 1024;
    public static final int MAX_JSON_DEPTH = 8;

    /** Maximum number of graphs a single player may own in one world. */
    public static final int MAX_GRAPHS_PER_PLAYER = 32;

    private GraphLimits() {
    }

    public static boolean isValidFrequency(int frequency) {
        return frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY;
    }

    public static boolean isValidNodeId(int id) {
        return id >= MIN_NODE_ID && id <= MAX_NODE_ID;
    }

    public static boolean isValidCoordinate(int value) {
        return value >= -MAX_COORDINATE && value <= MAX_COORDINATE;
    }
}
