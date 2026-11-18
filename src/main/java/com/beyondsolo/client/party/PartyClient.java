package com.beyondsolo.client.party;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.PartyActionPayload;
import com.beyondsolo.network.PartyEventPayload;
import com.beyondsolo.network.PartySyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the client knows of its party: the last {@link PartySyncPayload} (members with their health and whereabouts, the
 * invitations waiting, the regroup clock), the regroup in progress and the last answer to a button on the PARTY page.
 */
public final class PartyClient {
    private static PartySyncPayload state = PartySyncPayload.none(List.of(), 6, 0);
    /** Client game time the state came in (the clocks in it count from then). */
    private static long syncedAt;

    /** The regroup being gathered: since when (Anim time), how long, to whom. */
    private static double channelAt = -10;
    private static int channelTicks;
    private static Component channelTo = Component.empty();
    private static boolean channeling;
    /** The last regroup moment for the HUD: 1 broken, 2 arrived, 3 someone arrived by you. */
    private static int flashKind;
    private static Component flashText = Component.empty();
    private static double flashAt = -10;

    /** The last answer to a button on the PARTY page. */
    private static Component outcome = Component.empty();
    private static boolean outcomeOk;
    private static double outcomeAt = -10;

    public static void onSync(PartySyncPayload p) {
        state = p;
        syncedAt = gameTime();
    }

