package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalCategory;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.chronicle.journal.JournalService;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.chronicle.title.Feat;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.tower.RushTier;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The chronicle: titles given by features and earned by feats (worn by rank of tier unless the hunter picked one),
 * their perks, the journal filling in from sightings and kills, the rankings with the rival roster, and every line of
 * it written in English. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChronicleGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer hunter(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        ChronicleModule.data(p).reset();
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, PlayerRank.D, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    private static TitleDef def(String id) {
        TitleDef d = Titles.byId(id);
        if (d == null) throw new IllegalStateException("no title " + id);
        return d;
    }

    // ================================================================ titles

    /** A title a feature gives is kept and worn; a rarer one replaces it; one the hunter picked stays on. */
    @GameTest(template = ARENA)
    public static void givenTitlesAreKeptAndWorn(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "TitleGiven");
        ChronicleData c = ChronicleModule.data(p);
        SystemAPI.setTitle(p, Titles.RELENTLESS);
        helper.assertTrue(c.hasTitle(Titles.RELENTLESS), "a title given by a feature is kept");
        helper.assertTrue(SystemAPI.get(p).title().equals(Titles.RELENTLESS), "the first title is worn");
        helper.assertTrue(c.isFresh(Titles.RELENTLESS), "a new title is marked as not looked at yet");

        SystemAPI.setTitle(p, Titles.ARBITER_SLAYER);
        helper.assertTrue(SystemAPI.get(p).title().equals(Titles.ARBITER_SLAYER), "a rarer title replaces one the System picked");
        SystemAPI.setTitle(p, def("first_hunt").key());
        helper.assertTrue(SystemAPI.get(p).title().equals(Titles.ARBITER_SLAYER), "a commoner title does not replace a rarer one");

        helper.assertTrue(TitleService.equip(p, Titles.RELENTLESS, true), "an earned title can be worn by hand");
        helper.assertTrue(!c.isFresh(Titles.RELENTLESS), "wearing a title marks it as seen");
        SystemAPI.setTitle(p, Titles.CITADEL_CONQUEROR);
        helper.assertTrue(SystemAPI.get(p).title().equals(Titles.RELENTLESS), "a title the hunter picked stays on");
        helper.assertTrue(c.hasTitle(Titles.CITADEL_CONQUEROR), "the new one is still kept");

        helper.assertTrue(!TitleService.equip(p, def("the_endless").key(), true), "a title not earned can't be worn");
        helper.assertTrue(TitleService.equip(p, "", true), "the worn title can be taken off");
        helper.assertTrue(SystemAPI.get(p).title().isEmpty(), "nothing worn after taking it off");
        SystemAPI.setTitle(p, def("gate_walker").key());
        helper.assertTrue(SystemAPI.get(p).title().equals(def("gate_walker").key()), "with nothing worn, a new title is worn again");
        helper.succeed();
    }

    /** Feats the chronicle counts give their titles when they reach the goal, and not before. */
    @GameTest(template = ARENA)
    public static void featsEarnTitles(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "TitleFeats");
        ChronicleData c = ChronicleModule.data(p);
        c.set(Feat.KILLS.id(), 99);
        TitleService.check(p);
        helper.assertTrue(c.hasTitle(def("first_hunt").key()), "one kill: First Blood");
        helper.assertTrue(!c.hasTitle(def("gate_hunter").key()), "99 kills is not 100");
        c.set(Feat.KILLS.id(), 100);
        c.set(Feat.BEASTS.id(), 150);
        c.set(Feat.CLUTCH.id(), 1);
        int earned = TitleService.check(p);
        helper.assertTrue(c.hasTitle(def("gate_hunter").key()) && c.hasTitle(def("fangbreaker").key()) && c.hasTitle(def("one_who_endured").key()),
                "100 kills, 150 beasts and a clutch kill give their titles (got " + earned + ")");
        helper.assertTrue(!c.hasTitle(def("reaper").key()), "1,000 kills are still far");
        // the legendary one is worn: the System picks the rarest
        helper.assertTrue(SystemAPI.get(p).title().equals(def("one_who_endured").key()), "the rarest earned title is worn, not " + SystemAPI.get(p).title());
        // the level is read from the System itself
        c.set(Feat.KILLS.id(), 0);
        helper.assertTrue(Feat.LEVEL.value(p, c) == SystemAPI.get(p).level(), "the level feat reads the hunter's level");
        helper.assertTrue(Feat.JOURNAL.value(p, c) == c.pages(), "the journal feat counts its pages");
        helper.succeed();
    }

    /** Every title is in a category, has a tier and a goal it can reach; the given ones have no feat. */
    @GameTest(template = ARENA)
    public static void titleTable(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        int secrets = 0;
        for (TitleDef d : Titles.all()) {
            helper.assertTrue(ids.add(d.id()), "title ids are unique: " + d.id());
            helper.assertTrue(d.key().startsWith("title.beyondsolo."), d.id() + " is a System title");
            if (d.feat() != null) helper.assertTrue(d.goal() > 0, d.id() + " has a goal");
            helper.assertTrue(!d.perks().isEmpty(), d.id() + " has a perk");
            for (PerkLine l : d.perks()) {
                helper.assertTrue(l.value() > 0, d.id() + " perk " + l.perk() + " is a bonus");
                helper.assertTrue((l.perk() == TitlePerk.FAMILY_DAMAGE) == (l.family() != null), d.id() + " names a family only for family damage");
            }
            if (d.secret()) secrets++;
        }
        helper.assertTrue(Titles.all().size() >= 50, "about fifty titles, got " + Titles.all().size());
        helper.assertTrue(secrets >= 1, "some titles are secret");
        for (TitleCategory cat : TitleCategory.values()) {
            boolean any = false;
            for (TitleDef d : Titles.all()) any |= d.category() == cat;
            helper.assertTrue(any, "a title in " + cat);
        }
        for (String given : List.of(Titles.ARBITER_SLAYER, Titles.RELENTLESS, Titles.CITADEL_CONQUEROR, Titles.RUSH_SOVEREIGN)) {
            helper.assertTrue(Titles.byKey(given) != null, "the titles features already gave are in the chronicle: " + given);
        }
        helper.assertTrue(Titles.tierOf("title.beyondsolo.not_a_title") == TitleTier.RARE, "a title the chronicle does not know is rare");
        helper.succeed();
    }

    // ================================================================ perks

    /** Attribute perks go on with the title and come off with it; event perks change XP, gold and damage. */
    @GameTest(template = ARENA)
    public static void perksFollowTheWornTitle(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "TitlePerks");
        String peerless = def("peerless").key();
        TitleService.grant(p, peerless);
        TitleService.equip(p, peerless, true);
        AttributeInstance attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(attack.getModifier(TitleService.modifierId(TitlePerk.MIGHT)) != null, "Peerless adds attack damage");
        helper.assertTrue(health.getModifier(TitleService.modifierId(TitlePerk.VITALITY)) != null, "Peerless adds health");

        String fangs = def("fangbreaker").key();
        TitleService.grant(p, fangs);
        TitleService.equip(p, fangs, true);
        helper.assertTrue(attack.getModifier(TitleService.modifierId(TitlePerk.MIGHT)) == null, "the old title's perks come off");
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        GateMonsterEntity beast = (GateMonsterEntity) Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(MonsterKind.GLOOMFANG), at, EnemyTraits.NONE, -1);
        GateMonsterEntity bug = (GateMonsterEntity) Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(MonsterKind.BROODLING), at, EnemyTraits.NONE, -1);
        try {
            helper.assertTrue(CreatureFamily.of(beast) == CreatureFamily.BEAST && CreatureFamily.of(bug) == CreatureFamily.ARTHROPOD, "families");
            helper.assertTrue(Math.abs(TitleService.damageBonus(p, beast) - 0.10f) < 1e-4, "Fangbreaker: +10% on beasts, got " + TitleService.damageBonus(p, beast));
            helper.assertTrue(TitleService.damageBonus(p, bug) == 0, "and nothing on insects");
        } finally {
            beast.discard();
            bug.discard();
        }

        String first = def("first_hunt").key();
        TitleService.grant(p, first);
        TitleService.equip(p, first, true);
        SystemEvent.XpGain gain = new SystemEvent.XpGain(p, 1000, BeyondSolo.id("test"));
        NeoForge.EVENT_BUS.post(gain);
        helper.assertTrue(gain.getAmount() == 1020, "First Blood: +2% System XP, got " + gain.getAmount());

        String walker = def("gate_walker").key();
        TitleService.grant(p, walker);
        TitleService.equip(p, walker, true);
        helper.assertTrue(TitleService.gold(p, 1000, com.beyondsolo.economy.Economy.Reason.GATE) == 1030, "Gate Walker: +3% gold from a gate");
        helper.assertTrue(TitleService.gold(p, 1000, com.beyondsolo.economy.Economy.Reason.values()[0]) >= 1000, "never less gold");

        String shield = def("shield_of_the_city").key();
        TitleService.grant(p, shield);
        TitleService.equip(p, shield, true);
        helper.assertTrue(Math.abs(TitleService.guard(p) - 0.08f) < 1e-4, "Shield of the City: 8% less damage taken");
        TitleService.equip(p, "", true);
        helper.assertTrue(TitleService.guard(p) == 0 && p.getAttribute(Attributes.ARMOR).getModifier(TitleService.modifierId(TitlePerk.ARMOR)) == null,
                "no title, no perks");
        helper.succeed();
    }

    // ================================================================ the journal

    /** Kills fill in pages and count up (by family too); a boss remembers the rank it was met at; realms are discovered. */
    @GameTest(template = ARENA)
    public static void journalFillsIn(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Journal");
        ChronicleData c = ChronicleModule.data(p);
        helper.assertTrue(JournalService.discover(p, "realm:hollows"), "entering a realm fills its page");
        helper.assertTrue(!JournalService.discover(p, "realm:hollows"), "only once");
        helper.assertTrue(!JournalService.discover(p, "realm:no_such_place"), "a page the journal lacks is ignored");

        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        GateMonsterEntity beast = (GateMonsterEntity) Enemies.spawn(helper.getLevel(), PlayerRank.D, EnemyModule.type(MonsterKind.GLOOMFANG), at, EnemyTraits.NONE, -1);
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(helper.getLevel());
        try {
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(beast, p.damageSources().playerAttack(p)));
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(beast, p.damageSources().playerAttack(p)));
            ChronicleData.Mark m = c.mark("creature:gloomfang");
            helper.assertTrue(m != null && m.kills == 2, "two gloomfangs slain, got " + (m == null ? "no page" : m.kills));
            helper.assertTrue(c.count(Feat.KILLS.id()) == 2 && c.count(Feat.BEASTS.id()) == 2, "kills and beasts counted");

            boss.configure(PlayerRank.C, BossKind.KNIGHT, true);
            boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            helper.assertTrue(JournalService.saw(p, boss), "seeing a boss fills its page");
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(boss, p.damageSources().playerAttack(p)));
            ChronicleData.Mark b = c.mark("boss:knight");
            helper.assertTrue(b != null && b.kills == 1, "the knight slain once");
            helper.assertTrue((b.forms & (1 << JournalEntry.formBit(PlayerRank.C))) != 0, "met at C-Rank");
            helper.assertTrue((b.forms & (1 << JournalEntry.ASCENDED_BIT)) != 0, "met ascended");
            helper.assertTrue(c.count(Feat.BOSSES.id()) == 1 && c.count(Feat.ASCENDED.id()) == 1, "a boss and an ascended boss counted");
        } finally {
            beast.discard();
            boss.discard();
        }
        helper.assertTrue(c.pages() == 3, "three pages filled in, got " + c.pages());
        helper.succeed();
    }

    /** The journal has a page for every creature, boss, realm and weapon, each with a name and lore in English. */
    @GameTest(template = ARENA)
    public static void journalTable(GameTestHelper helper) {
        helper.assertTrue(Journal.size(JournalCategory.CREATURES) == MonsterKind.values().length, "a page per creature");
        helper.assertTrue(Journal.size(JournalCategory.BOSSES) >= BossKind.values().length + 2, "a page per boss, the Arbiter and the lord");
        helper.assertTrue(Journal.size(JournalCategory.ARSENAL) >= 40, "the arsenal's weapons");
        for (MonsterKind k : MonsterKind.values()) helper.assertTrue(Journal.get("creature:" + k.id) != null, "a page for " + k.id);
        helper.assertTrue(Journal.get("boss:" + Journal.ARBITER) != null && Journal.get("boss:" + Journal.CITADEL_LORD) != null, "the Arbiter and the lord");
        helper.succeed();
    }

    // ================================================================ rankings

    /** The hunters' board has the rivals and the player; every board is sent, one per rush tier. */
    @GameTest(template = ARENA)
    public static void rankingsHaveTheRivals(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, "Ranked");
        Rankings.send(p);
        List<Rankings.Row> rows = Rankings.hunters(p.server);
        int rivals = 0;
        boolean me = false;
        long last = Long.MAX_VALUE;
        for (Rankings.Row r : rows) {
            if (r.rival()) rivals++;
            if (p.getUUID().equals(r.player())) me = true;
            if (r.beaten()) continue; // a national-level hunter the player beat stands just under them
            helper.assertTrue(r.value() <= last, "the board goes from strongest down");
            last = r.value();
        }
        helper.assertTrue(rivals == Rivals.ALL.size(), "every rival is on the board");
        helper.assertTrue(me, "the player is on the board");
        int place = Rankings.place(p);
        helper.assertTrue(place > 4 && place <= rows.size(), "a new D-Rank is behind the S-Ranks, at " + place);
        CompoundTag boards = Rankings.boards(p);
        for (String b : List.of(Rankings.HUNTERS, Rankings.CITADEL, Rankings.GATES)) {
            helper.assertTrue(!boards.getList(b, Tag.TAG_COMPOUND).isEmpty(), "board " + b + " is sent");
        }
        for (RushTier t : RushTier.values()) {
            helper.assertTrue(boards.getCompound(Rankings.RUSH).contains(t.id()), "the " + t.id() + " rush board is sent");
        }
        helper.assertTrue(!Rankings.rush(p.server, RushTier.E).isEmpty(), "rivals have rush times");
        helper.succeed();
    }

    // ================================================================ every line written

    /** Every title, perk, family, guild, board and page has its English line (the Spanish is checked by TranslationGameTests). */
    @GameTest(template = ARENA)
    public static void everyLineIsWritten(GameTestHelper helper) {
        Set<String> en = new HashSet<>();
        for (String ns : TranslationGameTests.namespaces()) {
            JsonObject o = TranslationGameTests.read(ns, "en_us");
            if (o != null) en.addAll(o.keySet());
        }
        List<String> missing = new ArrayList<>();
        for (TitleDef d : Titles.all()) {
            need(en, d.key(), missing);
            need(en, d.key() + ".desc", missing);
            for (PerkLine l : d.perks()) need(l.describe(), en, missing);
            need(d.tier().displayName(), en, missing);
            need(d.category().displayName(), en, missing);
        }
        for (CreatureFamily f : CreatureFamily.values()) {
            need(f.displayName(), en, missing);
            need(f.plural(), en, missing);
        }
        for (var r : Rivals.ALL) need(Rivals.guildName(r.guild()), en, missing);
        for (JournalCategory cat : JournalCategory.values()) need(cat.displayName(), en, missing);
        for (JournalEntry e : Journal.all()) {
            if (e.item() != null) need(en, BuiltInRegistries.ITEM.get(e.item()).getDescriptionId(), missing);
            else need(e.displayName(), en, missing);
            boolean lore = false;
            for (String k : e.loreKeys()) lore |= en.contains(k);
            if (!lore) missing.add(e.loreKey());
        }
        helper.assertTrue(missing.isEmpty(), missing.size() + " line(s) not written: " + String.join(", ", missing.subList(0, Math.min(12, missing.size()))));
        helper.succeed();
    }

    private static void need(Set<String> en, String key, List<String> missing) {
        if (!en.contains(key)) missing.add(key);
    }

    private static void need(Component c, Set<String> en, List<String> missing) {
        if (c.getContents() instanceof TranslatableContents t) {
            need(en, t.getKey(), missing);
            for (Object arg : t.getArgs()) if (arg instanceof Component inner) need(inner, en, missing);
        }
    }

    private ChronicleGameTests() {}
}
