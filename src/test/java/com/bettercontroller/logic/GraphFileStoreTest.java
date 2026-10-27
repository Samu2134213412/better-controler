package com.bettercontroller.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphFileStoreTest {
    @TempDir
    Path temp;

    @Test
    void exportThenImport() throws Exception {
        GraphFileStore store = new GraphFileStore(temp.resolve("graphs"));
        LogicGraph graph = GraphJsonCodecTest.sample();
        String fileName = store.write(graph);
        assertEquals("Sample_Graph.bcgraph", fileName);
        assertEquals(List.of(fileName), store.list());
        LogicGraph loaded = store.read(fileName);
        assertEquals(GraphJsonCodec.encode(graph, false), GraphJsonCodec.encode(loaded, false));
    }

    @Test
    void rejectsPathsOutsideDirectory() throws Exception {
        Path dir = temp.resolve("graphs");
        Files.createDirectories(dir);
        Files.writeString(temp.resolve("secret.bcgraph"), GraphJsonCodec.encode(GraphJsonCodecTest.sample(), false));
        GraphFileStore store = new GraphFileStore(dir);
        for (String name : new String[]{"../secret.bcgraph", "..\\secret.bcgraph", "/etc/passwd",
                "C:secret.bcgraph", "graph.txt", "", "sub/graph.bcgraph"}) {
            assertThrows(GraphValidationException.class, () -> store.read(name), name);
        }
    }

    @Test
    void rejectsInvalidAndOversizedFiles() throws Exception {
        GraphFileStore store = new GraphFileStore(temp);
        Files.writeString(temp.resolve("broken.bcgraph"), "{\"format\":\"bettercontroller.graph\"");
        Files.writeString(temp.resolve("big.bcgraph"), "x".repeat(GraphLimits.MAX_JSON_CHARS * 4 + 1));
        assertThrows(GraphValidationException.class, () -> store.read("broken.bcgraph"));
        assertThrows(GraphValidationException.class, () -> store.read("big.bcgraph"));
        assertThrows(GraphValidationException.class, () -> store.read("missing.bcgraph"));
    }

    @Test
    void sanitizesFileNames() {
        assertEquals("My_Graph.bcgraph", GraphFileStore.fileNameFor("My Graph"));
        assertEquals("etcpasswd.bcgraph", GraphFileStore.fileNameFor("../etc/passwd"));
        assertEquals("graph.bcgraph", GraphFileStore.fileNameFor("äöü"));
    }
}
