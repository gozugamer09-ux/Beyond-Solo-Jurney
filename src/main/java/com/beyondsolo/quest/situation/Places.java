package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Places that answer the player walking into them. Each is an offer (exploring is the player's choice), and each turns
 * once they are in it: the Silent City wakes up, an outpost sends its counterattack, ruins raise their guardian.
 */
public final class Places {
    static final String BOX = "box";
    static final String OPENED = "opened";
    static final String LOST = "lost";

    /** A situation that starts inside a kind of structure. */
    abstract static class Place extends Situation {
        Place(String path) {
            super(path);
        }

        /** Whether this is the kind of place. */
        abstract boolean matches(Holder<Structure> structure);

        @Override
        public boolean offered() {
            return true;
        }

        /** The place the player is standing in, or null. Looked up every few seconds only. */
        @Nullable
        StructureStart at(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) return null;
            BlockPos pos = player.blockPosition();
            if (!level.isLoaded(pos)) return null;
            StructureStart s = level.structureManager().getStructureWithPieceAt(pos, this::matches);
            return s.isValid() ? s : null;
        }

        boolean due(ServerPlayer player) {
            return (QuestEngine.gameTime(player) / 20 + Math.abs(path().hashCode())) % 4 == 0;
        }

        @Override
        @Nullable
        public Quest detect(ServerPlayer player, Watch watch) {
            if (!due(player)) return null;
            StructureStart s = at(player);
            return s == null ? null : detectHere(player, s);
        }

        /** The first quest for a player standing in {@code s}, or null if the place gives none right now. */
        @Nullable
        abstract Quest detectHere(ServerPlayer player, StructureStart s);

        static void putBox(CompoundTag run, BoundingBox box) {
            run.putIntArray(BOX, new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        }

        /** Whether a spot is within the place (give or take {@code margin} blocks). */
        static boolean inside(Run run, BlockPos p, int margin) {
            int[] b = run.tag().getIntArray(BOX);
            if (b.length < 6) return false;
            return p.getX() >= b[0] - margin && p.getX() <= b[3] + margin && p.getY() >= b[1] - margin && p.getY() <= b[4] + margin
                    && p.getZ() >= b[2] - margin && p.getZ() <= b[5] + margin;
        }

        static BlockPos center(Run run) {
            int[] b = run.tag().getIntArray(BOX);
            if (b.length < 6) return BlockPos.ZERO;
            return new BlockPos((b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2);
        }

        CompoundTag startRun(ServerPlayer player, String stage, StructureStart s) {
            CompoundTag run = Run.start(this, stage, QuestEngine.gameTime(player));
            putBox(run, s.getBoundingBox());
            return run;
        }

        /** The player opened something in the place. Return true if it counted. */
        boolean opened(ServerPlayer player, Quest q, Run run, BlockPos pos) {
            return false;
        }

        /** Counts a container once per run. */
        static boolean firstOpening(Run run, BlockPos pos) {
            long key = pos.asLong();
            for (Tag t : run.tag().getList(OPENED, Tag.TAG_LONG)) if (((LongTag) t).getAsLong() == key) return false;
            var list = run.tag().getList(OPENED, Tag.TAG_LONG);
            list.add(LongTag.valueOf(key));
            run.tag().put(OPENED, list);
            return true;
        }
    }

    /** Something worth searching: a chest, a barrel, a pot, a block to brush. */
    static boolean searchable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity || state.getBlock() instanceof BrushableBlock
                || state.getBlock() instanceof DecoratedPotBlock;
    }

    /** The player used a block: it may be a place's chest. */
    static void opened(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !searchable(level, pos)) return;
        for (Quest q : List.copyOf(QuestEngine.data(player).active())) {
            Run run = Run.of(q);
            if (run == null || q.status() != Quest.Status.ACTIVE) continue;
            if (Situations.byId(run.situation()) instanceof Place place && Place.inside(run, pos, 2) && Place.firstOpening(run, pos)) {
                place.opened(player, q, run, pos);
            }
        }
    }

    // ================================================================ the Silent City

    /** An ancient city: plunder it without waking what sleeps there. Woken, it is a race to the surface. */
    public static final class SilentCity extends Place {
        public static final String EXPLORE = "explore";
        public static final String AWAKENED = "awakened";
        static final String LOOT = BeyondSolo.id("city_loot").toString();
        static final String ESCAPE = BeyondSolo.id("city_escape").toString();
        static final String HEALTH = BeyondSolo.id("city_warden").toString();
        public static final int CHESTS = 4;

        public SilentCity() {
            super("silent_city");
        }

        @Override
        public int cooldownTicks() {
            return 20 * 60 * 30;
        }

        @Override
        boolean matches(Holder<Structure> structure) {
            return structure.is(BuiltinStructures.ANCIENT_CITY);
        }

        @Override
        @Nullable
        Quest detectHere(ServerPlayer player, StructureStart s) {
            return explore(player, startRun(player, EXPLORE, s));
        }

        Quest explore(ServerPlayer player, CompoundTag run) {
            return quest(player, EXPLORE, QuestCategory.SURPRISE, run, CHESTS)
                    .custom(LOOT, CHESTS, text("obj.loot"))
                    .rule(text("obj.silence"))
                    .timeLimit(20 * 60 * 20)
                    .xp(450).statPoints(1)
                    .build();
        }

        @Override
        boolean opened(ServerPlayer player, Quest q, Run run, BlockPos pos) {
            if (!run.is(EXPLORE)) return false;
            QuestObjective o = q.objectives().get(Situations.objective(q, LOOT));
            if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("city_plundered"));
            Situations.add(player, q, LOOT, 1);
            return true;
        }

        @Override
        public void update(ServerPlayer player, Quest q, Run run) {
            if (run.is(EXPLORE)) {
                int lost = inside(run, player.blockPosition(), 24) ? 0 : run.count(LOST) + 1;
                run.setCount(LOST, lost);
                if (lost >= 10) {
                    QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("left_city"));
                    return;
                }
                List<Warden> wardens = player.level().getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(48), LivingEntity::isAlive);
                if (!wardens.isEmpty()) awaken(player, q, run, wardens.get(0));
                return;
            }
            LivingEntity warden = Foes.find(player, run.target());
            if (warden != null) {
                if (run.refreshTargetId(warden)) QuestEngine.sync(player);
                Situations.set(player, q, HEALTH, Foes.healthPercent(warden));
            }
            boolean clear = !inside(run, player.blockPosition(), 16) && (warden == null || warden.distanceToSqr(player) > 32 * 32);
            if (clear) {
                q.setOutcome(outcome("escaped_city"));
                Situations.set(player, q, ESCAPE, 1);
            }
        }

        private void awaken(ServerPlayer player, Quest q, Run run, Warden warden) {
            CompoundTag next = run.next(AWAKENED);
            Run.setTarget(next, warden);
            Component name = Foes.name(warden);
            Run.setName(next, name, player.registryAccess());
            Quest flee = quest(player, AWAKENED, QuestCategory.SURPRISE, next, name)
                    .line(ESCAPE, text("obj.escape"))
                    .watch(HEALTH, name, Foes.healthPercent(warden))
                    .xp(260)
                    .build();
            Situations.branch(player, q, flee, false);
        }

        @Override
        public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
            if (!run.is(AWAKENED) || !dead.getUUID().equals(run.target())) return;
            if (Foes.playerBehind(source.getEntity()) == player) {
                QuestEngine.complete(player, q, outcome("warden_slain"), explore(player, run.next(EXPLORE)).rewards());
            }
        }

        @Override
        public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
            if (reason == null && run.is(EXPLORE)) Secrets.reveal(player, Secrets.SILENT);
        }
    }

    // ================================================================ an outpost

    /** A pillager outpost: clear it, then hold it against the war party that comes to take it back. */
    public static final class Outpost extends Place {
        public static final String CLEAR = "clear";
        public static final String HOLD = "hold";
        static final String RAIDERS = BeyondSolo.id("outpost_raiders").toString();
        static final String HOLD_KEY = BeyondSolo.id("outpost_hold").toString();
        static final String WAVE = "wave";
        public static final int HOLD_SECONDS = 60;

        public Outpost() {
            super("outpost");
        }

        @Override
        public int cooldownTicks() {
            return 20 * 60 * 20;
        }

        @Override
        boolean matches(Holder<Structure> structure) {
            return structure.is(BuiltinStructures.PILLAGER_OUTPOST);
        }

        @Override
        @Nullable
        Quest detectHere(ServerPlayer player, StructureStart s) {
            BoundingBox box = s.getBoundingBox();
            int raiders = player.level().getEntitiesOfClass(Raider.class, net.minecraft.world.phys.AABB.of(box).inflate(12), LivingEntity::isAlive).size();
            if (raiders < 2) return null;
            return quest(player, CLEAR, QuestCategory.SURPRISE, startRun(player, CLEAR, s), raiders)
                    .custom(RAIDERS, raiders, text("obj.raiders"))
                    .timeLimit(20 * 60 * 10)
                    .xp(220)
                    .build();
        }

        @Override
        public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {
            if (!(victim instanceof Raider)) return;
            Quest q = Situations.running(player, this);
            Run run = q == null ? null : Run.of(q);
            if (run == null || !inside(run, victim.blockPosition(), 16)) return;
            if (run.is(CLEAR)) {
                QuestObjective o = q.objectives().get(Situations.objective(q, RAIDERS));
                if (o.progress() + 1 >= o.required()) counterattack(player, q, run);
                else Situations.add(player, q, RAIDERS, 1);
            }
        }

        /** The outpost is theirs: a war party comes to take it back. What clearing it paid is paid now. */
        private void counterattack(ServerPlayer player, Quest q, Run run) {
            if (!(player.level() instanceof ServerLevel level)) return;
            BlockPos center = center(run);
            int tier = SystemAPI.get(player).rank().tier();
            List<EntityType<? extends Raider>> party = new ArrayList<>();
            for (int i = 0; i < 3 + tier / 2; i++) party.add(EntityType.PILLAGER);
            party.add(EntityType.VINDICATOR);
            if (tier >= 2) party.add(EntityType.VINDICATOR);
            if (tier >= 4) party.add(EntityType.RAVAGER);
            List<java.util.UUID> wave = new ArrayList<>();
            for (EntityType<? extends Raider> type : party) {
                BlockPos at = Foes.spotAround(level, center, 22, 30);
                if (at == null) continue;
                Raider r = type.create(level);
                if (r == null) continue;
                r.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
                r.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
                r.setPersistenceRequired();
                level.addFreshEntity(r);
                r.setTarget(player);
                wave.add(r.getUUID());
                Situations.markTarget(r);
            }
            level.playSound(null, center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1f);
            CompoundTag next = run.next(HOLD);
            Run.putList(next, WAVE, wave);
            Run.setWaypoint(next, level.dimension(), center);
            Quest hold = quest(player, HOLD, QuestCategory.SURPRISE, next, wave.size())
                    .hold(HOLD_KEY, HOLD_SECONDS, text("obj.hold"))
                    .rule(text("obj.stay"))
                    .xp(260)
                    .item(new ItemStack(Items.EMERALD, 4 + 2 * tier))
                    .build();
            Situations.branch(player, q, hold, true);
        }

        @Override
        public void update(ServerPlayer player, Quest q, Run run) {
            if (!run.is(HOLD)) return;
            int lost = inside(run, player.blockPosition(), 14) ? 0 : run.count(LOST) + 1;
            run.setCount(LOST, lost);
            if (lost >= 8) {
                QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("outpost_lost"));
                return;
            }
            int left = 0;
            for (java.util.UUID id : run.list(WAVE)) if (Foes.find(player, id) != null) left++;
            if (left == 0) {
                QuestEngine.complete(player, q, outcome("outpost_held"));
                return;
            }
            QuestObjective o = q.objectives().get(Situations.objective(q, HOLD_KEY));
            if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("outpost_held"));
            Situations.add(player, q, HOLD_KEY, 1);
        }
    }

    // ================================================================ ruins

    /** Old ruins: search them, and their guardian rises. */
    public static final class Ruins extends Place {
        public static final String SEARCH = "search";
        public static final String GUARDIAN = "guardian";
        static final String SEARCH_KEY = BeyondSolo.id("ruins_search").toString();
        static final String KILL = BeyondSolo.id("ruins_guardian").toString();
        static final String HEALTH = BeyondSolo.id("ruins_health").toString();
        static final String RANK = "rank";
        public static final int SEARCHES = 2;
        private static final Set<ResourceKey<Structure>> KINDS = Set.of(BuiltinStructures.TRAIL_RUINS, BuiltinStructures.DESERT_PYRAMID,
                BuiltinStructures.JUNGLE_TEMPLE, BuiltinStructures.OCEAN_RUIN_COLD, BuiltinStructures.OCEAN_RUIN_WARM);
        /** Structures from other mods count when their name says they are ruins of some kind. */
        private static final List<String> WORDS = List.of("ruin", "temple", "tomb", "crypt", "catacomb", "pyramid", "shrine", "sanctum");

        public Ruins() {
            super("ruins");
        }

        @Override
        public int cooldownTicks() {
            return 20 * 60 * 15;
        }

        @Override
        boolean matches(Holder<Structure> structure) {
            for (ResourceKey<Structure> k : KINDS) if (structure.is(k)) return true;
            var key = structure.unwrapKey();
            if (key.isEmpty() || key.get().location().getNamespace().equals("minecraft")) return false;
            String path = key.get().location().getPath();
            for (String w : WORDS) if (path.contains(w)) return true;
            return false;
        }

        @Override
        @Nullable
        Quest detectHere(ServerPlayer player, StructureStart s) {
            return quest(player, SEARCH, QuestCategory.SURPRISE, startRun(player, SEARCH, s), SEARCHES)
                    .custom(SEARCH_KEY, SEARCHES, text("obj.search"))
                    .timeLimit(20 * 60 * 10)
                    .xp(120)
                    .build();
        }

        @Override
        boolean opened(ServerPlayer player, Quest q, Run run, BlockPos pos) {
            if (!run.is(SEARCH)) return false;
            QuestObjective o = q.objectives().get(Situations.objective(q, SEARCH_KEY));
            // the first search wakes the guardian; the quest goes on as its fight
            if (o.progress() == 0 && raise(player, q, run, pos)) return true;
            if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("ruins_searched"));
            Situations.add(player, q, SEARCH_KEY, 1);
            return true;
        }

        /** The ruins' guardian rises: one of the System's creatures, a champion, the player's rank (sometimes one above). */
        private boolean raise(ServerPlayer player, Quest q, Run run, BlockPos near) {
            if (!(player.level() instanceof ServerLevel level)) return false;
            BlockPos at = Foes.spotAround(level, near, 6, 12);
            if (at == null) return false;
            RandomSource r = level.random;
            PlayerRank own = SystemAPI.get(player).rank();
            PlayerRank rank = r.nextFloat() < 0.3f && own.ordinal() + 1 < PlayerRank.values().length ? PlayerRank.values()[own.ordinal() + 1] : own;
            EnemyRoster.Mix mix = EnemyRoster.random(rank, r);
            EntityType<?> type = mix.pick(r);
            Variant v = Variant.CHAMPION;
            EnemyTraits traits = new EnemyTraits(mix.affinityFor(type, r, true), v, Affix.mask(Affix.roll(v.affixes, r)));
            Mob guardian = Enemies.spawn(level, rank, type, at, traits, -1);
            if (guardian == null) return false;
            guardian.setTarget(player);
            ParticleGuard.send(level, ParticleTypes.SOUL, guardian.getX(), guardian.getY() + 1, guardian.getZ(), 30, 0.6, 1.0, 0.6, 0.03);
            level.playSound(null, at, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.4f, 0.6f);
            CompoundTag next = run.next(GUARDIAN);
            Run.setTarget(next, guardian);
            Component name = Foes.name(guardian);
            Run.setName(next, name, player.registryAccess());
            next.putInt(RANK, rank.ordinal());
            Quest fight = quest(player, GUARDIAN, QuestCategory.SURPRISE, next, name)
                    .line(KILL, text("obj.guardian", name))
                    .watch(HEALTH, name, Foes.healthPercent(guardian))
                    .xp(380).statPoints(1)
                    .item(DungeonKeys.key(rank))
                    .build();
            Situations.branch(player, q, fight, false);
            return true;
        }

        @Override
        public void update(ServerPlayer player, Quest q, Run run) {
            if (!run.is(GUARDIAN)) return;
            LivingEntity guardian = Foes.find(player, run.target());
            if (guardian != null) {
                if (run.refreshTargetId(guardian)) QuestEngine.sync(player);
                Situations.set(player, q, HEALTH, Foes.healthPercent(guardian));
            }
            int lost = guardian != null && guardian.distanceToSqr(player) < 48 * 48 ? 0 : run.count(LOST) + 1;
            run.setCount(LOST, lost);
            if (lost >= 12) QuestEngine.fail(player, q, QuestEvent.FailReason.FLED, outcome("guardian_left", run.name(player.registryAccess())));
        }

        @Override
        public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
            if (!run.is(GUARDIAN) || !dead.getUUID().equals(run.target())) return;
            Component name = run.name(player.registryAccess());
            if (Foes.playerBehind(source.getEntity()) == player) {
                q.setOutcome(outcome("guardian_slain", name));
                Situations.set(player, q, KILL, 1);
            } else {
                QuestEngine.complete(player, q, outcome("it_fell", name), q.rewards().partial(0.5f));
            }
        }
    }

    private Places() {}
}
