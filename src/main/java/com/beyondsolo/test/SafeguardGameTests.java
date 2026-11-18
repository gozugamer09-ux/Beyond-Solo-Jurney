package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Safeguards;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.GearRecovery;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Playtest 0.1.6: the storm spark chain, deaths called for more than once, the boss's fall and gear lost in a gate. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafeguardGameTests {
    private static final String ARENA = "evaluation_zone";

    /**
     * A storm creature's blow sparks once. The spark used to set off another spark from inside its own damage, and so
     * on, so a single hit chained until whatever it touched was dead (a 400-health zombie here).
     */
    @GameTest(template = ARENA)
    public static void aStormBlowSparksOnce(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        Mob storm = Enemies.spawn(helper.getLevel(), PlayerRank.E, EnemyModule.type(MonsterKind.GRAVE_THRALL), at,
                new EnemyTraits(Affinity.STORM, Variant.NORMAL, 0), -1);
        helper.assertTrue(storm != null, "a storm thrall");
        storm.setNoAi(true);
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        victim.setHealth(400);
        int[] deaths = {0};
        java.util.function.Consumer<LivingDeathEvent> counter = e -> {
            if (e.getEntity() == victim) deaths[0]++;
        };
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, counter);
        try {
            victim.hurt(storm.damageSources().mobAttack(storm), 4f);
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(victim.isAlive() && deaths[0] == 0, "one blow does not kill a 400-health zombie");
        float lost = 400 - victim.getHealth();
        helper.assertTrue(lost > 4f && lost < 12f, "the blow and one spark, nothing more: lost " + lost);
        helper.assertTrue(!Affinity.inSpark(), "no spark left hanging");
        storm.discard();
        victim.discard();
        helper.succeed();
    }

    /** A player who died can't die again before getting up: the second call is refused. */
    @GameTest(template = ARENA)
    public static void aPlayerDiesOncePerDeath(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "twice_fallen"));
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(0);
        LivingDeathEvent first = NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
        helper.assertTrue(!first.isCanceled(), "the first death goes through");
        helper.assertTrue(Safeguards.hasFallen(p), "and is remembered");
        LivingDeathEvent again = NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
        helper.assertTrue(again.isCanceled(), "a second death of the same fallen player is refused");
        p.setHealth(p.getMaxHealth());
        helper.assertTrue(!Safeguards.hasFallen(p), "someone standing again is not held to it");
        helper.succeed();
    }

    /** When a boss falls the hunters around it are healed, cured and can't be hurt for a few seconds. */
    @GameTest(template = ARENA)
    public static void theBossFallHealsAndShields(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "last_one_standing"));
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(2f);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
        p.igniteForSeconds(5);
        Safeguards.restore(p, Safeguards.BOSS_SHIELD_TICKS);
        helper.assertTrue(p.getHealth() == p.getMaxHealth(), "full health");
        helper.assertTrue(!p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WITHER), "poison and wither gone");
        helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "good effects stay");
        helper.assertTrue(!p.isOnFire(), "no longer burning");
        helper.assertTrue(Safeguards.shielded(p), "shielded");
        LivingIncomingDamageEvent hit = NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().magic(), 50f)));
        helper.assertTrue(hit.isCanceled(), "no harm while shielded");
        LivingIncomingDamageEvent kill = NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().fellOutOfWorld(), 50f)));
        helper.assertTrue(!kill.isCanceled(), "the void still gets through");
        p.getPersistentData().remove(Safeguards.SHIELD_KEY);
        helper.assertTrue(!Safeguards.shielded(p), "the shield runs out");
        helper.succeed();
    }

    /**
     * Falling in a gate: the System carries what the hunter held out (the cave will be wiped) and hands it back when
     * they get up, armour back on, the rest in the bag or System storage, for 100 gold, or all they have if less.
     */
    @GameTest(template = ARENA)
    public static void gearFallenInAGateComesBack(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fallen_in_a_gate"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        Economy.set(p, 250);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(Items.IRON_CHESTPLATE)));
        drops.add(new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(Items.DIAMOND_SWORD)));
        GearRecovery.carry(p, drops);
        helper.assertTrue(drops.isEmpty(), "nothing is left in the cave");
        helper.assertTrue(GearRecovery.holding(p), "the System holds it");
        // a full bag: what does not fit goes to System storage
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        int back = GearRecovery.handBack(p);
        helper.assertTrue(back == 2, "both came back: " + back);
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "the chestplate is worn again");
        helper.assertTrue(Economy.data(p).storage().stream().anyMatch(s -> s.is(Items.DIAMOND_SWORD)), "the sword waits in storage");
        helper.assertTrue(Economy.gold(p) == 150, "a 100 gold fee: " + Economy.gold(p));
        helper.assertTrue(!GearRecovery.holding(p), "nothing held any more");
        helper.assertTrue(GearRecovery.handBack(p) == 0 && Economy.gold(p) == 150, "nothing to give, nothing charged");
        // short of gold: all they have
        Economy.set(p, 30);
        List<ItemEntity> more = new ArrayList<>();
        more.add(new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(Items.IRON_HELMET)));
        GearRecovery.carry(p, more);
        helper.assertTrue(GearRecovery.handBack(p) == 1, "the helmet came back");
        helper.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "and is worn");
        helper.assertTrue(Economy.gold(p) == 0, "all 30 gold taken, no debt: " + Economy.gold(p));
        helper.succeed();
    }
}
