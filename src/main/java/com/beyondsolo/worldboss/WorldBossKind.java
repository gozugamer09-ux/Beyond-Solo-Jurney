package com.beyondsolo.worldboss;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The world's great threats. Three tiers:
 * <ul>
 *   <li><b>World bosses</b> ({@link Tier#WORLD}): colossal creatures that climb out of the earth somewhere in the
 *   overworld, roam the land round where they rose, and fight in three phases. The System calls every hunter to
 *   them.</li>
 *   <li><b>Monarchs</b> ({@link Tier#MONARCH}): rulers from beyond the gates. A monarch comes down out of a gate torn
 *   open in the sky, lays its domain over the land, brings its army, and fights with powers no gate boss has. A
 *   monarch can be beaten: no immunity, no second life, no regeneration; it is simply stronger than anything else.</li>
 *   <li><b>Rulers</b> ({@link Tier#RULER}): beings of light, the monarchs' enemies since before the gates
 *   ({@code com.beyondsolo.ruler}). A ruler comes down through a rift of light to make war on a monarch that has landed,
 *   or to pass sentence on a hunter who took a crown; it spares whoever does not stand against it. Beatable like a
 *   monarch: no immunity, no second life, no healing.</li>
 * </ul>
 * Names and looks are this mod's own.
 */
public enum WorldBossKind {
    /** Ghorrath, the Walking Mountain: a giant of basalt and magma carrying a mountain ridge on its back. */
    TITAN("titan", Tier.WORLD, Affinity.FLAME, DamageElement.FIRE, 0xFF8A3A, 6.4f, 1.4f, 0.66f, 0.33f),
    /** Thurvok, the Horned Calamity: a beast the size of a house with four great horns and a back of bone plates. */
    BEHEMOTH("behemoth", Tier.WORLD, Affinity.BLOOD, DamageElement.BLOOD, 0xFF3A44, 5.2f, 1.2f, 0.66f, 0.33f),
    /** Veyrith, Monarch of Stillness: a queen of ice with a crystal spear; where she rules, everything slows. */
    STILLNESS("stillness", Tier.MONARCH, Affinity.FROST, null, 0x9BE7FF, 5.5f, 1.2f, 0.6f, 0.25f),
    /** Khorvane, Monarch of Hunger: a feral king in a wolf's skull with blades for claws, faster the longer he hunts. */
    HUNGER("hunger", Tier.MONARCH, Affinity.BLOOD, DamageElement.BLOOD, 0xFF2E46, 5.2f, 1.15f, 0.6f, 0.25f),
    /** Thyrax, Monarch of Storms: a winged lord in storm-dark plate with a glaive of lightning. */
    STORMS("storms", Tier.MONARCH, Affinity.STORM, null, 0xB9A6FF, 5.2f, 1.2f, 0.6f, 0.25f),
    /** Aurenthe, Ruler of the Verdict: a knight of white gold under a crown of light, a greatsword of light in his hands. */
    VERDICT("verdict", Tier.RULER, Affinity.NONE, null, 0xFFD878, 5.2f, 1.15f, 0.6f, 0.25f),
    /** Caelistor, Ruler of a Thousand Wings: four wings of white steel and a lance of light; the sky is hers. */
    WINGS("wings", Tier.RULER, Affinity.NONE, null, 0xFFF0B8, 4.8f, 1.15f, 0.6f, 0.25f),
    /** Ithrael, Ruler of the Unseen Hand: a masked one in robes who never touches the ground, and never needs to. */
    HAND("hand", Tier.RULER, Affinity.NONE, null, 0xF6E2A8, 5.0f, 1.1f, 0.6f, 0.25f);

    public enum Tier {
        WORLD(0xFFB347), MONARCH(0xC77DFF), RULER(0xFFE7A0);

        public final int color;

        Tier(int color) {
            this.color = color;
        }

        public Component label() {
            return Component.translatable("worldboss.beyondsolo.tier." + name().toLowerCase(Locale.ROOT));
        }
    }

    public final String id;
    public final Tier tier;
    public final Affinity affinity;
    /** The buildup its hits leave (null: its own effect instead, see the boss). */
    @Nullable public final DamageElement element;
    /** Its light: the beacon, the bar's frame, its warnings on the ground. */
    public final int color;
    /**
     * Health and damage as multiples of an S-rank gate boss's ({@link Balance#bossHealth}, {@link Balance#bossDamage}),
     * so they move with the power curve. First guesses for the balancing pass.
     */
    public final float healthScale;
    public final float damageScale;
    /** Health fractions where phase II and phase III begin. */
    public final float phase2;
    public final float phase3;

    WorldBossKind(String id, Tier tier, Affinity affinity, @Nullable DamageElement element, int color, float healthScale, float damageScale,
                  float phase2, float phase3) {
        this.id = id;
        this.tier = tier;
        this.affinity = affinity;
        this.element = element;
        this.color = color;
        this.healthScale = healthScale;
        this.damageScale = damageScale;
        this.phase2 = phase2;
        this.phase3 = phase3;
    }

    /** Its max health (about 85,000 to 105,000 on today's curve). */
    public float health() {
        return Balance.bossHealth(PlayerRank.S) * healthScale;
    }

    /** The damage of its plain blow (about 560 to 680 on today's curve); each move is a multiple of it. */
    public float damage() {
        return Balance.bossDamage(PlayerRank.S) * damageScale;
    }

    public boolean monarch() {
        return tier == Tier.MONARCH;
    }

    public boolean ruler() {
        return tier == Tier.RULER;
    }

    /**
     * Whether it comes from beyond the world (a monarch or a ruler): down out of the sky, with a domain (a ruler's
     * sanctum), an authority and a host of its own.
     */
    public boolean sovereign() {
        return tier != Tier.WORLD;
    }

    /** The name its entity, model, animations and skin are filed under ("titan", "monarch_stillness", "ruler_verdict"). */
    public String asset() {
        return switch (tier) {
            case MONARCH -> "monarch_" + id;
            case RULER -> "ruler_" + id;
            default -> id;
        };
    }

    /** Its name ("Ghorrath"). */
    public Component bossName() {
        return Component.translatable("worldboss.beyondsolo." + id);
    }

    /** Its title ("the Walking Mountain", "Monarch of Stillness"). */
    public Component title() {
        return Component.translatable("worldboss.beyondsolo." + id + ".title");
    }

    /** Name and title together ("Ghorrath, the Walking Mountain"). */
    public Component fullName() {
        return Component.translatable("worldboss.beyondsolo.named", bossName(), title());
    }

    /** The name of a phase (1..3). */
    public Component phaseName(int phase) {
        return Component.translatable("worldboss.beyondsolo." + id + ".phase." + Math.max(1, Math.min(3, phase)));
    }

    /** A monarch's domain ("Stillness"), a ruler's sanctum; world bosses have none. */
    public Component domainName() {
        return Component.translatable("worldboss.beyondsolo." + id + ".domain");
    }

    /** A monarch's authority, its last and greatest power ("Absolute Zero"). */
    public Component authorityName() {
        return Component.translatable("worldboss.beyondsolo." + id + ".authority");
    }

    public static WorldBossKind[] worldBosses() {
        return new WorldBossKind[]{TITAN, BEHEMOTH};
    }

    public static WorldBossKind[] monarchs() {
        return new WorldBossKind[]{STILLNESS, HUNGER, STORMS};
    }

    public static WorldBossKind[] rulers() {
        return new WorldBossKind[]{VERDICT, WINGS, HAND};
    }

    @Nullable
    public static WorldBossKind byId(String id) {
        for (WorldBossKind k : values()) if (k.id.equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) return k;
        return null;
    }

    public static WorldBossKind byIndex(int i) {
        WorldBossKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
