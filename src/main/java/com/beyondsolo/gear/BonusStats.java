package com.beyondsolo.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** The random bonus stats a piece of gear rolled when it dropped ({@code beyondsolo:bonus_stats}). */
public record BonusStats(List<Roll> rolls) {
    public static final BonusStats NONE = new BonusStats(List.of());

    public record Roll(BonusStat stat, float value) {
        static final Codec<BonusStat> STAT_CODEC = Codec.STRING.xmap(s -> {
            BonusStat b = BonusStat.byId(s);
            return b == null ? BonusStat.DAMAGE : b;
        }, BonusStat::id);
        public static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
                STAT_CODEC.fieldOf("stat").forGetter(Roll::stat),
                Codec.FLOAT.fieldOf("value").forGetter(Roll::value)).apply(i, Roll::new));
        public static final StreamCodec<ByteBuf, Roll> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(i -> BonusStat.values()[i], Enum::ordinal), Roll::stat,
                ByteBufCodecs.FLOAT, Roll::value,
                Roll::new);
    }

    public static final Codec<BonusStats> CODEC = Roll.CODEC.listOf().xmap(BonusStats::new, BonusStats::rolls);
    public static final StreamCodec<ByteBuf, BonusStats> STREAM_CODEC = Roll.STREAM_CODEC.apply(ByteBufCodecs.list())
            .map(BonusStats::new, BonusStats::rolls);

    public BonusStats {
        rolls = List.copyOf(rolls);
    }

    public boolean isEmpty() {
        return rolls.isEmpty();
    }

    public float total(BonusStat stat) {
        float t = 0;
        for (Roll r : rolls) if (r.stat == stat) t += r.value;
        return t;
    }
}
