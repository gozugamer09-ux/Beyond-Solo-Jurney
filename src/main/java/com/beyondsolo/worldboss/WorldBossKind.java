package com.beyondsolo.worldboss;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.enemy.Affinity;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The world's great threats. Two tiers:
 * <ul>
 *   <li><b>World bosses</b> ({@link Tier#WORLD}): colossal creatures that climb out of the earth somewhere in the
 *   overworld, roam the land round where they rose, and fight in three phases. The System calls every hunter to
 *   them.</li>
 *   <li><b>Monarchs</b> ({@link Tier#MONARCH}): rulers from beyond the gates. A monarch comes down out of a gate torn
 *   open in the sky, lays its domain over the land, brings its army, and fights with powers no gate boss has. A
 *   monarch can be beaten: no immunity, no second life, no regeneration; it is simply stronger than anything else.</li>
 * </ul>
 * Names and looks are this mod's own.
 */
public enum WorldBossKind {
    /** Ghorrath, the Walking Mountain: a giant of basalt and magma carrying a mountain ridge on its back. */
    TITAN("titan", Tier.WORLD, Affinity.FLAME, DamageElement.FIRE, 0xFF8A3A, 4200f, 34f, 0.66f, 0.33f),
    /** Thurvok, the Horned Calamity: a beast the size of a house with four great horns and a back of bone plates. */
    BEHEMOTH("behemoth", Tier.WORLD, Affinity.BLOOD, DamageElement.BLOOD, 0xFF3A44, 3400f, 30f, 0.66f, 0.33f),
    /** Veyrith, Monarch of Stillness: a queen of ice with a crystal spear; where she rules, everything slows. */
    STILLNESS("stillness", Tier.MONARCH, Affinity.FROST, null, 0x9BE7FF, 3600f, 30f, 0.6f, 0.25f),
    /** Khorvane, Monarch of Hunger: a feral king in a wolf's skull with blades for claws, faster the longer he hunts. */
    HUNGER("hunger", Tier.MONARCH, Affinity.BLOOD, DamageElement.BLOOD, 0xFF2E46, 3400f, 28f, 0.6f, 0.25f),
    /** Thyrax, Monarch of Storms: a winged lord in storm-dark plate with a glaive of lightning. */
    STORMS("storms", Tier.MONARCH, Affinity.STORM, null, 0xB9A6FF, 3400f, 30f, 0.6f, 0.25f);

    public enum Tier {
        WORLD(0xFFB347), MONARCH(0xC77DFF);

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
    /** Health and damage before the balancing pass (first guesses). */
    public final float health;
    public final float damage;
    /** Health fractions where phase II and phase III begin. */
    public final float phase2;
    public final float phase3;

    WorldBossKind(String id, Tier tier, Affinity affinity, @Nullable DamageElement element, int color, float health, float damage,
                  float phase2, float phase3) {
        this.id = id;
        this.tier = tier;
        this.affinity = affinity;
        this.element = element;
        this.color = color;
        this.health = health;
        this.damage = damage;
        this.phase2 = phase2;
        this.phase3 = phase3;
    }

    public boolean monarch() {
        return tier == Tier.MONARCH;
    }

    /** The name its entity, model, animations and skin are filed under ("titan", "monarch_stillness"). */
    public String asset() {
        return monarch() ? "monarch_" + id : id;
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

    /** A monarch's domain ("Stillness"); world bosses have none. */
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
