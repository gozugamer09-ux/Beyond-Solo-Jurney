package com.beyondsolo.rune;

import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.network.RuneSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Rune stones: what a stone holds, who can absorb it, what absorbing does, and engravings.
 *
 * <p>A rune stone ({@link RuneModule#RUNE_STONE}) is either <b>inscribed</b> with a skill or <b>sealed</b>. An
 * inscribed stone holds a rune art (the essence of a gate creature or boss, any class can take it) or a class
 * skill (a class rune: only that class can). A sealed stone holds nothing yet: when it shatters it settles on a
 * skill for whoever absorbs it, a rune art or one of their own class's skills, no higher than its grade. Either
 * way it teaches the skill, or raises it if it is already known.</p>
 *
 * <p>Absorbing takes {@link #USE_TICKS} of holding the stone (the stone cracks, light leaks out, then it shatters
 * and the light pours into the player). A stone too far above the player's rank won't answer.</p>
 *
 * <p>Engraving runes ({@link Engraving}) carve one lasting change into a skill the player knows.</p>
 *
 * <p>Numbers are first guesses; the balancing pass tunes them.</p>
 */
public final class Runes {
    /** Ticks of holding a rune stone before it shatters into the player. */
    public static final int USE_TICKS = 40;
    /** A stone can be at most this many grades above the player's rank. */
    public static final int RANK_REACH = 1;
    /** Levels a rune adds to a skill the player already knows. */
    public static final int RAISE_LEVELS = 2;
    /** What the System shop pays for a rune stone, by grade E..S. */
    public static final long[] SELL = {80, 180, 400, 900, 2000, 5000};
    /** What the System shop pays for an engraving rune. */
    public static final long ENGRAVING_SELL = 600;
    /** The glyph of a sealed stone (the ten element glyphs come first). */
    public static final int SEALED_GLYPH = SkillElement.values().length;
    /** Glyph shapes in all: one per element, and the seal. */
    public static final int GLYPHS = SEALED_GLYPH + 1;
    /** What absorbing did (for the shatter effect). */
    public static final int LEARNED = 0, RAISED = 1;

    /** Why a rune stone won't answer. */
    public enum Refusal {
        /** The player has not awakened. */
        ASLEEP,
        /** The stone is too strong for the player's rank. */
        RANK,
        /** A class rune of another class (or subclass). */
        CLASS,
        /** The player has the skill at its highest level already. */
        MASTERED,
        /** A sealed stone with nothing left to give this player. */
        NOTHING;

        public String key() {
            return "rune.beyondsolo.refuse." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Set by the client at setup: the same refusal worked out from what the client knows, so the channel doesn't
     * start when the server will refuse. The server always decides.
     */
    public static BiFunction<Player, ItemStack, Refusal> clientCheck = (p, s) -> null;
    /** Set by the client at setup: opens the engraving screen for the engraving rune in this hand. */
    public static BiConsumer<InteractionHand, Engraving> clientEngrave = (h, e) -> {};

    // ================================================================ stacks

    /** A rune stone inscribed with a skill, at the skill's grade. */
    public static ItemStack of(Skill skill) {
        ItemStack stack = new ItemStack(RuneModule.RUNE_STONE.get());
        stack.set(ModComponents.SKILL.get(), skill.path());
        stack.set(RuneModule.GRADE.get(), skill.grade());
        return stack;
    }

    /** A sealed rune stone of a grade. */
    public static ItemStack sealed(PlayerRank grade) {
        ItemStack stack = new ItemStack(RuneModule.RUNE_STONE.get());
        stack.set(RuneModule.GRADE.get(), grade);
        return stack;
    }

    public static boolean isRune(ItemStack stack) {
        return stack.is(RuneModule.RUNE_STONE.get());
    }

    /** The skill a stone is inscribed with, or null if it is sealed. */
    public static @Nullable Skill skillOf(ItemStack stack) {
        String path = stack.get(ModComponents.SKILL.get());
        return path == null ? null : Skills.get(path);
    }

    /** A rune stone with no (known) skill on it. */
    public static boolean isSealed(ItemStack stack) {
        return isRune(stack) && skillOf(stack) == null;
    }

    /** A stone only one class (or subclass) can absorb. */
    public static boolean isClassRune(ItemStack stack) {
        Skill s = skillOf(stack);
        return s != null && (s.owner() != null || s.subclass() != null);
    }

    public static PlayerRank gradeOf(ItemStack stack) {
        PlayerRank g = stack.get(RuneModule.GRADE.get());
        if (g != null) return g;
        Skill s = skillOf(stack);
        return s != null ? s.grade() : PlayerRank.E;
    }

    /** Which glyph is carved on the stone: the skill's element, or the seal. */
    public static int glyphOf(ItemStack stack) {
        Skill s = skillOf(stack);
        return s == null ? SEALED_GLYPH : s.element().ordinal();
    }

    /** The colour of the stone's light: the skill's, or the grade's for a sealed stone. */
    public static int colorOf(ItemStack stack) {
        Skill s = skillOf(stack);
        return s != null ? s.color() : gradeColor(gradeOf(stack));
    }

    /** A seal's light by grade: pale for E, deep for S. */
    public static int gradeColor(PlayerRank grade) {
        return switch (grade) {
            case E -> 0xC8D4E0;
            case D -> 0x8FE0A0;
            case C -> 0x7FC4FF;
            case B -> 0xC79BFF;
            case A -> 0xFFD27A;
            case S -> 0xFF6A5A;
        };
    }

    /** Gold the System shop pays for one, or -1 if it is not a rune. */
    public static long sellPrice(ItemStack stack) {
        if (isRune(stack)) return SELL[gradeOf(stack).tier()];
        if (stack.getItem() instanceof EngravingRuneItem) return ENGRAVING_SELL;
        return -1;
    }

    // ================================================================ who can absorb what

    public static @Nullable Refusal refusal(ServerPlayer p, ItemStack stack) {
        Skill s = skillOf(stack);
        // a skill only some may hold (the shadow raisers' Umbral Exchange) is as good as another class's to the rest
        if (s != null && SystemAPI.get(p).isAwakened() && !s.allows(p)) return Refusal.CLASS;
        return refusal(SystemAPI.get(p), JobAPI.subclass(p), SkillAPI.get(p), stack);
    }

    /** Why this player can't absorb the stone, or null if they can. */
    public static @Nullable Refusal refusal(SystemData sys, @Nullable Subclass sub, SkillData skills, ItemStack stack) {
        if (!sys.isAwakened()) return Refusal.ASLEEP;
        PlayerRank grade = gradeOf(stack);
        if (grade.tier() > sys.rank().tier() + RANK_REACH) return Refusal.RANK;
        Skill s = skillOf(stack);
        if (s == null) return pool(sys.playerClass(), sub, skills, grade).isEmpty() ? Refusal.NOTHING : null;
        if (!s.fits(sys.playerClass(), sub)) return Refusal.CLASS;
        if (skills.level(s.path()) >= Skill.MAX_LEVEL) return Refusal.MASTERED;
        return null;
    }

    /** The lowest rank that can absorb a stone of this grade. */
    public static PlayerRank rankNeeded(PlayerRank grade) {
        return PlayerRank.values()[Math.max(0, grade.tier() - RANK_REACH)];
    }

    /** The message for a refusal. */
    public static Component message(Refusal r, ItemStack stack) {
        Skill s = skillOf(stack);
        return switch (r) {
            case RANK -> Component.translatable(r.key(), rankNeeded(gradeOf(stack)).name());
            case CLASS -> Component.translatable(r.key(), s == null ? Component.literal("?")
                    : s.subclass() != null ? s.subclass().displayName() : s.owner() != null ? s.owner().displayName() : Component.literal("?"));
            case MASTERED -> Component.translatable(r.key(), s == null ? Component.literal("?") : s.displayName());
            default -> Component.translatable(r.key());
        };
    }

    /** One skill a sealed stone could settle on, and how likely. */
    public record Pick(Skill skill, int weight) {}

    /**
     * What a sealed stone of this grade can settle on for a player of this class: rune arts and the class's own
     * skills (not the job change's), no higher than the grade, not already mastered. Rune arts weigh more, and
     * skills of the stone's own grade twice as much.
     */
    public static List<Pick> pool(@Nullable PlayerClass c, @Nullable Subclass sub, SkillData skills, PlayerRank grade) {
        List<Pick> out = new ArrayList<>();
        for (Skill s : Skills.all()) {
            if (s.grade().tier() > grade.tier() || s.advanced() || s.gated()) continue;
            if (c == null ? s.owner() != null || s.subclass() != null : !s.fits(c, sub)) continue;
            if (skills.level(s.path()) >= Skill.MAX_LEVEL) continue;
            int w = s.rune() ? 3 : 2;
            if (s.grade() == grade) w *= 2;
            out.add(new Pick(s, w));
        }
        return out;
    }

    /** Settles a sealed stone on a skill for this player (null if nothing is left). */
    public static @Nullable Skill roll(ServerPlayer p, PlayerRank grade, RandomSource r) {
        List<Pick> pool = pool(SystemAPI.get(p).playerClass(), JobAPI.subclass(p), SkillAPI.get(p), grade);
        int total = 0;
        for (Pick k : pool) total += k.weight();
        if (total <= 0) return null;
        int pick = r.nextInt(total);
        for (Pick k : pool) {
            pick -= k.weight();
            if (pick < 0) return k.skill();
        }
        return pool.get(0).skill();
    }

    // ================================================================ absorbing

    /** Starting to hold a stone: true if it may go on, else the player is told why not. */
    public static boolean begin(ServerPlayer p, ItemStack stack) {
        Refusal r = refusal(p, stack);
        if (r == null) return true;
        refuse(p, stack, r);
        return false;
    }

    /** Tells the player the stone won't answer: a puff of dust, the channel stops, a line on the action bar. */
    public static void refuse(ServerPlayer p, ItemStack stack, Refusal r) {
        RuneFx.self(p, RuneFxPayload.Kind.FIZZLE, colorOf(stack), r.ordinal());
        p.displayClientMessage(message(r, stack), true);
        sound(p, SoundEvents.BASALT_BREAK, 0.8f, 0.6f);
    }

    /**
     * The stone shatters into the player: teaches its skill, or raises it by {@link #RAISE_LEVELS} if known. A sealed
     * stone settles on a skill first. Returns true if it took (the caller uses the stone up).
     */
    public static boolean absorb(ServerPlayer p, ItemStack stack) {
        Refusal r = refusal(p, stack);
        if (r != null) {
            refuse(p, stack, r);
            return false;
        }
        PlayerRank grade = gradeOf(stack);
        Skill s = skillOf(stack);
        if (s == null) s = roll(p, grade, p.getRandom());
        if (s == null) {
            refuse(p, stack, Refusal.NOTHING);
            return false;
        }
        int outcome;
        if (SkillAPI.knows(p, s)) {
            if (SkillAPI.addLevels(p, s, RAISE_LEVELS) <= 0) {
                refuse(p, stack, Refusal.MASTERED);
                return false;
            }
            outcome = RAISED;
        } else {
            if (!SkillAPI.learn(p, s, 1, SkillEvent.Source.RUNE)) return false;
            outcome = LEARNED;
        }
        data(p).countAbsorbed();
        sync(p);
        int extra = s.element().ordinal() + 16 * grade.tier() + 256 * outcome;
        RuneFx.play(p, RuneFxPayload.Kind.SHATTER, s.color(), p.position(), p.getLookAngle(), 1, 0, extra);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 0.7f);
        sound(p, SoundEvents.GLASS_BREAK, 0.7f, 0.55f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.4f);
        if (outcome == RAISED) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                    Component.translatable("rune.beyondsolo.notify.raised.title"),
                    Component.translatable("rune.beyondsolo.notify.raised.body", s.displayName(), SkillAPI.level(p, s))));
        }
        return true;
    }

    // ================================================================ engravings

    public static RuneData data(Player p) {
        return p.getData(RuneModule.RUNES);
    }

    public static @Nullable Engraving engraving(Player p, Skill s) {
        return data(p).engraving(s.path());
    }

    /** A skill's mana cost for this player, engraving and mana efficiency (armor sets) included. */
    public static float manaCost(Player p, Skill s, int level) {
        return manaCost(data(p), s, level) * com.beyondsolo.combat.ModAttributes.manaFactor(p);
    }

    public static float manaCost(RuneData data, Skill s, int level) {
        float cost = s.manaCost(level);
        Engraving e = data.engraving(s.path());
        return e == null ? cost : cost * e.mana;
    }

    /** A skill's cooldown for this player in ticks, engraving and skill haste (armor sets) included. */
    public static int cooldown(Player p, Skill s, int level) {
        int cd = cooldown(data(p), s, level);
        float haste = com.beyondsolo.combat.ModAttributes.hasteFactor(p);
        return haste >= 1f ? cd : Math.max(1, Math.round(cd * haste));
    }

    public static int cooldown(RuneData data, Skill s, int level) {
        int cd = s.cooldown(level);
        Engraving e = data.engraving(s.path());
        return e == null ? cd : Math.max(1, Math.round(cd * e.cooldown));
    }

    /** What an engraving does to a skill's hits. */
    public static float damageMultiplier(Player p, Skill s) {
        Engraving e = engraving(p, s);
        return e == null ? 1f : e.damage;
    }

    /** Why this engraving can't go on this skill (a lang key), or null if it can. */
    public static @Nullable String engraveRefusal(Player p, @Nullable Skill s, Engraving e) {
        if (!SystemAPI.isAwakened(p)) return Refusal.ASLEEP.key();
        if (s == null || !SkillAPI.knows(p, s)) return "rune.beyondsolo.engrave.unknown";
        if (!s.isActive()) return "rune.beyondsolo.engrave.passive";
        if (engraving(p, s) == e) return "rune.beyondsolo.engrave.same";
        return null;
    }

    /** Carves the engraving rune held in a hand (0 main, 1 off) into a skill. Returns true if it took. */
    public static boolean engrave(ServerPlayer p, int hand, String path) {
        InteractionHand h = hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = p.getItemInHand(h);
        if (!(held.getItem() instanceof EngravingRuneItem rune)) return false;
        Engraving e = rune.engraving();
        Skill s = Skills.get(path);
        String no = engraveRefusal(p, s, e);
        if (no != null || s == null) {
            RuneFx.self(p, RuneFxPayload.Kind.ENGRAVE_REFUSED, e.color, e.ordinal());
            p.displayClientMessage(Component.translatable(no != null ? no : "rune.beyondsolo.engrave.unknown"), true);
            return false;
        }
        Engraving old = engraving(p, s);
        data(p).engrave(s.path(), e);
        if (!p.isCreative()) held.shrink(1);
        sync(p);
        SkillAPI.sync(p);
        RuneFx.play(p, RuneFxPayload.Kind.ENGRAVED, e.color, p.position(), Vec3.ZERO, 1, 0, e.ordinal() + 16 * (old == null ? 0 : old.ordinal() + 1));
        sound(p, SoundEvents.SMITHING_TABLE_USE, 0.8f, 0.8f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
        sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.7f);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable("rune.beyondsolo.notify.engraved.title"),
                Component.translatable(old == null ? "rune.beyondsolo.notify.engraved.body" : "rune.beyondsolo.notify.engraved.replaced",
                        e.displayName(), s.displayName(), e.effect(), old == null ? Component.empty() : old.displayName())));
        return true;
    }

    /** Takes an engraving off (commands). */
    public static void clearEngraving(ServerPlayer p, Skill s) {
        data(p).engrave(s.path(), null);
        sync(p);
        SkillAPI.sync(p);
    }

    /** Clears every engraving and the absorbed count (commands, tests). */
    public static void reset(ServerPlayer p) {
        data(p).clear();
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new RuneSyncPayload(data(p).serializeNBT(p.registryAccess())));
    }

    static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private Runes() {}
}
