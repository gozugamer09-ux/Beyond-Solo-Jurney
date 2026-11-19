package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyModule;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.key.KeyModule;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.rune.RuneModule;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * /solo, run the way an operator types it: through the server's command dispatcher, as the player (the commands with
 * no players named act on whoever sends them).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoloCommandGameTests {
    private static final String ARENA = "evaluation_zone";

    /** A fresh player at the arena's edge, looking south into it. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "op"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
        return p;
    }

    /** Runs a command as the player, with an operator's permission. */
    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    /** The message key of a /solo error. */
    private static String key(CommandSyntaxException e) {
        return e.getRawMessage() instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(e.getRawMessage());
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(template = ARENA)
    public static void everyGroupIsUnderSoloAndOpOnly(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        CommandDispatcher<CommandSourceStack> d = p.server.getCommands().getDispatcher();
        CommandNode<CommandSourceStack> solo = d.getRoot().getChild("solo");
        helper.assertTrue(solo != null, "/solo is registered");
        for (String child : List.of("help", "info", "awaken", "evaluate", "bind", "set", "add", "heal", "reset", "wipe", "skill", "quest", "shadow",
                "spawn", "gate", "dungeon", "trial", "tower", "give", "guild", "raid", "monarch", "ruler", "association", "dev")) {
            helper.assertTrue(solo.getChild(child) != null, "/solo " + child + " is there");
        }
        helper.assertTrue(solo.getChild("dev").getRedirect() == d.getRoot().getChild("system"), "/solo dev leads to the /system commands");
        helper.assertTrue(run(p, "solo") == 1 && run(p, "solo help world") == 1, "the help runs");
        helper.assertTrue(run(p, "solo dev gear info") == 1, "an old /system command runs through /solo dev");
        try {
            d.execute("solo info", p.createCommandSourceStack().withPermission(0).withSuppressedOutput());
            helper.fail("a player who isn't an operator ran /solo");
        } catch (CommandSyntaxException expected) {
            // not an operator: the command isn't there for them
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void awakenSetAddAndReset(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        try {
            run(p, "solo set level 5");
            helper.fail("set level worked on a player who has no rank yet");
        } catch (CommandSyntaxException e) {
            helper.assertTrue(key(e).equals("command.beyondsolo.solo.error.not_evaluated"), "says the player isn't evaluated: " + key(e));
        }
        run(p, "solo awaken s mage");
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(d.isAwakened() && d.rank() == PlayerRank.S && d.playerClass() == PlayerClass.MAGE, "awakened as an S-rank mage");
        int points = d.statPoints();
        run(p, "solo set level 20");
        helper.assertTrue(d.level() == 20, "level 20, got " + d.level());
        helper.assertTrue(d.statPoints() == points + 19 * Config.STAT_POINTS_PER_LEVEL.get(), "the levels' points came with them");
        run(p, "solo add levels -5");
        helper.assertTrue(d.level() == 15, "five levels down, got " + d.level());
        run(p, "solo set stat str 50");
        helper.assertTrue(d.stat(Stat.STR) == 50, "STR 50, got " + d.stat(Stat.STR));
        int before = d.statPoints();
        int spent = 0;
        for (Stat s : Stat.values()) spent += d.stat(s) - Stat.BASE;
        run(p, "solo reset stats");
        helper.assertTrue(d.stat(Stat.STR) == Stat.BASE && d.statPoints() == before + spent, "stats back to base, the points handed back");
        run(p, "solo set rank b");
        run(p, "solo set class warrior");
        helper.assertTrue(d.rank() == PlayerRank.B && d.playerClass() == PlayerClass.WARRIOR, "rank and class set");
        run(p, "solo set gold 1000");
        run(p, "solo add gold 250");
        helper.assertTrue(Economy.gold(p) == 1250, "1250 gold, got " + Economy.gold(p));
        run(p, "solo reset gold");
        helper.assertTrue(Economy.gold(p) == 0, "gold reset");
        run(p, "solo info");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aNumberIsALevelNotAName(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        run(p, "solo awaken c assassin");
        // "2" could be read as a player called 2: it has to be the level
        int taught = run(p, "solo skill learn all 2");
        helper.assertTrue(taught == 1, "taught the sender");
        List<Skill> known = Skills.known(SkillAPI.get(p));
        helper.assertTrue(!known.isEmpty(), "skills learned");
        for (Skill s : known) helper.assertTrue(SkillAPI.level(p, s) >= 2, s.path() + " is at level 2 or more");
        run(p, "solo skill forget all");
        helper.assertTrue(Skills.known(SkillAPI.get(p)).isEmpty(), "every skill forgotten");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void giveHandsOverTheModsItems(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        run(p, "solo awaken a ranger");
        run(p, "solo give item rune_stone 20");
        helper.assertTrue(count(p, RuneModule.RUNE_STONE.get()) == 20, "20 rune stones (a name with no namespace is the mod's)");
        run(p, "solo give key s 3");
        helper.assertTrue(count(p, KeyModule.KEYS.get(PlayerRank.S.tier()).get()) == 3, "three S-rank keys");
        run(p, "solo give license");
        helper.assertTrue(count(p, EconomyModule.HUNTER_LICENSE.get()) == 1, "a license");
        int before = 0;
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty()) before++;
        run(p, "solo give armor knight");
        int after = 0;
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty()) after++;
        helper.assertTrue(after == before + 4, "a whole set: four pieces, got " + (after - before));
        try {
            run(p, "solo give item diamond epic");
            helper.fail("a grade went on an item that has none");
        } catch (CommandSyntaxException e) {
            helper.assertTrue(key(e).equals("command.beyondsolo.solo.error.no_grade"), "says it has no grade: " + key(e));
        }
        helper.assertTrue(count(p, Items.DIAMOND) == 0, "nothing given for the refused command");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void questsStartAndComplete(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        run(p, "solo awaken e warrior");
        run(p, "solo quest start set/proof_of_strength");
        helper.assertTrue(QuestEngine.data(p).has(QuestTemplates.PROOF_OF_STRENGTH), "the quest started");
        int level = SystemAPI.get(p).level();
        long xp = SystemAPI.get(p).xp();
        run(p, "solo quest complete set/proof_of_strength");
        helper.assertTrue(QuestEngine.data(p).hasFinished(QuestTemplates.PROOF_OF_STRENGTH), "the quest is finished");
        helper.assertTrue(SystemAPI.get(p).level() > level || SystemAPI.get(p).xp() > xp, "with its reward");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void spawnCreaturesABossAndAGate(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        BlockPos at = p.blockPosition();
        AABB around = new AABB(at).inflate(12);
        var gloomfang = EnemyModule.type(MonsterKind.GLOOMFANG);
        helper.assertTrue(run(p, "solo spawn creature gloomfang 3 d elite") == 3, "three spawned");
        List<Mob> mobs = helper.getLevel().getEntitiesOfClass(Mob.class, around, m -> m.getType() == gloomfang);
        helper.assertTrue(mobs.size() == 3, "three gloomfangs in front, got " + mobs.size());
        mobs.forEach(Mob::discard);
        run(p, "solo spawn boss random e");
        List<GateBossEntity> bosses = helper.getLevel().getEntitiesOfClass(GateBossEntity.class, around);
        helper.assertTrue(bosses.size() == 1, "a boss in front, got " + bosses.size());
        bosses.forEach(Mob::discard);
        run(p, "solo spawn gate e");
        GateInstance gate = null;
        for (GateInstance g : GateData.get(p.server).all()) {
            if (g.gateDimension().equals(helper.getLevel().dimension()) && g.state() != GateInstance.State.CLOSED
                    && g.gatePos().closerToCenterThan(p.position(), 8)) {
                gate = g;
            }
        }
        helper.assertTrue(gate != null && gate.rank() == PlayerRank.E, "an E-rank gate opened in front");
        run(p, "solo gate close");
        helper.assertTrue(gate.state() == GateInstance.State.CLOSED, "and closed again");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wipeSystemStartsOver(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        run(p, "solo awaken b healer");
        run(p, "solo set level 30");
        run(p, "solo add gold 500");
        run(p, "solo skill learn all");
        run(p, "solo quest start set/proof_of_strength");
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        run(p, "solo wipe system");
        SystemData d = SystemAPI.get(p);
        helper.assertTrue(!d.isAwakened() && d.level() == 1, "no rank, class or levels left");
        helper.assertTrue(Economy.gold(p) == 0, "no gold left");
        helper.assertTrue(Skills.known(SkillAPI.get(p)).isEmpty(), "no skills left");
        helper.assertTrue(QuestEngine.data(p).all().isEmpty(), "no quests left");
        helper.assertTrue(count(p, Items.DIAMOND) == 3, "their own things stay");
        helper.assertTrue(SystemAPI.hasSystem(p) != Config.AWAKENING_TRIAL.get(), "the System waits for the awakening (or binds at once without it)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wipeAllMakesANewPlayer(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = player(helper);
        run(p, "solo awaken a guardian");
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        p.getEnderChestInventory().addItem(new ItemStack(Items.EMERALD, 2));
        p.setExperienceLevels(12);
        run(p, "solo wipe all");
        helper.assertTrue(!SystemAPI.isAwakened(p), "the System starts over");
        helper.assertTrue(p.getInventory().isEmpty() && p.getEnderChestInventory().isEmpty(), "nothing carried, nothing in the ender chest");
        helper.assertTrue(p.experienceLevel == 0, "no XP");
        helper.succeed();
    }

    private SoloCommandGameTests() {}
}
