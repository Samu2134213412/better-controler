package com.bettercontroller.logic;

/**
 * One node of a graph. Position is in canvas units and is independent of the editor's zoom and pan.
 * {@code key} is only used by {@link NodeType#KEYBOARD_INPUT}; {@code frequency}, the Create link
 * items and {@code strength} only by {@link NodeType#REDSTONE_OUTPUT}. Other types keep the defaults.
 */
public final class LogicNode {
    private final int id;
    private final NodeType type;
    private int x;
    private int y;
    private int key = KeyCodes.DEFAULT_KEY;
    private int frequency = GraphLimits.MIN_FREQUENCY;
    /** Item ids of the two Create Redstone Link frequency slots; "" is an empty slot. */
    private String linkItem1 = "";
    private String linkItem2 = "";
    private int strength = GraphLimits.MAX_STRENGTH;

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

    public String linkItem(int slot) {
        return slot == 0 ? linkItem1 : linkItem2;
    }

    /**
     * Sets one Create link frequency slot to an item id such as {@code minecraft:redstone}, or ""
     * to empty it. Only the format is checked here; the server also checks that the item exists.
     * @return false and leaves the slot unchanged if the id is malformed
     */
    public boolean setLinkItem(int slot, String itemId) {
        if (slot < 0 || slot > 1 || !GraphLimits.isValidItemId(itemId)) {
            return false;
        }
        if (slot == 0) {
            linkItem1 = itemId;
        } else {
            linkItem2 = itemId;
        }
        return true;
    }

    /** True if at least one Create link slot holds an item, like a Create Redstone Link needs. */
    public boolean hasLinkFrequency() {
        return !linkItem1.isEmpty() || !linkItem2.isEmpty();
    }

    /** Signal strength (1-15) sent on the Create link while the output is on. */
    public int strength() {
        return strength;
    }

    /** @return false and leaves the strength unchanged if it is outside 1-15 */
    public boolean setStrength(int strength) {
        if (strength < GraphLimits.MIN_STRENGTH || strength > GraphLimits.MAX_STRENGTH) {
            return false;
        }
        this.strength = strength;
        return true;
    }

    public LogicNode copy() {
        LogicNode copy = new LogicNode(id, type, x, y);
        copy.key = key;
        copy.frequency = frequency;
        copy.linkItem1 = linkItem1;
        copy.linkItem2 = linkItem2;
        copy.strength = strength;
        return copy;
    }

    private static int clampCoordinate(int value) {
        return Math.max(-GraphLimits.MAX_COORDINATE, Math.min(GraphLimits.MAX_COORDINATE, value));
    }
}
