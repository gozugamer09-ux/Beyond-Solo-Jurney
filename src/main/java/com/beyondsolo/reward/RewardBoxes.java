package com.beyondsolo.reward;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.manapoison.ManaPoisoning;
import com.beyondsolo.network.RewardBoxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.BodyParticles;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opening the random boxes ({@link RewardBoxItem}) and where they come from.
 *
 * <p><b>Opening.</b> Only a hunter the System is bound to can open one. A plain box rolls one thing
 * ({@link BoxRolls#roll}) and gives it at once. A blessed box shows its three offers ({@link BoxRolls#offers}, rolled
 * from the seed the box keeps) and gives the one the hunter picks ({@link #pick}); closing the window keeps the box and
 * its three. A cursed box rolls better than either and brings a curse down ({@link BoxCurse}): the Wardens hold what
 * it gave in a quest until they are all down. The screen plays every opening as a moment of its own
 * ({@link RewardBoxPayload}).
 *
 * <p><b>Where they come from.</b> The daily training pays a Random Box (as the System of the story did); a boss's
 * cache sometimes holds one, an ascended boss's or a hidden chamber's a blessed one, a red gate's a cursed one
 * ({@link #fromCache}); every secret quest pays a blessed one. Chances are {@link RewardConfig}'s.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RewardBoxes {
    /** The Wardens curse's quest. */
    public static final String CURSE = BeyondSolo.id("box/wardens").toString();
    /** A warden's persistent data: the token of the curse it guards (and whose box it was). */
    public static final String WARDEN = "beyondsolo_box_warden", OWNER = "beyondsolo_box_owner";
    private static final String TOKEN = "boxToken";

    /** Curses whose wardens still stand (tokens). A warden loaded from disk outside of one goes. */
    private static final Set<String> LIVE = new HashSet<>();
    /** Game tests listening in on what a player's screen is told (test players drop everything sent to them). */
    private static final java.util.Map<UUID, Consumer<RewardBoxPayload>> LISTENERS = new java.util.HashMap<>();

    // ------------------------------------------------------------------ opening

    /** Opens the box in the hunter's hand. Returns false if nothing happened. */
    public static boolean open(ServerPlayer p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (!(stack.getItem() instanceof RewardBoxItem box)) return false;
        if (!SystemAPI.isAwakened(p)) {
            p.displayClientMessage(Component.translatable("box.beyondsolo.locked"), true);
            return false;
        }
        if (p.getCooldowns().isOnCooldown(box)) return false;
        PlayerRank rank = RewardBoxItem.rank(stack);
        PlayerClass cls = SystemAPI.get(p).playerClass();
        switch (box.kind()) {
            case BLESSED -> {
                long seed = RewardBoxItem.seed(stack);
                if (seed == 0) {
                    seed = p.getRandom().nextLong() | 1;
                    RewardBoxItem.setSeed(stack, seed);
                }
                tell(p, RewardBoxPayload.offer(hand == InteractionHand.MAIN_HAND ? 0 : 1, seed, rank, BoxRolls.offers(rank, cls, seed), p.registryAccess()));
            }
            case PLAIN -> {
                BoxReward reward = BoxRolls.roll(BoxKind.PLAIN, rank, cls, p.getRandom());
                use(p, stack);
                give(p, reward);
                tell(p, RewardBoxPayload.opened(BoxKind.PLAIN, rank, reward, null, 0, p.registryAccess()));
            }
            case CURSED -> {
                BoxReward reward = BoxRolls.roll(BoxKind.CURSED, rank, cls, p.getRandom());
                use(p, stack);
                curse(p, rank, reward, BoxCurse.roll(p.getRandom()));
            }
        }
        p.getCooldowns().addCooldown(box, 20);
        return true;
    }

    /**
     * The hunter took offer {@code index} of the blessed box in {@code hand}. Refused unless that box is still there
     * with the seed its offers were shown from.
     */
    public static boolean pick(ServerPlayer p, int hand, long seed, int index) {
        ItemStack stack = p.getItemInHand(hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof RewardBoxItem box) || box.kind() != BoxKind.BLESSED) return false;
        if (seed == 0 || RewardBoxItem.seed(stack) != seed || index < 0 || index > 2 || !SystemAPI.isAwakened(p)) return false;
        PlayerRank rank = RewardBoxItem.rank(stack);
        BoxReward reward = BoxRolls.offers(rank, SystemAPI.get(p).playerClass(), seed).get(index);
        use(p, stack);
        // the boxes left in the stack hold three new things
        if (!stack.isEmpty()) RewardBoxItem.setSeed(stack, p.getRandom().nextLong() | 1);
        give(p, reward);
        tell(p, RewardBoxPayload.opened(BoxKind.BLESSED, rank, reward, null, 0, p.registryAccess()));
        return true;
    }

    private static void use(ServerPlayer p, ItemStack stack) {
        if (!p.getAbilities().instabuild) stack.shrink(1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /** Gives a reward: its items (dropped at the hunter's feet if there is no room), gold and stat points. */
    public static void give(ServerPlayer p, BoxReward reward) {
        for (ItemStack s : reward.items()) {
            ItemStack copy = s.copy();
            if (!p.getInventory().add(copy) && !copy.isEmpty()) p.drop(copy, false);
        }
        if (reward.gold() > 0) Economy.add(p, reward.gold(), Economy.Reason.BOX);
        if (reward.statPoints() > 0) SystemAPI.addStatPoints(p, reward.statPoints());
    }

    // ------------------------------------------------------------------ curses

    /**
     * Ticks between a cursed box opening and its wardens climbing out: the moment on screen names the curse first (at
     * about 2 s) and they come as it fades (it lasts about 6 s), so nothing hits the hunter while it covers the screen.
     */
    public static final int WARDEN_DELAY = 90;

    /** A cursed box's reward and its curse. Public for tests and commands. */
    public static void curse(ServerPlayer p, PlayerRank rank, BoxReward reward, BoxCurse curse) {
        long detail = 0;
        List<BlockPos> spots = List.of();
        switch (curse) {
            case WARDENS -> {
                spots = wardenSpots(p, rank);
                if (spots.isEmpty()) {
                    // nowhere for them to climb out: the curse takes another shape
                    curse(p, rank, reward, BoxCurse.FRAILTY);
                    return;
                }
                detail = spots.size();
            }
            case FRAILTY -> {
                give(p, reward);
                p.addEffect(new MobEffectInstance(RewardModule.FRAILTY, RewardConfig.frailtyTicks(), 0, false, false, true));
                detail = RewardConfig.frailtyTicks() / 1200;
            }
            case BACKLASH -> {
                give(p, reward);
                ManaPoisoning.add(p, RewardConfig.backlash());
                detail = RewardConfig.backlash();
            }
            case GREED -> {
                give(p, reward);
                long gold = Economy.gold(p);
                long taken = Math.min(gold, Math.round(gold * RewardConfig.greedShare()));
                if (taken > 0) Economy.spend(p, taken, Economy.Reason.BOX);
                detail = taken;
            }
        }
        ServerLevel level = p.serverLevel();
        BodyParticles.send(p, ParticleTypes.SOUL_FIRE_FLAME, 1, 30, 0.5, 0.6, 0.5, 0.03);
        level.playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.6f);
        tell(p, RewardBoxPayload.opened(BoxKind.CURSED, rank, reward, curse, detail, p.registryAccess()));
        if (curse == BoxCurse.WARDENS) {
            // the ground where they will climb out darkens first
            for (BlockPos at : spots) level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.01);
            List<BlockPos> where = spots;
            ServerScheduler.schedule(WARDEN_DELAY, () -> {
                if (p.isRemoved() || !p.isAlive() || p.serverLevel() != level) return;
                wardens(p, rank, reward, where);
            });
        }
    }

    /** How many wardens a box of this rank lets out. */
    public static int wardenCount(PlayerRank rank) {
        return 3 + rank.tier() / 2;
    }

    /** Where a cursed box's wardens will climb out around the hunter (empty: there is no room). */
    static List<BlockPos> wardenSpots(ServerPlayer p, PlayerRank rank) {
        List<BlockPos> out = new ArrayList<>();
        int want = wardenCount(rank);
        for (int i = 0; i < want * 2 && out.size() < want; i++) {
            BlockPos at = Foes.spotAround(p.serverLevel(), p.blockPosition(), 3, 8);
            if (at != null && !out.contains(at)) out.add(at);
        }
        return out;
    }

    /**
     * The Wardens: the box's guardians climb out at {@code spots}, and what it held goes into a quest that pays it once
     * they are all down. Returns how many came out (0: none could).
     */
    public static int wardens(ServerPlayer p, PlayerRank rank, BoxReward reward, List<BlockPos> spots) {
        ServerLevel level = p.serverLevel();
        RandomSource r = level.random;
        String token = UUID.randomUUID().toString();
        EnemyRoster.Mix mix = EnemyRoster.random(rank, r);
        List<Mob> out = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            EntityType<?> type = mix.pick(r);
            EnemyTraits traits = Enemies.roll(rank, mix, type, r, i == 0, false);
            Mob mob = Enemies.spawn(level, rank, type, spots.get(i), traits, -1);
            if (mob == null) continue;
            mob.getPersistentData().putString(WARDEN, token);
            mob.getPersistentData().putUUID(OWNER, p.getUUID());
            mob.setTarget(p);
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 16, 0.4, 0.8, 0.4, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.3, mob.getZ(), 10, 0.4, 0.2, 0.4, 0.02);
            out.add(mob);
        }
        if (out.isEmpty()) {
            // nothing could climb out after all: the box's things are the hunter's
            give(p, reward);
            return 0;
        }
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.6f, 1.4f);
        LIVE.add(token);
        CompoundTag extra = new CompoundTag();
        extra.putString(TOKEN, token);
        extra.putInt("rank", rank.tier());
        if (reward.gold() > 0) extra.putLong(Association.REWARD_GOLD, reward.gold());
        QuestBuilder b = QuestBuilder.of(CURSE, QuestCategory.EMERGENCY, rank.tier(), SystemAPI.get(p).level()).fixed()
                .title(Component.translatable("quest.beyondsolo.box.wardens"))
                .description(Component.translatable("quest.beyondsolo.box.wardens.desc", out.size()))
                .custom(key(token), out.size(), Component.translatable("quest_objective.beyondsolo.box.wardens"))
                .timeLimit(RewardConfig.wardenTicks())
                .area(p, p.blockPosition(), 32, 48)
                .extra(extra);
        for (ItemStack s : reward.items()) b.item(s.copy());
        if (reward.statPoints() > 0) b.statPoints(reward.statPoints());
        QuestEngine.start(p, b.build());
        return out.size();
    }

    private static String key(String token) {
        return CURSE + "/" + token;
    }

    @Nullable
    private static String tokenOf(Quest q) {
        return q.template().equals(CURSE) ? q.extra().getString(TOKEN) : null;
    }

    @SubscribeEvent
    static void onWardenDown(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (!dead.getPersistentData().contains(WARDEN) || !(dead.level() instanceof ServerLevel level)) return;
        String token = dead.getPersistentData().getString(WARDEN);
        UUID owner = dead.getPersistentData().hasUUID(OWNER) ? dead.getPersistentData().getUUID(OWNER) : null;
        ServerPlayer p = OnlinePlayers.get(level.getServer(), owner);
        if (p != null) QuestEngine.progress(p, key(token), 1);
    }

    @SubscribeEvent
    static void onCurseBroken(QuestEvent.Completed event) {
        Quest q = event.getQuest();
        String token = tokenOf(q);
        if (token == null) return;
        ServerPlayer p = event.getPlayer();
        long gold = q.extra().getLong(Association.REWARD_GOLD);
        if (gold > 0) Economy.add(p, gold, Economy.Reason.BOX);
        end(p.server, token);
    }

    @SubscribeEvent
    static void onCurseHeld(QuestEvent.Failed event) {
        Quest q = event.getQuest();
        String token = tokenOf(q);
        if (token == null) return;
        ServerPlayer p = event.getPlayer();
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.box.crumbled.title"), Component.translatable("notify.beyondsolo.box.crumbled")));
        end(p.server, token);
    }

    /** The curse is over: whatever wardens still stand go back where they came from. */
    private static void end(MinecraftServer server, String token) {
        LIVE.remove(token);
        for (ServerLevel level : server.getAllLevels()) {
            // gathered first, then sent off: taking one out of the level while walking its entities can skip the next
            List<Mob> wardens = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob mob && token.equals(mob.getPersistentData().getString(WARDEN))) wardens.add(mob);
            }
            for (Mob mob : wardens) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                mob.discard();
            }
        }
    }

    /** A warden coming back from disk (a chunk loading again, a restart) stays only while its curse still holds. */
    @SubscribeEvent
    static void onWardenLoaded(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().contains(WARDEN)) return;
        String token = e.getPersistentData().getString(WARDEN);
        if (LIVE.contains(token)) return;
        UUID owner = e.getPersistentData().hasUUID(OWNER) ? e.getPersistentData().getUUID(OWNER) : null;
        ServerPlayer p = OnlinePlayers.get(level.getServer(), owner);
        if (p != null) {
            for (Quest q : QuestEngine.data(p).active()) {
                if (token.equals(tokenOf(q))) {
                    LIVE.add(token);
                    return;
                }
            }
        }
        event.setCanceled(true);
    }

    /** Whether this curse's wardens still stand (for tests). */
    public static boolean live(Quest q) {
        String token = tokenOf(q);
        return token != null && LIVE.contains(token);
    }

    /** The wardens of a curse quest in a level (for tests and commands). */
    public static List<Mob> wardensOf(ServerLevel level, Quest q) {
        String token = tokenOf(q);
        List<Mob> out = new ArrayList<>();
        if (token == null) return out;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && mob.isAlive() && token.equals(mob.getPersistentData().getString(WARDEN))) out.add(mob);
        }
        return out;
    }

    // ------------------------------------------------------------------ where they come from

    /** The daily training's box: a plain one of the hunter's rank, or empty if the config says no. */
    public static ItemStack daily(ServerPlayer p) {
        return RewardConfig.dailyBox() ? RewardBoxItem.of(BoxKind.PLAIN, SystemAPI.get(p).rank(), 1) : ItemStack.EMPTY;
    }

    /** A secret quest's box: a blessed one of the hunter's rank, or empty if the config says no. */
    public static ItemStack secret(ServerPlayer p) {
        return RewardConfig.secretBox() ? RewardBoxItem.of(BoxKind.BLESSED, SystemAPI.get(p).rank(), 1) : ItemStack.EMPTY;
    }

    /**
     * What a reward cache adds: a cursed box now and then from a red gate's, a blessed one from an ascended boss's or a
     * hidden chamber's, a plain one from any boss's. At most one box a cache.
     */
    public static List<ItemStack> fromCache(PlayerRank rank, boolean boss, boolean ascended, boolean red, boolean hidden, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        if (red) {
            if (r.nextDouble() < RewardConfig.redChance()) out.add(RewardBoxItem.of(BoxKind.CURSED, rank, 1));
        } else if (hidden) {
            if (r.nextDouble() < RewardConfig.hiddenChance()) out.add(RewardBoxItem.of(BoxKind.BLESSED, rank, 1));
            else if (r.nextDouble() < Math.min(1, RewardConfig.bossChance() * 2)) out.add(RewardBoxItem.of(BoxKind.PLAIN, rank, 1));
        } else if (boss) {
            if (ascended && r.nextDouble() < RewardConfig.ascendedChance()) out.add(RewardBoxItem.of(BoxKind.BLESSED, rank, 1));
            else if (r.nextDouble() < RewardConfig.bossChance()) out.add(RewardBoxItem.of(BoxKind.PLAIN, rank, 1));
        }
        return out;
    }

    // ------------------------------------------------------------------ the screen

    private static void tell(ServerPlayer p, RewardBoxPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
        Consumer<RewardBoxPayload> listener = LISTENERS.get(p.getUUID());
        if (listener != null) listener.accept(payload);
    }

    /** For game tests: hears what a player's screen is told (null stops listening). */
    public static void listen(UUID player, @Nullable Consumer<RewardBoxPayload> listener) {
        if (listener == null) LISTENERS.remove(player);
        else LISTENERS.put(player, listener);
    }

    /** The offers a payload carries (for tests). */
    public static List<BoxReward> offers(RewardBoxPayload payload, net.minecraft.core.HolderLookup.Provider provider) {
        List<BoxReward> out = new ArrayList<>();
        for (net.minecraft.nbt.Tag t : payload.tag().getList("offers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            out.add(BoxReward.load((CompoundTag) t, provider));
        }
        return out;
    }

    private RewardBoxes() {}
}
