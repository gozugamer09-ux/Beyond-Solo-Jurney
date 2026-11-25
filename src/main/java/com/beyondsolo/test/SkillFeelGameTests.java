package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.SkillFeel;
import com.beyondsolo.skill.SkillFeelTestAccess;
import com.beyondsolo.skill.SkillSounds;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.skill.WarriorPath;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How a skill's blows feel: their weights, the payloads that carry them to the players who see them, the skills' own
 * sounds, and {@code /solo skill feel}. What the screens do with it is client side (client.skill.SkillImpacts).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillFeelGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer warrior(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "feel"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        return p;
    }

    /** A zombie that stands still at {@code at}, with the player turned to face it. */
    private static Zombie zombie(GameTestHelper helper, ServerPlayer p, BlockPos at, float health) {
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        z.setHealth(health);
        var dodge = z.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) {
            dodge.removeModifiers();
            dodge.setBaseValue(0);
        }
        Vec3 d = z.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
        return z;
    }

    private static int weight(int pending) {
        return pending & 0xFF;
    }

    private static int flags(int pending) {
        return pending >> 8;
    }

    @GameTest(template = ARENA)
    public static void blowsAreWeighedByWhatTheyTook(GameTestHelper helper) {
        byte L = SkillFeel.LIGHT, M = SkillFeel.MEDIUM, H = SkillFeel.HEAVY, X = SkillFeel.MASSIVE;
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.02f, false, false, false, false) == L, "a scratch is light");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.12f, false, false, false, false) == M, "a tenth of a creature is medium");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.3f, false, false, false, false) == H, "a quarter is heavy");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.6f, false, false, false, false) == X, "half of it in one blow is massive");
        helper.assertTrue(SkillFeelTestAccess.weigh(H, 0.02f, false, false, false, false) == H, "a big skill's blow lands heavy even on a giant");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.02f, false, false, false, true) == X, "an ultimate's blow is massive");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.02f, true, false, false, false) == M, "felling a creature is at least medium");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.02f, true, false, true, false) == X, "felling a boss or elite is massive");
        helper.assertTrue(SkillFeelTestAccess.weigh(H, 0.05f, false, true, false, false) == L, "a storm's later ticks stay light");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.6f, false, true, false, false) == H, "a later tick that bites deep is heavy, not massive");
        helper.assertTrue(SkillFeelTestAccess.weigh(L, 0.02f, true, true, false, false) == M, "a later tick that fells it still lands");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void ultimatesAreTheSkillsThatEarnIt(GameTestHelper helper) {
        int actives = 0, ultimates = 0;
        for (Skill s : Skills.all()) {
            if (!s.isActive()) {
                helper.assertTrue(!SkillFeel.ultimate(s), s.path() + " is passive, never an ultimate");
                continue;
            }
            if (s.sovereign()) {
                helper.assertTrue(SkillFeel.ultimate(s), s.path() + " is a sovereign's power, so an ultimate");
                helper.assertTrue(SkillFeel.stature(s) == SkillFeel.HEAVY, s.path() + " lands heavy");
                continue;
            }
            actives++;
            if (SkillFeel.ultimate(s)) ultimates++;
        }
        float share = ultimates / (float) Math.max(1, actives);
        helper.assertTrue(share > 0.08f && share < 0.35f, "ultimates stay the big moments: " + ultimates + " of " + actives + " actives");
        List<String> none = new ArrayList<>();
        for (Subclass sub : Subclass.values()) {
            if (Skills.forSubclass(sub).stream().noneMatch(SkillFeel::ultimate)) none.add(sub.name());
        }
        helper.assertTrue(none.isEmpty(), "every subclass has an ultimate to call; none for " + none);
        helper.assertTrue(SkillFeel.ultimate(WarriorPath.SEVERING_LINE), "Severing Line is a Blademaster's ultimate");
        helper.assertTrue(!SkillFeel.ultimate(Skills.RENDING_ARC) && SkillFeel.stature(Skills.RENDING_ARC) == SkillFeel.LIGHT,
                "Rending Arc is a first skill: light");
        helper.succeed();
    }

    /** Rending Arc's first cut is weighed as it lands and goes out at the end of the tick; its second follows. */
    @GameTest(template = ARENA)
    public static void aSkillsBlowsGoOutAsTheyLand(GameTestHelper helper) {
        ServerPlayer p = warrior(helper);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1), 2000);
        SkillAPI.learn(p, Skills.RENDING_ARC, 1, SkillEvent.Source.OTHER);
        SystemAPI.restoreMana(p, 100_000);
        int sentBefore = SkillFeelTestAccess.sent();
        helper.assertTrue(SkillAPI.cast(p, Skills.RENDING_ARC), "Rending Arc goes off");
        int first = SkillFeelTestAccess.pending(p, z);
        helper.assertTrue(first >= 0, "the cut that landed is waiting for the end of the tick");
        helper.assertTrue(weight(first) == SkillFeel.LIGHT && flags(first) == 0, "a first skill's cut on a 2000-health zombie is light, got " + first);
        helper.assertTrue(z.getHealth() < 2000, "it did hurt");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(SkillFeelTestAccess.pending(p, z) < 0, "sent at the end of the tick");
            helper.assertTrue(SkillFeelTestAccess.sent() > sentBefore, "a payload went out");
            int afterFirst = SkillFeelTestAccess.sent();
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(SkillFeelTestAccess.sent() > afterFirst, "the second cut went out too");
                z.discard();
                helper.succeed();
            });
        });
    }

    /** The same skill on the same creature a moment later is a repeat; half a creature at once is massive; a kill is marked. */
    @GameTest(template = ARENA)
    public static void repeatsKillsAndBigBlowsAreMarked(GameTestHelper helper) {
        ServerPlayer p = warrior(helper);
        Zombie a = zombie(helper, p, new BlockPos(3, 2, 1), 100);
        Zombie b = zombie(helper, p, new BlockPos(3, 2, 3), 20);
        Zombie c = zombie(helper, p, new BlockPos(1, 2, 3), 20);
        Skill arc = Skills.RENDING_ARC;
        SkillFeel.struck(p, arc, SkillElement.PHYSICAL, a, a.getHealth());
        int one = SkillFeelTestAccess.pending(p, a);
        helper.assertTrue(weight(one) == SkillFeel.LIGHT && flags(one) == 0, "a first touch is light, got " + one);
        // half of it at once
        b.setHealth(8);
        SkillFeel.struck(p, arc, SkillElement.PHYSICAL, b, 20);
        helper.assertTrue(weight(SkillFeelTestAccess.pending(p, b)) == SkillFeel.MASSIVE, "12 of 20 health in one blow is massive");
        // felled
        c.setHealth(0);
        SkillFeel.struck(p, arc, SkillElement.PHYSICAL, c, 3);
        int kill = SkillFeelTestAccess.pending(p, c);
        helper.assertTrue((flags(kill) & SkillFeel.KILL) != 0 && weight(kill) >= SkillFeel.MEDIUM, "a felled zombie is marked and lands, got " + kill);
        c.discard();
        // an ultimate's blow
        SkillFeel.struck(p, WarriorPath.SEVERING_LINE, SkillElement.PHYSICAL, b, b.getHealth());
        helper.assertTrue(weight(SkillFeelTestAccess.pending(p, b)) == SkillFeel.MASSIVE, "an ultimate's blow is massive");
        helper.runAfterDelay(1, () -> {
            SkillFeel.struck(p, arc, SkillElement.PHYSICAL, a, a.getHealth());
            int again = SkillFeelTestAccess.pending(p, a);
            helper.assertTrue((flags(again) & SkillFeel.REPEAT) != 0, "the same skill a tick later is a repeat, got " + again);
            helper.runAfterDelay(20, () -> {
                SkillFeel.struck(p, arc, SkillElement.PHYSICAL, a, a.getHealth());
                int later = SkillFeelTestAccess.pending(p, a);
                helper.assertTrue(later >= 0 && (flags(later) & SkillFeel.REPEAT) == 0, "a second apart it is a fresh blow, got " + later);
                a.discard();
                b.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void skillsSoundLikeThemselves(GameTestHelper helper) {
        JsonObject table = read("/assets/" + BeyondSolo.MOD_ID + "/sounds.json");
        helper.assertTrue(table != null, "sounds.json is in the jar");
        Set<String> english = new HashSet<>();
        for (String ns : TranslationGameTests.namespaces()) {
            JsonObject en = TranslationGameTests.read(ns, "en_us");
            if (en != null) english.addAll(en.keySet());
        }
        List<String> problems = new ArrayList<>();
        int files = 0;
        for (Map.Entry<String, JsonElement> e : table.entrySet()) {
            if (!e.getKey().startsWith("skill.")) continue;
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(BeyondSolo.id(e.getKey()))) problems.add(e.getKey() + " is not registered");
            JsonObject entry = e.getValue().getAsJsonObject();
            String subtitle = entry.get("subtitle").getAsString();
            if (!english.contains(subtitle)) problems.add(subtitle + " has no English line");
            for (JsonElement s : entry.getAsJsonArray("sounds")) {
                ResourceLocation file = ResourceLocation.parse(s.getAsJsonObject().get("name").getAsString());
                String path = "/assets/" + file.getNamespace() + "/sounds/" + file.getPath() + ".ogg";
                if (BeyondSolo.class.getResource(path) == null) problems.add(path + " is missing");
                files++;
            }
        }
        helper.assertTrue(problems.isEmpty(), "skill sounds: " + problems);
        helper.assertTrue(files >= 70, "every take is in the jar (" + files + ")");
        Set<ResourceLocation> seen = new HashSet<>();
        for (SkillElement el : SkillElement.values()) {
            seen.add(BuiltInRegistries.SOUND_EVENT.getKey(SkillSounds.impact(el)));
            seen.add(BuiltInRegistries.SOUND_EVENT.getKey(SkillSounds.cast(el)));
        }
        helper.assertTrue(seen.size() == SkillElement.values().length * 2, "every element sounds like itself, leaving and landing");
        // the vanilla sounds the skills borrowed are swapped for their own while a skill runs
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.GENERIC_EXPLODE.value()) == SkillSounds.BLAST, "a blast");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.ANVIL_LAND) == SkillSounds.SLAM, "a slam");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.BEACON_ACTIVATE) == SkillSounds.CHARGE, "power gathering");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.TRIDENT_THUNDER.value()) == SkillSounds.THUNDER, "thunder");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.PLAYER_ATTACK_SWEEP) == SkillSounds.SWING, "a swing");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.CROSSBOW_SHOOT) == SkillSounds.SHOT, "a shot");
        helper.assertTrue(SkillFeelTestAccess.swapFor(SoundEvents.ZOMBIE_AMBIENT) == null, "everything else is left alone");
        helper.succeed();
    }

    /** A skill's later blasts (scheduled from its action) still sound like the skill; other tasks don't. */
    @GameTest(template = ARENA)
    public static void whatASkillSchedulesKeepsItsSounds(GameTestHelper helper) {
        boolean[] seen = new boolean[3];
        helper.assertTrue(!SkillSounds.inSkill(), "no skill is running between ticks");
        SkillFeelTestAccess.inSkill(() -> ServerScheduler.schedule(2, () -> {
            seen[0] = SkillSounds.inSkill();
            ServerScheduler.schedule(2, () -> seen[1] = SkillSounds.inSkill());
        }));
        helper.assertTrue(!SkillSounds.inSkill(), "the skill's turn ended");
        ServerScheduler.schedule(2, () -> seen[2] = !SkillSounds.inSkill());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(seen[0], "what the skill scheduled runs as the skill");
            helper.assertTrue(seen[1], "and so does what that scheduled");
            helper.assertTrue(seen[2], "a task of anything else is not a skill");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void soloSkillFeelShowsABlow(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = warrior(helper);
        Zombie z = zombie(helper, p, new BlockPos(3, 2, 1), 20);
        helper.assertTrue(run(p, "solo skill feel heavy fire") == 1, "a heavy fire blow on the zombie");
        helper.assertTrue(run(p, "solo skill feel kill") == 1, "a felling blow, physical");
        helper.assertTrue(run(p, "solo skill feel ultimate shadow") == 1, "an ultimate's blow");
        helper.assertTrue(z.getHealth() == 20 && z.isAlive(), "nothing dealt");
        p.setYRot(p.getYRot() + 180);
        p.setYHeadRot(p.getYRot());
        try {
            run(p, "solo skill feel massive");
            helper.fail("looking away, there is nothing to strike");
        } catch (CommandSyntaxException e) {
            String key = e.getRawMessage() instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
            helper.assertTrue(key.equals("command.beyondsolo.solo.error.nothing_in_sight"), "says so, got " + key);
        }
        z.discard();
        helper.succeed();
    }

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    private static JsonObject read(String path) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream(path)) {
            if (in == null) return null;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private SkillFeelGameTests() {}
}
