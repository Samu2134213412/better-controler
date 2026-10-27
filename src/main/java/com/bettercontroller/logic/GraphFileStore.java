package com.bettercontroller.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes .bcgraph files in one directory (by default .minecraft/bettercontroller/graphs).
 * Only plain file names are accepted, so nothing outside that directory can be read or written.
 */
public final class GraphFileStore {
    public static final String EXTENSION = ".bcgraph";
    private static final int MAX_LISTED_FILES = 500;

    private final Path directory;

    public GraphFileStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    /** File names of all .bcgraph files in the directory, sorted case-insensitively. */
    public List<String> list() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    names.add(file.getFileName().toString());
                    if (names.size() >= MAX_LISTED_FILES) {
                        break;
                    }
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(names);
    }

    /** Reads and fully validates a graph file. Nothing is changed if this throws. */
    public LogicGraph read(String fileName) throws IOException, GraphValidationException {
        Path file = resolve(fileName);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new GraphValidationException("File not found: " + fileName);
        }
        // UTF-8 needs at most 4 bytes per char; checking the size first avoids reading huge files.
        if (Files.size(file) > GraphLimits.MAX_JSON_CHARS * 4L) {
            throw new GraphValidationException("File is too large");
        }
        return GraphJsonCodec.decode(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Writes the graph as {@code <name>.bcgraph}, replacing a file of the same name. @return the file name */
    public String write(LogicGraph graph) throws IOException {
        Files.createDirectories(directory);
        // fileNameFor only produces [A-Za-z0-9_-] names, so the file is always inside the directory.
        String fileName = fileNameFor(graph.name());
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve(fileName + ".tmp");
        Files.writeString(temp, GraphJsonCodec.encode(graph, true), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return fileName;
    }

    /** Turns a graph name into a safe file name: letters, digits, '-' and '_' only. */
    public static String fileNameFor(String graphName) {
        StringBuilder sb = new StringBuilder();
        for (char ch : graphName.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                sb.append(ch);
            } else if (ch == ' ' && sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        String base = sb.isEmpty() ? "graph" : sb.toString();
        return base + EXTENSION;
    }

    private Path resolve(String fileName) throws GraphValidationException {
        if (fileName == null || fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..") || fileName.indexOf(':') >= 0
                || !fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            throw new GraphValidationException("Invalid file name");
        }
        Path file = directory.resolve(fileName).normalize();
        if (!directory.equals(file.getParent())) {
            throw new GraphValidationException("Invalid file name");
        }
        return file;
    }
}
