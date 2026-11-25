package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a hunter pressed a skill and is winding up for it; it goes off in {@code ticks} ticks (then the
 * usual {@link SkillCastPayload} follows). Everyone who sees the hunter sees the body draw back and the power gather,
 * and the gesture's blow is timed to land as the skill does (see {@code client.skill.SkillGestures}).
 *
 * @param entityId the hunter
 * @param skill    the skill's path
 * @param look     the first effect the skill shows (as in {@link SkillCastPayload}), which picks the gesture
 * @param variant  that effect's duration
 * @param ticks    ticks until the skill goes off
 * @param delay    ticks after that until its first blow, for a skill that takes its own time (a stance), else 0
 */
public record SkillWindupPayload(int entityId, String skill, String look, int variant, int ticks, int delay) implements CustomPacketPayload {
    public static final Type<SkillWindupPayload> TYPE = new Type<>(BeyondSolo.id("skill_windup"));
    public static final StreamCodec<ByteBuf, SkillWindupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillWindupPayload::entityId,
            ByteBufCodecs.STRING_UTF8, SkillWindupPayload::skill,
            ByteBufCodecs.STRING_UTF8, SkillWindupPayload::look,
            ByteBufCodecs.VAR_INT, SkillWindupPayload::variant,
            ByteBufCodecs.VAR_INT, SkillWindupPayload::ticks,
            ByteBufCodecs.VAR_INT, SkillWindupPayload::delay,
            SkillWindupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
