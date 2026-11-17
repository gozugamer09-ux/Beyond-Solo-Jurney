package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.armory.ArmorCatalog;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.skill.RuneArts;
import com.beyondsolo.system.PlayerRank;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** The gate's creatures: who lives behind a gate, what they share, and the moves that need the world to check. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreatureGameTests {
    private static final String ARENA = "evaluation_zone";

    private static GateMonsterEntity spawn(GameTestHelper helper, MonsterKind kind, int x) {
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, 2));
        var mob = Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(kind), at, EnemyTraits.NONE, -1);
        helper.assertTrue(mob instanceof GateMonsterEntity, kind + " spawns");
        GateMonsterEntity m = (GateMonsterEntity) mob;
        Enemies.applySize(m, 1f);
        m.setNoAi(false);
        return m;
    }

    /** No vanilla monster behind any gate: every pool, every gate's mix and the awakening trial hold the gate's own. */
    @GameTest(template = ARENA)
    public static void gatesHoldOnlyTheirOwnCreatures(GameTestHelper helper) {
        EnumSet<MonsterKind> lives = EnumSet.noneOf(MonsterKind.class);
        for (PlayerRank rank : PlayerRank.values()) {
            for (EnemyRoster.Entry e : EnemyRoster.pool(rank)) {
                MonsterKind kind = EnemyRoster.kindOf(e.type().get());
                helper.assertTrue(kind != null, "only the gate's own creatures in the " + rank + " pool: " + EntityType.getKey(e.type().get()));
                lives.add(kind);
            }
            for (long seed = 0; seed < 60; seed++) {
                EnemyRoster.Mix mix = EnemyRoster.forSeed(rank, seed);
                for (EntityType<?> t : mix.types()) helper.assertTrue(EnemyRoster.kindOf(t) != null, "a gate's mix is all its own: " + EnemyRoster.describe(mix));
                helper.assertTrue(mix.types().stream().anyMatch(EnemyRoster::isFront), "a mix always has one that fights up close: " + EnemyRoster.describe(mix));
                helper.assertTrue(mix.types().size() >= 3 && mix.types().size() <= 5, "three to five kinds: " + EnemyRoster.describe(mix));
            }
        }
        for (EntityType<?> t : EnemyRoster.trial().types()) helper.assertTrue(EnemyRoster.kindOf(t) != null, "the awakening trial's dead are the gate's own");
        List<MonsterKind> missing = new ArrayList<>();
        for (MonsterKind k : MonsterKind.values()) if (!lives.contains(k)) missing.add(k);
        helper.assertTrue(missing.isEmpty(), "every creature lives behind some gate: missing " + missing);
        helper.succeed();
    }

    /** The later kinds share an original's drops: material, rune art and armour sets. */
    @GameTest(template = ARENA)
    public static void everyKindSharesAnOriginalsDrops(GameTestHelper helper) {
        for (MonsterKind k : MonsterKind.values()) {
            MonsterKind kin = k.kin();
            helper.assertTrue(kin.kin() == kin && kin.ordinal() < 6, k + " is kin to one of the six originals: " + kin);
            helper.assertTrue(RuneArts.of(k) != null && RuneArts.of(k) == RuneArts.of(kin), k + " carries its kin's rune");
            helper.assertTrue(!ArmorCatalog.forCreature(k).isEmpty() && ArmorCatalog.forCreature(k).equals(ArmorCatalog.forCreature(kin)), k + " leaves its kin's sets");
            helper.assertTrue(CraftingModule.MATERIALS.get(kin) != null, k + " leaves its kin's material");
        }
        helper.succeed();
    }

    /** Every creature has its model, every animation its moves play, seven skins and a name. */
    @GameTest(template = ARENA)
    public static void everyCreatureHasItsAssets(GameTestHelper helper) {
        for (MonsterKind k : MonsterKind.values()) {
            helper.assertTrue(exists("/assets/beyondsolo/geo/entity/" + k.id + ".geo.json"), k + " has a model");
            JsonObject anims = json("/assets/beyondsolo/animations/entity/" + k.id + ".animation.json");
            helper.assertTrue(anims != null, k + " has animations");
            JsonObject all = anims.getAsJsonObject("animations");
            List<String> needed = new ArrayList<>(List.of("idle", "walk", "death", k.attackAnim));
            if (k.specialAnim != null) needed.add(k.specialAnim);
            for (String a : needed) helper.assertTrue(all.has("animation.beyondsolo." + k.id + "." + a), k + " has its " + a + " animation");
            for (Affinity a : Affinity.values()) {
                helper.assertTrue(exists("/assets/beyondsolo/textures/entity/" + k.id + "_" + a.texture + ".png"), k + " has its " + a.texture + " skin");
            }
            helper.assertTrue(exists("/assets/beyondsolo/models/item/" + k.id + "_spawn_egg.json"), k + " has a spawn egg model");
        }
        JsonObject en = json("/assets/beyondsolo_enemies/lang/en_us.json");
        helper.assertTrue(en != null, "the roster has English names");
        for (MonsterKind k : MonsterKind.values()) helper.assertTrue(en.has("entity.beyondsolo." + k.id), k + " has a name");
        helper.succeed();
    }

    /** A grave thrall gets up once; finished while it is down, it stays down; the second time it stays down. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void graveThrallRisesOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateMonsterEntity rises = spawn(helper, MonsterKind.GRAVE_THRALL, 1);
        GateMonsterEntity finished = spawn(helper, MonsterKind.GRAVE_THRALL, 3);
        rises.hurt(level.damageSources().generic(), 10_000f);
        helper.assertTrue(rises.isAlive() && rises.downed() > 0 && rises.hasRisen(), "a thrall goes down instead of dying: hp " + rises.getHealth() + " downed " + rises.downed());
        finished.hurt(level.damageSources().generic(), 10_000f);
        finished.invulnerableTime = 0;
        finished.hurt(level.damageSources().generic(), 4f);
        helper.assertTrue(!finished.isAlive(), "finished while it is down, it stays down");
        helper.runAfterDelay(GateMonsterEntity.DOWN_TICKS + 5, () -> {
            helper.assertTrue(rises.isAlive() && rises.downed() == 0 && rises.getHealth() >= rises.getMaxHealth() * 0.49f,
                    "it gets back up with half its health: " + rises.getHealth() + " / " + rises.getMaxHealth());
            rises.invulnerableTime = 0;
            rises.hurt(level.damageSources().generic(), 10_000f);
            helper.assertTrue(!rises.isAlive(), "the second time it stays down");
            rises.discard();
            finished.discard();
            helper.succeed();
        });
    }

    /** The thorn spitter never moves: no pace, no knockback, nothing pushes it. */
    @GameTest(template = ARENA)
    public static void thornSpitterIsRooted(GameTestHelper helper) {
        GateMonsterEntity spitter = spawn(helper, MonsterKind.THORN_SPITTER, 2);
        spitter.setDeltaMovement(0, 0, 0);
        spitter.knockback(2.0, 1, 0);
        helper.assertTrue(spitter.getDeltaMovement().horizontalDistanceSqr() < 1e-6, "knockback does not move it: " + spitter.getDeltaMovement());
        helper.assertTrue(!spitter.isPushable(), "nothing pushes it");
        helper.assertTrue(spitter.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "it has no pace");
        spitter.discard();
        helper.succeed();
    }

    /** The gate's creatures never hurt one another (an archer's stray arrow, a brute's slam); other harm still lands. */
    @GameTest(template = ARENA)
    public static void creaturesNeverHurtEachOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateMonsterEntity archer = spawn(helper, MonsterKind.BONE_ARCHER, 1);
        GateMonsterEntity brute = spawn(helper, MonsterKind.TUSKBRUTE, 3);
        float hp = brute.getHealth();
        helper.assertTrue(!brute.hurt(level.damageSources().mobAttack(archer), 6f) && brute.getHealth() == hp, "a creature's hit does nothing to another");
        helper.assertTrue(brute.hurt(level.damageSources().generic(), 2f) && brute.getHealth() < hp, "other harm still lands");
        archer.discard();
        brute.discard();
        helper.succeed();
    }

    /** Flyers fly, and drop out of the air when they die. */
    @GameTest(template = ARENA)
    public static void flyersFallWhenKilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (MonsterKind k : MonsterKind.values()) {
            if (!k.flies()) continue;
            GateMonsterEntity m = spawn(helper, k, 2);
            helper.assertTrue(m.isNoGravity(), k + " flies");
            m.hurt(level.damageSources().generic(), 10_000f);
            helper.assertTrue(!m.isAlive() && !m.isNoGravity(), k + " falls when it dies");
            m.discard();
        }
        helper.succeed();
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

    private CreatureGameTests() {}
}
