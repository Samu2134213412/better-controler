package com.bettercontroller.logic;

import java.util.Locale;
import java.util.Optional;

/**
 * All node types a graph can contain. The {@link #id()} is the stable name used in
 * saved data and .bcgraph files; never rename an existing id.
 */
public enum NodeType {
    KEYBOARD_INPUT("keyboard_input", 0, 1),
    AND("and", 2, 1),
    OR("or", 2, 1),
    NOT("not", 1, 1),
    NAND("nand", 2, 1),
    NOR("nor", 2, 1),
    XOR("xor", 2, 1),
    REDSTONE_OUTPUT("redstone_output", 1, 0);

    private final String id;
    private final int inputs;
    private final int outputs;

    NodeType(String id, int inputs, int outputs) {
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public String id() {
        return id;
    }

    public int inputs() {
        return inputs;
    }

    public int outputs() {
        return outputs;
    }

    /** Translation key for the display name, e.g. {@code node.bettercontroller.and}. */
    public String translationKey() {
        return "node.bettercontroller." + id;
    }

    public static Optional<NodeType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (NodeType type : values()) {
            if (type.id.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Computes this gate's output from its inputs. Inputs are never null; unconnected inputs are false. */
    public boolean evaluate(boolean[] in) {
        return switch (this) {
            case AND -> in[0] && in[1];
            case OR -> in[0] || in[1];
            case NOT -> !in[0];
            case NAND -> !(in[0] && in[1]);
            case NOR -> !(in[0] || in[1]);
            case XOR -> in[0] != in[1];
            // Inputs and outputs are handled by GraphEvaluator; this only passes the value through.
            case KEYBOARD_INPUT -> false;
            case REDSTONE_OUTPUT -> in[0];
        };
    }
}
