package com.bettercontroller.signal;

import com.bettercontroller.logic.GraphLimits;
import com.bettercontroller.logic.LogicGraph;
import com.bettercontroller.logic.LogicNode;
import com.bettercontroller.logic.NodeType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Wireless signal state: which frequencies are currently on. Every redstone output node of every
 * graph is one source, identified by graph id and node id.
 *
 * <p>Rule for shared frequencies: a frequency is on while at least one source on it is on
 * (logical OR). The result does not depend on the order in which sources update, so it is
 * deterministic.
 *
 * <p>Pure Java; {@link SignalManager} keeps one instance per running server (= per world).
 */
public final class FrequencyRegistry {
    public record SourceKey(UUID graphId, int nodeId) {
    }

    private record Source(int frequency, boolean active) {
    }

    private final Map<SourceKey, Source> sources = new HashMap<>();
    /** Number of active sources per frequency; frequencies without active sources are absent. */
    private final Map<Integer, Integer> activeCount = new HashMap<>();

    /**
     * Replaces all sources of a graph with its current output nodes and their states.
     *
     * @param nodeStates node states from {@link com.bettercontroller.logic.GraphEvaluator}
     * @return frequencies whose on/off state changed
     */
    public Set<Integer> updateGraph(LogicGraph graph, Map<Integer, Boolean> nodeStates) {
        Map<SourceKey, Source> next = new HashMap<>();
        for (LogicNode node : graph.nodes()) {
            if (node.type() == NodeType.REDSTONE_OUTPUT && GraphLimits.isValidFrequency(node.frequency())) {
                next.put(new SourceKey(graph.id(), node.id()),
                        new Source(node.frequency(), nodeStates.getOrDefault(node.id(), false)));
            }
        }
        Set<Integer> before = snapshotFrequencies(graph.id());
        before.addAll(frequenciesOf(next));
        Map<Integer, Boolean> previous = statesOf(before);

        removeSources(graph.id());
        next.forEach(this::addSource);
        return changed(previous);
    }

    /** Removes all sources of a deleted or replaced graph. @return frequencies whose state changed */
    public Set<Integer> removeGraph(UUID graphId) {
        Map<Integer, Boolean> previous = statesOf(snapshotFrequencies(graphId));
        removeSources(graphId);
        return changed(previous);
    }

    public boolean isActive(int frequency) {
        return activeCount.containsKey(frequency);
    }

    /** Number of sources (on or off) that use the frequency. */
    public int sourceCount(int frequency) {
        int count = 0;
        for (Source source : sources.values()) {
            if (source.frequency() == frequency) {
                count++;
            }
        }
        return count;
    }

    public Set<Integer> activeFrequencies() {
        return Collections.unmodifiableSet(new TreeSet<>(activeCount.keySet()));
    }

    public void clear() {
        sources.clear();
        activeCount.clear();
    }

    private void addSource(SourceKey key, Source source) {
        sources.put(key, source);
        if (source.active()) {
            activeCount.merge(source.frequency(), 1, Integer::sum);
        }
    }

    private void removeSources(UUID graphId) {
        sources.entrySet().removeIf(entry -> {
            if (!entry.getKey().graphId().equals(graphId)) {
                return false;
            }
            Source source = entry.getValue();
            if (source.active()) {
                activeCount.computeIfPresent(source.frequency(), (f, n) -> n > 1 ? n - 1 : null);
            }
            return true;
        });
    }

    private Set<Integer> snapshotFrequencies(UUID graphId) {
        Set<Integer> result = new HashSet<>();
        sources.forEach((key, source) -> {
            if (key.graphId().equals(graphId)) {
                result.add(source.frequency());
            }
        });
        return result;
    }

    private static Set<Integer> frequenciesOf(Map<SourceKey, Source> map) {
        Set<Integer> result = new HashSet<>();
        map.values().forEach(source -> result.add(source.frequency()));
        return result;
    }

    private Map<Integer, Boolean> statesOf(Set<Integer> frequencies) {
        Map<Integer, Boolean> result = new HashMap<>();
        frequencies.forEach(f -> result.put(f, isActive(f)));
        return result;
    }

    private Set<Integer> changed(Map<Integer, Boolean> previous) {
        Set<Integer> result = new TreeSet<>();
        previous.forEach((frequency, wasActive) -> {
            if (wasActive != isActive(frequency)) {
                result.add(frequency);
            }
        });
        return result;
    }
}
