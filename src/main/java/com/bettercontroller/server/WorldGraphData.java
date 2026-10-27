package com.bettercontroller.server;

import com.bettercontroller.BetterController;
import com.bettercontroller.logic.GraphJsonCodec;
import com.bettercontroller.logic.GraphValidationException;
import com.bettercontroller.logic.LogicGraph;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All graphs of one world, stored in the overworld's data storage
 * ({@code <world>/data/bettercontroller_graphs.dat}). Each world has its own file, so graphs of
 * different worlds never mix. Graphs are stored as the same validated JSON used for .bcgraph files.
 */
public final class WorldGraphData extends SavedData {
    private static final String FILE_ID = BetterController.MOD_ID + "_graphs";

    public record Entry(LogicGraph graph, UUID owner, String ownerName) {
    }

    private final Map<UUID, Entry> graphs = new LinkedHashMap<>();
    /** Entries that failed validation on load. Kept unchanged so a bug never deletes player data. */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static WorldGraphData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldGraphData::new, WorldGraphData::load, null), FILE_ID);
    }

    public Entry get(UUID graphId) {
        return graphs.get(graphId);
    }

    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(graphs.values());
    }

    public int countOwnedBy(UUID owner) {
        int count = 0;
        for (Entry entry : graphs.values()) {
            if (entry.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** Stores a copy of the graph, replacing an existing graph with the same id. */
    public void put(LogicGraph graph, UUID owner, String ownerName) {
        graphs.put(graph.id(), new Entry(graph.copy(), owner, ownerName));
        setDirty();
    }

    public boolean remove(UUID graphId) {
        if (graphs.remove(graphId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : graphs.values()) {
            CompoundTag graphTag = new CompoundTag();
            graphTag.putUUID("owner", entry.owner());
            graphTag.putString("ownerName", entry.ownerName());
            graphTag.putString("json", GraphJsonCodec.encode(entry.graph(), false));
            list.add(graphTag);
        }
        unreadable.forEach(raw -> list.add(raw.copy()));
        tag.put("graphs", list);
        return tag;
    }

    /** Reads data written by {@link #save}; public for the game tests. */
    public static WorldGraphData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldGraphData data = new WorldGraphData();
        ListTag list = tag.getList("graphs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag graphTag = list.getCompound(i);
            try {
                if (!graphTag.hasUUID("owner")) {
                    throw new GraphValidationException("Missing owner");
                }
                LogicGraph graph = GraphJsonCodec.decode(graphTag.getString("json"));
                data.graphs.put(graph.id(), new Entry(graph, graphTag.getUUID("owner"), graphTag.getString("ownerName")));
            } catch (GraphValidationException e) {
                BetterController.LOGGER.warn("Skipping unreadable saved graph #{}: {}", i, e.getMessage());
                data.unreadable.add(graphTag.copy());
            }
        }
        return data;
    }
}
