package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: cast the skill in a bar slot, or put a skill into a slot (empty skill = clear the slot). */
public record SkillActionPayload(int action, int slot, String skill) implements CustomPacketPayload {
    public static final int CAST = 0;
    public static final int ASSIGN = 1;

    public static final Type<SkillActionPayload> TYPE = new Type<>(BeyondSolo.id("skill_action"));
    public static final StreamCodec<ByteBuf, SkillActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillActionPayload::action,
            ByteBufCodecs.VAR_INT, SkillActionPayload::slot,
            ByteBufCodecs.STRING_UTF8, SkillActionPayload::skill,
            SkillActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
