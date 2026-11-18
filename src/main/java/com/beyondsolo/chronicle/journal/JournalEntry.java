package com.beyondsolo.chronicle.journal;

import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * One page of the journal. {@link #id} is how the hunter's {@code ChronicleData} remembers it ({@code creature:gloomfang},
 * {@code boss:knight}, {@code realm:hollows}, {@code arsenal:knightsbane}). Its name comes from the thing itself; its
 * lore is a lang line of the chronicle's ({@code chronicle.beyondsolo.lore.<category>.<name>}), except a weapon's, which
 * is the weapon's own {@code .lore} line.
 */
public record JournalEntry(String id, JournalCategory category, String name, @Nullable MonsterKind creature,
                           @Nullable BossKind boss, @Nullable GateRealm realm, @Nullable ResourceLocation item, @Nullable CreatureFamily family) {

    public static JournalEntry creature(MonsterKind kind) {
        return new JournalEntry("creature:" + kind.id, JournalCategory.CREATURES, kind.id, kind, null, null, null, CreatureFamily.of(kind));
    }

    public static JournalEntry boss(BossKind kind) {
        return new JournalEntry("boss:" + kind.id, JournalCategory.BOSSES, kind.id, null, kind, null, null, null);
    }

    /** A boss with no kind (the Arbiter, the Citadel's lord, and whatever comes later). */
    public static JournalEntry boss(String name) {
        return new JournalEntry("boss:" + name, JournalCategory.BOSSES, name, null, null, null, null, null);
    }

    public static JournalEntry realm(GateRealm realm) {
        return new JournalEntry("realm:" + realm.id(), JournalCategory.REALMS, realm.id(), null, null, realm, null, null);
    }

    /** A place that is no gate's cave (the red gates' frozen world, the Citadel, the Penalty Zone). */
    public static JournalEntry place(String name) {
        return new JournalEntry("realm:" + name, JournalCategory.REALMS, name, null, null, null, null, null);
    }

    public static JournalEntry weapon(ResourceLocation item) {
        return new JournalEntry("arsenal:" + item.getPath(), JournalCategory.ARSENAL, item.getPath(), null, null, null, item, null);
    }

    public Component displayName() {
        if (creature != null) return Component.translatable("entity.beyondsolo." + creature.id);
        if (realm != null) return realm.displayName();
        if (item != null) {
            Item i = BuiltInRegistries.ITEM.get(item);
            return i.getDescription();
        }
        return Component.translatable("chronicle.beyondsolo.name." + category.id() + "." + name);
    }

    /** The chronicle's own lang key for its lore. */
    public String loreKey() {
        return "chronicle.beyondsolo.lore." + category.id() + "." + name;
    }

    /** Where its lore may be, best first: a weapon's own {@code .lore} line, else the chronicle's. */
    public java.util.List<String> loreKeys() {
        if (item != null) return java.util.List.of("item." + item.getNamespace() + "." + item.getPath() + ".lore", loreKey());
        return java.util.List.of(loreKey());
    }

    /** A boss's forms: one per rank it can be met at (the brute and its kin change their names with the rank). */
    public boolean hasForms() {
        return boss != null;
    }

    /** The bit a boss's form takes in {@code Mark.forms}: its rank, or {@link #ASCENDED_BIT}. */
    public static int formBit(PlayerRank rank) {
        return rank.ordinal();
    }

    public static final int ASCENDED_BIT = 6;
}