    public static void onEvent(PartyEventPayload p) {
        switch (p.kind()) {
            case PartyEventPayload.REGROUP_START -> {
                channeling = true;
                channelAt = Anim.now();
                channelTicks = Math.max(1, p.ticks());
                channelTo = p.text();
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
                // regrouping means standing still out in the world: the System window gets out of the way
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof com.beyondsolo.client.screen.SystemScreen) mc.screen.onClose();
            }
            case PartyEventPayload.REGROUP_CANCEL -> {
                channeling = false;
                flash(1, p.text());
                sound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.9f, 0.6f);
            }
            case PartyEventPayload.REGROUP_DONE -> {
                channeling = false;
                flash(2, p.text());
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.8f);
            }
            case PartyEventPayload.REGROUP_JOINED -> {
                flash(3, p.text());
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
            }
            case PartyEventPayload.OUTCOME -> {
                outcome = p.text();
                outcomeOk = p.ticks() == 1;
                outcomeAt = Anim.now();
                if (!outcomeOk) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
            }
            default -> {}
        }
    }

    private static void flash(int kind, Component text) {
        flashKind = kind;
        flashText = text;
        flashAt = Anim.now();
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Forgets everything (logged out). */
    public static void reset() {
        state = PartySyncPayload.none(List.of(), 6, 0);
        channeling = false;
        flashKind = 0;
        outcome = Component.empty();
        outcomeAt = -10;
    }

    // ------------------------------------------------------------------ reads

    public static PartySyncPayload state() {
        return state;
    }

    public static boolean inParty() {
        return state.inParty();
    }

    @Nullable
    public static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getUUID();
    }

    public static boolean isMe(UUID id) {
        return id.equals(me());
    }

    public static boolean leading() {
        return state.inParty() && state.leader().equals(me());
    }

    /**
     * True if a member stands where this player does, so how far and which way mean something: the same world out in
     * the open, or the same cave of the gate dimension (which holds every gate's cave side by side), or the same patch
     * of the other dimensions that keep their runs apart (a red gate, the Citadel).
     */
    public static boolean samePlace(PartySyncPayload.Member m) {
        Minecraft mc = Minecraft.getInstance();
        if (!m.online() || mc.player == null || mc.level == null || !mc.level.dimension().location().equals(m.dim())) return false;
        if (m.place() == PartySyncPayload.WORLD) return true;
        if (mc.level.dimension().equals(com.beyondsolo.gate.GateService.DIMENSION)) {
            int slot = com.beyondsolo.gate.GateData.slotAt(net.minecraft.core.BlockPos.containing(m.x(), m.y(), m.z()));
            return slot >= 1 && slot == com.beyondsolo.gate.GateData.slotAt(mc.player.blockPosition());
        }
        // their runs lie thousands of blocks apart: near means the same one
        double dx = m.x() - mc.player.getX(), dz = m.z() - mc.player.getZ();
        return dx * dx + dz * dz < 1024 * 1024;
    }

    /** The other members, in the order they joined. */
    public static List<PartySyncPayload.Member> others() {
        List<PartySyncPayload.Member> out = new ArrayList<>();
        for (PartySyncPayload.Member m : state.members()) if (!isMe(m.id())) out.add(m);
        return out;
    }

    /** Ticks until the player can regroup again (0 ready, -1 off), counted down since the last sync. */
    public static long regroupIn() {
        if (state.regroupIn() < 0) return -1;
        return Math.max(0, state.regroupIn() - (gameTime() - syncedAt));
    }

    /** Ticks an invitation still has. */
    public static int ticksLeft(PartySyncPayload.Invite i) {
        return (int) Math.max(0, i.ticksLeft() - (gameTime() - syncedAt));
    }

    public static boolean channeling() {
        return channeling;
    }

    /** 0..1 of the regroup gathered. */
    public static float channel() {
        return channeling ? Mth.clamp(Anim.since(channelAt) * 20f / channelTicks, 0f, 1f) : 0f;
    }

    public static Component channelTo() {
        return channelTo;
    }

    public static int flashKind() {
        return flashKind;
    }

    public static Component flashText() {
        return flashText;
    }

    public static float sinceFlash() {
        return Anim.since(flashAt);
    }

    public static Component outcome() {
        return outcome;
    }

    public static boolean outcomeOk() {
        return outcomeOk;
    }

    public static float sinceOutcome() {
        return Anim.since(outcomeAt);
    }

    private static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    // ------------------------------------------------------------------ actions

    public static void send(int action, UUID target) {
        PacketDistributor.sendToServer(new PartyActionPayload(action, target));
    }

    public static void send(int action) {
        PacketDistributor.sendToServer(PartyActionPayload.of(action));
    }

    /** Every client tick: light gathering round the player's feet while they regroup (kept low, out of their view). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!channeling || mc.player == null || mc.level == null || mc.isPaused()) return;
        float k = channel();
        double t = Anim.since(channelAt);
        for (int i = 0; i < 2; i++) {
            double a = t * 5 + i * Math.PI + k * 6;
            double r = 1.2 - 0.4 * k;
            mc.level.addParticle(ParticleTypes.END_ROD, mc.player.getX() + Math.cos(a) * r, mc.player.getY() + 0.05 + (t * 0.8 + i * 0.3) % 0.7,
                    mc.player.getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    /** Stands in a party for the UI test driver (screenshots without a second player). */
    public static void demo(PartySyncPayload fake) {
        onSync(fake);
    }

    /**
     * A made-up party round the player for screenshots: a mage out in the world to the north-east, a healer in a
     * D-rank gate, someone offline, and an invitation waiting ({@code "alone"}: no party, two invitations).
     */
    public static PartySyncPayload demoState(String variant) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        UUID me = p == null ? new UUID(0, 1) : p.getUUID();
        String myName = p == null ? "Dev" : p.getGameProfile().getName();
        var dim = mc.level == null ? net.minecraft.world.level.Level.OVERWORLD.location() : mc.level.dimension().location();
        double x = p == null ? 0 : p.getX(), y = p == null ? 64 : p.getY(), z = p == null ? 0 : p.getZ();
        UUID kael = UUID.nameUUIDFromBytes("demo:kael".getBytes()), mira = UUID.nameUUIDFromBytes("demo:mira".getBytes());
        UUID toru = UUID.nameUUIDFromBytes("demo:toru".getBytes()), sora = UUID.nameUUIDFromBytes("demo:sora".getBytes());
        List<PartySyncPayload.Invite> invites = new ArrayList<>();
        invites.add(new PartySyncPayload.Invite(sora, "Sora", 3, 20 * 42));
        if (variant.equals("alone")) {
            invites.add(new PartySyncPayload.Invite(kael, "Kael", 1, 20 * 18));
            return PartySyncPayload.none(invites, 6, 0);
        }
        var sys = com.beyondsolo.client.ClientSystemData.get();
        List<PartySyncPayload.Member> members = new ArrayList<>();
        members.add(new PartySyncPayload.Member(me, myName, true, true, sys.isAwakened() ? sys.rank().ordinal() : -1,
                sys.isAwakened() ? sys.playerClass().ordinal() : -1, -1, sys.level(), p == null ? 20 : p.getHealth(), p == null ? 20 : p.getMaxHealth(),
                PartySyncPayload.WORLD, -1, Component.empty(), dim, x, y, z));
        members.add(new PartySyncPayload.Member(kael, "Kael", true, true, 3, com.beyondsolo.system.PlayerClass.MAGE.ordinal(), -1, 24, 13, 20,
                PartySyncPayload.WORLD, -1, Component.empty(), dim, x + 96, y + 2, z - 118));
        members.add(new PartySyncPayload.Member(mira, "Mira", true, true, 1, com.beyondsolo.system.PlayerClass.HEALER.ordinal(), -1, 17, 5, 20,
                PartySyncPayload.GATE, 1, Component.literal("Ashen Hollow"), net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("beyondsolo", "gate_depths"), 0, 40, 0));
        members.add(new PartySyncPayload.Member(toru, "Toru", false, false, -1, -1, -1, 0, 0, 0,
                PartySyncPayload.OFFLINE, -1, Component.empty(), dim, 0, 0, 0));
        return new PartySyncPayload(true, me, members, invites, 6, 0);
    }

    /** A PARTY page button by name (driver). */
    public static int actionNamed(String name) {
        return switch (name) {
            case "invite" -> PartyActionPayload.INVITE;
            case "accept" -> PartyActionPayload.ACCEPT;
            case "decline" -> PartyActionPayload.DECLINE;
            case "leave" -> PartyActionPayload.LEAVE;
            case "kick" -> PartyActionPayload.KICK;
            case "promote" -> PartyActionPayload.PROMOTE;
            case "disband" -> PartyActionPayload.DISBAND;
            case "regroup" -> PartyActionPayload.REGROUP;
            default -> -1;
        };
    }

    /** A player's id by name: a member, someone who invited them, or anyone on the player list (driver). */
    public static UUID idOf(String name) {
        for (PartySyncPayload.Member m : state.members()) if (m.name().equalsIgnoreCase(name)) return m.id();
        for (PartySyncPayload.Invite i : state.invites()) if (i.name().equalsIgnoreCase(name)) return i.from();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            for (var info : conn.getOnlinePlayers()) if (info.getProfile().getName().equalsIgnoreCase(name)) return info.getProfile().getId();
        }
        return new UUID(0, 0);
    }

    private PartyClient() {}
}
