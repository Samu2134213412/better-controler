package com.bettercontroller.client;

import com.bettercontroller.logic.GraphFileStore;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.network.GraphPayloads;
import net.neoforged.fml.loading.FMLPaths;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Client-side state for the current connection. Cleared when leaving a world or server. */
public final class ClientGraphState {
    private static LogicGraph editing;
    private static boolean unsaved;
    private static List<GraphPayloads.GraphSummary> graphs = List.of();
    private static Set<Integer> boundKeys = Set.of();
    private static Set<Integer> lastSentKeys = Set.of();
    private static GraphFileStore fileStore;

    private ClientGraphState() {
    }

    /** The graph shown in the editor; a new empty graph if none was opened yet. */
    public static LogicGraph editingGraph() {
        if (editing == null) {
            editing = LogicGraph.create("New Graph");
            unsaved = false;
        }
        return editing;
    }

    public static boolean hasEditingGraph() {
        return editing != null;
    }

    public static void setEditingGraph(LogicGraph graph, boolean hasUnsavedChanges) {
        editing = graph;
        unsaved = hasUnsavedChanges;
    }

    public static boolean hasUnsavedChanges() {
        return unsaved;
    }

    public static void setUnsaved(boolean value) {
        unsaved = value;
    }

    public static List<GraphPayloads.GraphSummary> graphs() {
        return graphs;
    }

    static void setGraphs(List<GraphPayloads.GraphSummary> list) {
        graphs = List.copyOf(list);
    }

    static Set<Integer> boundKeys() {
        return boundKeys;
    }

    static void setBoundKeys(List<Integer> keys) {
        boundKeys = Set.copyOf(new TreeSet<>(keys));
    }

    static Set<Integer> lastSentKeys() {
        return lastSentKeys;
    }

    static void setLastSentKeys(Set<Integer> keys) {
        lastSentKeys = Set.copyOf(keys);
    }

    /** .minecraft/bettercontroller/graphs */
    public static GraphFileStore fileStore() {
        if (fileStore == null) {
            fileStore = new GraphFileStore(FMLPaths.GAMEDIR.get().resolve("bettercontroller").resolve("graphs"));
        }
        return fileStore;
    }

    static void reset() {
        editing = null;
        unsaved = false;
        graphs = List.of();
        boundKeys = Set.of();
        lastSentKeys = Set.of();
    }
}
