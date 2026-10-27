package com.bettercontroller.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphWireTest {
    @Test
    void roundTrip() throws Exception {
        String json = GraphJsonCodec.encode(GraphJsonCodecTest.sample(), false);
        assertEquals(json, GraphWire.decompress(GraphWire.compress(json)));
    }

    @Test
    void rejectsBombsAndGarbage() {
        byte[] bomb = GraphWire.compress(" ".repeat(GraphLimits.MAX_JSON_CHARS + 1));
        assertThrows(GraphValidationException.class, () -> GraphWire.decompress(bomb));
        assertThrows(GraphValidationException.class, () -> GraphWire.decompress(new byte[]{1, 2, 3, 4}));
        byte[] truncated = GraphWire.compress("{\"a\":1}");
        assertThrows(GraphValidationException.class,
                () -> GraphWire.decompress(java.util.Arrays.copyOf(truncated, truncated.length - 2)));
        assertThrows(GraphValidationException.class, () -> GraphWire.decompress(new byte[GraphLimits.MAX_COMPRESSED_BYTES + 1]));
    }
}
