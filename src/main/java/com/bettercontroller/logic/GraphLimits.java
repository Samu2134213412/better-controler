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

    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 15;
    public static final int MAX_ITEM_ID_LENGTH = 64;

    /** Maximum size of graph JSON (pretty-printed in .bcgraph files, compact in saves and packets). */
    public static final int MAX_JSON_CHARS = 96 * 1024;
    /** Maximum size of a compressed graph in a packet; below the 32 KiB limit of client-to-server packets. */
    public static final int MAX_COMPRESSED_BYTES = 30 * 1024;
    public static final int MAX_JSON_DEPTH = 8;

    /** Maximum number of graphs a single player may own in one world. */
    public static final int MAX_GRAPHS_PER_PLAYER = 32;

    private GraphLimits() {
    }

    public static boolean isValidFrequency(int frequency) {
        return frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY;
    }

    /** "" (empty slot) or a lower-case resource id like {@code minecraft:redstone}. */
    public static boolean isValidItemId(String id) {
        if (id == null || id.length() > MAX_ITEM_ID_LENGTH) {
            return false;
        }
        if (id.isEmpty()) {
            return true;
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.'
                    || ch == ':' || (ch == '/' && i > colon);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidNodeId(int id) {
        return id >= MIN_NODE_ID && id <= MAX_NODE_ID;
    }

    public static boolean isValidCoordinate(int value) {
        return value >= -MAX_COORDINATE && value <= MAX_COORDINATE;
    }
}
