package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.npc.NpcBoard;
import com.beyondsolo.npc.NpcQuestKind;
import com.beyondsolo.npc.NpcQuests;
import com.beyondsolo.npc.NpcService;
import com.beyondsolo.npc.TravelerEntity;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Villager request tests. Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcGameTests {
    private static final String ARENA = "evaluation_zone";

    private static ServerPlayer player(GameTestHelper helper, PlayerRank rank) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "villager_friend"));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        SystemAPI.awaken(p, rank, PlayerClass.WARRIOR);
        QuestTestAccess.markStarted(p);
        return p;
    }

    private static Villager villager(GameTestHelper helper, VillagerProfession profession, int level) {
        Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        v.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, level));
        v.setNoAi(true);
        return v;
    }

    private static Quest give(ServerPlayer p, Villager v, NpcQuestKind kind, long seed) {
        Quest q = NpcQuests.build(p, v, new NpcQuests.Plan(kind, seed), true);
        NpcService.give(p, v, q);
        return q;
    }

    private static void fill(ServerPlayer p, Quest q) {
        for (QuestObjective o : q.objectives()) {
            if (o.type() != ObjectiveType.COLLECT) continue;
            String id = o.target().startsWith("#") ? "minecraft:oak_log" : o.target();
            if (o.target().equals("#minecraft:wool")) id = "minecraft:white_wool";
            if (o.target().equals("#minecraft:small_flowers")) id = "minecraft:dandelion";
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), o.required()));
        }
    }

    private static int emeralds(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    @GameTest(template = ARENA)
    public static void boardSlotsAndDawnRefresh(GameTestHelper helper) {
        ServerPlayer a = player(helper, PlayerRank.D);
        ServerPlayer b = player(helper, PlayerRank.D);
        Villager novice = villager(helper, VillagerProfession.FARMER, 1);
        Villager expert = villager(helper, VillagerProfession.FARMER, 4);
        Villager child = villager(helper, VillagerProfession.NONE, 1);
        child.setAge(-24000);
        helper.assertTrue(NpcQuests.slots(novice) == 1 && NpcQuests.slots(expert) == 3 && NpcQuests.slots(child) == 0, "slots follow the job level");

        long day = NpcService.today(a);
        NpcBoard board = NpcService.board(expert, day);
        NpcQuests.Plan p0 = NpcQuests.plan(expert, board, 0);
        helper.assertTrue(p0.equals(NpcQuests.plan(expert, board, 0)), "a slot is the same request every time today");
        helper.assertTrue(NpcService.openSlots(a, expert).equals(List.of(0, 1, 2)), "three open slots");

        Quest taken = NpcService.accept(a, expert, 1);
        helper.assertTrue(taken != null && QuestEngine.data(a).find(taken.id()) != null, "accepted request is the player's");
        helper.assertTrue(NpcService.openSlots(a, expert).equals(List.of(0, 2)), "taken slot is gone for that player");
        helper.assertTrue(NpcService.openSlots(b, expert).equals(List.of(0, 1, 2)), "other players still see it");

        board.roll(day + 1);
        helper.assertTrue(!board.isTaken(a.getUUID(), 1), "a new day: every slot open again");
        helper.assertTrue(!NpcQuests.plan(expert, board, 0).equals(p0), "a new day rolls new requests");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gatherIsHandedIn(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.FARMER, 2);
        Quest q = give(p, v, NpcQuestKind.GATHER, 7);
        helper.assertTrue(q.needsTurnIn() && q.giver() != null && q.giver().is(v.getUUID()), "gather is handed back to its giver");
        fill(p, q);
        QuestEngine.tick(p);
        helper.assertTrue(q.status() == Quest.Status.READY, "items in hand: ready, not finished");
        helper.assertTrue(QuestEngine.data(p).completedCount() == 0 && emeralds(p) == 0, "no reward before the hand-in");

        // dropping the items puts it back in progress
        List<ItemStack> saved = p.getInventory().items.stream().map(ItemStack::copy).toList();
        p.getInventory().clearContent();
        QuestEngine.tick(p);
        helper.assertTrue(q.status() == Quest.Status.ACTIVE, "back in progress without the items");
        for (ItemStack s : saved) if (!s.isEmpty()) p.getInventory().add(s);
        QuestEngine.tick(p);
        helper.assertTrue(q.status() == Quest.Status.READY, "ready again");

        int rep = v.getPlayerReputation(p);
        helper.assertTrue(NpcService.turnIn(p, v, q.id()), "handed in");
        helper.assertTrue(QuestEngine.data(p).find(q.id()) == null && QuestEngine.data(p).completedCount() == 1, "completed");
        for (QuestObjective o : q.objectives()) {
            int left = 0;
            for (ItemStack s : p.getInventory().items) if (com.beyondsolo.quest.Targets.item(o.target(), s)) left += s.getCount();
            helper.assertTrue(left == 0, "the villager took the items");
        }
        helper.assertTrue(emeralds(p) >= 3, "paid in emeralds");
        helper.assertTrue(v.getPlayerReputation(p) > rep, "the villager likes you more");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rankScalesRequests(GameTestHelper helper) {
        ServerPlayer e = player(helper, PlayerRank.E);
        ServerPlayer s = player(helper, PlayerRank.S);
        Villager v = villager(helper, VillagerProfession.ARMORER, 3);
        for (NpcQuestKind kind : new NpcQuestKind[]{NpcQuestKind.GATHER, NpcQuestKind.HUNT, NpcQuestKind.CLEAR}) {
            Quest qe = NpcQuests.build(e, v, new NpcQuests.Plan(kind, 99), false);
            Quest qs = NpcQuests.build(s, v, new NpcQuests.Plan(kind, 99), false);
            helper.assertTrue(qs.objectives().get(0).required() > qe.objectives().get(0).required(), kind + ": S-rank has more to do");
            helper.assertTrue(qs.rewards().xp() > qe.rewards().xp(), kind + ": S-rank earns more XP");
            helper.assertTrue(qs.rewards().items().get(0).getCount() > qe.rewards().items().get(0).getCount(), kind + ": more emeralds");
        }
        Quest he = NpcQuests.build(e, v, new NpcQuests.Plan(NpcQuestKind.HUNT, 5), false);
        String mob = he.objectives().get(0).target();
        helper.assertTrue(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider").contains(mob), "E-rank hunts easy prey, got " + mob);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void handsFullAtThree(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.C);
        Villager a = villager(helper, VillagerProfession.LIBRARIAN, 3);
        Villager b = villager(helper, VillagerProfession.FARMER, 1);
        for (int i = 0; i < 3; i++) helper.assertTrue(NpcService.accept(p, a, i) != null, "take request " + i);
        helper.assertTrue(NpcService.held(p) == NpcQuests.MAX_HELD, "three held");
        helper.assertTrue(NpcService.accept(p, b, 0) == null, "a fourth is refused");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void huntReportsBack(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.D);
        Villager v = villager(helper, VillagerProfession.WEAPONSMITH, 2);
        Quest q = give(p, v, NpcQuestKind.HUNT, 3);
        QuestEngine.progress(p, ObjectiveType.KILL, o -> true, 999, null);
        helper.assertTrue(q.status() == Quest.Status.READY, "kills done: report back");
        Villager stranger = villager(helper, VillagerProfession.FARMER, 1);
        helper.assertTrue(!NpcService.turnIn(p, stranger, q.id()), "only the giver takes it");
        helper.assertTrue(NpcService.turnIn(p, v, q.id()), "the giver takes it");
        helper.assertTrue(QuestEngine.data(p).completedCount() == 1, "completed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rightClickOpensRequests(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.FARMER, 2);
        var event = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, v);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "a villager with requests opens its window instead of trading");
        helper.assertTrue(v.getTradingPlayer() == p, "and stands still for the player");
        NpcService.handle(p, NpcService.ACTION_CLOSE, v.getId(), 0, new UUID(0, 0));
        helper.assertTrue(v.getTradingPlayer() == null, "closing lets it go");

        ServerPlayer sleeper = player(helper, PlayerRank.E);
        SystemAPI.reset(sleeper);
        var e2 = new PlayerInteractEvent.EntityInteract(sleeper, InteractionHand.MAIN_HAND, v);
        NeoForge.EVENT_BUS.post(e2);
        helper.assertTrue(!e2.isCanceled(), "players the System hasn't evaluated trade as usual");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void failingShunsAndHurtsRapport(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.FARMER, 3);
        Quest q = NpcService.accept(p, v, 0);
        helper.assertTrue(q != null, "accepted");
        int rep = v.getPlayerReputation(p);
        helper.assertTrue(QuestEngine.abandon(p, q.id()), "abandoned");
        helper.assertTrue(v.getPlayerReputation(p) < rep, "the villager thinks less of you");
        helper.assertTrue(NpcService.openSlots(p, v).isEmpty(), "no more requests from them today");
        NpcService.board(v, NpcService.today(p) + 1);
        helper.assertTrue(!NpcService.board(v, NpcService.today(p) + 1).isShunned(p.getUUID(), NpcService.today(p) + 1), "forgiven the next day");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void giverDeathCompletesReadyQuest(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.FARMER, 1);
        Quest q = give(p, v, NpcQuestKind.HUNT, 11);
        QuestEngine.progress(p, ObjectiveType.KILL, o -> true, 999, null);
        helper.assertTrue(q.status() == Quest.Status.READY, "ready");
        v.kill();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(QuestEngine.data(p).find(q.id()) == null && QuestEngine.data(p).completedCount() == 1, "completes when the giver is gone");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void escortArrivesAndCompletes(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.CARTOGRAPHER, 2);
        Quest q = give(p, v, NpcQuestKind.ESCORT, 21);
        helper.assertTrue(!q.needsTurnIn() && q.area() != null, "escort completes on arrival");
        List<TravelerEntity> spawned = helper.getLevel().getEntitiesOfClass(TravelerEntity.class, new AABB(v.blockPosition()).inflate(4));
        helper.assertTrue(spawned.size() == 1 && p.getUUID().equals(spawned.get(0).owner()), "a traveller joined the player");
        spawned.get(0).discard();

        // a traveller standing at a destination next to the player: arrival completes the request
        TravelerEntity t = ModEntities.TRAVELER.get().create(helper.getLevel());
        BlockPos here = helper.absolutePos(new BlockPos(3, 2, 3));
        t.setup(p, q.extra().getString("token"), here, "Wren");
        t.moveTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
        helper.getLevel().addFreshEntity(t);
        helper.succeedWhen(() -> {
            helper.assertTrue(QuestEngine.data(p).find(q.id()) == null, "escort finished");
            helper.assertTrue(QuestEngine.data(p).completedCount() == 1, "counted as complete");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void escortFailsWhenTravellerDies(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.CARTOGRAPHER, 2);
        Quest q = give(p, v, NpcQuestKind.ESCORT, 22);
        TravelerEntity t = helper.getLevel().getEntitiesOfClass(TravelerEntity.class, new AABB(v.blockPosition()).inflate(4)).get(0);
        t.kill();
        helper.assertTrue(QuestEngine.data(p).find(q.id()) == null && QuestEngine.data(p).failedCount() == 1, "losing the traveller fails it");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void parcelDeliveredAtTheSpot(GameTestHelper helper) {
        ServerPlayer p = player(helper, PlayerRank.E);
        Villager v = villager(helper, VillagerProfession.LIBRARIAN, 2);
        Quest q = give(p, v, NpcQuestKind.DELIVER, 31);
        ItemStack parcel = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SEALED_PARCEL.get())) parcel = s;
        helper.assertTrue(!parcel.isEmpty(), "the parcel is in the inventory");
        helper.assertTrue(!NpcService.useParcel(p, parcel), "not delivered at the village");
        Vec3 spot = Vec3.atBottomCenterOf(q.area().center());
        p.moveTo(spot.x, spot.y, spot.z);
        helper.assertTrue(NpcService.useParcel(p, parcel), "delivered at the marked spot");
        helper.assertTrue(parcel.isEmpty(), "parcel handed over");
        helper.assertTrue(QuestEngine.data(p).find(q.id()) == null && QuestEngine.data(p).completedCount() == 1, "delivery done");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void nestSpawnsArmouredMonsters(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        NpcService.spawnNest(helper.getLevel(), center, 2, 4);
        List<Mob> mobs = helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(center).inflate(12), m -> m.getTags().contains("beyondsolo_nest"));
        helper.assertTrue(mobs.size() == 4, "four monsters, got " + mobs.size());
        for (Mob m : mobs) {
            helper.assertTrue(!m.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "helmet against the sun");
            helper.assertTrue(m.isPersistenceRequired(), "the nest doesn't despawn");
            m.discard();
        }
        helper.succeed();
    }
}
