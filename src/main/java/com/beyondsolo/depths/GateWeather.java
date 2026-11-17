package com.beyondsolo.depths;

import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The air inside a gate: most caves are still, others are full of mist, heat, snow, a mana storm, spores, a crimson
 * haze, a darkness that eats light, or tremors. Each condition favours one element and dulls another (skill hits and
 * element damage: infusion procs, burns, venom), most bring a hazard ({@link WeatherService}), and every one but
 * clear pays more for the clear. The client draws it: fog, the air itself, flashes, and a chip under the run clock.
 */
public enum GateWeather {
    CLEAR(0xCFE3FF, 0x000000, 0f, null, 0f, null, 0f, 1f),
    /** Thick mist: you see a few blocks. Water skills are stronger, fire fizzles. */
    MIST(0xC8D6E0, 0x8C98A2, 15f, SkillElement.WATER, 0.20f, SkillElement.FIRE, 0.15f, 1.10f),
    /** Searing heat: fire buildup while out of water. Fire is stronger, water steams away. */
    SCORCHING(0xFF8A3A, 0x4A1E0C, 34f, SkillElement.FIRE, 0.25f, SkillElement.WATER, 0.15f, 1.15f),
    /** A frost gale: chill slows. Water is stronger, fire is smothered. */
    BLIZZARD(0xDDF2FF, 0xAEBFD0, 22f, SkillElement.WATER, 0.25f, SkillElement.FIRE, 0.20f, 1.15f),
    /** A mana storm: bolts strike where they are marked. Lightning is stronger, earth is shaken. */
    STORM(0xB89CFF, 0x221A40, 36f, SkillElement.LIGHTNING, 0.30f, SkillElement.EARTH, 0.10f, 1.20f),
    /** Toxic spores: venom buildup. Nature is stronger, light is choked. */
    MIASMA(0x9BE05A, 0x26401A, 24f, SkillElement.NATURE, 0.25f, SkillElement.LIGHT, 0.10f, 1.15f),
    /** A crimson haze: the cave's creatures hit harder and drink from their hits. Shadow is stronger, light dims. */
    CRIMSON(0xFF4048, 0x420A10, 30f, SkillElement.SHADOW, 0.25f, SkillElement.LIGHT, 0.15f, 1.25f),
    /** A darkness that eats light: you see almost nothing, creatures are quicker. Light skills cut it, shadow drowns in it. */
    DARKNESS(0x8A7AC8, 0x040308, 11f, SkillElement.LIGHT, 0.30f, SkillElement.SHADOW, 0.20f, 1.20f),
    /** Tremors: quakes shake the cave and bring rocks down where they are marked. Earth is stronger, air is beaten down. */
    TREMORS(0xD9B36A, 0x3E3020, 34f, SkillElement.EARTH, 0.30f, SkillElement.AIR, 0.15f, 1.20f);

    /** The condition's colour (chip, flashes). */
    public final int color;
    /** Fog colour, and how far the fog lets you see (blocks; 0 keeps the cave's own). */
    public final int fogColor;
    public final float fogFar;
    @Nullable public final SkillElement boosted;
    public final float boost;
    @Nullable public final SkillElement dulled;
    public final float dull;
    /** Multiplier on the clear's XP and gold. */
    public final float reward;

    GateWeather(int color, int fogColor, float fogFar, @Nullable SkillElement boosted, float boost, @Nullable SkillElement dulled, float dull, float reward) {
        this.color = color;
        this.fogColor = fogColor;
        this.fogFar = fogFar;
        this.boosted = boosted;
        this.boost = boost;
        this.dulled = dulled;
        this.dull = dull;
        this.reward = reward;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("depths.beyondsolo.weather." + id());
    }

    public Component description() {
        return Component.translatable("depths.beyondsolo.weather." + id() + ".desc");
    }

    /** How much a hit of this element does in this weather. */
    public float factor(@Nullable SkillElement element) {
        if (element == null) return 1f;
        if (element == boosted) return 1f + boost;
        if (element == dulled) return 1f - dull;
        return 1f;
    }

    public static GateWeather byIndex(int i) {
        GateWeather[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    @Nullable
    public static GateWeather byId(String id) {
        for (GateWeather w : values()) if (w.id().equals(id)) return w;
        return null;
    }

    /** The weather a theme's caves lean towards (three times as likely as any other). */
    public static GateWeather themed(GateTheme theme) {
        return switch (theme) {
            case E -> MIST;
            case D -> MIASMA;
            case C -> BLIZZARD;
            case B -> STORM;
            case A -> SCORCHING;
            case S, TRIAL -> CRIMSON;
        };
    }

    /** The weather a realm's dungeons lean towards (the six original caves lean as their ranks did). */
    public static GateWeather themed(GateRealm realm) {
        return switch (realm) {
            case HOLLOWS, MOSSWEPT_RUINS, SUNKEN_SANCTUM -> MIST;
            case MIREWOOD, CHITIN_HIVE, SPOREFALL_DEEP -> MIASMA;
            case AZURE_GROTTO, RIMEFROST -> BLIZZARD;
            case GLOOMSHADE, STORMSPIRE -> STORM;
            case EMBERFORGE, SUNSCORCHED_TOMB, INFERNAL_CITADEL -> SCORCHING;
            case CRIMSON_ABYSS, THRESHOLD -> CRIMSON;
            case BONE_CATACOMBS, STARLESS_EXPANSE -> DARKNESS;
            case ROOTBOUND_DEN, CRYSTAL_SEAM, IRONBOUND_KEEP -> TREMORS;
        };
    }

    /** Chance of a still cave, by rank: half the E-rank caves, a quarter of the S-rank ones. */
    public static float clearChance(PlayerRank rank) {
        return 0.5f - 0.05f * rank.tier();
    }

    /** A gate's weather, from its seed: the same gate always has the same sky. */
    public static GateWeather roll(long seed, PlayerRank rank, GateTheme theme) {
        return roll(seed, rank, themed(theme));
    }

    /** A gate's weather, from its seed, leaning toward {@code themed} (three times as likely as any other). */
    public static GateWeather roll(long seed, PlayerRank rank, GateWeather themed) {
        RandomSource r = new LegacyRandomSource(seed ^ 0x5745415448455221L);
        if (r.nextFloat() < clearChance(rank)) return CLEAR;
        int total = (values().length - 2) + 3;
        int pick = r.nextInt(total);
        for (GateWeather w : values()) {
            if (w == CLEAR) continue;
            pick -= w == themed ? 3 : 1;
            if (pick < 0) return w;
        }
        return themed;
    }
}
