package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: a button in an association window. What it needs travels in {@code args}: {@code gate} (a gate's
 * id), {@code rank}, {@code offer} (a hand on the office's list, or a guild's offer) or {@code hire} (a hand on the job). The server checks
 * everything again, and that the hunter is still at the desk.
 */
public record AssociationActionPayload(int action, CompoundTag args) implements CustomPacketPayload {
    public static final int REFRESH = 0, CLOSE = 1, ACCEPT = 2, DROP = 3, PERMIT = 4, EXAM = 5, TIP = 6, SCOUT = 7, HIRE = 8, DISMISS = 9;
    /** A great guild's offer ({@code offer}: its id): sign it, hold out for more, turn it down; walk out on the contract. */
    public static final int SIGN = 10, HOLD_OUT = 11, DECLINE = 12, BREAK_CONTRACT = 13;

    public static final Type<AssociationActionPayload> TYPE = new Type<>(BeyondSolo.id("association_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssociationActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssociationActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, AssociationActionPayload::args,
            AssociationActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
