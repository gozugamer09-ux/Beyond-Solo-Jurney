package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: the player's party as it stands (sent when it changes, and twice a second while they are in one so
 * the HUD can follow everyone's health and whereabouts), the invitations waiting for them, and how long before they
 * can regroup again ({@code regroupIn} ticks, 0 ready, -1 turned off).
 */
public record PartySyncPayload(boolean inParty, UUID leader, List<Member> members, List<Invite> invites, int maxSize, long regroupIn)
        implements CustomPacketPayload {
    /** Where a member is: out in a world, or inside one of the places only some can go. */
    public static final int WORLD = 0, GATE = 1, RED_GATE = 2, CITADEL = 3, PENALTY = 4, TRIAL = 5, JOB_TRIAL = 6, OFFLINE = 7, THRONE = 8, HOME = 9;

    /**
     * One member. rank, cls, sub: ordinals (-1 for none: not evaluated, no subclass yet). place: {@link #WORLD} ..
     * {@link #OFFLINE}; placeRank the gate's rank (-1); placeName the gate's dungeon, empty otherwise.
     */
    public record Member(UUID id, String name, boolean online, boolean system, int rank, int cls, int sub, int level,
                         float health, float maxHealth, int place, int placeRank, Component placeName, ResourceLocation dim,
                         double x, double y, double z) {}

    /** An invitation: who from, how big their party is now (1 if they have none yet), ticks left to answer. */
    public record Invite(UUID from, String name, int size, int ticksLeft) {}

    public static final Type<PartySyncPayload> TYPE = new Type<>(BeyondSolo.id("party_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartySyncPayload> STREAM_CODEC = StreamCodec.of(PartySyncPayload::write, PartySyncPayload::read);

    public static PartySyncPayload none(List<Invite> invites, int maxSize, long regroupIn) {
        return new PartySyncPayload(false, new UUID(0, 0), List.of(), invites, maxSize, regroupIn);
    }

    private static void write(RegistryFriendlyByteBuf buf, PartySyncPayload p) {
        buf.writeBoolean(p.inParty);
        UUIDUtil.STREAM_CODEC.encode(buf, p.leader);
        buf.writeVarInt(p.members.size());
        for (Member m : p.members) {
            UUIDUtil.STREAM_CODEC.encode(buf, m.id);
            buf.writeUtf(m.name, 64);
            buf.writeBoolean(m.online);
            buf.writeBoolean(m.system);
            buf.writeByte(m.rank);
            buf.writeByte(m.cls);
            buf.writeByte(m.sub);
            buf.writeVarInt(m.level);
            buf.writeFloat(m.health);
            buf.writeFloat(m.maxHealth);
            buf.writeByte(m.place);
            buf.writeByte(m.placeRank);
            ComponentSerialization.STREAM_CODEC.encode(buf, m.placeName);
            buf.writeResourceLocation(m.dim);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
        }
        buf.writeVarInt(p.invites.size());
        for (Invite i : p.invites) {
            UUIDUtil.STREAM_CODEC.encode(buf, i.from);
            buf.writeUtf(i.name, 64);
            buf.writeVarInt(i.size);
            buf.writeVarInt(i.ticksLeft);
        }
        buf.writeVarInt(p.maxSize);
        buf.writeVarLong(p.regroupIn + 1);
    }

    private static PartySyncPayload read(RegistryFriendlyByteBuf buf) {
        boolean in = buf.readBoolean();
        UUID leader = UUIDUtil.STREAM_CODEC.decode(buf);
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(Math.min(n, 32));
        for (int i = 0; i < n; i++) {
            members.add(new Member(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(),
                    buf.readByte(), buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readByte(), buf.readByte(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readResourceLocation(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        int k = buf.readVarInt();
        List<Invite> invites = new ArrayList<>(Math.min(k, 32));
        for (int i = 0; i < k; i++) invites.add(new Invite(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
        int max = buf.readVarInt();
        long regroup = buf.readVarLong() - 1;
        return new PartySyncPayload(in, leader, members, invites, max, regroup);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
