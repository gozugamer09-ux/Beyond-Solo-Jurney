package com.beyondsolo.tower;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A boss rush: the gate bosses one after another at a rank, with short breathers between them. The six ranked tiers
 * bring all six bosses of the roster in a random order, the last one ascended. The Sovereign rush, opened by the
 * Sigil of Dominion, brings all six ascended at S rank and then the Citadel's lord himself.
 */
public enum RushTier {
    E(PlayerRank.E), D(PlayerRank.D), C(PlayerRank.C), B(PlayerRank.B), A(PlayerRank.A), S(PlayerRank.S),
    SOVEREIGN(PlayerRank.S);

    /** One challenger of a rush: a gate boss (kind set) or the lord (kind null). */
    public record Bout(BossKind kind, boolean ascended) {
        public boolean lord() {
            return kind == null;
        }
    }

    public final PlayerRank rank;

    RushTier(PlayerRank rank) {
        this.rank = rank;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean sovereign() {
        return this == SOVEREIGN;
    }

    public Component title() {
        return sovereign() ? Component.translatable("tower.beyondsolo.rush.sovereign")
                : Component.translatable("tower.beyondsolo.rush.tier", rank.displayName());
    }

    /** How many bosses stand in it. */
    public int bouts() {
        return sovereign() ? BossKind.values().length + 1 : BossKind.values().length;
    }

    /** The order the bosses come in: every boss once, shuffled, the last one ascended (the Sovereign rush: all, then the lord). */
    public List<Bout> order(RandomSource r) {
        List<BossKind> kinds = new ArrayList<>(List.of(BossKind.values()));
        for (int i = kinds.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            BossKind t = kinds.get(i);
            kinds.set(i, kinds.get(j));
            kinds.set(j, t);
        }
        List<Bout> out = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) out.add(new Bout(kinds.get(i), sovereign() || i == kinds.size() - 1));
        if (sovereign()) out.add(new Bout(null, true));
        return out;
    }

    /** The highest ranked rush a hunter may enter: one rank above their own, like a dungeon key. */
    public boolean openTo(PlayerRank hunter, boolean sigil) {
        if (sovereign()) return sigil;
        return rank.ordinal() <= hunter.ordinal() + 1;
    }

    public static RushTier byId(String id) {
        for (RushTier t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
