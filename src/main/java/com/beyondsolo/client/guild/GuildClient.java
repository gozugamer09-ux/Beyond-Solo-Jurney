package com.beyondsolo.client.guild;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.guild.GuildRole;
import com.beyondsolo.network.GuildActionPayload;
import com.beyondsolo.network.GuildEventPayload;
import com.beyondsolo.network.GuildSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the client knows of its guild: the last {@link GuildSyncPayload} (as {@code guild.Guilds.syncTag} built it: the
 * guild, its members, hired hunters, recruits, log and raid, the invitations waiting), the last answer to a button on
 * the GUILD page and the last guild moment (founded, levelled up).
 */
public final class GuildClient {
    private static CompoundTag state = new CompoundTag();
    /** Client game time the state came in (the clocks in it count from then). */
    private static long syncedAt;

    private static Component outcome = Component.empty();
    private static boolean outcomeOk;
    private static double outcomeAt = -10;

    /** The last guild moment: {@link GuildEventPayload#FOUNDED} or {@link GuildEventPayload#LEVEL_UP}. */
    private static int momentKind = -1, momentValue;
    private static Component momentText = Component.empty();
    private static double momentAt = -10;

    public static void onSync(GuildSyncPayload p) {
        state = p.data();
        syncedAt = gameTime();
    }

    public static void onEvent(GuildEventPayload p) {
        switch (p.kind()) {
            case GuildEventPayload.OUTCOME -> {
                outcome = p.text();
                outcomeOk = p.value() == 1;
                outcomeAt = Anim.now();
                if (!outcomeOk) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
            }
            case GuildEventPayload.FOUNDED -> {
                moment(p);
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f);
            }
            case GuildEventPayload.LEVEL_UP -> {
                moment(p);
                sound(SoundEvents.PLAYER_LEVELUP, 0.8f, 0.7f);
            }
            default -> {}
        }
    }

    private static void moment(GuildEventPayload p) {
        momentKind = p.kind();
        momentValue = p.value();
        momentText = p.text();
        momentAt = Anim.now();
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Forgets everything (logged out). */
    public static void reset() {
        state = new CompoundTag();
        outcome = Component.empty();
        outcomeAt = -10;
        momentKind = -1;
        momentAt = -10;
    }

    private static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    // ------------------------------------------------------------------ reads

    public static CompoundTag state() {
        return state;
    }

    public static boolean inGuild() {
        return state.getBoolean("in");
    }

    public static String name() {
        return state.getString("name");
    }

    public static String tag() {
        return state.getString("tag");
    }

    public static int crest() {
        return state.getInt("crest");
    }

    public static int color() {
        return state.getInt("color");
    }

    public static int level() {
        return state.getInt("level");
    }

    public static long treasury() {
        return state.getLong("treasury");
    }

    public static long foundingCost() {
        return state.getLong("foundCost");
    }

    /** The player's own rank in the guild (member if none). */
    public static GuildRole role() {
        GuildRole r = GuildRole.byId(state.getString("role"));
        return r == null ? GuildRole.MEMBER : r;
    }

    @Nullable
    public static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getUUID();
    }

    public static List<CompoundTag> list(String key) {
        return list(state, key);
    }

    public static List<CompoundTag> list(CompoundTag t, String key) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag l = t.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    public static List<CompoundTag> members() {
        return list("members");
    }

    public static List<CompoundTag> hunters() {
        return list("hunters");
    }

    public static List<CompoundTag> recruits() {
        return list("recruits");
    }

    public static List<CompoundTag> invites() {
        return list("invites");
    }

    public static List<CompoundTag> log() {
        return list("log");
    }

    /** The guild's raid (its permit's, or the one a member leads), or null. */
    @Nullable
    public static CompoundTag raid() {
        return state.contains("raid", Tag.TAG_COMPOUND) ? state.getCompound("raid") : null;
    }

    /** Ticks an invitation or injury clock still has, counted down since the sync. */
    public static long ticksLeft(long ticks) {
        return Math.max(0, ticks - (gameTime() - syncedAt));
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

    public static int momentKind() {
        return momentKind;
    }

    public static int momentValue() {
        return momentValue;
    }

    public static Component momentText() {
        return momentText;
    }

    public static float sinceMoment() {
        return Anim.since(momentAt);
    }

    // ------------------------------------------------------------------ actions

    public static void send(int action, CompoundTag args) {
        PacketDistributor.sendToServer(new GuildActionPayload(action, args));
    }

    public static void send(int action) {
        send(action, new CompoundTag());
    }

    public static void send(int action, UUID target) {
        CompoundTag t = new CompoundTag();
        t.putUUID("target", target);
        send(action, t);
    }

    /** Stands in a guild for the UI test driver (screenshots without other players). */
    public static void demo(CompoundTag fake) {
        state = fake;
        syncedAt = gameTime();
    }

    private GuildClient() {}
}
