package com.bettercontroller.logic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Compresses graph JSON for packets. Decompression stops at {@link GraphLimits#MAX_JSON_CHARS}
 * bytes, so a small malicious packet cannot expand into a huge string.
 */
public final class GraphWire {
    private GraphWire() {
    }

    public static byte[] compress(String json) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static String decompress(byte[] data) throws GraphValidationException {
        if (data == null || data.length > GraphLimits.MAX_COMPRESSED_BYTES) {
            throw new GraphValidationException("Graph data is too large");
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new GraphValidationException("Graph data is incomplete");
                }
                out.write(buffer, 0, n);
                if (out.size() > GraphLimits.MAX_JSON_CHARS) {
                    throw new GraphValidationException("Graph data is too large");
                }
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new GraphValidationException("Graph data is corrupt");
        } finally {
            inflater.end();
        }
    }
}
