package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.ShadowActionPayload;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.shadow.ShadowData;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.SovereignHand;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The utility skills: Sovereign Hand (grab, hurl, slam, pin, shove), Umbral Exchange, and shadows standing guard.
 * Run with {@code ./gradlew runGameTestServer}.
 *
 * <p>The creatures the hand takes are spawned with their AI on: a creature without it never moves however it is
 * pushed, so it would never be lifted, thrown or slammed. Blows are tested creature on creature, since a stand-in
 * player can't be hurt.</p>
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UtilitySkillGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A C-rank hunter who knows Sovereign Hand, in the arena's north-west corner, mana full. */
    private static ServerPlayer hunter(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hand"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, PlayerClass.WARRIOR);
        SkillAPI.learn(p, Skills.SOVEREIGN_HAND, 1, SkillEvent.Source.COMMAND);
        SystemAPI.restoreMana(p, 10000);
        SkillAPI.resetCooldowns(p);
        return p;
    }

    /** A C-rank mage with a subclass (a necromancer raises shadows), army emptied, in the same corner. */
    private static ServerPlayer mage(GameTestHelper helper, Subclass subclass) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "raiser"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.CREATIVE);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, subclass.base());
        JobAPI.changeJob(p, subclass);
        ShadowService.reset(p);
        return p;
    }

    /** A necromancer who knows Umbral Exchange. */
    private static ServerPlayer necromancer(GameTestHelper helper) {
        ServerPlayer p = mage(helper, Subclass.NECROMANCER);
        SkillAPI.learn(p, Skills.UMBRAL_EXCHANGE, 1, SkillEvent.Source.COMMAND);
        return p;
    }

    /** Turns the player to look at a point. */
    private static void face(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static long cooldown(ServerPlayer p, com.beyondsolo.skill.Skill s) {
        return SkillAPI.get(p).cooldownLeft(s.path(), p.level().getGameTime());
    }

    /** One of the player's shadows standing in the world, called up at a spot in the arena. */
    private static ShadowEntity raise(GameTestHelper helper, ServerPlayer p, ShadowRecord r, double x, double z) {
        ShadowEntity e = ShadowService.summonAt(p, r, helper.absoluteVec(new Vec3(x, 2, z)), false);
        helper.assertTrue(e != null, "the shadow rose");
        return e;
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    // ================================================================ Sovereign Hand

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void theHandLiftsAFoeAndHurlsItIntoAWall(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        // a wall across the east side of the arena, to throw against
        for (int y = 2; y <= 4; y++) for (int z = 0; z <= 2; z++) helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
        double wall = helper.absoluteVec(new Vec3(5, 0, 0)).x;
        double floor = helper.absoluteVec(new Vec3(0, 2, 0)).y;
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        Zombie by = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 5));
        face(p, z.getEyePosition());
        float mana = SystemAPI.get(p).mana();
        helper.assertTrue(SkillAPI.cast(p, Skills.SOVEREIGN_HAND), "cast");
        helper.assertTrue(SovereignHand.held(p) == z && SovereignHand.isHeld(z), "the hand holds the zombie");
        helper.assertTrue(SkillAPI.hasBuff(p, SovereignHand.BUFF_HOLD), "the hunter's hand is full");
        helper.assertTrue(z.isNoGravity(), "it hangs in the air");
        helper.assertTrue(SystemAPI.get(p).mana() < mana && cooldown(p, Skills.SOVEREIGN_HAND) > 0, "the grab costs mana and starts the cooldown");
        // held, it can't strike anyone
        by.hurt(by.damageSources().mobAttack(z), 4);
        helper.assertTrue(by.getHealth() == by.getMaxHealth(), "a held creature's blow does nothing");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(z.getY() > floor + 0.4, "lifted off the ground: " + (z.getY() - floor));
            helper.assertTrue(z.getBoundingBox().maxX < wall - 0.2, "held in front of the hunter, short of the wall: " + (z.getBoundingBox().maxX - wall));
            float hp = z.getHealth();
            float before = SystemAPI.get(p).mana();
            long cd = cooldown(p, Skills.SOVEREIGN_HAND);
            helper.assertTrue(SkillAPI.cast(p, Skills.SOVEREIGN_HAND), "the second press hurls it");
            helper.assertTrue(SystemAPI.get(p).mana() == before && cooldown(p, Skills.SOVEREIGN_HAND) == cd, "the hurl is free");
            helper.assertTrue(SovereignHand.held(p) == null && !SkillAPI.hasBuff(p, SovereignHand.BUFF_HOLD), "the hand let go");
            helper.assertTrue(SovereignHand.isFlying(z) && !z.isNoGravity(), "it flies, its weight back");
            // how far it got (it bounces back a little off the wall)
            double[] reached = {z.getBoundingBox().maxX};
            for (int i = 1; i <= 6; i++) helper.runAfterDelay(i, () -> reached[0] = Math.max(reached[0], z.getBoundingBox().maxX));
            helper.runAfterDelay(7, () -> {
                helper.assertFalse(SovereignHand.isFlying(z), "it crashed");
                helper.assertTrue(z.getHealth() < hp, "the wall hurt it: " + hp + " -> " + z.getHealth());
                helper.assertTrue(reached[0] > wall - 0.05, "it flew into the wall: " + (reached[0] - wall));
                // let go, its blows land again
                by.invulnerableTime = 0;
                by.hurt(by.damageSources().mobAttack(z), 4);
                helper.assertTrue(by.getHealth() < by.getMaxHealth(), "free again, it can strike");
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aHoldThatRunsOutSlamsItDown(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        face(p, z.getEyePosition());
        helper.assertTrue(SkillAPI.cast(p, Skills.SOVEREIGN_HAND), "cast");
        float hp = z.getHealth();
        int hold = Math.round(SovereignHand.holdSeconds(1) * 20);
        helper.runAfterDelay(hold - 4, () -> helper.assertTrue(SovereignHand.held(p) == z && z.getHealth() == hp, "still held and unhurt near the end"));
        helper.runAfterDelay(hold + 12, () -> {
            helper.assertTrue(SovereignHand.held(p) == null && !SkillAPI.hasBuff(p, SovereignHand.BUFF_HOLD), "the hand let go");
            helper.assertFalse(SovereignHand.isFlying(z), "it's down");
            helper.assertTrue(z.getHealth() < hp, "slammed into the ground: " + hp + " -> " + z.getHealth());
            helper.assertTrue(!z.isNoGravity() && z.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(BeyondSolo.id("sovereign_hold")) == null,
                    "free to walk again");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void theHandPinsWhatIsTooBigToLift(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        Zombie big = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        big.getAttribute(Attributes.SCALE).setBaseValue(1.7);
        big.refreshDimensions();
        helper.assertTrue(big.getBbHeight() > 3.2f, "a zombie grown past three blocks: " + big.getBbHeight());
        face(p, big.position().add(0, big.getBbHeight() * 0.5, 0));
        float hp = big.getHealth();
        double speed = big.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(SkillAPI.cast(p, Skills.SOVEREIGN_HAND), "cast");
        helper.assertTrue(SovereignHand.held(p) == null && !SovereignHand.isHeld(big), "too big to lift");
        helper.assertTrue(SovereignHand.isPinned(big), "pinned down instead");
        helper.assertTrue(big.getHealth() < hp, "and pressed hard: " + hp + " -> " + big.getHealth());
        helper.assertTrue(big.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.5, "slowed to a crawl");
        helper.assertTrue(cooldown(p, Skills.SOVEREIGN_HAND) > 0, "the cooldown started");
        int pin = Math.round(SovereignHand.pinSeconds(1) * 20);
        helper.runAfterDelay(pin + 4, () -> {
            helper.assertFalse(SovereignHand.isPinned(big), "the pin wears off");
            helper.assertTrue(Math.abs(big.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-6, "its legs back");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void theHandAtNothingShovesAndTurnsArrowsBack(GameTestHelper helper) {
        ServerPlayer p = hunter(helper);
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        // looking a little past it: nothing in the hand's sights, the zombie inside the shove
        face(p, z.getEyePosition());
        p.setYRot(p.getYRot() - 25);
        p.setYHeadRot(p.getYRot());
        Vec3 look = p.getLookAngle();
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        helper.assertTrue(arrow != null, "an arrow");
        Vec3 at = p.getEyePosition().add(look.scale(3));
        arrow.moveTo(at.x, at.y, at.z, 0f, 0f);
        arrow.setDeltaMovement(look.scale(-1.5));
        helper.getLevel().addFreshEntity(arrow);
        float hp = z.getHealth();
        double near = z.distanceTo(p);
        helper.assertTrue(SkillAPI.cast(p, Skills.SOVEREIGN_HAND), "cast at nothing");
        helper.assertTrue(SovereignHand.held(p) == null && !SovereignHand.isHeld(z), "nothing taken");
        helper.assertTrue(arrow.getOwner() == p, "the arrow is the hunter's now");
        helper.assertTrue(arrow.getDeltaMovement().normalize().dot(look) > 0.95, "and flies the way the hunter looks: " + arrow.getDeltaMovement());
        arrow.discard();
        helper.assertTrue(z.getHealth() < hp, "the shove hurt the zombie");
        helper.assertTrue(cooldown(p, Skills.SOVEREIGN_HAND) > 0, "the cooldown started");
        helper.succeedWhen(() -> helper.assertTrue(z.distanceTo(p) > near + 1.5, "and threw it back: " + near + " -> " + z.distanceTo(p)));
    }

    // ================================================================ Umbral Exchange

    @GameTest(template = ARENA)
    public static void onlyShadowRaisersHoldUmbralExchange(GameTestHelper helper) {
        ServerPlayer pyro = mage(helper, Subclass.PYROMANCER);
        ServerPlayer necro = mage(helper, Subclass.NECROMANCER);
        helper.assertTrue(Skills.UMBRAL_EXCHANGE.gated(), "the exchange is kept for those who raise shadows");
        helper.assertTrue(Skills.UMBRAL_EXCHANGE.allows(necro) && !Skills.UMBRAL_EXCHANGE.allows(pyro), "a necromancer may hold it, a pyromancer may not");
        for (PlayerClass c : PlayerClass.values()) {
            helper.assertFalse(Skills.forClass(c).contains(Skills.UMBRAL_EXCHANGE), "never in a class's own list: " + c);
            helper.assertTrue(Skills.forClass(c).contains(Skills.SOVEREIGN_HAND), "the hand is for every class: " + c);
        }
        helper.assertTrue(!Skills.SOVEREIGN_HAND.gated() && Skills.SOVEREIGN_HAND.allows(pyro), "anyone may hold the hand");
        // given to one who can't raise shadows, it's taken back
        SkillAPI.learn(pyro, Skills.UMBRAL_EXCHANGE, 1, SkillEvent.Source.COMMAND);
        SkillAPI.refreshGated(pyro);
        helper.assertFalse(SkillAPI.knows(pyro, Skills.UMBRAL_EXCHANGE), "forgotten by a pyromancer");
        helper.assertTrue(SkillAPI.get(pyro).slotOf(Skills.UMBRAL_EXCHANGE.path()) < 0, "and off their bar");
        SkillAPI.learn(necro, Skills.UMBRAL_EXCHANGE, 1, SkillEvent.Source.COMMAND);
        SkillAPI.refreshGated(necro);
        helper.assertTrue(SkillAPI.knows(necro, Skills.UMBRAL_EXCHANGE), "kept by a necromancer");
        ShadowService.reset(necro);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void exchangeTradesPlacesWithTheShadowLookedAt(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        helper.assertFalse(SkillAPI.cast(p, Skills.UMBRAL_EXCHANGE), "no shadows, nothing to trade places with");
        helper.assertTrue(cooldown(p, Skills.UMBRAL_EXCHANGE) == 0, "and no cooldown for it");
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowEntity e = raise(helper, p, r, 4.5, 1.5);
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 2, () -> {
            face(p, e.getEyePosition());
            e.setHealth(e.getMaxHealth() * 0.5f);
            float wounded = e.getHealth();
            Vec3 here = p.position();
            Vec3 there = e.position();
            helper.assertTrue(SkillAPI.cast(p, Skills.UMBRAL_EXCHANGE), "cast");
            helper.assertTrue(p.position().distanceTo(there) < 1.0e-3, "the hunter stands where the shadow stood");
            ShadowEntity now = ShadowService.entity(p, r.id());
            helper.assertTrue(now != null && e.isRemoved(), "the shadow rose anew");
            helper.assertTrue(now.position().distanceTo(here) < 1.0e-3, "where the hunter stood: " + now.position() + " vs " + here);
            helper.assertTrue(Math.abs(now.getHealth() - wounded) < 1.0e-3, "as wounded as it was: " + now.getHealth() + " vs " + wounded);
            helper.assertTrue(ShadowService.army(p).isActive(r.id()) && !r.posted(), "still out, still following");
            helper.assertTrue(cooldown(p, Skills.UMBRAL_EXCHANGE) > 0, "the cooldown started");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void exchangeReachesAGuardFarAway(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        ShadowRecord given = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        // a guard left at a post two thousand blocks off, nobody near it (its spot isn't even loaded)
        Vec3 far = p.position().add(2000, 0, 300);
        ShadowData army = ShadowService.army(p);
        CompoundTag tag = army.serializeNBT(helper.getLevel().registryAccess());
        CompoundTag saved = tag.getList("shadows", Tag.TAG_COMPOUND).getCompound(0);
        saved.putString("postDim", helper.getLevel().dimension().location().toString());
        saved.putDouble("postX", far.x);
        saved.putDouble("postY", far.y);
        saved.putDouble("postZ", far.z);
        army.deserializeNBT(helper.getLevel().registryAccess(), tag);
        ShadowRecord guard = army.get(given.id());
        helper.assertTrue(guard != null && guard.posted() && army.awayPosts() == 1 && army.used() == 1, "a guard away at its post takes a place");
        face(p, far.add(0, 1, 0));
        Vec3 here = p.position();
        helper.assertTrue(SkillAPI.cast(p, Skills.UMBRAL_EXCHANGE), "the exchange reaches it");
        helper.assertTrue(p.position().distanceTo(far) < 1.0e-3, "the hunter stepped out of the dark at the post");
        ShadowEntity e = ShadowService.entity(p, guard.id());
        helper.assertTrue(e != null && e.position().distanceTo(here) < 0.6, "the guard rose where the hunter stood");
        helper.assertTrue(guard.post().distanceTo(here) < 0.6 && e.post() != null && e.post().distanceTo(guard.post()) < 1.0e-6,
                "and guards that spot now");
        ShadowService.reset(p);
        helper.succeed();
    }

    // ================================================================ standing guard

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aGuardHoldsItsPostAndMovesWithTheExchange(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowRecord resting = ShadowService.give(p, EntityType.SKELETON, ShadowRank.SOLDIER, false);
        ShadowEntity e = raise(helper, p, r, 4.5, 1.5);
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 2, () -> {
            ShadowService.action(p, ShadowActionPayload.POST, r.id().toString());
            helper.assertTrue(r.posted() && r.postedIn(helper.getLevel().dimension()) && e.post() != null, "it stands guard");
            helper.assertTrue(r.post().distanceTo(e.position()) < 0.6, "where it stood");
            helper.assertFalse(ShadowService.post(p, resting.id()), "a shadow resting in the dark can't be left on guard");
            helper.assertTrue(ShadowService.recallAll(p) == 0 && ShadowService.army(p).isActive(r.id()) && !e.isSinking(),
                    "calling the army back leaves a guard at its post");
            helper.assertTrue(ShadowService.army(p).used() == 1, "it still takes a place");
            Vec3 here = p.position();
            Vec3 there = e.position();
            helper.assertTrue(ShadowService.exchange(p, r.id()), "exchange");
            ShadowEntity g = ShadowService.entity(p, r.id());
            helper.assertTrue(g != null && p.position().distanceTo(there) < 1.0e-3 && g.position().distanceTo(here) < 1.0e-3, "they traded places");
            helper.assertTrue(r.posted() && r.post().distanceTo(here) < 1.0e-3 && g.post() != null && g.post().distanceTo(here) < 1.0e-3,
                    "a guard keeps guarding, from where it stands now");
            try {
                helper.assertTrue(run(p, "solo shadow posts") == 1, "listed");
                helper.assertTrue(run(p, "solo shadow unpost") == 1, "called off");
            } catch (CommandSyntaxException ex) {
                helper.fail("command failed: " + ex.getMessage());
            }
            helper.assertTrue(!r.posted() && g.post() == null, "called off its post, it follows its master again");
            try {
                helper.assertTrue(run(p, "solo shadow post") == 1, "posted by command");
            } catch (CommandSyntaxException ex) {
                helper.fail("command failed: " + ex.getMessage());
            }
            helper.assertTrue(r.posted() && !resting.posted(), "every shadow out stands guard, the resting stay resting");
            ShadowService.action(p, ShadowActionPayload.UNPOST, r.id().toString());
            helper.assertFalse(r.posted(), "and off again from the Shadows page");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void aGuardLeftBehindStandsAtItsPostAgain(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowEntity e = raise(helper, p, r, 4.5, 1.5);
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 2, () -> {
            helper.assertTrue(ShadowService.post(p, r.id()), "posted");
            Vec3 post = r.post();
            ShadowData army = ShadowService.army(p);
            // nobody near its spot any more: the world keeps it frozen out of sight (here, carried off where nothing is loaded)
            e.teleportTo(e.getX() + 3000, e.getY(), e.getZ());
            helper.assertFalse(army.isActive(r.id()), "out of sight, it went back into its master's shadow");
            helper.assertTrue(r.posted() && army.awayPosts() == 1 && army.used() == 1, "still on guard, away at its post");
            helper.succeedWhen(() -> {
                helper.assertTrue(e.isRemoved(), "the frozen one is let go");
                ShadowEntity back = ShadowService.entity(p, r.id());
                helper.assertTrue(back != null && back != e, "with its spot near again, it stands up");
                helper.assertTrue(back.position().distanceTo(post) < 1.0 && back.post() != null, "at its post, guarding");
                helper.assertTrue(army.awayPosts() == 0, "no longer away");
                ShadowService.reset(p);
            });
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void aFollowerLeftFarBehindRisesAtItsMastersSide(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        // facing north: the army rises behind its master, inside the arena
        p.setYRot(180);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowEntity e = raise(helper, p, r, 4.5, 1.5);
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 2, () -> {
            e.teleportTo(e.getX() + 3000, e.getY(), e.getZ());
            helper.assertFalse(ShadowService.army(p).isActive(r.id()), "left behind out of sight, it went back into its master's shadow");
            helper.succeedWhen(() -> {
                helper.assertTrue(e.isRemoved(), "the frozen one is let go");
                ShadowEntity back = ShadowService.entity(p, r.id());
                helper.assertTrue(back != null && back != e && back.distanceTo(p) < 4, "it rose again at its master's side");
                ShadowService.reset(p);
            });
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void postsAreSavedAndOnlyMadeOutInTheWorld(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper);
        helper.assertTrue(ShadowService.canGuardIn(helper.getLevel()), "shadows stand guard out in the world");
        // (the test server has none of the System's own worlds, so this only checks the ones it has)
        for (ServerLevel level : helper.getLevel().getServer().getAllLevels()) {
            boolean ours = level.dimension().location().getNamespace().equals(BeyondSolo.MOD_ID);
            helper.assertTrue(ShadowService.canGuardIn(level) != ours, "never inside a gate or the System's other worlds: " + level.dimension().location());
        }
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.SOLDIER, false);
        ShadowRecord other = ShadowService.give(p, EntityType.SKELETON, ShadowRank.SOLDIER, false);
        raise(helper, p, r, 4.5, 1.5);
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 2, () -> {
            helper.assertTrue(ShadowService.post(p, r.id()), "posted");
            var provider = helper.getLevel().registryAccess();
            ShadowData copy = new ShadowData();
            copy.deserializeNBT(provider, ShadowService.army(p).serializeNBT(provider));
            ShadowRecord c = copy.get(r.id());
            helper.assertTrue(c != null && c.posted() && c.postedIn(helper.getLevel().dimension()) && c.post().distanceTo(r.post()) < 1.0e-6,
                    "the post survives a save");
            helper.assertFalse(copy.get(other.id()).posted(), "a shadow that wasn't guarding isn't after a load");
            helper.assertTrue(copy.activeCount() == 0 && copy.awayPosts() == 1 && copy.used() == 1, "loaded, the guard is away at its post until its spot is near");
            ShadowService.reset(p);
            helper.succeed();
        });
    }
}
