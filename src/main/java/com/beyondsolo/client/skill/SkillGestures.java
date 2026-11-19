package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.SkillCastPayload;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
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

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    public static void cast(SkillCastPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        Skill skill = Skills.get(p.skill());
        if (!(e instanceof AbstractClientPlayer player) || skill == null) return;
        String gesture = pick(skill, p.look(), p.variant());
        play(player, gesture);
        castLight(player, skill, gesture);
    }

    /** The gesture for a skill that showed {@code look} first. */
    static String pick(Skill skill, String look, int variant) {
        PlayerClass owner = skill.owner();
        boolean melee = owner == PlayerClass.WARRIOR || owner == PlayerClass.ASSASSIN || owner == PlayerClass.GUARDIAN;
        boolean heavy = owner == PlayerClass.WARRIOR || owner == PlayerClass.GUARDIAN;
        if (look.startsWith("rune:")) {
            return switch (look.substring(5)) {
                case "BREATH", "DREAD", "HOWL" -> "roar";
                case "FLURRY" -> "slash";
                case "IMPACT" -> "slam";
                case "SIGIL" -> "point";
                case "WEB" -> "place";
                case "WALL" -> "guard";
                case "SWARM" -> "summon";
                case "SHARDS" -> "burst";
                default -> "cast";
            };
        }
        SkillFxPayload.Kind kind = null;
        for (SkillFxPayload.Kind k : SkillFxPayload.Kind.values()) if (k.name().equals(look)) kind = k;
        if (kind == null) return byClass(owner);
        return switch (kind) {
            case SLASH -> variant == 1 ? "slash_back" : "slash";
            case DASH -> "lunge";
            case BLINK, VEIL, EXCHANGE -> "vanish";
            // the hand nobody sees: reached out to take, thrown with the arm, pushed out with both palms
            case GRASP -> "point";
            case HURL -> "throw";
            case FORCE -> "cast_both";
            case SLAM, CRACK -> melee ? "slam" : "ground";
            case QUAKE -> "stomp";
            case ROAR -> "roar";
            case AURA -> melee || owner == PlayerClass.RANGER ? "power" : "channel";
            case ZONE, VORTEX, MIST -> "channel";
            case HEAL, HALO, WINGS, BUBBLE, ORBIT -> "pray";
            case BOLT, ORB, BALL, STREAM -> "cast";
            case SPLASH -> owner == PlayerClass.GUARDIAN ? "lunge" : melee ? "slash" : owner == PlayerClass.RANGER ? "bow" : "cast";
            case BEAM -> "cast_both";
            case SHOT -> owner == PlayerClass.RANGER ? "bow" : melee ? "throw" : "cast";
            case ARROW, GALE, SEEKER -> "bow";
            case SKYSHOT -> "bow_sky";
            case DOME, SHIELD -> "guard";
            case NOVA, PULSE -> heavy ? "stomp" : "burst";
            case BLOSSOM, SPIN -> "spin";
            case MARK, SIGHT, BIND -> "point";
            // fire rolls out from a swept hand, rock spikes are called up from the ground, a charge leaves a wake, wind is cut loose
            case WAVE -> switch (variant) {
                case 1 -> melee ? "slam" : "ground";
                case 2 -> "lunge";
                case 3 -> "slash";
                default -> melee ? "slash" : "sweep";
            };
            case BEAST, SWARM -> "summon";
            case FAN, DISCUS -> "throw_side";
            case PILLAR, METEOR, RAIN, THUNDER, CLOUD -> "raise";
            case CLEAVE -> melee ? "chop" : "raise";
            case HAMMER, CHAIN, NET -> "throw";
            case TRAP, WIRE, ROPE -> "place";
            case UPDRAFT -> "leap";
            case STATIC, QUIVER, BLADES -> "power";
            case WINDWALL -> "sweep";
            case CLAW -> "claw";
            case GEYSER -> owner == PlayerClass.ASSASSIN ? "slash" : melee ? "slam" : "ground";
            case BANNER -> "slam";
            // the casting circle and element marks say nothing about the body; a kind added later falls back the same way
            default -> byClass(owner);
        };
    }

    private static String byClass(PlayerClass owner) {
        if (owner == null) return "cast";
        return switch (owner) {
            case WARRIOR, ASSASSIN -> "slash";
            case GUARDIAN -> "guard";
            case HEALER -> "pray";
            case RANGER -> "bow";
            case MAGE -> "cast";
        };
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
        if (gesture.equals("spin")) {
            // a full turn of the body would carry the arms round behind the eyes: the wielder keeps their own view
            anim.setFirstPersonMode(FirstPersonMode.NONE);
        } else {
            anim.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            anim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, false));
        }
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
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
