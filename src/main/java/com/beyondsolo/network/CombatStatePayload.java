package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: one entity's combat state, for the buildup meters and the status visuals.
 * Buildups are fractions of their threshold (0..1).
 */
public record CombatStatePayload(int entityId, float burn, float venom, float crimson, float blood, int flags, int venomStacks)
        implements CustomPacketPayload {
    public static final int DEEP_BURN = 1;
    public static final int HEAVY_VENOM = 2;
    public static final int CRIMSON_BRAND = 4;

    public static final Type<CombatStatePayload> TYPE = new Type<>(BeyondSolo.id("combat_state"));
    public static final StreamCodec<ByteBuf, CombatStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
                buf.writeByte(Math.round(p.burn * 255));
                buf.writeByte(Math.round(p.venom * 255));
                buf.writeByte(Math.round(p.crimson * 255));
                buf.writeByte(Math.round(p.blood * 255));
                buf.writeByte(p.flags);
                buf.writeByte(p.venomStacks);
            },
            buf -> new CombatStatePayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readUnsignedByte() / 255f,
                    buf.readUnsignedByte() / 255f, buf.readUnsignedByte() / 255f, buf.readUnsignedByte() / 255f,
                    buf.readUnsignedByte(), buf.readUnsignedByte()));

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public boolean isEmpty() {
        return flags == 0 && venomStacks == 0 && burn + venom + crimson + blood < 0.004f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
