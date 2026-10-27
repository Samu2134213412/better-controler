package com.bettercontroller.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphJsonCodecTest {
    private static final String HEADER = "\"format\":\"bettercontroller.graph\",\"version\":1,"
            + "\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"name\":\"t\",";

    static LogicGraph sample() {
        LogicGraph graph = LogicGraph.create("Sample Graph");
        LogicNode key = graph.addNode(NodeType.KEYBOARD_INPUT, -40, 25);
        key.setKey(290);
        LogicNode not = graph.addNode(NodeType.NOT, 60, 25);
        LogicNode out = graph.addNode(NodeType.REDSTONE_OUTPUT, 160, 25);
        out.setFrequency(123456);
        graph.connect(new Connection(key.id(), 0, not.id(), 0));
        graph.connect(new Connection(not.id(), 0, out.id(), 0));
        return graph;
    }

    @Test
    void roundTripKeepsEverything() throws Exception {
        LogicGraph original = sample();
        for (boolean pretty : new boolean[]{false, true}) {
            LogicGraph decoded = GraphJsonCodec.decode(GraphJsonCodec.encode(original, pretty));
            assertEquals(original.id(), decoded.id());
            assertEquals(original.name(), decoded.name());
            assertEquals(original.connections(), decoded.connections());
            assertEquals(original.nodes().size(), decoded.nodes().size());
            for (LogicNode node : original.nodes()) {
                LogicNode other = decoded.node(node.id());
                assertEquals(node.type(), other.type());
                assertEquals(node.x(), other.x());
                assertEquals(node.y(), other.y());
                assertEquals(node.key(), other.key());
                assertEquals(node.frequency(), other.frequency());
            }
            assertEquals(GraphJsonCodec.encode(original, false), GraphJsonCodec.encode(decoded, false));
        }
    }

    @Test
    void missingSettingsUseDefaults() throws Exception {
        LogicGraph graph = GraphJsonCodec.decode("{" + HEADER + "\"nodes\":["
                + "{\"id\":1,\"type\":\"keyboard_input\",\"x\":0,\"y\":0},"
                + "{\"id\":2,\"type\":\"redstone_output\",\"x\":0,\"y\":0,\"settings\":{}}],"
                + "\"connections\":[{\"from\":1,\"fromPort\":0,\"to\":2,\"toPort\":0}],\"future\":true}");
        assertEquals(KeyCodes.DEFAULT_KEY, graph.node(1).key());
        assertEquals(0, graph.node(2).frequency());
    }

    @Test
    void rejectsBadData() {
        String[] bad = {
                "",
                "not json",
                "[]",
                "{\"format\":\"other\",\"version\":1}",
                "{" + HEADER.replace("\"version\":1", "\"version\":2") + "\"nodes\":[],\"connections\":[]}",
                "{" + HEADER.replace("\"version\":1", "\"version\":0") + "\"nodes\":[],\"connections\":[]}",
                "{" + HEADER.replace("123e4567-e89b-12d3-a456-426614174000", "nope") + "\"nodes\":[],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"teleporter\",\"x\":0,\"y\":0}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":0,\"type\":\"and\",\"x\":0,\"y\":0}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"and\",\"x\":0,\"y\":0},{\"id\":1,\"type\":\"or\",\"x\":0,\"y\":0}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"and\",\"x\":1.5,\"y\":0}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"and\",\"x\":9999999,\"y\":0}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"keyboard_input\",\"x\":0,\"y\":0,\"settings\":{\"key\":256}}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"redstone_output\",\"x\":0,\"y\":0,\"settings\":{\"frequency\":1000000}}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"redstone_output\",\"x\":0,\"y\":0,\"settings\":{\"frequency\":-1}}],\"connections\":[]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"and\",\"x\":0,\"y\":0}],\"connections\":[{\"from\":1,\"fromPort\":0,\"to\":7,\"toPort\":0}]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"and\",\"x\":0,\"y\":0},{\"id\":2,\"type\":\"and\",\"x\":0,\"y\":0}],"
                        + "\"connections\":[{\"from\":1,\"fromPort\":0,\"to\":2,\"toPort\":5}]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"not\",\"x\":0,\"y\":0},{\"id\":2,\"type\":\"not\",\"x\":0,\"y\":0}],"
                        + "\"connections\":[{\"from\":1,\"fromPort\":0,\"to\":2,\"toPort\":0},{\"from\":2,\"fromPort\":0,\"to\":1,\"toPort\":0}]}",
                "{" + HEADER + "\"nodes\":[{\"id\":1,\"type\":\"keyboard_input\",\"x\":0,\"y\":0},{\"id\":2,\"type\":\"keyboard_input\",\"x\":0,\"y\":0},"
                        + "{\"id\":3,\"type\":\"not\",\"x\":0,\"y\":0}],"
                        + "\"connections\":[{\"from\":1,\"fromPort\":0,\"to\":3,\"toPort\":0},{\"from\":2,\"fromPort\":0,\"to\":3,\"toPort\":0}]}",
                "{" + HEADER + "\"nodes\":[" + "[".repeat(20) + "]".repeat(20) + "],\"connections\":[]}",
                "x".repeat(GraphLimits.MAX_JSON_CHARS + 1),
        };
        for (String text : bad) {
            assertThrows(GraphValidationException.class, () -> GraphJsonCodec.decode(text),
                    () -> "accepted: " + text.substring(0, Math.min(200, text.length())));
        }
    }

    @Test
    void rejectsTooManyNodes() {
        StringBuilder nodes = new StringBuilder();
        for (int i = 1; i <= GraphLimits.MAX_NODES + 1; i++) {
            nodes.append(i > 1 ? "," : "").append("{\"id\":").append(i).append(",\"type\":\"and\",\"x\":0,\"y\":0}");
        }
        String text = "{" + HEADER + "\"nodes\":[" + nodes + "],\"connections\":[]}";
        assertThrows(GraphValidationException.class, () -> GraphJsonCodec.decode(text));
    }

    @Test
    void largestAllowedGraphFitsSizeLimit() throws Exception {
        // Worst case for size, not for validity: every field at its longest value.
        LogicGraph graph = LogicGraph.create("x".repeat(GraphLimits.MAX_NAME_LENGTH));
        for (int i = 0; i < GraphLimits.MAX_NODES; i++) {
            LogicNode node = new LogicNode(GraphLimits.MAX_NODE_ID - i, NodeType.REDSTONE_OUTPUT,
                    -GraphLimits.MAX_COORDINATE, -GraphLimits.MAX_COORDINATE);
            node.setFrequency(GraphLimits.MAX_FREQUENCY);
            graph.putNodeUnchecked(node);
        }
        for (int i = 0; i < GraphLimits.MAX_CONNECTIONS; i++) {
            graph.putConnectionUnchecked(new Connection(GraphLimits.MAX_NODE_ID, 0, GraphLimits.MAX_NODE_ID - 1, 0));
        }
        String json = GraphJsonCodec.encode(graph, true);
        assertTrue(json.length() < GraphLimits.MAX_JSON_CHARS, "size " + json.length());
    }
}
