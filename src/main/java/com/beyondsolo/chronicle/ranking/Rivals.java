package com.beyondsolo.chronicle.ranking;

import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The rival hunters on the rankings, from the world's strongest S-ranks down to an E-rank who started the same week as
 * the player. Every name is our own. Their numbers are a stand-in until the hunter association brings the national
 * rankings (and are balanced with everything else at the end). Each stands near their rank's home level on the power
 * curve ({@code system.Balance#homeLevel}: E 3, D 13, C 26, B 41, A 56, S 74), and even the best of them stays below
 * the level cap however long the world runs, so a hunter at the cap can take first place.
 */
public final class Rivals {
    public static final String AZURE_TIDE = "azure_tide";
    public static final String IRON_BASTION = "iron_bastion";
    public static final String SUNFALL = "sunfall";
    public static final String RED_LANTERN = "red_lantern";
    /** No guild. */
    public static final String NONE = "";

    // best boss rush times in seconds, E D C B A S Sovereign (0: never cleared)
    private static final int[] RUSH_S = {150, 175, 215, 265, 330, 420, 0};
    private static final int[] RUSH_S_TOP = {140, 160, 195, 240, 300, 380, 640};
    private static final int[] RUSH_A = {190, 230, 280, 345, 430, 0, 0};
    private static final int[] RUSH_B = {245, 295, 365, 455, 0, 0, 0};
    private static final int[] RUSH_C = {305, 385, 470, 0, 0, 0, 0};
    private static final int[] RUSH_D = {390, 480, 0, 0, 0, 0, 0};
    private static final int[] NO_RUSH = {0, 0, 0, 0, 0, 0, 0};

    public static final List<Rival> ALL = List.of(
            new Rival("han_seori", "Han Seo-ri", PlayerRank.S, PlayerClass.MAGE, AZURE_TIDE, 76, 0.06f, 100, 64, RUSH_S_TOP, 910),
            new Rival("viktor_kael", "Viktor Kael", PlayerRank.S, PlayerClass.WARRIOR, IRON_BASTION, 72, 0.06f, 100, 51, RUSH_S, 860),
            new Rival("amara_okafor", "Amara Okafor", PlayerRank.S, PlayerClass.RANGER, SUNFALL, 68, 0.05f, 100, 38, RUSH_S, 720),
            new Rival("ren_takashiro", "Ren Takashiro", PlayerRank.S, PlayerClass.ASSASSIN, NONE, 64, 0.07f, 96, 22, RUSH_S, 640),
            new Rival("ines_calderon", "Inés Calderón", PlayerRank.A, PlayerClass.HEALER, SUNFALL, 61, 0.08f, 78, 0, RUSH_A, 330),
            new Rival("mateo_rivas", "Mateo Rivas", PlayerRank.A, PlayerClass.GUARDIAN, IRON_BASTION, 58, 0.08f, 71, 0, RUSH_A, 290),
            new Rival("yuna_seol", "Yuna Seol", PlayerRank.A, PlayerClass.ASSASSIN, AZURE_TIDE, 54, 0.09f, 64, 0, RUSH_A, 255),
            new Rival("oskar_lind", "Oskar Lind", PlayerRank.A, PlayerClass.MAGE, NONE, 50, 0.09f, 52, 0, RUSH_A, 180),
            new Rival("kwon_daeho", "Kwon Dae-ho", PlayerRank.B, PlayerClass.WARRIOR, RED_LANTERN, 46, 0.1f, 40, 0, RUSH_B, 120),
            new Rival("priya_raman", "Priya Raman", PlayerRank.B, PlayerClass.HEALER, AZURE_TIDE, 42, 0.1f, 33, 0, RUSH_B, 96),
            new Rival("elias_brandt", "Elias Brandt", PlayerRank.B, PlayerClass.RANGER, IRON_BASTION, 38, 0.1f, 27, 0, RUSH_B, 74),
            new Rival("moon_seoyeon", "Moon Seo-yeon", PlayerRank.C, PlayerClass.GUARDIAN, RED_LANTERN, 29, 0.1f, 19, 0, RUSH_C, 48),
            new Rival("lucas_ferreira", "Lucas Ferreira", PlayerRank.C, PlayerClass.ASSASSIN, NONE, 23, 0.1f, 12, 0, RUSH_C, 31),
            new Rival("noah_pierce", "Noah Pierce", PlayerRank.D, PlayerClass.RANGER, RED_LANTERN, 15, 0.08f, 6, 0, RUSH_D, 17),
            new Rival("cho_mina", "Cho Mi-na", PlayerRank.D, PlayerClass.MAGE, NONE, 11, 0.08f, 0, 0, NO_RUSH, 9),
            new Rival("tobias_grell", "Tobias Grell", PlayerRank.E, PlayerClass.WARRIOR, NONE, 4, 0.05f, 0, 0, NO_RUSH, 2));

    /** The guild's name, or "Independent". */
    public static Component guildName(String guild) {
        return Component.translatable(guild == null || guild.isEmpty() ? "chronicle.beyondsolo.guild.none" : "chronicle.beyondsolo.guild." + guild);
    }

    private Rivals() {}
}
