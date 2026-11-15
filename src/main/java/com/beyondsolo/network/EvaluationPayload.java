package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server -> client: drives the evaluation overlay.
 *
 * @param phase       {@link #START} (scan begins), {@link #CANCEL} (scan interrupted) or {@link #REVEAL}
 * @param scanTicks   scan length, for START
 * @param rank        rank ordinal, for REVEAL
 * @param playerClass class ordinal, for REVEAL
 * @param stats       the five starting stats in {@code Stat} order, for REVEAL. A re-evaluation sends ten: the
 *                    current stats, then those of the last evaluation
 * @param previousRank for a re-evaluation (START and REVEAL), the rank before it; -1 for the first evaluation
 */
public record EvaluationPayload(int phase, int scanTicks, int rank, int playerClass, List<Integer> stats, int previousRank) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int CANCEL = 1;
    public static final int REVEAL = 2;

    public static final Type<EvaluationPayload> TYPE = new Type<>(BeyondSolo.id("evaluation"));
    public static final StreamCodec<ByteBuf, EvaluationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EvaluationPayload::phase,
            ByteBufCodecs.VAR_INT, EvaluationPayload::scanTicks,
            ByteBufCodecs.VAR_INT, EvaluationPayload::rank,
            ByteBufCodecs.VAR_INT, EvaluationPayload::playerClass,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), EvaluationPayload::stats,
            ByteBufCodecs.VAR_INT, p -> p.previousRank() + 1,
            (phase, scan, rank, cls, stats, prev) -> new EvaluationPayload(phase, scan, rank, cls, stats, prev - 1));

    public static EvaluationPayload start(int scanTicks) {
        return start(scanTicks, -1);
    }

    public static EvaluationPayload start(int scanTicks, int previousRank) {
        return new EvaluationPayload(START, scanTicks, 0, 0, List.of(), previousRank);
    }

    public static EvaluationPayload cancel() {
        return new EvaluationPayload(CANCEL, 0, 0, 0, List.of(), -1);
    }

    public static EvaluationPayload reveal(int rank, int playerClass, List<Integer> stats) {
        return reveal(rank, playerClass, stats, -1);
    }

    public static EvaluationPayload reveal(int rank, int playerClass, List<Integer> stats, int previousRank) {
        return new EvaluationPayload(REVEAL, 0, rank, playerClass, stats, previousRank);
    }

    public boolean reevaluation() {
        return previousRank >= 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
