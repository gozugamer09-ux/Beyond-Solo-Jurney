package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.ruler.HeraldEntity;
import com.beyondsolo.ruler.RulerBoss;
import com.beyondsolo.ruler.RulerJournal;
import com.beyondsolo.ruler.RulerModule;
import com.beyondsolo.ruler.RulerRecord;
import com.beyondsolo.ruler.Rulers;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.beyondsolo.worldboss.WorldBosses;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The rulers of light: they can be killed like a monarch, they choose their foes (monarchs and their armies, crowned
 * hunters, the hunter they judge, whoever strikes them) and leave every other hunter be, their sanctum keeps their
 * friends, a sentence can be overturned and a war with a monarch blesses the hunters who fought at the ruler's side.
 * Run with {@code ./gradlew runGameTestServer}.
 * <p>
 * A ruler's event lives in one slot for the whole server, so the tests that use it begin and end it within one tick.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RulerGameTests {
    private static final String ARENA = "evaluation_zone";

    /** In its last phase nothing holds a ruler up: it can be killed (no second life, no immunity). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aRulerCanDie(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (WorldBossKind kind : WorldBossKind.rulers()) {
            ColossalBoss boss = spawn(helper, kind, false);
            helper.assertTrue(boss instanceof RulerBoss, kind + " is a ruler");
            float max = boss.getMaxHealth();
            boss.hurt(level.damageSources().generic(), max * 10);
            helper.assertTrue(boss.isAlive() && boss.getHealth() >= max * kind.phase2 - 1f, kind + "'s first phase can't be skipped by one blow");
            boss.forcePhase(level, 3);
            helper.assertTrue(boss.phaseFloor() == 0f, kind + " has no floor in its last phase");
            boss.invulnerableTime = 0;
            boss.hurt(level.damageSources().generic(), max * 2);
            helper.assertTrue(boss.isDeadOrDying(), kind + " dies to a blow greater than its health in its last phase");
            boss.discard();
        }
        helper.succeed();
    }

    /** Its foes: monarchs, their soldiers, crowned hunters, the hunter it judges. Never its own, never a plain hunter. */
    @GameTest(template = ARENA)
    public static void theLightChoosesItsFoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer plain = hunter(helper, "plain_hunter", null);
        ServerPlayer crowned = hunter(helper, "crowned_hunter", Dominion.NIGHT);
        RulerBoss ruler = (RulerBoss) spawn(helper, WorldBossKind.VERDICT, false);
        ColossalBoss monarch = WorldBossModule.type(WorldBossKind.STORMS).create(level);
        Zombie soldier = EntityType.ZOMBIE.create(level);
        soldier.getPersistentData().putUUID(WorldBosses.ARMY_TAG, UUID.randomUUID());
        HeraldEntity herald = RulerModule.HERALD.get().create(level);
        herald.setRuler(ruler);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 6));
        herald.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        herald.setNoAi(true);
        level.addFreshEntity(herald);

        helper.assertTrue(ruler.isFoe(monarch), "a monarch is its foe");
        helper.assertTrue(monarch.canHit(ruler), "and a monarch strikes the light");
        helper.assertTrue(ruler.isFoe(soldier), "a monarch's soldier is its foe");
        helper.assertTrue(ruler.isFoe(crowned) && ruler.canHit(crowned), "a crowned hunter is its foe");
        helper.assertTrue(!ruler.isFoe(plain) && !ruler.canHit(plain), "a hunter with no crown is left be: its blows pass through them");
        helper.assertTrue(!ruler.isFoe(herald) && !herald.foe(ruler), "the light never turns on itself");
        helper.assertTrue(Rulers.light(herald) && Rulers.rulerOf(herald) == ruler, "a herald answers to its ruler");
        helper.assertTrue(herald.foe(monarch) && herald.foe(crowned) && !herald.foe(plain), "a herald's foes are its ruler's");
        helper.assertTrue(Rulers.spares(plain.getUUID(), ruler) && Rulers.spares(plain.getUUID(), herald),
                "a plain hunter's area blows, shadows and hired hunters pass the light by");
        helper.assertTrue(!Rulers.spares(crowned.getUUID(), ruler), "a crowned hunter's land on it");
        helper.assertTrue(!Rulers.spares(plain.getUUID(), monarch), "nothing spares a monarch");

        // come to judge the plain hunter: then they are its foe, and only until it is done
        ruler.setQuarry(Rulers.SENTENCE, plain.getUUID());
        helper.assertTrue(ruler.isFoe(plain) && !Rulers.spares(plain.getUUID(), ruler), "the hunter it came to judge is its foe");
        ruler.victory();
        helper.assertTrue(ruler.quarry() == null && !ruler.isFoe(plain), "once the sentence is over they are left be again");

        herald.discard();
        ruler.discard();
        monarch.discard();
        soldier.discard();
        helper.succeed();
    }

    /**
     * A hunter the light doesn't stand against has their first blow stayed, with a warning; a second blow soon after is
     * a choice: they stand against it from then on, and that blow lands. A crowned hunter's blows land at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aHuntersFirstBlowIsStayed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = hunter(helper, "rash_hunter", null);
        ServerPlayer crowned = hunter(helper, "crowned_rival", Dominion.CINDERS);
        RulerBoss ruler = (RulerBoss) spawn(helper, WorldBossKind.WINGS, false);
        float max = ruler.getHealth();
        boolean hit = ruler.hurt(level.damageSources().playerAttack(p), 40f);
        helper.assertTrue(!hit && ruler.getHealth() == max, "the first blow is stayed (" + ruler.getHealth() + " of " + max + ")");
        helper.assertTrue(!ruler.isAggressor(p.getUUID()) && !ruler.isFoe(p), "one blow is a warning, not a choice");
        helper.runAfterDelay(12, () -> {
            boolean second = ruler.hurt(level.damageSources().playerAttack(p), 40f);
            helper.assertTrue(second && ruler.getHealth() < max, "the second blow lands (" + ruler.getHealth() + " of " + max + ")");
            helper.assertTrue(ruler.isAggressor(p.getUUID()) && ruler.isFoe(p) && ruler.canHit(p), "the hunter stands against the light now");
            helper.assertTrue(ruler.dealt().getOrDefault(p.getUUID(), 0f) > 0, "and their share counts");
            ruler.invulnerableTime = 0;
            float before = ruler.getHealth();
            helper.assertTrue(ruler.hurt(level.damageSources().playerAttack(crowned), 40f) && ruler.getHealth() < before,
                    "a crowned hunter's first blow lands");
            ruler.discard();
            helper.succeed();
        });
    }

    /** Its sanctum keeps whoever it doesn't stand against: a monarch's domain and presence don't reach them there. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void theSanctumKeepsItsFriends(GameTestHelper helper) {
        ServerPlayer plain = hunter(helper, "sheltered_hunter", null);
        ServerPlayer crowned = hunter(helper, "crowned_outcast", Dominion.WINTER);
        ServerPlayer above = hunter(helper, "far_hunter", null);
        BlockPos high = helper.absolutePos(new BlockPos(1, 2 + RulerBoss.SANCTUM + 6, 1));
        above.moveTo(high.getX() + 0.5, high.getY(), high.getZ() + 0.5);
        RulerBoss ruler = (RulerBoss) spawn(helper, WorldBossKind.HAND, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(ruler.landed(), "it has landed");
            helper.assertTrue(Rulers.shelters(plain), "a hunter it doesn't stand against is kept in its sanctum");
            helper.assertTrue(!Rulers.shelters(crowned), "a crowned hunter is not");
            helper.assertTrue(!Rulers.shelters(above), "nor anyone outside it");
            helper.assertTrue(!Rulers.shelters(ruler), "the light needs no shelter");
            ruler.discard();
        });
    }

    /**
     * A sentence: the ruler comes for a crowned hunter, who gets the System's call to overturn it; one ruler at a time.
     * The judged hunter felling it overturns the sentence, and they are paid like a monarch's kill, with motes besides.
     */
    @GameTest(template = ARENA)
    public static void aSentenceCanBeOverturned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldBossData data = WorldBossData.get(level.getServer());
        helper.assertTrue(data.ruler() == null, "no ruler is abroad when the test starts");
        ServerPlayer p = hunter(helper, "judged_hunter", Dominion.THUNDER);
        RulerRecord record = p.getData(RulerModule.RECORD.get());
        ChronicleData chronicle = ChronicleModule.data(p);
        chronicle.reset();
        try {
            WorldBossData.Event ev = Rulers.sentence(level, p, WorldBossKind.VERDICT);
            helper.assertTrue(ev != null && ev.reason() == Rulers.SENTENCE && p.getUUID().equals(ev.quarry()), "a ruler comes to judge the crowned hunter");
            helper.assertTrue(data.ruler() == ev && ev.kind == WorldBossKind.VERDICT, "in the rulers' own slot");
            helper.assertTrue(record.sentences() == 1, "the sentence is in their record");
            boolean called = false;
            for (Quest q : QuestEngine.data(p).offers()) called |= Rulers.SENTENCE_QUEST.equals(q.template());
            helper.assertTrue(called, "the System calls on them to overturn it");
            helper.assertTrue(Rulers.begin(level, WorldBossKind.WINGS, p.blockPosition(), Rulers.CALLED, null) == null, "one ruler at a time");

            // it lands (here, in the arena)
            RulerBoss ruler = (RulerBoss) spawn(helper, WorldBossKind.VERDICT, false);
            ruler.setEventId(ev.id);
            ev.setEntity(ruler.getUUID());
            ev.setLandAt(0);
            ruler.setQuarry(Rulers.SENTENCE, p.getUUID());
            helper.assertTrue(Rulers.current(level) == ruler, "the ruler abroad is this one");
            helper.assertTrue(ruler.isFoe(p) && !Rulers.spares(p.getUUID(), ruler), "the judged hunter is its foe");

            // the judged hunter stands and fights; the last blow falls in its last phase
            helper.assertTrue(ruler.hurt(level.damageSources().playerAttack(p), 60f), "the judged hunter's blow lands at once");
            helper.assertTrue(ruler.share(p.getUUID()) > 0.99f, "all of its wounds are theirs");
            ruler.forcePhase(level, 3);
            ruler.invulnerableTime = 0;
            ruler.hurt(level.damageSources().generic(), ruler.getMaxHealth() * 3);
            helper.assertTrue(ruler.isDeadOrDying(), "the ruler falls");
            helper.assertTrue(data.ruler() == null, "its slot is free again");
            helper.assertTrue(record.overturned() == 1 && record.slain(WorldBossKind.VERDICT) == 1 && record.top() == 1,
                    "overturned, felled and the most damage, in the record");
            helper.assertTrue(count(p, RulerModule.MOTE.get()) >= 3, "motes of radiance were left to them: " + count(p, RulerModule.MOTE.get()));
            helper.assertTrue(chronicle.hasTitle(RulerJournal.RULERBREAKER) && chronicle.hasTitle(RulerJournal.ECLIPSE)
                    && chronicle.hasTitle(RulerJournal.UNSENTENCED), "Rulerbreaker, Eclipse and Unsentenced earned");
            ruler.discard();
        } finally {
            Rulers.stop(level);
        }
        helper.succeed();
    }

    /**
     * The Eternal War: a ruler comes to make war on a monarch. The hunters who bring the monarch down at its side are
     * blessed (harder blows on monarchs, a mote, the war in their record); a crowned hunter never is.
     */
    @GameTest(template = ARENA)
    public static void theWarBlessesTheRulersAllies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldBossData data = WorldBossData.get(level.getServer());
        helper.assertTrue(data.ruler() == null, "no ruler is abroad when the test starts");
        ServerPlayer ally = hunter(helper, "dawn_ally", null);
        ServerPlayer crowned = hunter(helper, "crowned_ally", Dominion.NIGHT);
        RulerRecord record = ally.getData(RulerModule.RECORD.get());
        ChronicleModule.data(ally).reset();
        MonarchBoss monarch = (MonarchBoss) spawn(helper, WorldBossKind.STORMS, false);
        RulerBoss ruler = (RulerBoss) spawn(helper, WorldBossKind.WINGS, false);
        try {
            WorldBossData.Event ev = Rulers.war(level, monarch, WorldBossKind.WINGS);
            helper.assertTrue(ev != null && ev.reason() == Rulers.WAR && monarch.getUUID().equals(ev.quarry()), "the light makes war on the monarch");
            ruler.setEventId(ev.id);
            ev.setEntity(ruler.getUUID());
            ev.setLandAt(0);
            ruler.setQuarry(Rulers.WAR, monarch.getUUID());
            helper.assertTrue(Rulers.spares(ally.getUUID(), ruler), "a hunter fighting the same monarch is left be");

            monarch.hurt(level.damageSources().playerAttack(ally), 80f);
            monarch.invulnerableTime = 0;
            monarch.hurt(level.damageSources().playerAttack(crowned), 80f);
            helper.assertTrue(monarch.share(ally.getUUID()) > 0.05f && monarch.share(crowned.getUUID()) > 0.05f, "both hunters wounded the monarch");
            monarch.forcePhase(level, 3);
            monarch.invulnerableTime = 0;
            monarch.hurt(level.damageSources().generic(), monarch.getMaxHealth() * 3);
            helper.assertTrue(monarch.isDeadOrDying(), "the monarch falls");

            helper.assertTrue(Rulers.blessed(ally) && record.blessings() == 1 && record.alliedWars() == 1, "the ally is blessed, and the war is in their record");
            helper.assertTrue(count(ally, RulerModule.MOTE.get()) == 1, "and handed a mote of radiance");
            helper.assertTrue(ChronicleModule.data(ally).hasTitle(RulerJournal.ALLY_OF_THE_DAWN), "Ally of the Dawn earned");
            helper.assertTrue(!Rulers.blessed(crowned) && count(crowned, RulerModule.MOTE.get()) == 0, "the light doesn't bless a crown");
            helper.assertTrue(!Rulers.bless(crowned, 200, true), "nor can it be made to");
            helper.assertTrue(ruler.quarry() == null, "the ruler's war is won");
        } finally {
            monarch.discard();
            ruler.discard();
            Rulers.stop(level);
        }
        helper.succeed();
    }

    /** A mote of radiance blesses whoever uses it, unless they wear a crown. */
    @GameTest(template = ARENA)
    public static void aMoteBlessesTheUncrowned(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "mote_bearer", null);
        ServerPlayer crowned = hunter(helper, "mote_crowned", Dominion.WINTER);
        ItemStack mote = new ItemStack(RulerModule.MOTE.get(), 2);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mote);
        mote.use(helper.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(Rulers.blessed(p) && p.getMainHandItem().getCount() == 1, "used, it blesses and is spent");
        helper.assertTrue(p.getData(RulerModule.RECORD.get()).blessings() == 0, "a mote's blessing isn't a ruler's own (the record keeps those)");
        ItemStack other = new ItemStack(RulerModule.MOTE.get(), 2);
        crowned.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, other);
        other.use(helper.getLevel(), crowned, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!Rulers.blessed(crowned) && crowned.getMainHandItem().getCount() == 2, "the light doesn't answer a crowned hunter");
        helper.succeed();
    }

    /** /solo ruler: motes, a blessing, the record, the status; call one down, land it, stop it; a sentence; the next one. */
    @GameTest(template = ARENA)
    public static void rulerCommandsWork(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        WorldBossData data = WorldBossData.get(level.getServer());
        helper.assertTrue(data.ruler() == null, "no ruler is abroad when the test starts");
        ServerPlayer p = hunter(helper, "ruler_operator", null);
        RulerRecord record = p.getData(RulerModule.RECORD.get());
        long next = data.nextSentenceAt();
        try {
            helper.assertTrue(run(p, "solo ruler status") == 1, "the status with no ruler abroad");
            run(p, "solo ruler mote 3");
            helper.assertTrue(count(p, RulerModule.MOTE.get()) == 3, "three motes given: " + count(p, RulerModule.MOTE.get()));
            run(p, "solo ruler bless");
            helper.assertTrue(Rulers.blessed(p) && record.blessings() == 1, "blessed, as by a ruler's hand");
            helper.assertTrue(run(p, "solo ruler record") == 1, "the record");

            run(p, "solo ruler call verdict");
            WorldBossData.Event ev = data.ruler();
            helper.assertTrue(ev != null && ev.kind == WorldBossKind.VERDICT && ev.reason() == Rulers.CALLED && ev.descending(), "Aurenthe is called down");
            error(helper, p, "solo ruler call wings", "ruler_abroad");
            error(helper, p, "solo ruler move cleave", "no_ruler_landed");
            helper.assertTrue(run(p, "solo ruler status") == 1, "the status while it comes down");
            run(p, "solo ruler land");
            helper.assertTrue(ev.landAt() == level.getGameTime(), "it lands now");
            run(p, "solo ruler stop");
            helper.assertTrue(data.ruler() == null, "stopped");
            error(helper, p, "solo ruler stop", "no_ruler");
            error(helper, p, "solo ruler war", "no_monarch_abroad");
            error(helper, p, "solo ruler call sunlight", "unknown_ruler");

            run(p, "solo ruler sentence hand on");
            ev = data.ruler();
            helper.assertTrue(ev != null && ev.kind == WorldBossKind.HAND && ev.reason() == Rulers.SENTENCE && p.getUUID().equals(ev.quarry()),
                    "Ithrael comes to judge the hunter");
            run(p, "solo ruler stop");

            run(p, "solo ruler next 5");
            helper.assertTrue(data.nextSentenceAt() == level.getGameTime() + 5 * 1200L, "the next sentence in five minutes");
        } finally {
            Rulers.stop(level);
            data.setNextSentenceAt(next);
        }
        helper.succeed();
    }

    // ================================================================ helpers

    /** Runs a command as the player, with an operator's permission. */
    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    /** The command fails with this /solo error. */
    private static void error(GameTestHelper helper, ServerPlayer p, String command, String error) {
        try {
            run(p, command);
            helper.fail("/" + command + " worked");
        } catch (CommandSyntaxException e) {
            String key = e.getRawMessage() instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey()
                    : String.valueOf(e.getRawMessage());
            helper.assertTrue(key.equals("command.beyondsolo.solo.error." + error), "/" + command + " says " + key);
        }
    }

    /** An evaluated S-rank hunter in the arena, crowned with {@code crown} if one is given, found by id while the test runs. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, Dominion crown) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        for (EquipmentSlot slot : EquipmentSlot.values()) p.setItemSlot(slot, ItemStack.EMPTY);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.S, PlayerClass.WARRIOR);
        if (crown != null) MonarchAPI.crown(p, crown);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** A great threat awake in the arena; {@code ai} false holds it still (it still ticks, lands and keeps its sanctum). */
    private static ColossalBoss spawn(GameTestHelper helper, WorldBossKind kind, boolean ai) {
        ServerLevel level = helper.getLevel();
        ColossalBoss boss = WorldBossModule.type(kind).create(level);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boss.setupStats();
        boss.setNoAi(!ai);
        level.addFreshEntity(boss);
        boss.wakeNow();
        return boss;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private RulerGameTests() {}
}
