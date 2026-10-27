package com.bettercontroller.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.UUID;

/**
 * Converts graphs to and from JSON. The same format is used for .bcgraph files, world saves and
 * network packets. Decoding never trusts the input: size and nesting depth are checked before
 * parsing, every field is type checked and the finished graph must pass {@link LogicGraph#validate}.
 *
 * <p>Format (version 1):
 * <pre>
 * {
 *   "format": "bettercontroller.graph", "version": 1, "id": "uuid", "name": "...",
 *   "nodes": [ { "id": 1, "type": "keyboard_input", "x": 0, "y": 0, "settings": { "key": 66 } } ],
 *   "connections": [ { "from": 1, "fromPort": 0, "to": 2, "toPort": 0 } ]
 * }
 * </pre>
 * Unknown fields are ignored so later versions can add data. Missing settings get their defaults
 * (key B, frequency 0), which keeps older files loadable.
 */
public final class GraphJsonCodec {
    public static final String FORMAT = "bettercontroller.graph";
    public static final int CURRENT_VERSION = 1;

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private GraphJsonCodec() {
    }

    public static String encode(LogicGraph graph, boolean pretty) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", CURRENT_VERSION);
        root.addProperty("id", graph.id().toString());
        root.addProperty("name", graph.name());

        JsonArray nodes = new JsonArray();
        for (LogicNode node : graph.nodes()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", node.id());
            json.addProperty("type", node.type().id());
            json.addProperty("x", node.x());
            json.addProperty("y", node.y());
            JsonObject settings = new JsonObject();
            if (node.type() == NodeType.KEYBOARD_INPUT) {
                settings.addProperty("key", node.key());
            } else if (node.type() == NodeType.REDSTONE_OUTPUT) {
                settings.addProperty("frequency", node.frequency());
            }
            json.add("settings", settings);
            nodes.add(json);
        }
        root.add("nodes", nodes);

        JsonArray connections = new JsonArray();
        for (Connection c : graph.connections()) {
            JsonObject json = new JsonObject();
            json.addProperty("from", c.fromNode());
            json.addProperty("fromPort", c.fromPort());
            json.addProperty("to", c.toNode());
            json.addProperty("toPort", c.toPort());
            connections.add(json);
        }
        root.add("connections", connections);
        return (pretty ? PRETTY : COMPACT).toJson(root);
    }

    public static LogicGraph decode(String text) throws GraphValidationException {
        if (text == null) {
            throw new GraphValidationException("No data");
        }
        if (text.length() > GraphLimits.MAX_JSON_CHARS) {
            throw new GraphValidationException("Graph data is too large");
        }
        checkDepth(text);

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonParseException | IllegalStateException e) {
            throw new GraphValidationException("Not valid JSON");
        }
        JsonObject root = asObject(parsed, "root");

        if (!FORMAT.equals(optString(root, "format"))) {
            throw new GraphValidationException("Not a Better Controller graph");
        }
        int version = getInt(root, "version");
        if (version < 1) {
            throw new GraphValidationException("Invalid format version " + version);
        }
        if (version > CURRENT_VERSION) {
            throw new GraphValidationException("Graph was saved by a newer version (format " + version + ")");
        }

        UUID id;
        try {
            id = UUID.fromString(getString(root, "id"));
        } catch (IllegalArgumentException e) {
            throw new GraphValidationException("Invalid graph id");
        }
        String name = getString(root, "name");
        if (name.length() > GraphLimits.MAX_NAME_LENGTH) {
            throw new GraphValidationException("Graph name is too long");
        }
        LogicGraph graph = new LogicGraph(id, name);

        JsonArray nodes = getArray(root, "nodes");
        if (nodes.size() > GraphLimits.MAX_NODES) {
            throw new GraphValidationException("Too many nodes (max " + GraphLimits.MAX_NODES + ")");
        }
        for (JsonElement element : nodes) {
            JsonObject json = asObject(element, "node");
            int nodeId = getInt(json, "id");
            if (!GraphLimits.isValidNodeId(nodeId)) {
                throw new GraphValidationException("Invalid node id " + nodeId);
            }
            if (graph.node(nodeId) != null) {
                throw new GraphValidationException("Duplicate node id " + nodeId);
            }
            String typeId = getString(json, "type");
            NodeType type = NodeType.byId(typeId)
                    .orElseThrow(() -> new GraphValidationException("Unknown node type '" + typeId + "'"));
            int x = getInt(json, "x");
            int y = getInt(json, "y");
            if (!GraphLimits.isValidCoordinate(x) || !GraphLimits.isValidCoordinate(y)) {
                throw new GraphValidationException("Node " + nodeId + " is outside the canvas");
            }
            LogicNode node = new LogicNode(nodeId, type, x, y);
            JsonObject settings = json.has("settings") ? asObject(json.get("settings"), "settings") : new JsonObject();
            if (type == NodeType.KEYBOARD_INPUT && settings.has("key")) {
                int key = getInt(settings, "key");
                if (!node.setKey(key)) {
                    throw new GraphValidationException("Invalid key code " + key + " on node " + nodeId);
                }
            }
            if (type == NodeType.REDSTONE_OUTPUT && settings.has("frequency")) {
                int frequency = getInt(settings, "frequency");
                if (!node.setFrequency(frequency)) {
                    throw new GraphValidationException("Frequency " + frequency + " on node " + nodeId
                            + " is outside " + GraphLimits.MIN_FREQUENCY + "-" + GraphLimits.MAX_FREQUENCY);
                }
            }
            graph.putNodeUnchecked(node);
        }

        JsonArray connections = getArray(root, "connections");
        if (connections.size() > GraphLimits.MAX_CONNECTIONS) {
            throw new GraphValidationException("Too many connections (max " + GraphLimits.MAX_CONNECTIONS + ")");
        }
        for (JsonElement element : connections) {
            JsonObject json = asObject(element, "connection");
            graph.putConnectionUnchecked(new Connection(
                    getInt(json, "from"), getInt(json, "fromPort"), getInt(json, "to"), getInt(json, "toPort")));
        }

        graph.validate();
        return graph;
    }

    /** Rejects deeply nested input before Gson parses it recursively. */
    private static void checkDepth(String text) throws GraphValidationException {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                if (++depth > GraphLimits.MAX_JSON_DEPTH) {
                    throw new GraphValidationException("Graph data is nested too deeply");
                }
            } else if (ch == '}' || ch == ']') {
                depth--;
            }
        }
    }

    private static JsonObject asObject(JsonElement element, String what) throws GraphValidationException {
        if (element == null || !element.isJsonObject()) {
            throw new GraphValidationException("Expected an object for " + what);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray getArray(JsonObject json, String field) throws GraphValidationException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new GraphValidationException("Missing list '" + field + "'");
        }
        return element.getAsJsonArray();
    }

    private static String optString(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    private static String getString(JsonObject json, String field) throws GraphValidationException {
        String value = optString(json, field);
        if (value == null) {
            throw new GraphValidationException("Missing text '" + field + "'");
        }
        return value;
    }

    private static int getInt(JsonObject json, String field) throws GraphValidationException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive) element).isNumber()) {
            throw new GraphValidationException("Missing number '" + field + "'");
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new GraphValidationException("'" + field + "' must be a whole number");
        }
        return (int) value;
    }
}
