package com.beyondsolo.chronicle.journal;

import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The five families the journal sorts the gates' creatures into, and that the slayer titles are earned against: beasts,
 * arthropods, the undead, the humanoid tribes, and arcane things (spirits, constructs and growths born of mana).
 */
public enum CreatureFamily {
    BEAST(0xFFE0A060),
    ARTHROPOD(0xFFB8D85A),
    UNDEAD(0xFF8FE3D0),
    HUMANOID(0xFFE07F6A),
    ARCANE(0xFFB48CFF);

    private final int color;

    CreatureFamily(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Beasts". */
    public Component displayName() {
        return Component.translatable("chronicle.beyondsolo.family." + id());
    }

    /** "beasts", for the middle of a sentence. */
    public Component plural() {
        return Component.translatable("chronicle.beyondsolo.family." + id() + ".plural");
    }

    public static CreatureFamily of(MonsterKind kind) {
        return switch (kind) {
            case GLOOMFANG, RIMECLAW, PIT_RAVAGER, DUSKWING, COILFANG -> BEAST;
            case BROODLING, SHARDBACK, HIVE_DRONE, HIVE_REAVER, CRAGCRAWLER -> ARTHROPOD;
            case GRAVE_THRALL, BONE_ARCHER, WAILING_WRAITH -> UNDEAD;
            case HOLLOW_ACOLYTE, ASHEN_SENTINEL, GUTTERKIN, RIME_STALKER, TUSKBRUTE, TUSK_SHAMAN, DEEPKIN -> HUMANOID;
            // spirits, constructs, growths, and anything new until it is sorted
            default -> ARCANE;
        };
    }

    /** A gate creature's family, or null for anything else (bosses, vanilla creatures with traits). */
    public static @Nullable CreatureFamily of(LivingEntity e) {
        return e instanceof GateMonsterEntity m ? of(m.kind()) : null;
    }

    public static @Nullable CreatureFamily byId(String id) {
        for (CreatureFamily f : values()) if (f.id().equals(id)) return f;
        return null;
    }
}
