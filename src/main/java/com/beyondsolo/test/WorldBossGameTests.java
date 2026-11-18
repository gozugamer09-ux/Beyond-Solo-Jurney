package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchArmor;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The great threats: world bosses and monarchs, and the monarch armor. Run with {@code ./gradlew runGameTestServer}.
 * Their fights are long and wide; these check what must hold whatever the numbers are (the balancing pass comes
 * last): every kind has its model, skin and every animation its moves play, a phase can't be skipped by one huge
 * blow, nothing heals them, and monarch armor takes its wearer's aura.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldBossGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final String LANG = "/assets/beyondsolo_worldboss/lang/en_us.json";

    /** Every animation a boss plays is in its animation file, and its model and skin are there. */
    @GameTest(template = ARENA)
    public static void everyKindHasItsAssets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> missing = new ArrayList<>();
        for (WorldBossKind kind : WorldBossKind.values()) {
            String asset = kind.asset();
            ColossalBoss boss = WorldBossModule.type(kind).create(level);
            helper.assertTrue(boss != null, kind + " can be made");
            JsonObject anims = json("/assets/beyondsolo/animations/entity/" + asset + ".animation.json");
            helper.assertTrue(anims != null, asset + " has an animation file");
            JsonObject all = anims.getAsJsonObject("animations");
            for (String name : boss.animNames()) {
                if (!all.has("animation.beyondsolo." + asset + "." + name)) missing.add(asset + "." + name);
            }
            if (!exists("/assets/beyondsolo/geo/entity/" + asset + ".geo.json")) missing.add(asset + " model");
            if (!exists("/assets/beyondsolo/textures/entity/" + asset + "_main.png")) missing.add(asset + " skin");
            boss.discard();
        }
        if (!exists("/assets/beyondsolo/textures/entity/titan_molten.png")) missing.add("titan molten skin");
        helper.assertTrue(missing.isEmpty(), "missing: " + missing);
        helper.succeed();
    }

    /** Every name the HUD and the System's pop-ups ask for exists: names, titles, phases, domains, authorities. */
    @GameTest(template = ARENA)
    public static void everyKindIsNamed(GameTestHelper helper) {
        JsonObject lang = json(LANG);
        helper.assertTrue(lang != null, "the great threats have their own lang file");
        List<String> missing = new ArrayList<>();
        for (WorldBossKind kind : WorldBossKind.values()) {
            String k = "worldboss.beyondsolo." + kind.id;
            List<String> keys = new ArrayList<>(List.of(k, k + ".title", k + ".phase.1", k + ".phase.2", k + ".phase.3",
                    "entity.beyondsolo." + kind.asset()));
            if (kind.monarch()) keys.addAll(List.of(k + ".domain", k + ".domain.hint", k + ".authority", k + ".authority.hint"));
            for (String key : keys) if (!lang.has(key)) missing.add(key);
        }
        for (ArmorSets.Set set : MonarchArmor.sets()) {
            for (String key : List.of("set.beyondsolo." + set.name, "perk.beyondsolo." + set.perk, "perk.beyondsolo." + set.perk + ".desc")) {
                if (!lang.has(key)) missing.add(key);
            }
            WorldBossModule.PIECES.get(set.name).forEach(p -> {
                if (!lang.has(p.get().getDescriptionId())) missing.add(p.get().getDescriptionId());
            });
        }
        helper.assertTrue(missing.isEmpty(), "missing lines: " + missing);
        helper.succeed();
    }

    /** One huge blow stops at the next phase's line; the phase then begins, and no health comes back. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aPhaseCannotBeSkipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ColossalBoss boss = spawn(helper, WorldBossKind.STILLNESS);
        float max = boss.getMaxHealth();
        boss.hurt(level.damageSources().generic(), max * 10);
        helper.assertTrue(boss.isAlive(), "a monarch survives a blow ten times its health in its first phase");
        helper.assertTrue(Math.abs(boss.getHealth() - max * WorldBossKind.STILLNESS.phase2) < 1f,
                "the blow stops at the phase line (" + boss.getHealth() + " of " + max + ")");
        float held = boss.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(boss.phase() == 2, "the second phase begins");
            helper.assertTrue(boss.getHealth() <= held + 0.01f, "nothing heals it");
            boss.discard();
        });
    }

    /** In its last phase nothing holds it up: a monarch can be killed (no second life, no immunity). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aMonarchCanDie(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (WorldBossKind kind : WorldBossKind.monarchs()) {
            ColossalBoss boss = spawn(helper, kind);
            boss.forcePhase(level, 3);
            helper.assertTrue(boss.phaseFloor() == 0f, kind + " has no floor in its last phase");
            boss.hurt(level.damageSources().generic(), boss.getMaxHealth() * 2);
            helper.assertTrue(boss.isDeadOrDying(), kind + " dies to a blow greater than its health in its last phase");
            boss.discard();
        }
        helper.succeed();
    }

    /** Its name reads in the threat colours like every System creature's: far stronger than a hunter fresh from the crystal. */
    @GameTest(template = ARENA)
    public static void aGreatThreatReadsFarStronger(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        for (WorldBossKind kind : WorldBossKind.values()) {
            ColossalBoss boss = spawn(helper, kind);
            helper.assertTrue(HiddenLevel.has(boss), kind + " has a hidden level");
            helper.assertTrue(HiddenLevel.threat(boss, p) == HiddenLevel.Threat.FAR_STRONGER,
                    kind + " is far stronger than a new S-rank hunter (level " + HiddenLevel.of(boss) + ")");
            boss.discard();
        }
        helper.succeed();
    }

    /** A piece of a monarch's regalia: monarch grade, above the divine sets, and in its new wearer's aura. */
    @GameTest(template = ARENA)
    public static void monarchArmorTakesItsWearersAura(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        RandomSource r = RandomSource.create(7);
        for (ArmorSets.Set set : MonarchArmor.sets()) {
            helper.assertTrue(set.tier > ArmorSets.DIVINE_TIER, set.name + " is above divine");
            helper.assertTrue(set.requiredLevel == MonarchArmor.LEVEL, set.name + " needs level " + MonarchArmor.LEVEL);
            ItemStack piece = MonarchArmor.piece(p, set, r);
            helper.assertTrue(MonarchArmor.is(piece), "a piece of " + set.name + " is monarch armor");
            helper.assertTrue(piece.get(ModComponents.GRADE.get()) == ItemGrade.MONARCH, set.name + " is of the monarch grade");
            helper.assertTrue(MonarchArmor.aura(piece) == PlayerClass.MAGE.color(), set.name + " glows in a mage's colour");
            helper.assertTrue(piece.getItem() instanceof SystemArmorItem a && a.set() == set, "the piece belongs to " + set.name);
        }
        // never worn: the monarchs' own violet
        ItemStack fresh = new ItemStack(WorldBossModule.PIECES.get(MonarchArmor.STORMS.name).get(1).get());
        helper.assertTrue(MonarchArmor.aura(fresh) == MonarchArmor.DEFAULT_AURA, "an unworn piece glows violet");
        // a hunter without a class has the monarchs' violet too
        ServerPlayer plain = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "plain_hunter"));
        SystemAPI.reset(plain);
        helper.assertTrue(MonarchArmor.auraOf(plain) == MonarchArmor.DEFAULT_AURA, "no class, no colour of one's own");
        helper.succeed();
    }

    /** The regalia a monarch leaves is its own, and a hunter is handed a piece they don't have yet. */
    @GameTest(template = ARENA)
    public static void aMonarchLeavesItsOwnRegalia(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        helper.assertTrue(MonarchArmor.setOf(WorldBossKind.STILLNESS) == MonarchArmor.STILLNESS, "Veyrith leaves the regalia of stillness");
        helper.assertTrue(MonarchArmor.setOf(WorldBossKind.HUNGER) == MonarchArmor.HUNGER, "Khorvane leaves the regalia of hunger");
        helper.assertTrue(MonarchArmor.setOf(WorldBossKind.STORMS) == MonarchArmor.STORMS, "Thyrax leaves the regalia of storms");
        RandomSource r = RandomSource.create(3);
        // wearing three of the four pieces: the fourth is the one handed over
        var pieces = WorldBossModule.PIECES.get(MonarchArmor.HUNGER.name);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS};
        for (int i = 0; i < 3; i++) p.setItemSlot(slots[i], new ItemStack(pieces.get(i).get()));
        for (int k = 0; k < 8; k++) {
            ItemStack got = MonarchArmor.piece(p, MonarchArmor.HUNGER, r);
            helper.assertTrue(got.is(pieces.get(3).get()), "the missing piece is the one given (" + got + ")");
        }
        helper.succeed();
    }

    // ================================================================ helpers

    private static ColossalBoss spawn(GameTestHelper helper, WorldBossKind kind) {
        ServerLevel level = helper.getLevel();
        ColossalBoss boss = WorldBossModule.type(kind).create(level);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boss.setupStats();
        boss.setNoAi(false);
        level.addFreshEntity(boss);
        boss.wakeNow();
        return boss;
    }

    private static ServerPlayer hunter(GameTestHelper helper, PlayerClass cls) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "monarch_hunter"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        for (EquipmentSlot slot : EquipmentSlot.values()) p.setItemSlot(slot, ItemStack.EMPTY);
        p.getInventory().clearContent();
        SystemAPI.awaken(p, PlayerRank.S, cls);
        return p;
    }

    private static boolean exists(String path) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject json(String path) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private WorldBossGameTests() {}
}
