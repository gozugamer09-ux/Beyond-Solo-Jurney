package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchDomain;
import com.beyondsolo.monarch.MonarchModule;
import com.beyondsolo.monarch.ThroneHall;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monarch ascension: the call, the crown and what it gives, the powers, the throne trial. Powers that reach other
 * creatures are tried in the throne's hall, far from the other tests. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MonarchGameTests {
    private static final String ARENA = "evaluation_zone";

    /** An evaluated C-rank hunter of this class, with nothing of a monarch. */
    private static ServerPlayer hunter(GameTestHelper helper, PlayerClass c) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pretender"));
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
        SystemAPI.awaken(p, PlayerRank.C, c);
        return p;
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

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0e-4f;
    }

    // ================================================================ the hall, for powers that reach others

    /**
     * Takes the hunter into the throne's hall (far from every other test) and holds its chunks loaded, since fake
     * players load none. Returns the chunks this test forced, for {@link #release}.
     */
    private static List<ChunkPos> hall(GameTestHelper helper, ServerPlayer p) {
        helper.assertTrue(ThroneTrial.begin(p, true), "into the hall");
        ServerLevel level = p.serverLevel();
        // the hunter starts on the south side of the hall, 18 blocks from its middle
        BlockPos c = p.blockPosition().offset(0, 0, -18);
        int r = ThroneHall.RADIUS + 3;
        List<ChunkPos> all = new ArrayList<>();
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                all.add(chunk);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(chunk);
                }
            }
        }
        helper.assertTrue(TestChunks.ticking(level, all), "the hall is loaded");
        return forced;
    }

    /** Raises the court's first wave and brings it round the hunter, held still. */
    private static List<LivingEntity> court(GameTestHelper helper, ServerPlayer p) {
        ThroneTrial.jump(p, "1", null);
        List<LivingEntity> court = new ArrayList<>(ThroneTrial.foes(p));
        helper.assertTrue(court.size() >= 4, "the court rises: " + court.size());
        for (int i = 0; i < court.size(); i++) {
            LivingEntity e = court.get(i);
            double a = i * Math.PI * 2 / court.size();
            e.teleportTo(p.getX() + Math.cos(a) * 4, p.getY(), p.getZ() + Math.sin(a) * 4);
            if (e instanceof Mob m) m.setNoAi(true);
        }
        return court;
    }

    /** Ends what the test left running: the domain, the trial (the hunter goes home) and the forced chunks. */
    private static void release(ServerPlayer p, List<ChunkPos> forced) {
        ServerLevel level = p.serverLevel();
        MonarchDomain.end(p);
        ThroneTrial.cancel(p);
        for (ChunkPos c : forced) level.setChunkForced(c.x, c.z, false);
    }

    private static boolean hurt(List<LivingEntity> court) {
        for (LivingEntity e : court) if (!e.isAlive() || e.getHealth() < e.getMaxHealth()) return true;
        return false;
    }

    // ================================================================ the crown

    /** The crown: the three powers every crown gives and its own authority, a monarch's strength, its title. */
    @GameTest(template = ARENA)
    public static void crownGivesPowersStrengthAndTitle(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        float health = p.getMaxHealth();
        float mana = SystemAPI.get(p).maxMana();
        int out = ShadowService.activeLimit(p);
        helper.assertTrue(!MonarchAPI.crowned(p), "nobody is born crowned");

        MonarchAPI.crown(p, Dominion.CINDERS);
        helper.assertTrue(MonarchAPI.dominion(p) == Dominion.CINDERS, "crowned with Cinders");
        for (Skill s : List.of(MonarchPowers.DOMAIN, MonarchPowers.DREAD, MonarchPowers.CROWN, MonarchPowers.ASHEN_HEAVEN)) {
            helper.assertTrue(SkillAPI.knows(p, s), "knows " + s.path());
            helper.assertTrue(SkillAPI.get(p).slotOf(s.path()) < 0, s.path() + " sits on a crown key, not the skill bar");
        }
        helper.assertTrue(!SkillAPI.knows(p, MonarchPowers.ECLIPSE), "not another crown's authority");
        helper.assertTrue(p.getMaxHealth() > health * 1.2f, "a monarch's health: " + health + " -> " + p.getMaxHealth());
        helper.assertTrue(SystemAPI.get(p).maxMana() >= mana + 100, "a monarch's mana: " + mana + " -> " + SystemAPI.get(p).maxMana());
        helper.assertTrue(ShadowService.activeLimit(p) == out + 4, "more shadows out at once");
        helper.assertTrue(TitleService.data(p).hasTitle(Dominion.CINDERS.titleKey()), "the crown's title");

        // another crown: its authority takes the old one's place
        MonarchAPI.crown(p, Dominion.NIGHT);
        helper.assertTrue(SkillAPI.knows(p, MonarchPowers.ECLIPSE) && !SkillAPI.knows(p, MonarchPowers.ASHEN_HEAVEN), "Night's authority replaces Cinders'");
        helper.assertTrue(ShadowService.activeLimit(p) == out + 8, "Night: more shadows again");
        helper.assertTrue(ShadowService.canRaise(p), "Night raises shadows whatever the class");

        MonarchAPI.uncrown(p);
        helper.assertTrue(!MonarchAPI.crowned(p), "uncrowned");
        helper.assertTrue(Skills.monarchPowers().stream().noneMatch(s -> SkillAPI.knows(p, s)), "every power forgotten");
        helper.assertTrue(near(p.getMaxHealth(), health), "the strength goes with the crown");
        helper.assertTrue(!ShadowService.canRaise(p), "a mage without the crown raises no shadows");
        helper.succeed();
    }

    /** The powers come only with a crown: no class or subclass lists them, and without a crown they don't fire. */
    @GameTest(template = ARENA)
    public static void powersComeOnlyWithACrown(GameTestHelper helper) {
        helper.assertTrue(Skills.monarchPowers().size() == 7, "three powers and four authorities: " + Skills.monarchPowers().size());
        for (PlayerClass c : PlayerClass.values()) {
            helper.assertTrue(Skills.forClass(c).stream().noneMatch(Skill::monarch), c + " lists no monarch power");
            helper.assertTrue(Dominion.leaning(c) != null, c + " leans to a crown");
        }
        for (Subclass sub : Subclass.values()) {
            helper.assertTrue(Skills.forSubclass(sub).stream().noneMatch(Skill::monarch), sub + " lists no monarch power");
        }
        for (Dominion d : Dominion.values()) {
            helper.assertTrue(MonarchPowers.onKey(d, 3) == MonarchPowers.authority(d), "the fourth crown key is the authority");
            helper.assertTrue(MonarchPowers.authority(d).element() == d.element(), d + "'s authority is of its element");
            helper.assertTrue(Dominion.byId(d.id()) == d, "found by name");
        }
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        // learned without a crown (a command, a stray book): it still won't fire
        SkillAPI.learn(p, MonarchPowers.CROWN, 1, SkillEvent.Source.OTHER);
        refill(p);
        helper.assertTrue(!SkillAPI.cast(p, MonarchPowers.CROWN), "no crown, no power");
        helper.assertTrue(!MonarchPowers.castCrown(p, 2), "the crown keys do nothing for a hunter without a crown");
        helper.assertTrue(!MonarchDomain.inCrown(p), "no crown form");
        helper.succeed();
    }

    /** A hunter strong enough is called to the throne: the job changed, the sigil held, the level reached. */
    @GameTest(template = ARENA)
    public static void theThroneCallsTheReady(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.RANGER);
        helper.assertTrue(!MonarchAPI.eligible(p), "not at level 1");
        helper.assertTrue(!MonarchAPI.raiseSigil(p), "the sigil stays cold");
        helper.assertTrue(!MonarchAPI.get(p).called(), "not called");
        TowerAPI.data(p).sigil = true;
        JobAPI.changeJob(p, Subclass.of(PlayerClass.RANGER).stream().filter(s -> !s.rare()).findFirst().orElseThrow());
        helper.assertTrue(!MonarchAPI.get(p).called(), "still too weak");
        SystemAPI.setLevel(p, MonarchAPI.minLevel());
        helper.assertTrue(MonarchAPI.get(p).called(), "reaching the level calls them");
        helper.assertTrue(QuestEngine.data(p).has(MonarchModule.QUEST_TEMPLATE), "the throne's quest");
        helper.assertTrue(!MonarchAPI.callIfReady(p), "called only once");
        MonarchAPI.crown(p, Dominion.THUNDER);
        helper.assertTrue(!MonarchAPI.eligible(p), "a monarch is not called again");
        helper.assertTrue(!MonarchAPI.raiseSigil(p), "the sigil is quiet for a monarch");
        helper.succeed();
    }

    /** A crowned hunter's shadows climb past marshal to regent, and keep the rank if the crown is lost. */
    @GameTest(template = ARENA)
    public static void aMonarchsShadowsBecomeRegents(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        JobAPI.changeJob(p, Subclass.NECROMANCER);
        ShadowRecord r = ShadowService.give(p, EntityType.ZOMBIE, ShadowRank.HUSK, false);
        ShadowService.addXp(p, r, 50_000_000);
        helper.assertTrue(r.rank() == ShadowRank.MARSHAL && ShadowService.maxed(p, r), "marshal is the top for a hunter: " + r.rank());
        int army = ShadowService.capacity(p);
        float marshal = ShadowService.maxHealth(r, 20);

        MonarchAPI.crown(p, Dominion.NIGHT);
        helper.assertTrue(!ShadowService.maxed(p, r), "a monarch's marshal can climb on");
        helper.assertTrue(ShadowService.capacity(p) == army * 3 / 2, "a monarch's army is bigger");
        ShadowService.addXp(p, r, 50_000_000);
        helper.assertTrue(r.rank() == ShadowRank.REGENT && ShadowService.maxed(p, r), "a regent: " + r.rank());
        helper.assertTrue(ShadowService.maxHealth(r, 20) > marshal, "stronger than any marshal");

        MonarchAPI.uncrown(p);
        helper.assertTrue(r.rank() == ShadowRank.REGENT, "a regent keeps its rank when the crown is lost");
        ShadowService.reset(p);
        helper.succeed();
    }

    /** Crown form: much stronger, but never untouchable, even with every power at its top level and a domain round them. */
    @GameTest(template = ARENA)
    public static void crownFormIsStrongButNeverUntouchable(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.GUARDIAN);
        MonarchAPI.crown(p, Dominion.WINTER);
        refill(p);
        helper.assertTrue(MonarchPowers.castCrown(p, 2), "the crown form");
        helper.assertTrue(MonarchDomain.inCrown(p) && MonarchDomain.formOf(p) == Dominion.WINTER, "in Winter's crown form");
        helper.assertTrue(near(MonarchDomain.might(p), 1 + MonarchPowers.crownMight(1) / 100f), "hits harder: " + MonarchDomain.might(p));
        helper.assertTrue(near(MonarchDomain.guard(p), 1 - MonarchPowers.crownGuard(1) / 100f), "takes less: " + MonarchDomain.guard(p));
        MonarchDomain.end(p);
        helper.assertTrue(!MonarchDomain.inCrown(p) && near(MonarchDomain.guard(p), 1), "it ends");

        // every power at its top level, the form and the domain at once: still well within another crown's reach
        MonarchPowers.forgetAll(p);
        for (int key = 0; key < 4; key++) SkillAPI.learn(p, MonarchPowers.onKey(Dominion.WINTER, key), Skill.MAX_LEVEL, SkillEvent.Source.OTHER);
        MonarchDomain.open(p, Dominion.WINTER, 40, Skill.MAX_LEVEL);
        MonarchDomain.crown(p, Dominion.WINTER, 40, Skill.MAX_LEVEL);
        float guard = MonarchDomain.guard(p);
        MonarchDomain.end(p);
        helper.assertTrue(guard >= 0.5f, "at most half of a blow is held back: " + guard);
        helper.succeed();
    }

    // ================================================================ powers on the court

    /** The domain shelters the monarch and their allies and wears the court down; dread marks every foe near. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void domainAndDreadOnTheCourt(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        MonarchAPI.crown(p, Dominion.CINDERS);
        List<ChunkPos> forced = hall(helper, p);
        List<LivingEntity> court = court(helper, p);
        ServerPlayer ally = FakePlayerFactory.get(p.serverLevel(), new GameProfile(UUID.randomUUID(), "herald"));
        ally.moveTo(p.getX() + 3, p.getY(), p.getZ());

        refill(p);
        helper.assertTrue(MonarchPowers.castCrown(p, 0), "the domain is laid down");
        helper.assertTrue(MonarchDomain.hasDomain(p) && MonarchDomain.domainLeft(p) > 0, "it holds");
        helper.assertTrue(MonarchDomain.shelters(p) && MonarchDomain.shelters(ally), "the monarch and an ally inside are sheltered");
        float might = 1 + MonarchPowers.domainMight(1) / 100f;
        helper.assertTrue(near(MonarchDomain.might(p), might) && near(MonarchDomain.might(ally), might), "both hit harder");
        helper.assertTrue(near(MonarchDomain.guard(ally), 1 - MonarchPowers.DOMAIN_GUARD / 100f), "and take less");
        ally.moveTo(p.getX() + MonarchPowers.radius() + 6, p.getY(), p.getZ());
        helper.assertTrue(!MonarchDomain.shelters(ally) && near(MonarchDomain.might(ally), 1), "outside it, nothing");

        refill(p);
        helper.assertTrue(MonarchPowers.castCrown(p, 1), "dread");
        for (LivingEntity e : court) {
            helper.assertTrue(MonarchDomain.dreadBonus(e) > 1.15f, "dread marks " + e.getType() + ": " + MonarchDomain.dreadBonus(e));
            helper.assertTrue(e.hasEffect(MobEffects.WEAKNESS), "and weakens it");
        }
        // within a couple of seconds the domain has bitten the court
        helper.onEachTick(() -> {
            if (hurt(court)) {
                release(p, forced);
                helper.assertTrue(!MonarchDomain.hasDomain(p), "the domain ends with the test");
                helper.succeed();
            }
        });
    }

    private static void authorityStrikesTheCourt(GameTestHelper helper, Dominion d) {
        ServerPlayer p = hunter(helper, PlayerClass.MAGE);
        MonarchAPI.crown(p, d);
        List<ChunkPos> forced = hall(helper, p);
        List<LivingEntity> court = court(helper, p);
        face(p, court.get(0));
        refill(p);
        helper.assertTrue(MonarchPowers.castCrown(p, 3), d + "'s authority");
        helper.assertTrue(MonarchAPI.get(p).authorities() == 1, "counted");
        helper.onEachTick(() -> {
            if (hurt(court)) {
                release(p, forced);
                helper.succeed();
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void eclipseOfKingsStrikesTheCourt(GameTestHelper helper) {
        authorityStrikesTheCourt(helper, Dominion.NIGHT);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theLongWinterStrikesTheCourt(GameTestHelper helper) {
        authorityStrikesTheCourt(helper, Dominion.WINTER);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void ashenHeavenStrikesTheCourt(GameTestHelper helper) {
        authorityStrikesTheCourt(helper, Dominion.CINDERS);
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void thousandThundersStrikeTheCourt(GameTestHelper helper) {
        authorityStrikesTheCourt(helper, Dominion.THUNDER);
    }

    // ================================================================ the throne trial

    /** The whole trial: the court's last wave, the Arbiter (untouched), the choice, the coronation, home a monarch. */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void throneTrialRunsToTheEnd(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.HEALER);
        MonarchAPI.call(p);
        Vec3 home = p.position();
        List<ChunkPos> forced = hall(helper, p);
        helper.assertTrue(!ThroneTrial.begin(p, true), "one trial at a time");
        helper.assertTrue(p.position().distanceTo(home) > 1000, "taken to the hall");
        helper.assertTrue(MonarchAPI.get(p).inTrial(), "marked as in the trial");
        int done = QuestEngine.data(p).completedCount();

        int[] stage = {0};
        helper.onEachTick(() -> {
            ThroneTrial.Phase phase = ThroneTrial.phase(p);
            switch (stage[0]) {
                case 0 -> {
                    ThroneTrial.jump(p, "2", null);
                    stage[0] = 1;
                }
                case 1 -> {
                    // the last wave of the court falls: the Arbiter comes on its own
                    if (phase == ThroneTrial.Phase.COURT) {
                        ThroneTrial.foes(p).forEach(LivingEntity::kill);
                    } else if (phase == ThroneTrial.Phase.ARBITER) {
                        helper.assertTrue(ThroneTrial.flawless(p), "untouched so far");
                        for (LivingEntity e : ThroneTrial.foes(p)) {
                            e.setHealth(1f);
                            e.kill();
                        }
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (phase == ThroneTrial.Phase.CHOICE) {
                        ThroneTrial.choose(p, Dominion.WINTER);
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    if (phase == ThroneTrial.Phase.CORONATION) {
                        ThroneTrial.skipCoronation(p);
                    } else if (phase == null) {
                        release(p, forced);
                        helper.assertTrue(MonarchAPI.dominion(p) == Dominion.WINTER, "crowned with Winter: " + MonarchAPI.dominion(p));
                        helper.assertTrue(SkillAPI.knows(p, MonarchPowers.LONG_WINTER), "Winter's authority");
                        helper.assertTrue(!MonarchAPI.get(p).inTrial(), "no longer in the trial");
                        helper.assertTrue(p.position().distanceTo(home) < 2, "back where they started");
                        helper.assertTrue(TitleService.data(p).hasTitle(MonarchModule.UNBLEMISHED), "untouched by the Arbiter: its title");
                        helper.assertTrue(TitleService.data(p).hasTitle(Dominion.WINTER.titleKey()), "the crown's title");
                        helper.assertTrue(QuestEngine.data(p).completedCount() > done, "the throne's quest is done");
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /** Commands can skip to the throne and claim a crown at once; a skipped coronation still crowns. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void jumpToTheThroneClaimsTheCrown(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.ASSASSIN);
        Vec3 home = p.position();
        helper.assertTrue(!ThroneTrial.jump(p, "throne", Dominion.THUNDER), "nothing to skip without a trial");
        helper.assertTrue(ThroneTrial.begin(p, true), "trial starts");
        helper.assertTrue(ThroneTrial.jump(p, "throne", Dominion.THUNDER), "skips to the throne");
        helper.assertTrue(ThroneTrial.phase(p) == ThroneTrial.Phase.CORONATION, "the crown is claimed at once");
        helper.assertTrue(!MonarchAPI.crowned(p), "the crown comes at the end of the coronation");
        ThroneTrial.skipCoronation(p);
        helper.onEachTick(() -> {
            if (ThroneTrial.phase(p) == null) {
                helper.assertTrue(MonarchAPI.dominion(p) == Dominion.THUNDER, "crowned with Thunder");
                helper.assertTrue(SkillAPI.knows(p, MonarchPowers.THOUSAND_THUNDERS), "Thunder's authority");
                helper.assertTrue(p.position().distanceTo(home) < 2, "home");
                helper.succeed();
            }
        });
    }

    /** Falling in the hall throws the hunter out alive, uncrowned, and the throne is sealed to them for a while. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void fallingInTheHallSendsYouHome(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.GUARDIAN);
        Vec3 home = p.position();
        ThroneTrial.begin(p, true);
        ThroneTrial.jump(p, "1", null);
        helper.runAfterDelay(5, () -> {
            // fake players can't be hurt, so post the death the way a fatal hit would
            var death = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p, p.damageSources().genericKill()));
            helper.assertTrue(death.isCanceled(), "the death is cancelled");
            helper.assertTrue(p.isAlive(), "the hunter lives");
            helper.assertTrue(!ThroneTrial.inTrial(p) && !MonarchAPI.get(p).inTrial(), "the trial is over");
            helper.assertTrue(p.position().distanceTo(home) < 2, "sent home");
            helper.assertTrue(!MonarchAPI.crowned(p), "no crown");
            helper.assertTrue(MonarchAPI.get(p).retryAt() > p.level().getGameTime(), "the throne is sealed to them for a while");
            helper.succeed();
        });
    }

    /** Logging out at the throne keeps the crown (it is settled before the hunter is saved); before it, nothing. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void leavingAtTheThroneKeepsTheCrown(GameTestHelper helper) {
        ServerPlayer p = hunter(helper, PlayerClass.WARRIOR);
        Vec3 home = p.position();
        ThroneTrial.begin(p, true);
        ThroneTrial.jump(p, "throne", null);
        helper.assertTrue(ThroneTrial.phase(p) == ThroneTrial.Phase.CHOICE, "at the throne, choosing");
        ThroneTrial.loggedOut(p);
        helper.assertTrue(!ThroneTrial.inTrial(p), "the session ends with the logout");
        helper.assertTrue(MonarchAPI.dominion(p) == Dominion.leaning(PlayerClass.WARRIOR), "the crown their class leans to");
        ThroneTrial.loggedIn(p);
        helper.assertTrue(!MonarchAPI.get(p).inTrial() && p.position().distanceTo(home) < 2, "back home on the next login");

        ServerPlayer q = hunter(helper, PlayerClass.WARRIOR);
        ThroneTrial.begin(q, true);
        ThroneTrial.jump(q, "1", null);
        ThroneTrial.loggedOut(q);
        helper.assertTrue(!MonarchAPI.crowned(q), "leaving before the throne crowns nobody");
        ThroneTrial.loggedIn(q);
        helper.assertTrue(!MonarchAPI.get(q).inTrial(), "sent home too");
        helper.succeed();
    }

    // ================================================================ commands

    private static int run(ServerPlayer p, String command) throws CommandSyntaxException {
        return p.server.getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
    }

    private static String key(CommandSyntaxException e) {
        return e.getRawMessage() instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(e.getRawMessage());
    }

    /** /solo monarch: crown, a power, uncrown, reset, and the mistakes an operator can make. */
    @GameTest(template = ARENA)
    public static void soloMonarchCommands(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer p = hunter(helper, PlayerClass.HEALER);
        helper.assertTrue(run(p, "solo help monarch") == 1, "the help page");
        helper.assertTrue(run(p, "solo monarch crown night") == 1 && MonarchAPI.dominion(p) == Dominion.NIGHT, "crowned by command");
        helper.assertTrue(run(p, "solo monarch power crown") == 1 && MonarchDomain.inCrown(p), "a power by command");
        MonarchDomain.end(p);
        try {
            run(p, "solo monarch crown moon");
            helper.fail("an unknown crown was taken");
        } catch (CommandSyntaxException e) {
            helper.assertTrue(key(e).equals("command.beyondsolo.solo.error.unknown_crown"), "says the crown is unknown: " + key(e));
        }
        try {
            run(p, "solo monarch jump throne");
            helper.fail("jumped without a trial");
        } catch (CommandSyntaxException e) {
            helper.assertTrue(key(e).equals("command.beyondsolo.solo.error.not_in_throne"), "says there is no trial: " + key(e));
        }
        helper.assertTrue(run(p, "solo monarch uncrown") == 1 && !MonarchAPI.crowned(p), "uncrowned by command");
        try {
            run(p, "solo monarch power domain");
            helper.fail("a power without a crown");
        } catch (CommandSyntaxException e) {
            helper.assertTrue(key(e).equals("command.beyondsolo.solo.error.not_monarch"), "says they are no monarch: " + key(e));
        }
        helper.assertTrue(run(p, "solo monarch call") == 1 && MonarchAPI.get(p).called(), "called by command");
        helper.assertTrue(run(p, "solo monarch reset") == 1 && !MonarchAPI.get(p).called(), "the record wiped");
        helper.succeed();
    }
}
