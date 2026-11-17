package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: show a damage number over an entity. element = index in DamageElement.all(). For a skill or
 * infusion hit, skillElement is its SkillElement ordinal and rating how it landed (ElementalProfile.Rating ordinal:
 * immune, resisted, weak...); both -1 otherwise.
 */
public record DamageNumberPayload(int entityId, float amount, int element, boolean crit, int skillElement, int rating) implements CustomPacketPayload {
    public static final Type<DamageNumberPayload> TYPE = new Type<>(BeyondSolo.id("damage_number"));
    public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DamageNumberPayload::entityId,
            ByteBufCodecs.FLOAT, DamageNumberPayload::amount,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::element,
            ByteBufCodecs.BOOL, DamageNumberPayload::crit,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::skillElement,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::rating,
            DamageNumberPayload::new);

    public DamageNumberPayload(int entityId, float amount, int element, boolean crit) {
        this(entityId, amount, element, crit, -1, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
