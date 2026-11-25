package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.SkillCastPayload;
import com.beyondsolo.network.SkillWindupPayload;
import com.beyondsolo.skill.Gestures;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The caster's body when a skill goes off: a short gesture through playerAnimator (the library Better Combat ships)
 * that fits what the skill shows. A cut swings the blade across, a slam brings both hands down, a stomp drives a
 * foot into the ground, a bolt is thrown from the hand, a heal is prayed for, an arrow is drawn and loosed, a trap
 * is set down... The server says which effect the cast showed first ({@link SkillCastPayload}); the gesture is picked
 * from that and the skill's class. Everyone who sees the caster sees it, and the caster sees their own arms do it in
 * first person. Gestures live in {@code assets/beyondsolo/player_animations/skill_*.json}, written by
 * {@code tools/gen_skill_anims.py}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillGestures {
    private static final ResourceLocation LAYER = BeyondSolo.id("skill");
    /** Above Better Combat's attacks (about 1000), under the weapon arts' body layer (2000), runes and keys. */
    private static final int PRIORITY = 1950;

    /** Each player's skill layer runs through a speed control, so the body can hang on a blow (see {@link #hold}). */
    private static final java.util.Map<AbstractClientPlayer, SpeedModifier> SPEED = new java.util.WeakHashMap<>();
    /** Until when (on the UI clock) each player's body hangs. */
    private static final java.util.Map<AbstractClientPlayer, Double> HELD = new java.util.WeakHashMap<>();

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            SpeedModifier speed = new SpeedModifier(1f);
            SPEED.put(player, speed);
            return new ModifierLayer<>(null, speed);
        });
    }

    /**
     * The player's skill gesture hangs for {@code seconds}, as a blade hangs in what it cut: the other half of a
     * hitstop ({@link SkillImpacts}). Only a gesture still playing is held; it then carries on where it stopped.
     */
    static void hold(AbstractClientPlayer player, float seconds) {
        SpeedModifier speed = SPEED.get(player);
        if (speed == null || seconds <= 0) return;
        double until = com.beyondsolo.client.gfx.Anim.now() + seconds;
        HELD.merge(player, until, Math::max);
        speed.speed = 0.02f;
    }

    /** Lets go of bodies whose hold is over. */
    @SubscribeEvent
    static void release(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        if (HELD.isEmpty()) return;
        double now = com.beyondsolo.client.gfx.Anim.now();
        var it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue() > now) continue;
            SpeedModifier speed = SPEED.get(e.getKey());
            if (speed != null) speed.speed = 1f;
            it.remove();
        }
    }

    // ================================================================ the wind-up and the blow

    /**
     * A strike waiting for its tick after a wind-up: the gesture, timed so its blow lands as the skill goes off, or,
     * for a skill that takes its own time first ({@code late}), as its first blow does.
     */
    private static final class Strike {
        final String gesture;
        final String skill;
        final boolean late;
        int ticks;

        Strike(String gesture, String skill, int ticks, boolean late) {
            this.gesture = gesture;
            this.skill = skill;
            this.ticks = ticks;
            this.late = late;
        }
    }

    private static final java.util.Map<AbstractClientPlayer, Strike> STRIKES = new java.util.WeakHashMap<>();
    /** The skill each player's wind-up last struck for, and on which client tick, so its cast doesn't swing again. */
    private static final java.util.Map<AbstractClientPlayer, Object[]> STRUCK = new java.util.WeakHashMap<>();
    private static long ticks;

    /**
     * A hunter winds up for a skill ({@link SkillWindupPayload}): the body draws back into the pose the gesture strikes
     * from and holds it while the power gathers ({@link SkillCharge}), then the gesture plays so its blow lands on the
     * tick the skill goes off. An ultimate's call and cut-in come now, at the start ({@link SkillImpacts#windup}).
     */
    public static void windup(SkillWindupPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        Skill skill = Skills.get(p.skill());
        if (!(e instanceof AbstractClientPlayer player) || skill == null) return;
        String gesture = Gestures.pick(skill, p.look(), p.variant());
        // a skill with a stance of its own goes off first and lands its blow a moment later: the body holds till then
        int blow = p.ticks() + p.delay();
        int hold = blow - Gestures.strike(gesture);
        SkillCharge.begin(player, skill, gesture, blow);
        SkillImpacts.windup(player, skill, gesture, blow);
        SkillDomains.windup(player, skill, blow);
        if (hold > 0) {
            play(player, gesture + "_windup");
            STRIKES.put(player, new Strike(gesture, skill.path(), hold, p.delay() > 0));
        } else {
            strike(player, gesture, skill.path());
        }
    }

    /** The blow after a wind-up starts: the gesture itself, the charge bursting into a glint. */
    private static void strike(AbstractClientPlayer player, String gesture, String skill) {
        STRIKES.remove(player);
        play(player, gesture);
        STRUCK.put(player, new Object[]{skill, ticks});
        SkillCharge.release(player);
        SkillImpacts.swing(player, gesture);
    }

    @SubscribeEvent
    static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ticks++;
        if (STRIKES.isEmpty()) return;
        for (var entry : new java.util.ArrayList<>(STRIKES.entrySet())) {
            Strike s = entry.getValue();
            if (--s.ticks <= 0) strike(entry.getKey(), s.gesture, s.skill);
        }
    }

    public static void cast(SkillCastPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        Skill skill = Skills.get(p.skill());
        if (!(e instanceof AbstractClientPlayer player) || skill == null) return;
        String gesture = pick(skill, p.look(), p.variant());
        // after a wind-up the blow has already started (a moment early, so it lands now): no second swing
        Strike waiting = STRIKES.get(player);
        if (waiting != null && waiting.skill.equals(p.skill())) {
            // went off, its blow still to come: the body keeps its stance
            if (waiting.late && p.look().isEmpty()) return;
            strike(player, waiting.gesture, waiting.skill);
        }
        Object[] struck = STRUCK.remove(player);
        boolean wound = struck != null && struck[0].equals(p.skill()) && ticks - (long) struck[1] <= 12;
        if (!wound) play(player, gesture);
        castLight(player, skill, gesture);
        SkillImpacts.cast(player, skill, gesture, wound);
        SkillDomains.cast(player, skill);
    }

    /** The gesture for a skill that showed {@code look} first. */
    static String pick(Skill skill, String look, int variant) {
        return Gestures.pick(skill, look, variant);
    }

    /** Plays {@code skill_<gesture>} on the player's skill layer. */
    static boolean play(AbstractClientPlayer player, String gesture) {
        IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (!(stored instanceof ModifierLayer<?> raw)) return false;
        var playable = PlayerAnimationRegistry.getAnimation(BeyondSolo.id("skill_" + gesture));
        if (playable == null) {
            BeyondSolo.LOGGER.warn("Missing skill gesture {}", gesture);
            return false;
        }
        IActualAnimation<?> anim = playable.playAnimation();
        if (gesture.startsWith("spin")) {
            // a full turn of the body would carry the arms round behind the eyes: the wielder keeps their own view
            anim.setFirstPersonMode(FirstPersonMode.NONE);
        } else {
            anim.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            anim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, false));
        }
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        // a spin starts a whole turn round: fading into it from a wind-up would turn the body twice
        if (gesture.equals("spin")) layer.setAnimation(null);
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), anim);
        return true;
    }

    /** Light gathering in the hands of a spell's caster as it leaves them; a weapon skill shows nothing here. */
    private static void castLight(AbstractClientPlayer player, Skill skill, String gesture) {
        boolean spell = switch (gesture) {
            case "cast", "cast_both", "raise", "channel", "burst", "point", "pray", "ground", "summon" -> true;
            default -> false;
        };
        if (!spell) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 view = player.getViewVector(1);
        Vec3 right = view.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-4 ? Vec3.ZERO : right.normalize();
        int c = skill.color();
        // the hands: in front of the chest, the casting hand a little to the right (further out in first person)
        Vec3 hand = player.getEyePosition().add(view.scale(self ? 0.9 : 0.55)).add(right.scale(self ? 0.35 : 0.3)).add(0, self ? -0.35 : -0.45, 0);
        if (gesture.equals("raise")) hand = player.position().add(0, player.getBbHeight() + 0.5, 0).add(view.scale(0.2));
        if (gesture.equals("pray")) hand = player.getEyePosition().add(view.scale(self ? 0.8 : 0.45)).add(0, self ? -0.45 : -0.55, 0);
        FxParticles.motesBurst(hand.x, hand.y, hand.z, self ? 6 : 10, 0.09f, c);
        if (!self) FxParticles.glow(hand.x, hand.y, hand.z, 0.45f, c, 8, 0.8f);
    }

    private SkillGestures() {}
}
