package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a player's skill went off, so their body acts it out (a swing, a slam, a cast, a draw...). Sent
 * to everyone who can see the caster and to the caster.
 *
 * @param entityId the caster
 * @param skill    the skill's path
 * @param look     the first effect the skill showed: a {@link SkillFxPayload.Kind} name, {@code rune:} and a
 *                 {@link RuneFxPayload.Kind} name for a rune skill, or empty if it showed none
 * @param variant  that effect's duration, which for a few kinds picks a look (a cut back the other way, a wave of rock)
 */
public record SkillCastPayload(int entityId, String skill, String look, int variant) implements CustomPacketPayload {
    public static final Type<SkillCastPayload> TYPE = new Type<>(BeyondSolo.id("skill_cast"));
    public static final StreamCodec<ByteBuf, SkillCastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillCastPayload::entityId,
            ByteBufCodecs.STRING_UTF8, SkillCastPayload::skill,
            ByteBufCodecs.STRING_UTF8, SkillCastPayload::look,
            ByteBufCodecs.VAR_INT, SkillCastPayload::variant,
            SkillCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
