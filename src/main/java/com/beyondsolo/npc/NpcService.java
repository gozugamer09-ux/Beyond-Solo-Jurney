package com.beyondsolo.npc;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ParcelItem;
import com.beyondsolo.network.NpcBoardPayload;
import com.beyondsolo.network.NpcMarkersPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestGiver;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The villager request flow on the server: right-clicking a villager that has something for you opens its
 * request window (sneak to trade as usual), taking a request, handing one in, the markers over villagers' heads,
 * the nest of a clearing request, the parcel, and what villagers think of you afterwards (gossip).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class NpcService {
    /** Marker states sent to the client, per villager. */
    public static final int MARK_NONE = 0, MARK_OFFER = 1, MARK_READY = 2, MARK_WAITING = 3, MARK_TRAVELLER = 4;
    private static final double MARKER_RANGE = 40;
    /** Reputation a finished request earns with its giver (minor positive gossip, like a good trade). */
    public static final int GOSSIP_DONE = 12;
    public static final int GOSSIP_FAILED = 8;

    /** Player -> the villager whose window they have open (it stands still and faces them meanwhile). */
    private static final Map<UUID, Integer> OPEN = new HashMap<>();
    private static final Map<UUID, Long> OPENED_AT = new HashMap<>();
    /** Players who took a request, so travellers and givers find them again (also fake players, which aren't in the player list). */
    private static final Map<UUID, java.lang.ref.WeakReference<ServerPlayer>> TAKERS = new HashMap<>();
    /** Player -> the markers last sent, to send only changes. */
    private static final Map<UUID, String> LAST_MARKERS = new HashMap<>();

    // ------------------------------------------------------------------ the board

    public static long today(ServerPlayer p) {
        return QuestEngine.day(p);
    }

    public static NpcBoard board(AbstractVillager v, long day) {
        NpcBoard b = v.getData(ModAttachments.NPC_BOARD);
        b.roll(day);
        return b;
    }

    /** True if this villager gives requests at all (adults, awake, not in a panic). */
    public static boolean gives(AbstractVillager v) {
        return v.isAlive() && NpcQuests.slots(v) > 0 && !v.isSleeping();
    }

    /** The board slots this player can still take from this villager today. */
    public static List<Integer> openSlots(ServerPlayer p, AbstractVillager v) {
        List<Integer> list = new ArrayList<>();
        if (!SystemAPI.isAwakened(p) || !gives(v)) return list;
        long day = today(p);
        NpcBoard b = board(v, day);
        if (b.isShunned(p.getUUID(), day)) return list;
        int n = NpcQuests.slots(v);
        for (int i = 0; i < n; i++) if (!b.isTaken(p.getUUID(), i)) list.add(i);
        return list;
    }

    /** The player's quests from this giver (in progress or ready to hand in). */
    public static List<Quest> fromGiver(ServerPlayer p, UUID giver) {
        List<Quest> list = new ArrayList<>();
        for (Quest q : QuestEngine.data(p).active()) {
            if (q.giver() != null && q.giver().is(giver)) list.add(q);
        }
        return list;
    }

    /** How many villager requests the player holds. */
    public static int held(ServerPlayer p) {
        int n = 0;
        for (Quest q : QuestEngine.data(p).active()) if (q.category() == QuestCategory.NPC) n++;
        return n;
    }

    // ------------------------------------------------------------------ actions

    /** Opens (or refreshes) the request window of a villager for a player. */
    public static void open(ServerPlayer p, AbstractVillager v) {
        if (!v.isTrading()) v.setTradingPlayer(p);
        OPEN.put(p.getUUID(), v.getId());
        OPENED_AT.put(p.getUUID(), p.level().getGameTime());
        PacketDistributor.sendToPlayer(p, new NpcBoardPayload(boardTag(p, v)));
    }

    static CompoundTag boardTag(ServerPlayer p, AbstractVillager v) {
        var provider = p.registryAccess();
        long day = today(p);
        NpcBoard b = board(v, day);
        CompoundTag tag = new CompoundTag();
        tag.putInt("entity", v.getId());
        tag.putString("name", Component.Serializer.toJson(NpcNames.name(v), provider));
        tag.putString("job", Component.Serializer.toJson(NpcNames.job(v), provider));
        tag.putString("profession", NpcQuests.profession(v));
        tag.putInt("level", v instanceof Villager villager ? villager.getVillagerData().getLevel() : 0);
        tag.putInt("rep", v instanceof Villager villager ? villager.getPlayerReputation(p) : 0);
        tag.putInt("held", held(p));
        tag.putInt("max", NpcQuests.MAX_HELD);
        tag.putBoolean("canTrade", canTrade(v));
        tag.putBoolean("shunned", b.isShunned(p.getUUID(), day));
        tag.putInt("slots", NpcQuests.slots(v));
        long dayTime = p.server.overworld().getDayTime() % 24000L;
        tag.putLong("refresh", 24000L - dayTime);
        ListTag offers = new ListTag();
        for (int slot : openSlots(p, v)) {
            CompoundTag o = new CompoundTag();
            o.putInt("slot", slot);
            o.put("quest", NpcQuests.build(p, v, NpcQuests.plan(v, b, slot), false).save(provider));
            offers.add(o);
        }
        tag.put("offers", offers);
        ListTag mine = new ListTag();
        for (Quest q : fromGiver(p, v.getUUID())) mine.add(q.save(provider));
        tag.put("mine", mine);
        return tag;
    }

    private static boolean canTrade(AbstractVillager v) {
        if (v instanceof Villager villager) {
            VillagerProfession prof = villager.getVillagerData().getProfession();
            return prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT;
        }
        return true;
    }

    @Nullable
    private static AbstractVillager villager(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof AbstractVillager v) || !v.isAlive() || v.distanceToSqr(p) > 10 * 10) return null;
        return v;
    }

    /** Takes the request in {@code slot}. Returns the quest, or null if it can't be taken. */
    @Nullable
    public static Quest accept(ServerPlayer p, AbstractVillager v, int slot) {
        if (!openSlots(p, v).contains(slot)) return null;
        if (held(p) >= NpcQuests.MAX_HELD) {
            p.displayClientMessage(Component.translatable("npc.beyondsolo.full", NpcQuests.MAX_HELD), true);
            return null;
        }
        long day = today(p);
        NpcBoard b = board(v, day);
        Quest q = NpcQuests.build(p, v, NpcQuests.plan(v, b, slot), true);
        b.take(p.getUUID(), slot);
        give(p, v, q);
        return q;
    }

    /** Hands a built villager quest to the player: the traveller, the parcel, then the quest itself. */
    public static void give(ServerPlayer p, AbstractVillager v, Quest q) {
        TAKERS.put(p.getUUID(), new java.lang.ref.WeakReference<>(p));
        NpcQuestKind kind = NpcQuestKind.byTemplate(q.template());
        if (kind == NpcQuestKind.ESCORT) spawnTraveller(p, v, q);
        if (kind == NpcQuestKind.DELIVER) {
            ItemStack parcel = ParcelItem.forToken(new ItemStack(ModItems.SEALED_PARCEL.get()), q.extra().getString("token"),
                    NpcNames.name(v).getString());
            if (!p.getInventory().add(parcel)) p.drop(parcel, false);
        }
        QuestEngine.start(p, q);
        v.playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
    }

    /** Hands in the player's READY quest {@code id} to villager {@code v}. */
    public static boolean turnIn(ServerPlayer p, AbstractVillager v, UUID id) {
        Quest q = QuestEngine.data(p).find(id);
        if (q == null || q.giver() == null || !q.giver().is(v.getUUID())) return false;
        return QuestEngine.turnIn(p, q);
    }

    private static void spawnTraveller(ServerPlayer p, AbstractVillager v, Quest q) {
        if (!(v.level() instanceof ServerLevel level) || q.area() == null) return;
        TravelerEntity t = ModEntities.TRAVELER.get().create(level);
        if (t == null) return;
        t.setup(p, q.extra().getString("token"), q.area().center(), q.extra().getString("traveller"));
        t.moveTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0);
        t.moveToSafety(v.blockPosition());
        level.addFreshEntity(t);
        level.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + 1, t.getZ(), 14, 0.3, 0.6, 0.3, 0.02);
    }

    /** The parcel was used: delivers it if the player is at the marked spot. */
    public static boolean useParcel(ServerPlayer p, ItemStack stack) {
        String token = ParcelItem.token(stack);
        Quest q = null;
        for (Quest x : QuestEngine.data(p).active()) {
            if (x.template().equals(NpcQuestKind.DELIVER.template()) && x.extra().getString("token").equals(token)) q = x;
        }
        if (q == null) {
            p.displayClientMessage(Component.translatable("npc.beyondsolo.parcel.void"), true);
            stack.shrink(1);
            return false;
        }
        if (q.area() == null || !q.area().contains(p.level(), p.position())) {
            if (q.area() != null) {
                BlockPos c = q.area().center();
                int dist = (int) Math.sqrt(c.distSqr(p.blockPosition()));
                float angle = (float) Math.atan2(c.getZ() - p.getZ(), c.getX() - p.getX());
                p.displayClientMessage(Component.translatable("npc.beyondsolo.parcel.not_here", dist, NpcQuests.direction(angle)), true);
            }
            return false;
        }
        stack.shrink(1);
        if (p.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
            level.playSound(null, p.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1f, 1f);
        }
        QuestEngine.progress(p, NpcQuests.deliverKey(q.extra()), 1);
        return true;
    }

    // ------------------------------------------------------------------ packets from the window

    public static void handle(ServerPlayer p, int action, int entityId, int slot, UUID quest) {
        if (action == ACTION_CLOSE) {
            release(p);
            return;
        }
        AbstractVillager v = villager(p, entityId);
        if (v == null) return;
        switch (action) {
            case ACTION_ACCEPT -> {
                accept(p, v, slot);
                open(p, v);
            }
            case ACTION_TURN_IN -> {
                turnIn(p, v, quest);
                open(p, v);
            }
            case ACTION_TRADE -> {
                release(p);
                v.interact(p, InteractionHand.MAIN_HAND);
            }
            default -> {}
        }
    }

    public static final int ACTION_ACCEPT = 0, ACTION_TURN_IN = 1, ACTION_TRADE = 2, ACTION_CLOSE = 3;

    /** The window closed: the villager may walk on. */
    private static void release(ServerPlayer p) {
        Integer id = OPEN.remove(p.getUUID());
        OPENED_AT.remove(p.getUUID());
        if (id == null) return;
        Entity e = p.level().getEntity(id);
        if (e instanceof AbstractVillager v && v.getTradingPlayer() == p) v.setTradingPlayer(null);
    }

    // ------------------------------------------------------------------ hooks

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractVillager v) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity().isShiftKeyDown() || event.getEntity().isSpectator()) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (v.isTrading() && v.getTradingPlayer() != p) return;
        if (!SystemAPI.isAwakened(p)) return;
        ItemStack held = p.getMainHandItem();
        if (held.is(ModItems.SEALED_PARCEL.get())) {
            // handing the parcel to someone at the destination counts as delivering it
            useParcel(p, held);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!gives(v) && fromGiver(p, v.getUUID()).isEmpty()) return;
        if (openSlots(p, v).isEmpty() && fromGiver(p, v.getUUID()).isEmpty() && !board(v, today(p)).isShunned(p.getUUID(), today(p))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        open(p, v);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        int age = p.tickCount + Math.floorMod(p.getUUID().hashCode(), 20);
        if (age % 20 == 7) tickPlayer(p);
    }

    /** Once a second per player: closes stale windows, wakes nests, sends markers. Public for game tests. */
    public static void tickPlayer(ServerPlayer p) {
        // an open window whose player walked off or went quiet
        Integer open = OPEN.get(p.getUUID());
        if (open != null) {
            Entity e = p.level().getEntity(open);
            long since = p.level().getGameTime() - OPENED_AT.getOrDefault(p.getUUID(), 0L);
            if (!(e instanceof AbstractVillager v) || v.distanceToSqr(p) > 10 * 10 || since > 20 * 120) release(p);
        }
        if (!SystemAPI.isAwakened(p)) return;
        nests(p);
        markers(p);
    }

    /** Spawns the monsters of a clearing request once the player comes close to the spot. */
    private static void nests(ServerPlayer p) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (!q.template().equals(NpcQuestKind.CLEAR.template()) || q.area() == null || q.extra().getBoolean("spawned")) continue;
            if (!p.level().dimension().equals(q.area().dimension())) continue;
            if (!p.position().closerThan(Vec3.atCenterOf(q.area().center()), 30)) continue;
            q.extra().putBoolean("spawned", true);
            spawnNest((ServerLevel) p.level(), q.area().center(), q.rankTier(), Math.max(1, q.extra().getInt("nest")));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("npc.beyondsolo.nest.title"), Component.translatable("npc.beyondsolo.nest.body")));
            QuestEngine.sync(p);
        }
    }

    public static void spawnNest(ServerLevel level, BlockPos center, int tier, int count) {
        List<String> pool = NpcQuests.NEST.get(Math.min(tier, NpcQuests.NEST.size() - 1));
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = ResourceLocation.tryParse(pool.get(random.nextInt(pool.size())));
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2 + random.nextDouble() * 7;
            int x = (int) Math.floor(center.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(center.getZ() + Math.sin(a) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
            // a helmet keeps the undead from burning in daylight; higher ranks get better ones
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(tier >= 4 ? Items.DIAMOND_HELMET : tier >= 2 ? Items.IRON_HELMET : Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0f);
            }
            mob.setPersistenceRequired();
            mob.addTag("beyondsolo_nest");
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 0.5, z + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Tells the client which villagers around have something for this player. */
    private static void markers(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(MARKER_RANGE);
        List<Integer> ids = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean full = held(p) >= NpcQuests.MAX_HELD;
        for (AbstractVillager v : p.level().getEntitiesOfClass(AbstractVillager.class, box, Entity::isAlive)) {
            int state = MARK_NONE;
            for (Quest q : fromGiver(p, v.getUUID())) {
                if (q.status() == Quest.Status.READY) state = MARK_READY;
                else if (state == MARK_NONE) state = MARK_WAITING;
            }
            if (state == MARK_NONE && !full && !openSlots(p, v).isEmpty()) state = MARK_OFFER;
            if (state == MARK_NONE) continue;
            ids.add(v.getId());
            states.add(state);
            key.append(v.getId()).append(':').append(state).append(',');
        }
        for (TravelerEntity t : p.level().getEntitiesOfClass(TravelerEntity.class, box, Entity::isAlive)) {
            if (!p.getUUID().equals(t.owner()) || t.hasArrived()) continue;
            ids.add(t.getId());
            states.add(MARK_TRAVELLER);
            key.append(t.getId()).append(":t,");
        }
        String k = key.toString();
        if (k.equals(LAST_MARKERS.get(p.getUUID()))) return;
        LAST_MARKERS.put(p.getUUID(), k);
        PacketDistributor.sendToPlayer(p, new NpcMarkersPayload(ids, states));
    }

    /** Monsters go after travellers like they go after villagers. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster m)) return;
        m.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(m, TravelerEntity.class, true));
    }

    /** A giver died: their requests can't be handed back to them, so they complete on their own. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager v) || v.level().isClientSide) return;
        for (ServerPlayer p : takers(v.level().getServer())) {
            for (Quest q : fromGiver(p, v.getUUID())) {
                if (!q.needsTurnIn()) continue;
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                        Component.translatable("npc.beyondsolo.giver_gone.title", NpcNames.name(v)),
                        Component.translatable("npc.beyondsolo.giver_gone.body", q.title())));
                QuestEngine.releaseFromGiver(p, q);
            }
        }
    }

    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        Quest q = event.getQuest();
        if (q.category() != QuestCategory.NPC) return;
        ServerPlayer p = event.getPlayer();
        removeParcels(p, q);
        AbstractVillager v = giverEntity(p, q.giver());
        if (v == null) return;
        if (v instanceof Villager villager) villager.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, GOSSIP_DONE);
        if (v.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.4, v.getZ(), 14, 0.4, 0.5, 0.4, 0);
        }
        v.playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1f);
    }

    @SubscribeEvent
    static void onFailed(QuestEvent.Failed event) {
        Quest q = event.getQuest();
        if (q.category() != QuestCategory.NPC) return;
        ServerPlayer p = event.getPlayer();
        removeParcels(p, q);
        AbstractVillager v = giverEntity(p, q.giver());
        if (v == null) return;
        if (v instanceof Villager villager) villager.getGossips().add(p.getUUID(), GossipType.MINOR_NEGATIVE, GOSSIP_FAILED);
        long day = today(p);
        board(v, day).shun(p.getUUID(), day);
    }

    /** An online player by UUID (or a fake player that took a request). */
    @Nullable
    public static ServerPlayer player(net.minecraft.server.MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        var ref = TAKERS.get(id);
        p = ref == null ? null : ref.get();
        return p == null || p.hasDisconnected() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) ? null : p;
    }

    private static List<ServerPlayer> takers(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>(server.getPlayerList().getPlayers());
        for (var ref : TAKERS.values()) {
            ServerPlayer p = ref.get();
            if (p != null && !list.contains(p)) list.add(p);
        }
        return list;
    }

    @Nullable
    public static AbstractVillager giverEntity(ServerPlayer p, @Nullable QuestGiver giver) {
        if (giver == null) return null;
        ServerLevel level = p.server.getLevel(giver.dimension());
        if (level == null) return null;
        return level.getEntity(giver.entity()) instanceof AbstractVillager v ? v : null;
    }

    private static void removeParcels(ServerPlayer p, Quest q) {
        if (!q.template().equals(NpcQuestKind.DELIVER.template())) return;
        String token = q.extra().getString("token");
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.SEALED_PARCEL.get()) && ParcelItem.token(s).equals(token)) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) release(p);
        LAST_MARKERS.remove(event.getEntity().getUUID());
        TAKERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LAST_MARKERS.remove(event.getEntity().getUUID());
    }

    private NpcService() {}
}
