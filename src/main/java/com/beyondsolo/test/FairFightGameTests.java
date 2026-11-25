package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.CombatDamage;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.duel.DuelModule;
import com.beyondsolo.enemy.EnemyHooks;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.guild.GuildModule;
import com.beyondsolo.light.LightSanctum;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchDomain;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.ruler.RulerModule;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.story.StoryModule;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.BalanceHooks;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.test.BalanceLab.Hunter;
import com.beyondsolo.tower.TowerModule;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the final balancing pass promises: a monarch (a crowned hunter, or a monarch from beyond the gates) can fell
 * another monarch and be felled by one; nothing makes a body untouchable (guards keep at least a quarter of a blow,
 * dodges let at most half of blows pass by, a death refused is refused once a cast); healing over time has a ceiling;
 * fire reaches every boss; burns and blood barely scratch the great threats and allies only nick them; the great threats
 * are sized for the hunters who meet them; a domain or a sanctum stands only with its own; a shadow's rank is a real step.
 * The fights are read off the real damage pipeline on the bench ({@link BalanceLab}).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairFightGameTests {
    private static final String ARENA = "evaluation_zone";
    private static final String BATCH = "fair_fights";

    /** A great threat of {@code kind} sized for hunters of {@code level}, awake, with no lucky blows. Not in the world. */
    private static ColossalBoss threat(GameTestHelper helper, WorldBossKind kind, int level) {
        ColossalBoss b = WorldBossModule.create(helper.getLevel(), kind);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        b.setThreatLevel(level);
        b.setupStats();
        b.wakeNow();
        still(b);
        b.setHealth(b.getMaxHealth());
        return b;
    }

    /** No critical hits and no dodges at all, the gear's included. */
    private static void still(LivingEntity e) {
        BalanceLab.steady(e);
        AttributeInstance dodge = e.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) dodge.removeModifiers();
        AttributeInstance crit = e.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) crit.removeModifiers();
    }

    /** What the blows of {@code amount} from {@code source} that land on {@code target} do, on average, from full health. */
    private static double landed(LivingEntity target, DamageSource source, float amount, int n) {
        double total = 0;
        int got = 0;
        for (int i = 0; i < n; i++) {
            target.setHealth(target.getMaxHealth());
            target.setAbsorptionAmount(0);
            target.invulnerableTime = 0;
            float before = target.getHealth();
            target.hurt(source, amount);
            float lost = before - target.getHealth();
            if (lost > 0) {
                total += lost;
                got++;
            }
        }
        target.setHealth(target.getMaxHealth());
        return got == 0 ? 0 : total / got;
    }

    private static <T extends Mob> T made(GameTestHelper helper, EntityType<T> type) {
        T e = type.create(helper.getLevel());
        if (e == null) throw new IllegalStateException("can't make " + type);
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        still(e);
        return e;
    }

    // ================================================================ monarch against monarch

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
    public static void aCrownedHunterAndAMonarchCanFellEachOther(GameTestHelper helper) {
        List<String> fights = new ArrayList<>();
        for (PlayerClass cls : new PlayerClass[]{PlayerClass.WARRIOR, PlayerClass.GUARDIAN}) {
            for (WorldBossKind kind : new WorldBossKind[]{WorldBossKind.STILLNESS, WorldBossKind.HUNGER, WorldBossKind.STORMS}) {
                Hunter h = BalanceLab.hunter(helper, cls, Balance.MAX_LEVEL, true);
                BalanceLab.crown(h, Dominion.leaning(cls));
                still(h);
                ColossalBoss boss = threat(helper, kind, Balance.MAX_LEVEL);
                double hit = BalanceLab.swing(h, boss, 8);
                double felled = boss.getMaxHealth() / Math.max(0.01, hit * h.getAttributeValue(Attributes.ATTACK_SPEED));
                double taken = landed(h, boss.damageSources().mobAttack(boss), boss.damage(), 8);
                double blows = h.getMaxHealth() / Math.max(0.01, taken);
                fights.add(cls + " vs " + kind + ": felled in " + Math.round(felled) + " s, falls to " + Math.round(blows) + " blows");
                if (cls == PlayerClass.WARRIOR) {
                    helper.assertTrue(hit > 0 && felled < 400, "a crowned warrior can fell " + kind + ": " + fights.get(fights.size() - 1));
                    helper.assertTrue(felled > 20, "but not in a moment: " + fights.get(fights.size() - 1));
                }
                helper.assertTrue(taken > 0 && blows < 60, "the monarch's blows land on a crowned " + cls + ": " + fights.get(fights.size() - 1));
                boss.discard();
                BalanceLab.release(h);
            }
        }
        BeyondSolo.LOGGER.info("[fair fights] {}", fights);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
    public static void twoCrownedHuntersCanFellEachOther(GameTestHelper helper) {
        // hunters may fight each other where the server allows it (the test server doesn't)
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        try {
            crownedPair(helper);
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    private static void crownedPair(GameTestHelper helper) {
        Hunter warrior = BalanceLab.hunter(helper, PlayerClass.WARRIOR, Balance.MAX_LEVEL, true);
        Hunter guardian = BalanceLab.hunter(helper, PlayerClass.GUARDIAN, Balance.MAX_LEVEL, true);
        BalanceLab.crown(warrior, Dominion.leaning(PlayerClass.WARRIOR));
        BalanceLab.crown(guardian, Dominion.leaning(PlayerClass.GUARDIAN));
        still(warrior);
        still(guardian);
        double onGuardian = BalanceLab.swing(warrior, guardian, 8);
        double onWarrior = BalanceLab.swing(guardian, warrior, 8);
        helper.assertTrue(onGuardian > 0 && guardian.getMaxHealth() / onGuardian < 200,
                "a crowned warrior wears down a crowned guardian: " + onGuardian + " a swing on " + guardian.getMaxHealth());
        helper.assertTrue(onWarrior > 0 && warrior.getMaxHealth() / onWarrior < 200,
                "and the guardian the warrior: " + onWarrior + " a swing on " + warrior.getMaxHealth());
        BalanceLab.release(warrior);
        BalanceLab.release(guardian);
    }

    // ================================================================ nothing untouchable

    @GameTest(template = ARENA, batch = BATCH)
    public static void stackedGuardsKeepAQuarterOfABlow(GameTestHelper helper) {
        Hunter p = BalanceLab.hunter(helper, PlayerClass.GUARDIAN, 74, true);
        BalanceHooks.freshBlow(p);
        float kept = 1;
        for (float guard : new float[]{0.4f, 0.7f, 0.5f, 0.1f, 0.6f}) kept *= BalanceHooks.keep(p, guard);
        helper.assertTrue(Math.abs(kept - Balance.GUARD_FLOOR) < 1e-4f, "five guards over one blow keep a quarter of it: " + kept);
        helper.assertTrue(Math.abs(BalanceHooks.keep(p, 0.5f) - 1f) < 1e-4f, "a sixth finds nothing left to take");
        BalanceHooks.freshBlow(p);
        helper.assertTrue(Math.abs(BalanceHooks.keep(p, 0.6f) - 0.6f) < 1e-4f, "the next blow starts over");
        helper.assertTrue(BalanceHooks.keep(p, 1.2f) == 1.2f, "a blow made bigger is not a guard");
        BalanceLab.release(p);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH)
    public static void dodgesLetAtMostHalfOfBlowsPass(GameTestHelper helper) {
        Hunter p = BalanceLab.hunter(helper, PlayerClass.ASSASSIN, 74, true);
        still(p);
        Zombie z = made(helper, EntityType.ZOMBIE);
        DamageSource bite = p.damageSources().mobAttack(z);
        int n = 4000;
        int passed = 0;
        for (int i = 0; i < n; i++) {
            BalanceHooks.freshBlow(p);
            // a mirror image, the mist and a riposte, rolled one after another as the hooks do
            if (BalanceHooks.avoids(p, bite, 0.5f) || BalanceHooks.avoids(p, bite, 0.5f) || BalanceHooks.avoids(p, bite, 0.9f)) passed++;
        }
        float share = passed / (float) n;
        helper.assertTrue(share > 0.44f && share < 0.56f, "however many dodges stack, half of blows pass by at most: " + share);
        // Apothic's dodge already had its 60% at the blow: the System's own have nothing left to roll
        p.getAttribute(ALObjects.Attributes.DODGE_CHANCE).setBaseValue(0.6);
        int more = 0;
        for (int i = 0; i < 400; i++) {
            BalanceHooks.freshBlow(p);
            if (BalanceHooks.avoids(p, bite, 0.5f)) more++;
        }
        helper.assertTrue(more == 0, "over Apothic's 60%, no more dodges: " + more);
        z.discard();
        BalanceLab.release(p);
        helper.succeed();
    }

    // ================================================================ healing

    @GameTest(template = ARENA, batch = BATCH)
    public static void healingOverTimeHasACeiling(GameTestHelper helper) {
        Hunter p = BalanceLab.hunter(helper, PlayerClass.WARRIOR, 74, false);
        // no gear: only VIT's healing received counts
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) p.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        BalanceLab.wear(p);
        p.setHealth(p.getMaxHealth() * 0.1f);
        float cap = Math.max(Balance.REGEN_CAP * p.getMaxHealth(), Balance.REGEN_FLOOR);
        float first = Balance.regen(p, p.getMaxHealth());
        float second = Balance.regen(p, p.getMaxHealth());
        helper.assertTrue(first > 0 && first <= cap * 1.01f, "a second's mending stops at 2% of max health: " + first + " of " + cap);
        helper.assertTrue(second < 0.01f, "and nothing more mends in the same second: " + second);
        BalanceLab.release(p);

        // a body of 20 still mends its two points a second
        ServerPlayer fresh = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fair_mend"));
        SystemAPI.reset(fresh);
        fresh.setGameMode(GameType.SURVIVAL);
        SystemAPI.awaken(fresh, PlayerRank.E, PlayerClass.HEALER);
        fresh.setHealth(5);
        float small = Balance.regen(fresh, 10);
        helper.assertTrue(small > 1.5f && small <= Balance.REGEN_FLOOR * 1.01f, "a small body mends its floor of two a second: " + small);

        // VIT: at most double healing, however much is spent on it
        SystemAPI.addStatPoints(fresh, 600);
        SystemAPI.allocateStats(fresh, Stat.VIT, 600);
        AttributeInstance received = fresh.getAttribute(ALObjects.Attributes.HEALING_RECEIVED);
        helper.assertTrue(received != null && received.getValue() <= received.getBaseValue() + Balance.VIT_HEALING_CAP + 1e-6,
                "600 VIT gives at most double healing: x" + (received == null ? -1 : received.getValue()));
        SystemAPI.reset(fresh);
        helper.succeed();
    }

    // ================================================================ bosses

    @GameTest(template = ARENA, batch = BATCH)
    public static void fireReachesEveryBoss(GameTestHelper helper) {
        List<LivingEntity> bosses = new ArrayList<>();
        GateBossEntity gate = ModEntities.GATE_BOSS.get().create(helper.getLevel());
        gate.configure(PlayerRank.S, BossKind.BRUTE, false);
        gate.displayAs(PlayerRank.S, BossKind.BRUTE);
        bosses.add(gate);
        bosses.add(made(helper, RulerModule.HERALD.get()));
        for (WorldBossKind kind : WorldBossKind.values()) bosses.add(threat(helper, kind, 74));
        Zombie z = made(helper, EntityType.ZOMBIE);
        for (LivingEntity boss : bosses) {
            DamageSource fire = CombatDamage.source(boss, DamageElement.FIRE, z);
            helper.assertTrue(!boss.isInvulnerableTo(fire), boss.getType() + " takes the System's fire");
            helper.assertTrue(EnemyHooks.skillMultiplier(boss, SkillElement.FIRE) > 0, boss.getType() + " takes fire skills: x"
                    + EnemyHooks.skillMultiplier(boss, SkillElement.FIRE));
            if (boss.getType().fireImmune()) {
                helper.assertTrue(boss.isInvulnerableTo(boss.damageSources().lava()), boss.getType() + " still wades through lava");
            }
            boss.discard();
        }
        // the citadel's lord sleeps until his hall is entered: what spares him from fire once he's up
        LivingEntity lord = made(helper, TowerModule.CITADEL_LORD.get());
        DamageSource fire = CombatDamage.source(lord, DamageElement.FIRE, z);
        helper.assertTrue(!CombatDamage.spared(lord, fire, true) && CombatDamage.spared(lord, lord.damageSources().lava(), true),
                "the citadel's lord takes the System's fire, and wades through lava");
        helper.assertTrue(EnemyHooks.skillMultiplier(lord, SkillElement.FIRE) > 0, "the citadel's lord takes fire skills");
        lord.discard();
        z.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH)
    public static void burnsAndAlliesBarelyScratchTheGreatThreats(GameTestHelper helper) {
        ColossalBoss monarch = threat(helper, WorldBossKind.STILLNESS, 74);
        helper.assertTrue(Balance.dotShare(monarch) == Balance.GREAT_DOT_SHARE, "a great threat takes a twentieth of a burn's share");
        float pop = Statuses.popCap(monarch);
        helper.assertTrue(Math.abs(pop - monarch.getMaxHealth() * Statuses.POP_SHARE * Balance.GREAT_DOT_SHARE) < 1f,
                "a blood pop takes half a percent of a monarch: " + pop + " of " + monarch.getMaxHealth());
        LivingEntity duelist = made(helper, DuelModule.DUELIST.get());
        helper.assertTrue(Balance.dotShare(duelist) == Balance.DUEL_DOT_SHARE, "a duelist takes a quarter of a burn's share");
        Zombie z = made(helper, EntityType.ZOMBIE);
        helper.assertTrue(Balance.dotShare(z) == 1f, "a creature takes a burn whole");

        // hired hands, guild hunters and the hunter's father nick them; a blow of no one's lands whole
        ColossalBoss titan = threat(helper, WorldBossKind.TITAN, 74);
        double whole = landed(titan, titan.damageSources().magic(), 1000, 3);
        for (LivingEntity ally : new LivingEntity[]{made(helper, GuildModule.HUNTER.get()), made(helper, StoryModule.FATHER.get())}) {
            helper.assertTrue(ColossalBoss.ally(ally), ally.getType() + " is an ally");
            double share = landed(titan, titan.damageSources().indirectMagic(ally, ally), 1000, 3) / Math.max(0.01, whole);
            helper.assertTrue(Math.abs(share - Balance.ALLY_GREAT_SHARE) < 0.02, ally.getType() + "'s blows land a quarter: x" + share);
            ally.discard();
        }
        monarch.discard();
        titan.discard();
        duelist.discard();
        z.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH)
    public static void greatThreatsAreSizedForTheHuntersWhoMeetThem(GameTestHelper helper) {
        WorldBossKind titan = WorldBossKind.TITAN;
        WorldBossKind monarch = WorldBossKind.STILLNESS;
        helper.assertTrue(titan.floorLevel() < WorldBossKind.S_LEVEL && monarch.floorLevel() == WorldBossKind.S_LEVEL,
                "a world boss may be sized below S, a monarch never: " + titan.floorLevel() + ", " + monarch.floorLevel());
        helper.assertTrue(titan.health(titan.floorLevel()) < titan.health(WorldBossKind.S_LEVEL) * 0.5f,
                "a titan met at its floor is far smaller than one met at S");
        double plain = Balance.BOSS_HEALTH * Balance.healthAt(Balance.MAX_LEVEL);
        helper.assertTrue(Math.abs(titan.health(Balance.MAX_LEVEL) - plain * titan.healthScale) < 1f, "a titan doesn't grow past the curve");
        helper.assertTrue(Math.abs(monarch.health(Balance.MAX_LEVEL) - plain * monarch.healthScale * WorldBossKind.SOVEREIGN_HEALTH_GROWTH) < 1f,
                "a monarch at the cap has its sovereign growth on top");
        helper.assertTrue(Math.abs(monarch.health(WorldBossKind.S_LEVEL) - monarch.health()) < 1f, "unsized, a monarch is sized for S");

        ColossalBoss b = threat(helper, WorldBossKind.TITAN, 0);
        helper.assertTrue(b.threatLevel() == titan.floorLevel() && Math.abs(b.getMaxHealth() - titan.health(titan.floorLevel())) < 1f,
                "a titan nobody was near is sized for its floor: " + b.threatLevel());
        b.setThreatLevel(500);
        helper.assertTrue(b.threatLevel() == Balance.MAX_LEVEL, "and never past the cap");
        b.discard();
        ColossalBoss host = threat(helper, WorldBossKind.HUNGER, Balance.MAX_LEVEL);
        double[] grow = host.hostScale();
        helper.assertTrue(grow[0] > 1 && grow[1] > 1, "a monarch's army grows with it past S: " + grow[0] + ", " + grow[1]);
        host.discard();
        helper.succeed();
    }

    // ================================================================ the domain, the sanctum and the shadows

    @GameTest(template = ARENA, batch = BATCH)
    public static void aDomainAndASanctumStandOnlyWithTheirOwn(GameTestHelper helper) {
        Hunter monarch = BalanceLab.hunter(helper, PlayerClass.WARRIOR, 90, false);
        Hunter ruler = BalanceLab.hunter(helper, PlayerClass.HEALER, 90, false);
        Hunter stranger = BalanceLab.hunter(helper, PlayerClass.ASSASSIN, 90, false);
        BalanceLab.crown(monarch, Dominion.leaning(PlayerClass.WARRIOR));
        BalanceLab.invest(ruler, Mantle.leaning(PlayerClass.HEALER));
        helper.assertTrue(MonarchDomain.shelters(monarch) && MonarchDomain.guard(monarch) < 1, "the domain shelters its monarch");
        helper.assertTrue(!MonarchDomain.shelters(stranger) && MonarchDomain.guard(stranger) == 1f && MonarchDomain.might(stranger) == 1f,
                "a hunter who walks into it is held, never sheltered or made mightier");
        helper.assertTrue(LightSanctum.shelters(ruler) && LightSanctum.guard(ruler) < 1, "the sanctum shelters its ruler");
        helper.assertTrue(!LightSanctum.shelters(stranger) && LightSanctum.guard(stranger) == 1f && LightSanctum.might(stranger) == 1f,
                "and not a stranger in it");
        BalanceLab.release(monarch);
        BalanceLab.release(ruler);
        BalanceLab.release(stranger);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = BATCH)
    public static void eachShadowRankIsARealStep(GameTestHelper helper) {
        ShadowRank[] ranks = ShadowRank.values();
        for (int i = 1; i < ranks.length; i++) {
            float entry = ranks[i].power(1);
            float best = ranks[i - 1].power(ShadowRank.LEVEL_CAP);
            helper.assertTrue(entry > best * 1.05f, ranks[i] + " at its first level beats " + ranks[i - 1] + " at its last: " + entry + " vs " + best);
        }
        helper.succeed();
    }

    private FairFightGameTests() {}
}
