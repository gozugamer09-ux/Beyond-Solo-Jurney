package com.beyondsolo.client.chronicle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.journal.JournalCategory;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.client.gfx.Icons;
import net.minecraft.resources.ResourceLocation;

/** The chronicle's icons (signed distance fields like every System icon; the new ones come from tools/gen_chronicle_assets.py). */
public final class ChronicleIcons {
    public static final ResourceLocation RANKINGS = icon("chronicle_rankings");
    public static final ResourceLocation JOURNAL = icon("chronicle_journal");
    public static final ResourceLocation SWORD = icon("chronicle_sword");
    public static final ResourceLocation CASTLE = icon("chronicle_castle");
    public static final ResourceLocation SCROLL = icon("chronicle_scroll");
    public static final ResourceLocation REALM = icon("chronicle_realm");
    public static final ResourceLocation BEAST = icon("family_beast");
    public static final ResourceLocation ARTHROPOD = icon("family_arthropod");
    public static final ResourceLocation UNDEAD = icon("family_undead");
    public static final ResourceLocation ARCANE = icon("family_arcane");
    public static final ResourceLocation MIGHT = icon("perk_might");
    public static final ResourceLocation ELEMENT = icon("element_arcane");

    public static ResourceLocation of(TitleCategory c) {
        return switch (c) {
            case HUNT -> SWORD;
            case GATES -> Icons.SPIRAL;
            case CITADEL -> CASTLE;
            case SYSTEM -> SCROLL;
            case MASTERY -> Icons.SPARKLE;
        };
    }

    public static ResourceLocation of(CreatureFamily f) {
        return switch (f) {
            case BEAST -> BEAST;
            case ARTHROPOD -> ARTHROPOD;
            case UNDEAD -> UNDEAD;
            case HUMANOID -> Icons.VILLAGERS;
            case ARCANE -> ARCANE;
        };
    }

    public static ResourceLocation of(JournalCategory c) {
        return switch (c) {
            case CREATURES -> BEAST;
            case BOSSES -> Icons.SKULL;
            case REALMS -> REALM;
            case ARSENAL -> SWORD;
        };
    }

    public static ResourceLocation of(TitlePerk p) {
        return switch (p) {
            case XP -> Icons.STAR;
            case GOLD -> Icons.COIN;
            case CREATURE_DAMAGE -> SWORD;
            case BOSS_DAMAGE -> Icons.SKULL;
            case FAMILY_DAMAGE -> SWORD;
            case GIANT_DAMAGE -> Icons.FLAME;
            case GUARD, ARMOR -> Icons.SHIELD;
            case LAST_STAND -> Icons.HEART_BREAK;
            case MIGHT -> MIGHT;
            case VITALITY -> Icons.HEART;
            case SWIFTNESS -> Icons.RUN;
            case LUCK -> Icons.DIAMOND;
            case ELEMENTAL -> ELEMENT;
            case SPELL -> ARCANE;
            case FOCUS -> Icons.DROP;
            case HASTE -> Icons.HOURGLASS;
            case STAMINA -> Icons.WEIGHT;
        };
    }

    private static ResourceLocation icon(String name) {
        return BeyondSolo.id("textures/gui/icon/" + name + ".png");
    }

    private ChronicleIcons() {}
}
