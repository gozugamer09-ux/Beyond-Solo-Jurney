package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the blows one hunter's skills landed this tick, so everyone who sees it feels them land: the
 * struck flash white and hang for a moment, sparks fly off the side that was hit, the hit is heard, and the hunter's
 * own camera kicks (see {@code client.skill.SkillImpacts}). One payload a tick per hunter, however many were struck.
 *
 * @param casterId the hunter whose skill struck
 * @param element  the skill's {@code SkillElement} ordinal (what the sparks and the hit sound are made of)
 * @param ultimate the skill is an ultimate or a sovereign's power (the screen answers with an ink frame)
 * @param targets  the struck creatures' entity ids
 * @param weights  how hard each blow landed: {@code SkillFeel.LIGHT} to {@code SkillFeel.MASSIVE}
 * @param flags    per blow: {@code SkillFeel.KILL} the blow felled it, {@code SkillFeel.REPEAT} a storm's later tick
 */
public record SkillHitPayload(int casterId, int element, boolean ultimate, int[] targets, byte[] weights, byte[] flags)
        implements CustomPacketPayload {
    public static final Type<SkillHitPayload> TYPE = new Type<>(BeyondSolo.id("skill_hit"));
    public static final StreamCodec<ByteBuf, SkillHitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVarInt(p.casterId);
                f.writeByte(p.element);
                f.writeBoolean(p.ultimate);
                f.writeVarInt(p.targets.length);
                for (int i = 0; i < p.targets.length; i++) {
                    f.writeVarInt(p.targets[i]);
                    f.writeByte(p.weights[i]);
                    f.writeByte(p.flags[i]);
                }
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                int caster = f.readVarInt();
                int element = f.readByte();
                boolean ultimate = f.readBoolean();
                int n = Math.min(f.readVarInt(), 256);
                int[] targets = new int[n];
                byte[] weights = new byte[n];
                byte[] flags = new byte[n];
                for (int i = 0; i < n; i++) {
                    targets[i] = f.readVarInt();
                    weights[i] = f.readByte();
                    flags[i] = f.readByte();
                }
                return new SkillHitPayload(caster, element, ultimate, targets, weights, flags);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
