package com.beyondsolo.skill;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Hooks for the game tests of how skills feel (SkillFeel, SkillSounds). Not for gameplay code. */
public final class SkillFeelTestAccess {
    /** {@link SkillFeel#weigh}: the weight of one blow. */
    public static byte weigh(byte stature, float fraction, boolean killed, boolean repeat, boolean big, boolean ultimate) {
        return SkillFeel.weigh(stature, fraction, killed, repeat, big, ultimate);
    }

    /** The weight queued this tick for the hunter's blows on the creature, with its flags shifted 8 up, or -1. */
    public static int pending(ServerPlayer p, LivingEntity e) {
        return SkillFeel.pending(p, e);
    }

    /** How many blow payloads have gone out since the server started. */
    public static int sent() {
        return SkillFeel.sent;
    }

    /** Runs {@code r} as if a skill's action were running it (its vanilla sounds are swapped for the skills' own). */
    public static void inSkill(Runnable r) {
        SkillSounds.enter();
        try {
            r.run();
        } finally {
            SkillSounds.exit();
        }
    }

    /** {@link SkillSounds#swapFor}: the skills' own sound for a vanilla one, or null. */
    public static @Nullable Holder<SoundEvent> swapFor(SoundEvent sound) {
        return SkillSounds.swapFor(sound);
    }

    private SkillFeelTestAccess() {}
}
