package com.beyondsolo.skill;

import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Umbral Exchange: the necromancer and one of their shadows trade places at once, through the dark (the series' shadow
 * exchange). Pressed looking at one of their shadows, they swap with it. Pressed looking anywhere else, they swap with
 * the shadow that lies the way they look: a guard at its post, however far away in this world and whether or not its
 * spot is loaded, or a shadow out in the world that they can see. The shadow rises where they stood (a guard keeps
 * guarding, from that spot now) and they step out of the dark where it was.
 *
 * <p>Only those who raise shadows hold it ({@link Skill#gated()}): the necromancer, and a monarch of the Night.</p>
 */
public final class UmbralExchange {
    /** How far off the line they look along (degrees) a shadow or a guard's post may lie and still be the one meant. */
    static final double CONE = 22;

    /** How far away a shadow out in the world can be picked by looking at it. */
    public static float sight(int l) { return 48 + 4 * (l - 1); }

    public static boolean cast(ServerPlayer p, Skill s, int l) {
        if (!ShadowService.canRaise(p)) {
            p.displayClientMessage(Component.translatable("skill.beyondsolo.umbral_exchange.no_army"), true);
            return false;
        }
        ShadowRecord r = pick(p, l);
        if (r == null) {
            p.displayClientMessage(Component.translatable(ShadowService.army(p).shadows().isEmpty()
                    ? "skill.beyondsolo.umbral_exchange.no_army" : "skill.beyondsolo.umbral_exchange.none"), true);
            return false;
        }
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position();
        if (!ShadowService.exchange(p, r.id())) {
            p.displayClientMessage(Component.translatable("skill.beyondsolo.umbral_exchange.none"), true);
            return false;
        }
        Vec3 to = p.position();
        SkillFx.note(SkillFxPayload.Kind.EXCHANGE, 0);
        fx(p, level, from, s);
        fx(p, level, to, s);
        for (Vec3 at : new Vec3[]{from, to}) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.45f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 0.55f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
        return true;
    }

    /**
     * The shadow the hunter means: the one of theirs they look at, else the one that lies closest to the line they look
     * along (a guard at its post anywhere in this world, or a shadow out in the world they can see), or null.
     */
    static @Nullable ShadowRecord pick(ServerPlayer p, int l) {
        LivingEntity seen = SkillActions.aim(p, sight(l), e -> e instanceof ShadowEntity sh && mine(p, sh));
        if (seen instanceof ShadowEntity sh) return ShadowService.army(p).get(sh.recordId());
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        ShadowRecord best = null;
        double bestDot = Math.cos(Math.toRadians(CONE));
        long now = p.level().getGameTime();
        for (ShadowRecord r : ShadowService.army(p).shadows()) {
            if (r.recovering(now)) continue;
            ShadowEntity e = ShadowService.entity(p, r.id());
            Vec3 at;
            if (e != null && e.isAlive()) {
                if (!mine(p, e)) continue;
                // one following along has to be seen: no trading places through a wall into a sealed room
                if (!r.posted() && (e.distanceTo(p) > sight(l) || !p.hasLineOfSight(e))) continue;
                at = e.position();
            } else if (r.postedIn(p.level().dimension()) && !ShadowService.army(p).isActive(r.id())) {
                at = r.post();
            } else {
                continue;
            }
            Vec3 to = at.add(0, 1, 0).subtract(eye);
            double d = to.length();
            if (d < 1.5) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    /** One of this hunter's shadows standing in the world, not the mount they sit on. */
    private static boolean mine(ServerPlayer p, ShadowEntity e) {
        return ShadowService.isOwn(p, e) && e != p.getVehicle() && !e.isSinking();
    }

    /** One end of the trade: seen by whoever is near that spot. */
    private static void fx(ServerPlayer p, ServerLevel level, Vec3 at, Skill s) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 64, new SkillFxPayload(p.getId(), SkillFxPayload.Kind.EXCHANGE,
                s.color() & 0xFFFFFF, (float) at.x, (float) at.y, (float) at.z, 1.3f, 24, SkillElement.SHADOW.ordinal()));
    }

    private UmbralExchange() {}
}
