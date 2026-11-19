package com.beyondsolo.shadow;

import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What a master does with the shadows of their army beyond calling them out (the series' named shadows and their
 * promotions):
 * <ul>
 *   <li><b>Shadow essence.</b> A soul its master leaves to fade, or one that crumbles after three refusals, drifts into
 *   the master's shadow as essence ({@link #soulEssence}) if the master is near; a shadow that is let go leaves a
 *   little too ({@link #releaseEssence}).</li>
 *   <li><b>Promotions.</b> Up to a knight a shadow climbs by itself. From a knight on, a shadow at level 10 of its rank
 *   waits ({@link ShadowRecord#ready}) until its master promotes it ({@link #promote}): that takes the master's level
 *   and essence ({@link ShadowRank#promotionLevel}, {@link ShadowRank#promotionCost}). A monarch's marshals can be
 *   promoted to regents.</li>
 *   <li><b>Given names.</b> A knight or stronger, or any boss's shadow, can be given a name by its master ({@link #name});
 *   a named shadow is bound closer and {@link #BOND} stronger. How many: {@link #nameSlots}.</li>
 * </ul>
 * Numbers are first guesses for the final balancing pass.
 */
public final class ShadowPromotions {
    /** A shadow with a name its master gave it is this much stronger, in health and in blows. */
    public static final float BOND = 1.10f;
    /** Shadows near one of their master's commanders (or anything higher) hit this much harder. */
    public static final float LEAD = 1.10f;
    /** How far from a fading soul its master can be and still draw in its essence. */
    public static final double ESSENCE_REACH = 48;
    /** The titles: the first name given, the first marshal promoted. */
    public static final String NAMER_ID = "keeper_of_names", MARSHAL_ID = "marshal_maker";
    public static final String NAMER = TitleDef.PREFIX + NAMER_ID, MARSHAL = TitleDef.PREFIX + MARSHAL_ID;

    static void titles() {
        Titles.register(new TitleDef(NAMER_ID, TitleCategory.MASTERY, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.FOCUS, 0.04f)), false));
        Titles.register(new TitleDef(MARSHAL_ID, TitleCategory.MASTERY, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.MIGHT, 0.05f), new PerkLine(TitlePerk.FOCUS, 0.05f)), false));
    }

    // ================================================================ numbers

    /** How many shadows a master can give names to: two, one more every 20 levels, three more for a monarch. */
    public static int nameSlots(Player p) {
        int n = 2 + SystemAPI.get(p).level() / 20;
        return MonarchAPI.crowned(p) ? n + 3 : n;
    }

    /** Whether a shadow can take a name from its master: a knight or stronger, or any boss's shadow. */
    public static boolean nameable(ShadowRecord r) {
        return r.boss() || r.rank().ordinal() >= ShadowRank.KNIGHT.ordinal();
    }

    /** The essence a soul that was not kept leaves its master (a boss's four times as much). */
    public static int soulEssence(float health, boolean boss) {
        int e = Math.max(1, Math.round(health / 8f));
        return boss ? e * 4 : e;
    }

    /** The essence a shadow leaves when its master lets it go: a little of what it grew to. */
    public static int releaseEssence(ShadowRecord r) {
        int e = Math.round((2 + 3 * r.rank().ordinal()) * (1 + 0.2f * r.level()));
        return r.boss() ? e * 2 : e;
    }

    // ================================================================ essence

    /**
     * A soul of this master's faded unclaimed, or crumbled: if the master is near, it drifts into their shadow as
     * essence (and is drawn going there). Returns the essence it gave (0 if the master was too far or gone).
     */
    static int soulFaded(@Nullable ServerPlayer owner, ServerLevel level, ShadowService.Soul s) {
        if (owner == null || owner.isRemoved() || owner.level() != level || !ShadowService.canRaise(owner)
                || owner.position().distanceToSqr(s.pos) > ESSENCE_REACH * ESSENCE_REACH) return 0;
        int e = soulEssence(s.health, s.boss);
        ShadowService.army(owner).addEssence(e);
        int color = s.boss ? 0xFF4A6A : 0x9B7BE0;
        PacketDistributor.sendToPlayersNear(level, null, s.pos.x, s.pos.y, s.pos.z, 64, new ShadowFxPayload(ShadowFxPayload.Kind.ESSENCE.ordinal(),
                s.id, (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, color, owner.getId(), Integer.toString(e)));
        ShadowService.sync(owner);
        return e;
    }

    /** Staging and tests: sets a master's essence. */
    public static void setEssence(ServerPlayer p, int amount) {
        ShadowService.army(p).setEssence(amount);
        ShadowService.sync(p);
    }

    /** Staging and tests: adds to a master's essence. */
    public static void addEssence(ServerPlayer p, int amount) {
        ShadowService.army(p).addEssence(amount);
        ShadowService.sync(p);
    }

    // ================================================================ promotions

    /**
     * Why this shadow can't be promoted right now, as the end of a lang key ({@code shadow.beyondsolo.promote.<why>}),
     * or null if it can.
     */
    public static @Nullable String refusal(ServerPlayer p, ShadowRecord r) {
        if (!ShadowService.canRaise(p)) return "not_raiser";
        ShadowRank top = ShadowService.top(p);
        if (r.rank().ordinal() >= top.ordinal()) return r.rank() == ShadowRank.MARSHAL && top == ShadowRank.MARSHAL ? "monarch_only" : "top";
        ShadowRank to = r.rank().next();
        if (!to.byPromotion()) return "climbs";
        if (r.level() < ShadowRank.LEVEL_CAP) return "level";
        if (SystemAPI.get(p).level() < to.promotionLevel()) return "master";
        if (ShadowService.army(p).essence() < to.promotionCost()) return "essence";
        return null;
    }

    /** The master promotes one of their shadows (from the Shadows page). False, with a pop-up saying why, if it can't be. */
    public static boolean promote(ServerPlayer p, UUID recordId) {
        ShadowRecord r = ShadowService.army(p).get(recordId);
        if (r == null) return false;
        String why = refusal(p, r);
        if (why != null) {
            ShadowRank to = r.rank().next();
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable("shadow.beyondsolo.promote.refused"),
                    Component.translatable("shadow.beyondsolo.promote." + why, r.displayName(), to.displayName(), to.promotionLevel(),
                            to.promotionCost(), ShadowService.army(p).essence())));
            return false;
        }
        promoted(p, r, false);
        return true;
    }

    /**
     * Raises a shadow one rank as a promotion, with its moment: the essence is paid unless {@code free} (staging), it
     * comes into its new rank at the level that keeps its strength ({@link ShadowRank#entryLevel}). The caller has
     * checked it may climb.
     */
    static void promoted(ServerPlayer p, ShadowRecord r, boolean free) {
        ShadowRank before = r.rank();
        ShadowRank to = before.next();
        if (!free) ShadowService.army(p).addEssence(-to.promotionCost());
        r.setLevel(before.entryLevel(r.level()));
        r.setRank(to);
        r.setXp(0);
        ShadowEntity e = ShadowService.entity(p, r.id());
        if (e != null) e.refresh(r, p);
        Vec3 at = e != null ? e.position() : p.position();
        ShadowFxPayload fx = new ShadowFxPayload(ShadowFxPayload.Kind.PROMOTED.ordinal(), e != null ? e.getId() : -1,
                (float) at.x, (float) at.y, (float) at.z, to.color(), to.ordinal(), r.wireName());
        if (e != null) PacketDistributor.sendToPlayersTrackingEntity(e, fx);
        PacketDistributor.sendToPlayer(p, fx);
        ServerLevel level = p.serverLevel();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.7f, 1.4f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 0.5f);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD, Component.translatable("shadow.beyondsolo.promoted"),
                Component.translatable("shadow.beyondsolo.promoted.body", r.displayName(), before.displayName(), to.displayName())));
        if (to == ShadowRank.MARSHAL || to == ShadowRank.REGENT) TitleService.grant(p, MARSHAL);
        ShadowService.sync(p);
    }

    /**
     * Staging: promotes a shadow at once, whatever its level, the master's level or their essence, with its moment.
     * False if it is already at the highest rank its master's shadows reach.
     */
    public static boolean promoteFree(ServerPlayer p, ShadowRecord r) {
        if (r.rank().ordinal() >= ShadowService.top(p).ordinal()) return false;
        promoted(p, r, true);
        return true;
    }

    /** A shadow just reached the top of a rank its master has to promote it out of: the System says so, once. */
    static void becameReady(ServerPlayer p, ShadowRecord r) {
        ShadowRank to = r.rank().next();
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.LEVEL_UP, Component.translatable("shadow.beyondsolo.ready"),
                Component.translatable("shadow.beyondsolo.ready.body", r.displayName(), to.displayName(), to.promotionLevel(), to.promotionCost())));
    }

    // ================================================================ given names

    /**
     * Why this name, as the master typed it, can't be given to this shadow, as the end of a lang key
     * ({@code shadow.beyondsolo.name.<why>}), or null if it can. It is cleaned first ({@link ShadowNames#clean}); an
     * empty one takes a given name back, one that cleans away to nothing is unfit.
     */
    public static @Nullable String nameRefusal(ServerPlayer p, ShadowRecord r, String typed) {
        if (!ShadowService.canRaise(p)) return "not_raiser";
        String name = ShadowNames.clean(typed).strip();
        if (name.isEmpty()) return typed != null && !typed.isBlank() ? "unfit" : r.bonded() ? null : "nothing";
        if (!nameable(r)) return "too_weak";
        if (!ShadowNames.fit(name)) return "unfit";
        String base = ShadowNames.base(name);
        for (ShadowRecord o : ShadowService.army(p).shadows()) {
            if (o != r && o.named() && !o.name().startsWith("#") && ShadowNames.base(o.name()).equals(base)) return "taken";
        }
        if (!r.bonded() && ShadowService.army(p).bonded() >= nameSlots(p)) return "slots";
        return null;
    }

    /**
     * The master gives one of their shadows a name (or, with an empty one, takes back the name they gave it: it is
     * known by its creature again). False, with a pop-up saying why, if it can't be.
     */
    public static boolean name(ServerPlayer p, UUID recordId, String typed) {
        ShadowRecord r = ShadowService.army(p).get(recordId);
        if (r == null) return false;
        String why = nameRefusal(p, r, typed);
        if (why != null) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable("shadow.beyondsolo.name.refused"),
                    Component.translatable("shadow.beyondsolo.name." + why, ShadowNames.MIN_GIVEN, ShadowNames.MAX_GIVEN, nameSlots(p))));
            return false;
        }
        String name = ShadowNames.clean(typed).strip();
        Component before = r.displayName();
        boolean first = !r.bonded();
        r.bestow(name);
        ShadowEntity e = ShadowService.entity(p, r.id());
        if (e != null) e.refresh(r, p);
        if (name.isEmpty()) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("shadow.beyondsolo.unnamed"),
                    Component.translatable("shadow.beyondsolo.unnamed.body", before, r.displayName())));
        } else {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD, Component.translatable("shadow.beyondsolo.named"),
                    Component.translatable(first ? "shadow.beyondsolo.named.body" : "shadow.beyondsolo.renamed.body", before, name)));
            if (e != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShadowFxPayload(ShadowFxPayload.Kind.NAMED.ordinal(), e.getId(),
                        (float) e.getX(), (float) e.getY(), (float) e.getZ(), r.rank().color(), 0, r.wireName()));
                p.serverLevel().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
            }
            TitleService.grant(p, NAMER);
        }
        ShadowService.sync(p);
        return true;
    }

    private ShadowPromotions() {}
}
