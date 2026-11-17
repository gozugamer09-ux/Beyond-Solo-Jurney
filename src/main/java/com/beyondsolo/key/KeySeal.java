package com.beyondsolo.key;

import com.beyondsolo.gate.BossKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * What a dungeon key is bound to, if anything ({@code beyondsolo:key_seal}): a boss's sigil (the gate it opens ends
 * with that boss), and whether that boss comes ascended. A plain key carries no seal at all.
 *
 * @param sigil     the boss's id ({@link BossKind#id}), empty for none
 * @param ascendant the boss comes ascended (bigger, tougher, better loot)
 */
public record KeySeal(String sigil, boolean ascendant) {
    public static final Codec<KeySeal> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("sigil", "").forGetter(KeySeal::sigil),
            Codec.BOOL.optionalFieldOf("ascendant", false).forGetter(KeySeal::ascendant)
    ).apply(i, KeySeal::new));
    public static final StreamCodec<ByteBuf, KeySeal> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), KeySeal::sigil,
            ByteBufCodecs.BOOL, KeySeal::ascendant,
            KeySeal::new);

    public static KeySeal of(@Nullable BossKind boss, boolean ascendant) {
        return new KeySeal(boss == null ? "" : boss.id, ascendant);
    }

    @Nullable
    public BossKind boss() {
        return sigil.isEmpty() ? null : BossKind.byId(sigil);
    }

    /** 0 plain, 1 sealed to a boss, 2 ascendant (the looks and the price go by it). */
    public int style() {
        return ascendant ? 2 : boss() != null ? 1 : 0;
    }
}
