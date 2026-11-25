package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The skills' own sounds ({@code assets/beyondsolo/sounds/skill/}, made by {@code tools/gen_skill_sounds.py}): what each
 * element sounds like as it leaves the hunter and as it lands, the weight under a heavy blow, the crack of a felled
 * creature, an ultimate's call.
 *
 * <p>The cast and impact sounds are played by each player's own game when a skill goes off or lands
 * ({@code client.skill.SkillImpacts}). The skills' older vanilla sounds (a TNT blast, an anvil, a beacon, a crossbow)
 * are swapped here for the skills' own while a skill's action, or something it scheduled, is running: the skills keep
 * their timing and volume, and sound like themselves.</p>
 */
public final class SkillSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BeyondSolo.MOD_ID);

    private static final Map<SkillElement, DeferredHolder<SoundEvent, SoundEvent>> IMPACT = new EnumMap<>(SkillElement.class);
    private static final Map<SkillElement, DeferredHolder<SoundEvent, SoundEvent>> CAST = new EnumMap<>(SkillElement.class);

    static {
        for (SkillElement e : SkillElement.values()) {
            String name = e.name().toLowerCase(java.util.Locale.ROOT);
            IMPACT.put(e, sound("skill.impact." + name));
            CAST.put(e, sound("skill.cast." + name));
        }
    }

    /** The weight under a heavy blow: a deep thump. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY = sound("skill.impact.heavy");
    /** Under a massive blow: a boom that rolls on. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MASSIVE = sound("skill.impact.massive");
    /** A creature felled by a skill: a sharp, ringing crack. */
    public static final DeferredHolder<SoundEvent, SoundEvent> KILL = sound("skill.impact.kill");
    /** An ultimate is called: a rising rush and a struck chord. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTIMATE = sound("skill.ultimate");
    /** A blade cut through the air (in place of the vanilla sweep). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING = sound("skill.swing");
    /** A skill's blast (in place of the TNT explosion). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST = sound("skill.blast");
    /** Something brought down on the ground (in place of the anvil). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound("skill.slam");
    /** A shot loosed (in place of the crossbow and bow). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT = sound("skill.shot");
    /** A skill's thunder (in place of the trident's and the lightning bolt's). */
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER = sound("skill.thunder");
    /** Power gathering (in place of the beacon's). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("skill.charge");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BeyondSolo.id(name)));
    }

    public static SoundEvent impact(SkillElement element) {
        return IMPACT.get(element).get();
    }

    public static SoundEvent cast(SkillElement element) {
        return CAST.get(element).get();
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        NeoForge.EVENT_BUS.addListener(SkillSounds::swap);
        // what a skill schedules runs later, outside its action: it takes the skill's sounds along
        ServerScheduler.carry(task -> depth <= 0 ? task : () -> {
            enter();
            try {
                task.run();
            } finally {
                exit();
            }
        });
    }

    // ---- while a skill runs (server thread) ----

    /** How deep in skill actions (and what they scheduled) the server thread is. */
    private static int depth;

    static void enter() {
        depth++;
    }

    static void exit() {
        if (depth > 0) depth--;
    }

    /** True while a skill's action, or a task it scheduled, is running on the server thread. */
    public static boolean inSkill() {
        return depth > 0;
    }

    private static void swap(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel().isClientSide() || depth <= 0 || event.getSource() != SoundSource.PLAYERS || event.getSound() == null) return;
        Holder<SoundEvent> to = swapFor(event.getSound().value());
        if (to == null) return;
        event.setSound(to);
        // the vanilla sounds were pitched far up or down to pass for a skill's; the skills' own need far less of it
        event.setNewPitch(1 + (event.getNewPitch() - 1) * 0.35f);
    }

    /** The skill's own sound for a vanilla one a skill plays, or null to keep it. */
    static @Nullable Holder<SoundEvent> swapFor(SoundEvent sound) {
        if (sound == SoundEvents.GENERIC_EXPLODE.value()) return BLAST;
        if (sound == SoundEvents.ANVIL_LAND) return SLAM;
        if (sound == SoundEvents.BEACON_ACTIVATE || sound == SoundEvents.BEACON_POWER_SELECT) return CHARGE;
        if (sound == SoundEvents.TRIDENT_THUNDER.value() || sound == SoundEvents.LIGHTNING_BOLT_THUNDER
                || sound == SoundEvents.LIGHTNING_BOLT_IMPACT) return THUNDER;
        if (sound == SoundEvents.PLAYER_ATTACK_SWEEP) return SWING;
        if (sound == SoundEvents.CROSSBOW_SHOOT || sound == SoundEvents.ARROW_SHOOT) return SHOT;
        return null;
    }

    private SkillSounds() {}
}
