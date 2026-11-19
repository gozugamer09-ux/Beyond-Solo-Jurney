package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.light.Chapel;
import com.beyondsolo.light.Covenant;
import com.beyondsolo.light.EnvoyEntity;
import com.beyondsolo.light.LightAPI;
import com.beyondsolo.light.LightConfig;
import com.beyondsolo.light.LightConstruct;
import com.beyondsolo.light.LightModule;
import com.beyondsolo.light.LightSanctum;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.light.Vigil;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.util.OnlinePlayers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The light, a hunter's side of it: the chapel hidden in the awakening temple and the Envoy's covenant, waking sworn to
 * the light, its powers on the crown keys, the call and the vigil, the Investiture and its mantles, and the one road
 * or the other (a crown or the light, never both). Powers that reach creatures are tried on a stone floor far from
 * every other test. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightGameTests {
    private static final String ARENA = "evaluation_zone";
    /** Each far floor gets its own spot, so tests running side by side never meet. */
    private static int plots;

    /** An evaluated C-rank hunter of this class, with nothing of the light or a crown. */
    private static ServerPlayer hunter(GameTestHelper helper, PlayerClass c) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dawnsworn"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        LightAPI.reset(p);
        MonarchAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, c);
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** A brand new player: nothing bound, nothing evaluated. */
    private static ServerPlayer newcomer(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "seeker"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        AwakeningService.reset(p);
        LightAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        p.getInventory().clearContent();
        OnlinePlayers.addTestPlayer(helper, p);
        return p;
    }

    /** The awakening temple sealed for this player. */
    private static GateInstance temple(GameTestHelper helper, ServerPlayer p) {
        GateBlockEntity gate = GateService.spawnSealedGate(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 5)), Direction.NORTH, p);
        return GateData.get(helper.getLevel().getServer()).get(gate.gateId());
    }

    private static void refill(ServerPlayer p) {
        SystemAPI.restoreMana(p, 100_000);
        SkillAPI.resetCooldowns(p);
        SystemAPI.addExhaustion(p, -100);
    }

    private static void face(ServerPlayer p, LivingEntity e) {
        Vec3 d = e.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    /**
     * A stone floor far from every other test, its chunk held loaded (fake players load none), and the hunter on it.
     * Returns the floor's middle; {@link #leave} lets the chunk go.
     */
    private static BlockPos far(GameTestHelper helper, ServerPlayer p) {
        ServerLevel level = helper.getLevel();
        int k;
        synchronized (LightGameTests.class) {
            k = plots++;
        }
        // the middle of a chunk (41 000 is 8 past a chunk's edge, and 96 is six chunks), so the floor stays in it
        BlockPos c = new BlockPos(41_000 + 96 * (k % 40), 180, 41_000 + 96 * (k / 40));
        ChunkPos chunk = new ChunkPos(c);
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.assertTrue(TestChunks.ticking(level, List.of(chunk)), "the far floor is loaded");
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) level.setBlock(c.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        // a fake player's teleportTo does nothing (its connection ignores it): moved directly
        p.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
        return c;
    }

    private static void leave(GameTestHelper helper, BlockPos c) {
        ChunkPos chunk = new ChunkPos(c);
        helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
    }

    /** A husk standing still on the far floor (a husk, so the sun never burns it), never dodging. */
    private static Mob dummy(GameTestHelper helper, BlockPos at) {
        Mob m = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(m != null, "a husk");
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        m.setNoAi(true);
        var dodge = m.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) dodge.setBaseValue(0);
        m.setPersistenceRequired();
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    // ================================================================ the chapel and the covenant

    /** Every awakening temple hides the chapel: walled off its antechamber, with its sign and the Envoy's altar. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theTempleHidesTheChapel(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        GateInstance g = temple(helper, p);
        GateService.buildNow(helper.getLevel().getServer(), g);
        helper.assertTrue(g.isTrial() && g.built(), "the temple is built");
        helper.assertTrue(Chapel.has(g) && g.depths().hidden(g) == HiddenKind.CHAPEL, "it hides the Envoy's chapel");
        helper.assertTrue(!g.depths().hiddenOpen(), "behind a wall that passes for rock");
        helper.assertTrue(DepthsBuild.sealMouth(g) != null, "the wall is in the rock");
        helper.assertTrue(g.layout().hidden() != g.layout().boss(), "a second room, not the Arbiter's hall");
        g.setState(GateInstance.State.CLOSED);
        helper.succeed();
    }

    /**
     * Turned away, the way stays open; sworn, the System binds in the light instead of the Arbiter's dark: the hunter
     * wakes with it, the light's first power and the title, and evaluation later keeps it all.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void swearingAtTheChapelWakesTheHunterInTheLight(GameTestHelper helper) {
        ServerPlayer p = newcomer(helper);
        GateInstance g = temple(helper, p);
        ServerLevel d = GateService.dungeon(helper.getLevel().getServer());
        helper.assertTrue(GateService.arrive(p, g), "into the temple");
        helper.assertTrue(AwakeningService.trialAt(p) == g, "in the trial");
        // the chapel's room held loaded while the test walks it
        BlockPos room = g.world(g.layout().hidden().center());
        ChunkPos chunk = new ChunkPos(room);
        List<ChunkPos> forced = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                ChunkPos c = new ChunkPos(chunk.x + x, chunk.z + z);
                if (!d.getForcedChunks().contains(c.toLong())) {
                    d.setChunkForced(c.x, c.z, true);
                    forced.add(c);
                }
            }
        }
        helper.assertTrue(TestChunks.ticking(d, List.of(chunk)), "the chapel is loaded");
        // three knocks bring the wall down (here at once)
        DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
        HiddenRooms.openAt(d, BlockPos.containing(m.center()), p);
        helper.assertTrue(g.depths().hiddenOpen(), "the wall is down");
        EnvoyEntity envoy = Chapel.envoyOf(d, g);
        helper.assertTrue(envoy != null, "the Envoy of Dawn waits over the altar");
        DepthsBuild.Door altar = DepthsBuild.chapelAltar(d, g);
        BlockPos before = altar.pos().relative(altar.facing(), 3);
        p.moveTo(before.getX() + 0.5, before.getY() + 0.5, before.getZ() + 0.5, 0, 0);

        Covenant.answer(p, 0);
        helper.assertTrue(LightAPI.get(p).refused() && !LightAPI.sworn(p), "turned away: not sworn");
        helper.assertTrue(!AwakeningService.inSequence(p) && !SystemAPI.hasSystem(p), "the Arbiter still waits");
        Covenant.answer(p, 1);
        helper.assertTrue(LightAPI.sworn(p), "came back and swore");
        helper.assertTrue(AwakeningService.inSequence(p) && AwakeningService.stage(p) == AwakeningData.Stage.AWAKENING, "the light's awakening");
        AwakeningService.finishNow(p);
        helper.assertTrue(SystemAPI.hasSystem(p) && AwakeningService.stage(p) == AwakeningData.Stage.DONE, "the System is theirs");
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.JUDGEMENT), "the light's first power comes with it");
        helper.assertTrue(SkillAPI.get(p).slotOf(RulerPowers.JUDGEMENT.path()) < 0, "on a crown key, not the skill bar");
        helper.assertTrue(TitleService.data(p).hasTitle(LightModule.VESSEL), "the title of one who woke sworn");
        helper.assertTrue(p.level() == helper.getLevel(), "back in the world");

        SystemAPI.awaken(p, PlayerRank.D, PlayerClass.HEALER);
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.JUDGEMENT) && LightAPI.sworn(p), "evaluation keeps the light");
        for (ChunkPos c : forced) d.setChunkForced(c.x, c.z, false);
        helper.succeed();
    }

    // ================================================================ the powers

    /** The powers come only from the light: no class lists them, and without it they never fire. */
    @GameTest(template = ARENA)
    public static void powersComeOnlyFromTheLight(GameTestHelper helper) {
        helper.assertTrue(Skills.rulerPowers().size() == 7, "three powers and four authorities: " + Skills.rulerPowers().size());
        helper.assertTrue(Skills.monarchPowers().size() == 7, "the monarchs' own are untouched");
        for (PlayerClass c : PlayerClass.values()) {
            helper.assertTrue(Skills.forClass(c).stream().noneMatch(Skill::ruler), c + " lists no ruler power");
            helper.assertTrue(Mantle.leaning(c) != null, c + " leans to a mantle");
        }
        for (Subclass sub : Subclass.values()) {
            helper.assertTrue(Skills.forSubclass(sub).stream().noneMatch(Skill::ruler), sub + " lists no ruler power");
        }
        for (Mantle m : Mantle.values()) {
            helper.assertTrue(RulerPowers.onKey(m, 3) == RulerPowers.authority(m), "the fourth key is " + m.id() + "'s authority");
            helper.assertTrue(RulerPowers.onKey(m, 0) == RulerPowers.SANCTUM, "the first key is the sanctum");
        }
        helper.assertTrue(RulerPowers.onKey(null, 1) == RulerPowers.JUDGEMENT && RulerPowers.onKey(null, 2) == RulerPowers.SERAPH, "judgement, then seraph form");
        for (Skill s : Skills.rulerPowers()) helper.assertTrue(s.ruler() && s.sovereign() && !s.monarch(), s.path() + " is the light's");
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        // learned without the light (a command, a stray book): it still won't fire
        SkillAPI.learn(p, RulerPowers.JUDGEMENT, 1, SkillEvent.Source.OTHER);
        refill(p);
        helper.assertTrue(!SkillAPI.cast(p, RulerPowers.JUDGEMENT), "not sworn, no power");
        helper.assertTrue(!RulerPowers.castKey(p, 1), "the crown keys do nothing for them");
        helper.succeed();
    }

    /** Judgement: a lance onto every foe round them; the judged take more from every blow for a while. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void judgementMarksAndStrikes(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        LightAPI.awoken(p);
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.JUDGEMENT) && !SkillAPI.knows(p, RulerPowers.SANCTUM), "judgement first, the sanctum later");
        BlockPos c = far(helper, p);
        Mob husk = dummy(helper, c.offset(0, 0, 4));
        float full = husk.getHealth();
        refill(p);
        helper.assertTrue(RulerPowers.castKey(p, 1), "judgement goes off");
        helper.runAfterDelay(RulerPowers.JUDGEMENT_FALL + 6, () -> {
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < full, "the lance lands");
            if (husk.isAlive()) {
                helper.assertTrue(LightSanctum.judged(husk), "judged");
                helper.assertTrue(LightSanctum.judgedBonus(husk) > 1.1f, "takes more from every blow: " + LightSanctum.judgedBonus(husk));
            }
            helper.assertTrue(LightAPI.get(p).judged() >= 1, "remembered");
            husk.discard();
            leave(helper, c);
            helper.succeed();
        });
    }

    /** The sanctum: a dome that goes where the ruler goes; they hit harder and take less, foes inside burn under the mantle's law. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theSanctumShieldsAndBurns(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.GUARDIAN);
        LightAPI.invest(p, Mantle.VERDICT);
        BlockPos c = far(helper, p);
        Mob husk = dummy(helper, c.offset(3, 0, 0));
        float full = husk.getHealth();
        refill(p);
        helper.assertTrue(RulerPowers.castKey(p, 0), "the sanctum is raised");
        helper.assertTrue(LightSanctum.hasSanctum(p), "it holds");
        helper.assertTrue(LightSanctum.might(p) > 1.05f, "they hit harder inside: " + LightSanctum.might(p));
        helper.assertTrue(LightSanctum.guard(p) < 0.95f && LightSanctum.guard(p) > 0.3f, "they take less, never nothing: " + LightSanctum.guard(p));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < full, "the light burns the foe inside");
            if (husk.isAlive()) helper.assertTrue(LightSanctum.judged(husk), "the Verdict's law: everyone inside stands judged");
            LightSanctum.end(p);
            helper.assertTrue(!LightSanctum.hasSanctum(p) && Math.abs(LightSanctum.guard(p) - 1f) < 1.0e-4f, "ended, nothing left on them");
            husk.discard();
            leave(helper, c);
            helper.succeed();
        });
    }

    /** Seraph form: wings and a halo for a while, harder blows, less taken, faster; never untouchable. */
    @GameTest(template = ARENA)
    public static void seraphFormLiftsButNeverShields(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.RANGER);
        LightAPI.awoken(p);
        helper.assertTrue(!SkillAPI.knows(p, RulerPowers.SERAPH), "not at level 1");
        SystemAPI.setLevel(p, LightConfig.seraphLevel());
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.SERAPH), "learned on the road");
        float speed = (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        refill(p);
        helper.assertTrue(RulerPowers.castKey(p, 2), "seraph form");
        helper.assertTrue(LightSanctum.inSeraph(p), "in it");
        helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed * 1.1f, "faster");
        helper.assertTrue(LightSanctum.guard(p) < 1f && LightSanctum.guard(p) >= 0.4f, "takes less, still takes: " + LightSanctum.guard(p));
        helper.assertTrue(!p.isInvulnerable(), "never untouchable");
        LightSanctum.end(p);
        helper.assertTrue(!LightSanctum.inSeraph(p) && Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-4, "back as they were");
        helper.succeed();
    }

    /** The Verdict's Blade of Heaven: a sword the size of a tower comes down where they look. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theBladeOfHeavenFalls(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        LightAPI.invest(p, Mantle.VERDICT);
        BlockPos c = far(helper, p);
        Mob husk = dummy(helper, c.offset(0, 0, 5));
        float full = husk.getHealth();
        face(p, husk);
        refill(p);
        helper.assertTrue(RulerPowers.castKey(p, 3), "the Verdict's authority");
        AABB box = new AABB(c).inflate(40);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(LightConstruct.class, box, e -> e.form() == LightConstruct.Form.BLADE).isEmpty(),
                "the blade comes down");
        helper.runAfterDelay(RulerPowers.BLADE_FALL + 6, () -> {
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < full, "it lands on them");
            helper.assertTrue(LightAPI.get(p).authorities() >= 1, "the authority is remembered");
            husk.discard();
            for (LightConstruct e : helper.getLevel().getEntitiesOfClass(LightConstruct.class, box)) e.discard();
            leave(helper, c);
            helper.succeed();
        });
    }

    // ================================================================ the call, the vigil, the Investiture

    /** The light calls the sworn who are ready: the job changed, the level reached. With the call: the feather and the quest. */
    @GameTest(template = ARENA)
    public static void theLightCallsTheReady(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.ASSASSIN);
        helper.assertTrue(!LightAPI.eligible(p), "not sworn");
        LightAPI.awoken(p);
        helper.assertTrue(!LightAPI.eligible(p) && !LightAPI.get(p).called(), "too weak yet");
        JobAPI.changeJob(p, Subclass.of(PlayerClass.ASSASSIN).stream().filter(s -> !s.rare()).findFirst().orElseThrow());
        helper.assertTrue(!LightAPI.get(p).called(), "still too weak");
        SystemAPI.setLevel(p, LightAPI.minLevel());
        helper.assertTrue(LightAPI.get(p).called(), "reaching the level calls them");
        helper.assertTrue(p.getInventory().contains(new ItemStack(LightModule.FEATHER.get())), "the Envoy's feather");
        helper.assertTrue(QuestEngine.data(p).has(LightModule.QUEST_TEMPLATE), "the Investiture's quest");
        helper.assertTrue(!LightAPI.callIfReady(p), "called only once");
        LightAPI.invest(p, Mantle.HAND);
        helper.assertTrue(!LightAPI.eligible(p), "a ruler is not called again");
        helper.assertTrue(!Vigil.begin(p), "the feather is quiet for a ruler");
        helper.succeed();
    }

    /** The vigil: the light gathers, three waves of the dark, then the Envoy lays the mantles before them. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theVigilLeadsToTheInvestiture(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        LightAPI.awoken(p);
        helper.assertTrue(!Vigil.begin(p), "too weak for the vigil");
        helper.assertTrue(Vigil.begin(p, true), "the vigil begins (forced)");
        helper.assertTrue(Vigil.keeping(p) && Vigil.wave(p) == 0, "the light gathers");
        helper.assertTrue(LightAPI.get(p).called() && QuestEngine.data(p).has(LightModule.QUEST_TEMPLATE), "keeping it calls them");
        AABB near = p.getBoundingBox().inflate(Vigil.RADIUS + 12);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(EnvoyEntity.class, near).isEmpty(), "the Envoy comes down over the ring");
        for (int wave = 1; wave <= Vigil.WAVES; wave++) {
            Vigil.advance(p);
            helper.assertTrue(Vigil.wave(p) == wave, "wave " + wave + ": " + Vigil.wave(p));
            Vigil.advance(p);
        }
        helper.assertTrue(Vigil.choosing(p), "the mantles are laid before them");
        Vigil.choose(p, Mantle.DAWN);
        helper.assertTrue(LightAPI.invested(p) && LightAPI.mantle(p) == Mantle.DAWN, "a ruler of Dawn");
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.SANCTUM) && SkillAPI.knows(p, RulerPowers.DAYBREAK), "the sanctum and Dawn's authority");
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.SERAPH), "every ruler has seraph form");
        helper.assertTrue(TitleService.data(p).hasTitle(Mantle.DAWN.titleKey()), "the mantle's title");
        helper.assertTrue(!QuestEngine.data(p).has(LightModule.QUEST_TEMPLATE) || QuestEngine.data(p).active().stream()
                .noneMatch(q -> q.template().equals(LightModule.QUEST_TEMPLATE)), "the Investiture's quest is done");
        Vigil.cancel(p);
        for (EnvoyEntity e : helper.getLevel().getEntitiesOfClass(EnvoyEntity.class, near)) e.discard();
        helper.succeed();
    }

    /** Walking out of the ring fails the vigil; the light waits a while before it can be kept again. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void leavingTheRingFailsTheVigil(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.RANGER);
        LightAPI.awoken(p);
        BlockPos c = far(helper, p);
        helper.assertTrue(Vigil.begin(p, true), "the vigil begins");
        p.moveTo(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + Vigil.RADIUS + 5.5, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(!Vigil.keeping(p), "the vigil fails");
            helper.assertTrue(LightAPI.get(p).retryAt() > helper.getLevel().getServer().overworld().getGameTime(), "the light waits");
            helper.assertTrue(!Vigil.begin(p) && !Vigil.keeping(p), "not again yet");
            for (EnvoyEntity e : helper.getLevel().getEntitiesOfClass(EnvoyEntity.class, new AABB(c).inflate(30))) e.discard();
            leave(helper, c);
        });
    }

    // ================================================================ one road or the other

    /** Rulers and monarchs: the mantle and its strength, another mantle's authority, and never a crown with the light. */
    @GameTest(template = ARENA)
    public static void aMantleOrACrownNeverBoth(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        float health = p.getMaxHealth();
        float mana = SystemAPI.get(p).maxMana();
        LightAPI.awoken(p);
        LightAPI.invest(p, Mantle.WINGS);
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.SANCTUM) && SkillAPI.knows(p, RulerPowers.CHOIR_OF_WINGS), "Wings' authority");
        helper.assertTrue(!SkillAPI.knows(p, RulerPowers.BLADE_OF_HEAVEN), "not another mantle's");
        helper.assertTrue(p.getMaxHealth() > health * 1.15f, "a ruler's health: " + health + " -> " + p.getMaxHealth());
        helper.assertTrue(SystemAPI.get(p).maxMana() >= mana + 100, "a ruler's mana: " + mana + " -> " + SystemAPI.get(p).maxMana());
        helper.assertTrue(TitleService.data(p).hasTitle(Mantle.WINGS.titleKey()), "the mantle's title");

        LightAPI.invest(p, Mantle.VERDICT);
        helper.assertTrue(SkillAPI.knows(p, RulerPowers.BLADE_OF_HEAVEN) && !SkillAPI.knows(p, RulerPowers.CHOIR_OF_WINGS), "the Verdict's replaces Wings'");

        // the throne never takes one sworn to the light
        TowerAPI.data(p).sigil = true;
        JobAPI.changeJob(p, Subclass.of(PlayerClass.WARRIOR).stream().filter(s -> !s.rare()).findFirst().orElseThrow());
        SystemAPI.setLevel(p, Math.max(MonarchAPI.minLevel(), LightAPI.minLevel()));
        helper.assertTrue(!MonarchAPI.eligible(p) && !MonarchAPI.get(p).called(), "the throne never calls them");
        helper.assertTrue(!MonarchAPI.raiseSigil(p), "the sigil stays dark");

        // a crown (a command) sets them free of the light; the light (a command) takes a crown back
        MonarchAPI.crown(p, Dominion.NIGHT);
        helper.assertTrue(MonarchAPI.crowned(p) && !LightAPI.sworn(p), "crowned, no longer sworn");
        helper.assertTrue(Skills.rulerPowers().stream().noneMatch(s -> SkillAPI.knows(p, s)), "every power of the light forgotten");
        LightAPI.invest(p, Mantle.HAND);
        helper.assertTrue(!MonarchAPI.crowned(p) && LightAPI.invested(p), "invested, the crown taken back");
        helper.assertTrue(MonarchPowers.authority(Dominion.NIGHT) != null && !SkillAPI.knows(p, MonarchPowers.authority(Dominion.NIGHT)), "the crown's powers gone");
        LightAPI.reset(p);
        // (the titles stay: they were earned, and their perks with them)
        helper.assertTrue(!LightAPI.sworn(p) && p.getAttribute(Attributes.MAX_HEALTH).getModifier(BeyondSolo.id("light/health")) == null
                && p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(BeyondSolo.id("light/damage")) == null, "reset: the ruler's strength goes with the light");
        helper.succeed();
    }

    // ================================================================ the light's shapes

    /** Every one of the light's models has its geometry, skin, glow and every animation the game asks for. */
    @GameTest(template = ARENA)
    public static void everyShapeOfTheLightIsModelled(GameTestHelper helper) {
        Map<String, List<String>> anims = new java.util.LinkedHashMap<>();
        anims.put("light_envoy", List.of("idle", "leave", "talk", "bless"));
        anims.put("light_seraph", List.of("idle"));
        for (LightConstruct.Form f : LightConstruct.Form.values()) {
            List<String> names = switch (f) {
                case HAND -> List.of("open", "close", "fist");
                case SUN, CHOIR -> List.of("rise", "idle");
                default -> List.of("fall", "land", "stand");
            };
            anims.put("light_" + f.id(), names);
        }
        for (Map.Entry<String, List<String>> e : anims.entrySet()) {
            String n = e.getKey();
            helper.assertTrue(json("geo/entity/" + n + ".geo.json") != null, n + " has its geometry");
            helper.assertTrue(exists("textures/entity/" + n + "_main.png") && exists("textures/entity/" + n + "_main_glowmask.png"), n + " has its skin and glow");
            JsonObject a = json("animations/entity/" + n + ".animation.json");
            helper.assertTrue(a != null && a.has("animations"), n + " has its animations");
            for (String anim : e.getValue()) {
                String key = "animation.beyondsolo." + n + "." + anim;
                helper.assertTrue(a.getAsJsonObject("animations").has(key), n + " plays " + key);
            }
        }
        for (Skill s : Skills.rulerPowers()) {
            helper.assertTrue(exists("textures/gui/icon/skill_" + s.path() + ".png"), s.path() + " has its icon");
        }
        for (Mantle m : Mantle.values()) helper.assertTrue(exists("textures/gui/icon/light_mantle_" + m.id() + ".png"), m.id() + " has its emblem");
        helper.assertTrue(exists("textures/item/envoy_feather.png") && json("models/item/envoy_feather.json") != null, "the feather has its look");
        helper.succeed();
    }

    private static boolean exists(String path) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream("/assets/" + BeyondSolo.MOD_ID + "/" + path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static JsonObject json(String path) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream("/assets/" + BeyondSolo.MOD_ID + "/" + path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private LightGameTests() {}
}
