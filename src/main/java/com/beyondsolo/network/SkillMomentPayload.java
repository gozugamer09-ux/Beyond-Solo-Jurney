package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a skill moment for the overlay.
 *
 * @param moment 0 = learned, 1 = levelled up, 2 = refused (not enough mana / on cooldown / no target)
 * @param skill  the skill's path
 * @param level  its new level (learned / level up), or a reason index (refused)
 */
public record SkillMomentPayload(int moment, String skill, int level) implements CustomPacketPayload {
    public static final int LEARNED = 0;
    public static final int LEVEL_UP = 1;
    public static final int REFUSED = 2;

    public static final int NO_MANA = 0;
    public static final int COOLDOWN = 1;
    public static final int NO_TARGET = 2;
    public static final int BLOCKED = 3;

    public static final Type<SkillMomentPayload> TYPE = new Type<>(BeyondSolo.id("skill_moment"));
    public static final StreamCodec<ByteBuf, SkillMomentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillMomentPayload::moment,
            ByteBufCodecs.STRING_UTF8, SkillMomentPayload::skill,
            ByteBufCodecs.VAR_INT, SkillMomentPayload::level,
            SkillMomentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
