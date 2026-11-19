package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.manapoison.ManaPoisoning;
import com.beyondsolo.network.RewardBoxPayload;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.reward.BoxCurse;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.BoxReward;
import com.beyondsolo.reward.BoxRolls;
import com.beyondsolo.reward.RewardBoxItem;
import com.beyondsolo.reward.RewardBoxes;
import com.beyondsolo.reward.RewardConfig;
import com.beyondsolo.reward.RewardModule;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Random boxes: the plain one opens into one thing, the blessed one offers three and gives the one picked, the cursed
 * one rolls better and brings a curse down. Test players drop everything sent to their screen, so what the screen is
 * told is heard with {@link RewardBoxes#listen}. A fake player's item cooldowns never run out (it never ticks), so a
 * test that opens twice clears the cooldown by hand.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RewardBoxGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A hunter in the plot at that rank (null: not evaluated), a warrior, with no gold. */
    private static ServerPlayer hunter(GameTestHelper helper, String name, @Nullable PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        Economy.reset(p);
        ManaPoisoning.clear(p);
        p.removeAllEffects();
        p.getInventory().clearContent();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        if (rank != null) SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** Everything the hunter's screen is told from now on. */
    private static List<RewardBoxPayload> listen(GameTestHelper helper, ServerPlayer p) {
        List<RewardBoxPayload> heard = new ArrayList<>();
        RewardBoxes.listen(p.getUUID(), heard::add);
        return heard;
    }

    private static void unlisten(ServerPlayer p) {
        RewardBoxes.listen(p.getUUID(), null);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Whether the hunter now holds everything a reward gives (its items, at least that many of each). */
    private static boolean holds(ServerPlayer p, BoxReward reward) {
        for (ItemStack s : reward.items()) if (count(p, s.getItem()) < s.getCount()) return false;
        return true;
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    /** A reward as plain words, to compare two rolls of the same seed. */
    private static String describe(BoxReward r) {
        StringBuilder b = new StringBuilder(r.category().name()).append(' ').append(r.gold()).append(' ').append(r.statPoints());
        for (ItemStack s : r.items()) b.append(' ').append(s.getCount()).append('x').append(s.getItem());
        return b.toString();
    }

    private static boolean empty(BoxReward r) {
        return r.items().isEmpty() && r.gold() <= 0 && r.statPoints() <= 0;
    }

    // ================================================================ opening

    /** A plain box opens into one thing for its hunter and is used up; the screen plays the opening. */
    @GameTest(template = ARENA)
    public static void aPlainBoxOpensIntoOneThing(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "PlainOpener", PlayerRank.C);
        List<RewardBoxPayload> heard = listen(helper, p);
        p.setItemInHand(InteractionHand.MAIN_HAND, RewardBoxItem.of(BoxKind.PLAIN, PlayerRank.C, 2));
        int points = SystemAPI.get(p).statPoints();
        helper.assertTrue(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "the box opens");
        unlisten(p);
        helper.assertTrue(p.getMainHandItem().getCount() == 1, "one box is used up: " + p.getMainHandItem().getCount());
        helper.assertTrue(heard.size() == 1 && heard.get(0).mode() == RewardBoxPayload.OPENED, "the screen plays one opening: " + heard.size());
        RewardBoxPayload told = heard.get(0);
        helper.assertTrue(told.kind() == BoxKind.PLAIN && told.rank() == PlayerRank.C && told.curse() == null, "a plain C-rank box, no curse");
        BoxReward reward = told.reward(p.registryAccess());
        helper.assertFalse(empty(reward), "it held something: " + describe(reward));
        helper.assertTrue(holds(p, reward), "its things are in the inventory: " + describe(reward));
        helper.assertTrue(Economy.gold(p) == reward.gold(), "its gold is paid: " + Economy.gold(p) + " of " + reward.gold());
        helper.assertTrue(SystemAPI.get(p).statPoints() - points == reward.statPoints(), "its stat points are given");
        helper.assertFalse(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "not again in the same second");
        p.getCooldowns().removeCooldown(RewardModule.RANDOM_BOX.get());
        helper.assertTrue(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "the second box opens after a moment");
        // (what the second one held may land in the hand it left empty)
        helper.assertTrue(count(p, RewardModule.RANDOM_BOX.get()) == 0, "both boxes used up: " + count(p, RewardModule.RANDOM_BOX.get()));
        helper.succeed();
    }

    /** The System opens its boxes only for its own hunters. */
    @GameTest(template = ARENA)
    public static void anUnawakenedPlayerCannotOpenABox(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Stranger", null);
        p.setItemInHand(InteractionHand.MAIN_HAND, RewardBoxItem.of(BoxKind.PLAIN, PlayerRank.E, 1));
        helper.assertFalse(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "the box stays shut");
        helper.assertTrue(p.getMainHandItem().getCount() == 1, "and stays in the hand");
        helper.succeed();
    }

    /**
     * A blessed box shows three offers (the same three until one is taken, closing the window keeps the box) and gives
     * the one picked; the boxes left in the stack hold three new things.
     */
    @GameTest(template = ARENA)
    public static void aBlessedBoxGivesTheOfferPicked(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "BlessedOpener", PlayerRank.B);
        List<RewardBoxPayload> heard = listen(helper, p);
        p.setItemInHand(InteractionHand.MAIN_HAND, RewardBoxItem.of(BoxKind.BLESSED, PlayerRank.B, 2));
        helper.assertTrue(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "the box opens");
        helper.assertTrue(p.getMainHandItem().getCount() == 2, "opening it only shows the offers");
        helper.assertTrue(heard.size() == 1 && heard.get(0).mode() == RewardBoxPayload.OFFER, "the screen is offered three");
        RewardBoxPayload offer = heard.get(0);
        long seed = offer.seed();
        helper.assertTrue(seed != 0 && seed == RewardBoxItem.seed(p.getMainHandItem()), "the box keeps the seed it showed");
        List<BoxReward> offers = RewardBoxes.offers(offer, p.registryAccess());
        helper.assertTrue(offers.size() == 3, "three offers: " + offers.size());
        Set<BoxReward.Category> kinds = EnumSet.noneOf(BoxReward.Category.class);
        for (BoxReward o : offers) kinds.add(o.category());
        helper.assertTrue(kinds.size() == 3, "of three different kinds: " + kinds);

        p.getCooldowns().removeCooldown(RewardModule.BLESSED_BOX.get());
        helper.assertTrue(RewardBoxes.open(p, InteractionHand.MAIN_HAND), "opened again");
        List<BoxReward> again = RewardBoxes.offers(heard.get(1), p.registryAccess());
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(describe(again.get(i)).equals(describe(offers.get(i))), "the same three: " + describe(again.get(i)) + " / " + describe(offers.get(i)));
        }

        helper.assertFalse(RewardBoxes.pick(p, 0, seed + 2, 0), "a pick from another seed is refused");
        helper.assertFalse(RewardBoxes.pick(p, 0, seed, 3), "there is no fourth offer");
        helper.assertFalse(RewardBoxes.pick(p, 1, seed, 0), "nothing blessed in the other hand");
        long gold = Economy.gold(p);
        int points = SystemAPI.get(p).statPoints();
        helper.assertTrue(RewardBoxes.pick(p, 0, seed, 1), "the second offer is taken");
        unlisten(p);
        BoxReward taken = offers.get(1);
        helper.assertTrue(p.getMainHandItem().getCount() == 1, "one box is used up");
        helper.assertTrue(holds(p, taken) && Economy.gold(p) - gold == taken.gold() && SystemAPI.get(p).statPoints() - points == taken.statPoints(),
                "what was picked is given: " + describe(taken));
        RewardBoxPayload opened = heard.get(heard.size() - 1);
        helper.assertTrue(opened.mode() == RewardBoxPayload.OPENED && opened.kind() == BoxKind.BLESSED, "the screen plays the blessed opening");
        helper.assertTrue(describe(opened.reward(p.registryAccess())).equals(describe(taken)), "with what was picked");
        long next = RewardBoxItem.seed(p.getMainHandItem());
        helper.assertTrue(next != 0 && next != seed, "the box left holds three new things");
        helper.assertFalse(RewardBoxes.pick(p, 0, seed, 1), "the old offers can't be taken twice");
        helper.succeed();
    }

    /**
     * Every blessed box offers three things of different kinds, none of them empty, the same three for the same seed;
     * a cursed box never holds the small stuff (remedies, cores).
     */
    @GameTest(template = ARENA)
    public static void rollsKeepTheirPromises(GameTestHelper helper) {
        for (PlayerRank rank : PlayerRank.values()) {
            for (long seed = 1; seed <= 60; seed++) {
                List<BoxReward> offers = BoxRolls.offers(rank, PlayerClass.MAGE, seed * 7919);
                Set<BoxReward.Category> kinds = EnumSet.noneOf(BoxReward.Category.class);
                for (BoxReward o : offers) {
                    kinds.add(o.category());
                    helper.assertFalse(empty(o), "an offer holds something: " + rank + " " + describe(o));
                }
                helper.assertTrue(offers.size() == 3 && kinds.size() == 3, "three kinds: " + kinds);
                List<BoxReward> same = BoxRolls.offers(rank, PlayerClass.MAGE, seed * 7919);
                for (int i = 0; i < 3; i++) helper.assertTrue(describe(same.get(i)).equals(describe(offers.get(i))), "a seed rolls the same");
            }
            RandomSource r = new LegacyRandomSource(rank.tier() * 31L + 7);
            for (int i = 0; i < 80; i++) {
                BoxReward cursed = BoxRolls.roll(BoxKind.CURSED, rank, PlayerClass.ASSASSIN, r);
                helper.assertTrue(cursed.category() != BoxReward.Category.REMEDIES && cursed.category() != BoxReward.Category.CORES,
                        "a cursed box never holds the small stuff: " + describe(cursed));
                helper.assertFalse(empty(cursed), "a cursed box holds something");
                helper.assertFalse(empty(BoxRolls.roll(BoxKind.PLAIN, rank, PlayerClass.ASSASSIN, r)), "a plain box holds something");
            }
        }
        helper.succeed();
    }

    // ================================================================ curses

    /**
     * The Wardens: the box's guardians climb out and what it held goes into a quest; it is the hunter's once every one
     * of them is down.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theWardensHoldTheBoxUntilTheyFall(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "CursedOpener", PlayerRank.E);
        BoxReward reward = new BoxReward(BoxReward.Category.WEAPON, List.of(new ItemStack(Items.DIAMOND_SWORD)), 500, 1);
        List<BlockPos> spots = List.of(helper.absolutePos(new BlockPos(5, 2, 5)), helper.absolutePos(new BlockPos(1, 2, 5)), helper.absolutePos(new BlockPos(5, 2, 1)));
        int points = SystemAPI.get(p).statPoints();
        int out = RewardBoxes.wardens(p, PlayerRank.E, reward, spots);
        helper.assertTrue(out == 3, "three wardens climb out: " + out);
        Quest q = QuestEngine.data(p).active().stream().filter(x -> x.template().equals(RewardBoxes.CURSE)).findFirst().orElse(null);
        helper.assertTrue(q != null && RewardBoxes.live(q), "the curse is a quest");
        List<Mob> wardens = RewardBoxes.wardensOf(helper.getLevel(), q);
        helper.assertTrue(wardens.size() == 3, "its wardens stand: " + wardens.size());
        helper.assertTrue(count(p, Items.DIAMOND_SWORD) == 0 && Economy.gold(p) == 0, "the box's things are held back");
        wardens.get(0).kill();
        wardens.get(1).kill();
        helper.assertTrue(QuestEngine.data(p).find(q.id()) != null && q.status() == Quest.Status.ACTIVE, "not while one stands");
        wardens.get(2).kill();
        helper.assertTrue(QuestEngine.data(p).find(q.id()) == null, "the last one down breaks the curse: " + q.objectives().get(0).progress() + "/3");
        helper.assertTrue(count(p, Items.DIAMOND_SWORD) == 1, "the sword is the hunter's");
        // every quest pays a little gold of its own on top (a quarter of its XP, at least 5)
        long questPay = Math.max(5, q.rewards().xp() / 4);
        helper.assertTrue(Economy.gold(p) == 500 + questPay, "and the gold: " + Economy.gold(p));
        helper.assertTrue(SystemAPI.get(p).statPoints() - points == 1, "and the stat point");
        helper.assertFalse(RewardBoxes.live(q), "the curse is over");
        helper.succeed();
    }

    /** A curse held to the end (the hunter fled, the time ran out): the wardens go and take the box's things with them. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aHeldCurseTakesTheBoxAway(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "FleeingOpener", PlayerRank.D);
        BoxReward reward = new BoxReward(BoxReward.Category.WEAPON, List.of(new ItemStack(Items.DIAMOND_SWORD)), 300, 0);
        List<BlockPos> spots = List.of(helper.absolutePos(new BlockPos(5, 2, 5)), helper.absolutePos(new BlockPos(1, 2, 5)));
        helper.assertTrue(RewardBoxes.wardens(p, PlayerRank.D, reward, spots) == 2, "two wardens climb out");
        Quest q = QuestEngine.data(p).active().stream().filter(x -> x.template().equals(RewardBoxes.CURSE)).findFirst().orElseThrow();
        QuestEngine.fail(p, q, QuestEvent.FailReason.TIME);
        helper.assertTrue(RewardBoxes.wardensOf(helper.getLevel(), q).isEmpty(), "the wardens go");
        helper.assertFalse(RewardBoxes.live(q), "the curse is over");
        helper.assertTrue(count(p, Items.DIAMOND_SWORD) == 0 && Economy.gold(p) == 0, "and the box's things with them");
        helper.succeed();
    }

    /** Hex of Frailty: the box's things, and a share of the hunter's health gone until it wears off or is washed out. */
    @GameTest(template = ARENA)
    public static void frailtyLastsUntilPurified(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "FrailOpener", PlayerRank.C);
        double before = p.getMaxHealth();
        RewardBoxes.curse(p, PlayerRank.C, BoxReward.stats(1), BoxCurse.FRAILTY);
        helper.assertTrue(p.hasEffect(RewardModule.FRAILTY), "the hex is on");
        helper.assertTrue(p.getEffect(RewardModule.FRAILTY).getDuration() == RewardConfig.frailtyTicks(), "for its minutes");
        double frail = p.getMaxHealth();
        helper.assertTrue(Math.abs(frail - before * (1 - RewardModule.FRAILTY_SHARE)) < 0.01, "a share of the health is gone: " + frail + " of " + before);
        ManaPoisoning.purify(p);
        helper.assertFalse(p.hasEffect(RewardModule.FRAILTY), "a purifying draught lifts it");
        helper.assertTrue(Math.abs(p.getMaxHealth() - before) < 0.01, "the health is back: " + p.getMaxHealth());
        helper.succeed();
    }

    /** Greed takes a tenth of the hunter's gold; Backlash poisons them with mana. */
    @GameTest(template = ARENA)
    public static void greedAndBacklashTakeTheirShare(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "GreedyOpener", PlayerRank.C);
        Economy.add(p, 1000, Economy.Reason.BOX);
        int points = SystemAPI.get(p).statPoints();
        List<RewardBoxPayload> heard = listen(helper, p);
        RewardBoxes.curse(p, PlayerRank.C, BoxReward.stats(2), BoxCurse.GREED);
        long taken = Math.round(1000 * RewardConfig.greedShare());
        helper.assertTrue(Economy.gold(p) == 1000 - taken, "a tenth of the gold is gone: " + Economy.gold(p));
        helper.assertTrue(SystemAPI.get(p).statPoints() - points == 2, "the box's points are given");
        helper.assertTrue(!heard.isEmpty() && heard.get(0).curse() == BoxCurse.GREED && heard.get(0).detail() == taken, "the screen names the curse and what it took");
        RewardBoxes.curse(p, PlayerRank.C, BoxReward.stats(1), BoxCurse.BACKLASH);
        unlisten(p);
        helper.assertTrue(Math.abs(ManaPoisoning.level(p) - RewardConfig.backlash()) < 0.01, "the backlash poisons: " + ManaPoisoning.level(p));
        helper.succeed();
    }

    // ================================================================ where they come from

    /** A reward cache adds at most one box: cursed from a red gate, blessed or plain from a hidden chamber or an ascended boss, plain from a boss. */
    @GameTest(template = ARENA)
    public static void cachesHoldBoxesByWhereTheyStand(GameTestHelper helper) {
        RandomSource r = new LegacyRandomSource(42);
        int red = 0, blessed = 0, plain = 0;
        for (int i = 0; i < 200; i++) {
            helper.assertTrue(RewardBoxes.fromCache(PlayerRank.B, false, false, false, false, r).isEmpty(), "a plain cache holds no box");
            for (ItemStack s : RewardBoxes.fromCache(PlayerRank.B, true, false, true, false, r)) {
                helper.assertTrue(s.getItem() instanceof RewardBoxItem b && b.kind() == BoxKind.CURSED && RewardBoxItem.rank(s) == PlayerRank.B, "a red gate's box is cursed");
                red++;
            }
            List<ItemStack> boss = RewardBoxes.fromCache(PlayerRank.B, true, false, false, false, r);
            helper.assertTrue(boss.size() <= 1, "one box at most");
            for (ItemStack s : boss) helper.assertTrue(s.getItem() instanceof RewardBoxItem b && b.kind() == BoxKind.PLAIN, "a boss's box is plain");
            for (ItemStack s : RewardBoxes.fromCache(PlayerRank.B, true, true, false, false, r)) {
                if (s.getItem() instanceof RewardBoxItem b && b.kind() == BoxKind.BLESSED) blessed++;
                else plain++;
            }
        }
        helper.assertTrue(red > 80, "red gates give cursed boxes often: " + red);
        helper.assertTrue(blessed > 30 && plain > 0, "ascended bosses give blessed ones, now and then plain: " + blessed + " / " + plain);
        helper.succeed();
    }

    /** The daily training pays a Random Box of the hunter's rank. */
    @GameTest(template = ARENA)
    public static void theDailyTrainingPaysARandomBox(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "DailyHunter", PlayerRank.D);
        Quest daily = QuestTemplates.create(QuestTemplates.DAILY, p, QuestTemplate.Context.NONE);
        helper.assertTrue(daily != null, "the daily is made");
        boolean box = daily.rewards().items().stream().anyMatch(s -> s.getItem() instanceof RewardBoxItem b && b.kind() == BoxKind.PLAIN && RewardBoxItem.rank(s) == PlayerRank.D);
        helper.assertTrue(box, "it pays a D-rank Random Box");
        helper.succeed();
    }

    /** /solo box give hands boxes out, of the rank asked for or the player's own. */
    @GameTest(template = ARENA)
    public static void soloBoxGiveHandsBoxesOut(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, "BoxOperator", PlayerRank.C);
        run(p, "solo box give blessed b 2");
        int blessed = 0, plain = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!(s.getItem() instanceof RewardBoxItem b)) continue;
            if (b.kind() == BoxKind.BLESSED && RewardBoxItem.rank(s) == PlayerRank.B) blessed += s.getCount();
        }
        helper.assertTrue(blessed == 2, "two blessed B-rank boxes: " + blessed);
        run(p, "solo box give plain");
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof RewardBoxItem b && b.kind() == BoxKind.PLAIN && RewardBoxItem.rank(s) == PlayerRank.C) plain += s.getCount();
        }
        helper.assertTrue(plain == 1, "a plain box of the player's own rank: " + plain);
        List<RewardBoxPayload> heard = listen(helper, p);
        run(p, "solo box curse greed");
        unlisten(p);
        helper.assertTrue(heard.size() == 1 && heard.get(0).kind() == BoxKind.CURSED && heard.get(0).curse() == BoxCurse.GREED, "a cursed box opens on the spot, with Greed");
        helper.assertTrue(count(p, RewardModule.CURSED_BOX.get()) == 0, "and leaves no box behind");
        helper.succeed();
    }
}
