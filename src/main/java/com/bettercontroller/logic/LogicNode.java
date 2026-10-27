package com.bettercontroller.logic;

/**
 * One node of a graph. Position is in canvas units and is independent of the editor's zoom and pan.
 * {@code key} is only used by {@link NodeType#KEYBOARD_INPUT}, {@code frequency} only by
 * {@link NodeType#REDSTONE_OUTPUT}; other types keep the defaults.
 */
public final class LogicNode {
    private final int id;
    private final NodeType type;
    private int x;
    private int y;
    private int key = KeyCodes.DEFAULT_KEY;
    private int frequency = GraphLimits.MIN_FREQUENCY;

    public LogicNode(int id, NodeType type, int x, int y) {
        this.id = id;
        this.type = type;
        this.x = clampCoordinate(x);
        this.y = clampCoordinate(y);
    }

    public int id() {
        return id;
    }

    public NodeType type() {
        return type;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = clampCoordinate(x);
        this.y = clampCoordinate(y);
    }

    public int key() {
        return key;
    }

    /** @return false and leaves the key unchanged if the key code is not assignable */
    public boolean setKey(int key) {
        if (!KeyCodes.isAssignable(key)) {
            return false;
        }
        this.key = key;
        return true;
    }

    public int frequency() {
        return frequency;
    }

    /** @return false and leaves the frequency unchanged if it is out of range */
    public boolean setFrequency(int frequency) {
        if (!GraphLimits.isValidFrequency(frequency)) {
            return false;
        }
        this.frequency = frequency;
        return true;
    }

    public LogicNode copy() {
        LogicNode copy = new LogicNode(id, type, x, y);
        copy.key = key;
        copy.frequency = frequency;
        return copy;
    }

    private static int clampCoordinate(int value) {
        return Math.max(-GraphLimits.MAX_COORDINATE, Math.min(GraphLimits.MAX_COORDINATE, value));
    }
}
