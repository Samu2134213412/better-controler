package com.bettercontroller.network;

import com.bettercontroller.BetterController;
import com.bettercontroller.logic.GraphLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * All packets of the mod. Graphs travel as compressed JSON (see GraphJsonCodec and GraphWire) and
 * are validated after decompression; sizes are capped here so oversized packets are rejected while decoding.
 */
public final class GraphPayloads {
    /** At most this many keys are reported as held at once. */
    public static final int MAX_HELD_KEYS = 16;
    private static final int MAX_BOUND_KEYS = 256;
    public static final int MAX_LISTED_GRAPHS = 1024;
    private static final int MAX_MESSAGE_LENGTH = 256;

    private GraphPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(BetterController.id(path));
    }

    // ---- client -> server ----

    /** Asks for the list of graphs the player may open. */
    public record RequestGraphList() implements CustomPacketPayload {
        public static final Type<RequestGraphList> TYPE = payloadType("request_graph_list");
        public static final StreamCodec<ByteBuf, RequestGraphList> CODEC = StreamCodec.unit(new RequestGraphList());

        @Override
        public Type<RequestGraphList> type() {
            return TYPE;
        }
    }

    public record OpenGraph(UUID graphId) implements CustomPacketPayload {
        public static final Type<OpenGraph> TYPE = payloadType("open_graph");
        public static final StreamCodec<ByteBuf, OpenGraph> CODEC = UUIDUtil.STREAM_CODEC.map(OpenGraph::new, OpenGraph::graphId);

        @Override
        public Type<OpenGraph> type() {
            return TYPE;
        }
    }

    /**
     * Creates or replaces a graph, as JSON compressed with {@link com.bettercontroller.logic.GraphWire}.
     * The server validates it again and checks ownership.
     */
    public record SaveGraph(byte[] data) implements CustomPacketPayload {
        public static final Type<SaveGraph> TYPE = payloadType("save_graph");
        public static final StreamCodec<ByteBuf, SaveGraph> CODEC =
                ByteBufCodecs.byteArray(GraphLimits.MAX_COMPRESSED_BYTES).map(SaveGraph::new, SaveGraph::data);

        @Override
        public Type<SaveGraph> type() {
            return TYPE;
        }
    }

    public record DeleteGraph(UUID graphId) implements CustomPacketPayload {
        public static final Type<DeleteGraph> TYPE = payloadType("delete_graph");
        public static final StreamCodec<ByteBuf, DeleteGraph> CODEC = UUIDUtil.STREAM_CODEC.map(DeleteGraph::new, DeleteGraph::graphId);

        @Override
        public Type<DeleteGraph> type() {
            return TYPE;
        }
    }

    /** Keys the client currently holds, limited to the keys the server announced via {@link BoundKeys}. Sent only on change. */
    public record HeldKeys(List<Integer> keys) implements CustomPacketPayload {
        public static final Type<HeldKeys> TYPE = payloadType("held_keys");
        public static final StreamCodec<ByteBuf, HeldKeys> CODEC =
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_HELD_KEYS)).map(HeldKeys::new, HeldKeys::keys);

        @Override
        public Type<HeldKeys> type() {
            return TYPE;
        }
    }

    // ---- server -> client ----

    public record GraphSummary(UUID id, String name, String ownerName, int nodeCount, boolean own) {
        public static final StreamCodec<ByteBuf, GraphSummary> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraphSummary::id,
                ByteBufCodecs.stringUtf8(GraphLimits.MAX_NAME_LENGTH), GraphSummary::name,
                ByteBufCodecs.stringUtf8(64), GraphSummary::ownerName,
                ByteBufCodecs.VAR_INT, GraphSummary::nodeCount,
                ByteBufCodecs.BOOL, GraphSummary::own,
                GraphSummary::new);
    }

    public record GraphList(List<GraphSummary> graphs) implements CustomPacketPayload {
        public static final Type<GraphList> TYPE = payloadType("graph_list");
        public static final StreamCodec<ByteBuf, GraphList> CODEC =
                GraphSummary.CODEC.apply(ByteBufCodecs.list(MAX_LISTED_GRAPHS)).map(GraphList::new, GraphList::graphs);

        @Override
        public Type<GraphList> type() {
            return TYPE;
        }
    }

    /** Full graph data for the editor, compressed like {@link SaveGraph}. */
    public record GraphData(byte[] data) implements CustomPacketPayload {
        public static final Type<GraphData> TYPE = payloadType("graph_data");
        public static final StreamCodec<ByteBuf, GraphData> CODEC =
                ByteBufCodecs.byteArray(GraphLimits.MAX_COMPRESSED_BYTES).map(GraphData::new, GraphData::data);

        @Override
        public Type<GraphData> type() {
            return TYPE;
        }
    }

    /** Key codes used by the player's own graphs; the client only reports these keys. */
    public record BoundKeys(List<Integer> keys) implements CustomPacketPayload {
        public static final Type<BoundKeys> TYPE = payloadType("bound_keys");
        public static final StreamCodec<ByteBuf, BoundKeys> CODEC =
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_BOUND_KEYS)).map(BoundKeys::new, BoundKeys::keys);

        @Override
        public Type<BoundKeys> type() {
            return TYPE;
        }
    }

    /** Result of a request, shown in the editor. {@code saved} is true only for a successful save. */
    public record Status(String message, boolean error, boolean saved) implements CustomPacketPayload {
        public static final Type<Status> TYPE = payloadType("status");
        public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_MESSAGE_LENGTH), Status::message,
                ByteBufCodecs.BOOL, Status::error,
                ByteBufCodecs.BOOL, Status::saved,
                Status::new);

        public Status {
            if (message.length() > MAX_MESSAGE_LENGTH) {
                message = message.substring(0, MAX_MESSAGE_LENGTH);
            }
        }

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }
}
