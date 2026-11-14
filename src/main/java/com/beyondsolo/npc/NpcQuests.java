package com.beyondsolo.npc;

import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestArea;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestGiver;
import com.beyondsolo.quest.RankScaling;
import com.beyondsolo.quest.Targets;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What villagers ask for. {@link #plan} rolls which kind of request sits in a board slot (the same for every
 * player that day); {@link #build} turns it into a quest scaled to one player's rank.
 *
 * <p>Scaling: counts and XP go through {@link RankScaling} like every other quest; monsters come from pools that
 * open up rank by rank; distances grow with rank; emeralds and the chance of a remedy or a stat point grow too.</p>
 */
public final class NpcQuests {
    /** How many requests a player can hold at once. */
    public static final int MAX_HELD = 3;
    private static final int DAY = 24000;
    private static final int MINUTE = 20 * 60;

    public record Plan(NpcQuestKind kind, long seed) {}

    /** An item request: an item id or #tag and its base count for an E-rank. */
    private record Want(String item, int count) {}

    /** A monster and its base count for an E-rank hunt. */
    private record Prey(String entity, int count) {}

    // ------------------------------------------------------------------ pools

    private static final Map<String, List<Want>> GATHER = Map.ofEntries(
            Map.entry("farmer", List.of(new Want("minecraft:wheat", 16), new Want("minecraft:carrot", 12), new Want("minecraft:potato", 12),
                    new Want("minecraft:beetroot", 10), new Want("minecraft:pumpkin", 4), new Want("minecraft:melon_slice", 16))),
            Map.entry("fisherman", List.of(new Want("minecraft:cod", 6), new Want("minecraft:salmon", 5), new Want("minecraft:string", 6))),
            Map.entry("shepherd", List.of(new Want("#minecraft:wool", 8), new Want("minecraft:string", 8), new Want("minecraft:shears", 1))),
            Map.entry("fletcher", List.of(new Want("minecraft:stick", 24), new Want("minecraft:feather", 10), new Want("minecraft:flint", 8),
                    new Want("minecraft:arrow", 16))),
            Map.entry("librarian", List.of(new Want("minecraft:paper", 12), new Want("minecraft:book", 4), new Want("minecraft:ink_sac", 4),
                    new Want("minecraft:feather", 6))),
            Map.entry("cartographer", List.of(new Want("minecraft:paper", 12), new Want("minecraft:glass_pane", 8), new Want("minecraft:compass", 1))),
            Map.entry("cleric", List.of(new Want("minecraft:rotten_flesh", 12), new Want("minecraft:bone", 8), new Want("minecraft:spider_eye", 4),
                    new Want("minecraft:redstone", 10))),
            Map.entry("armorer", List.of(new Want("minecraft:iron_ingot", 6), new Want("minecraft:coal", 12), new Want("minecraft:copper_ingot", 10))),
            Map.entry("weaponsmith", List.of(new Want("minecraft:iron_ingot", 5), new Want("minecraft:coal", 12), new Want("minecraft:flint", 6))),
            Map.entry("toolsmith", List.of(new Want("minecraft:iron_ingot", 5), new Want("minecraft:coal", 12), new Want("#minecraft:logs", 16))),
            Map.entry("butcher", List.of(new Want("minecraft:porkchop", 6), new Want("minecraft:beef", 6), new Want("minecraft:chicken", 6),
                    new Want("minecraft:mutton", 6), new Want("minecraft:coal", 8))),
            Map.entry("leatherworker", List.of(new Want("minecraft:leather", 8), new Want("minecraft:rabbit_hide", 4), new Want("minecraft:flint", 6))),
            Map.entry("mason", List.of(new Want("minecraft:clay_ball", 12), new Want("minecraft:stone", 32), new Want("minecraft:andesite", 16),
                    new Want("minecraft:granite", 16))));

    /** For villagers without a job, nitwits and traders. */
    private static final List<Want> GATHER_ANYONE = List.of(new Want("#minecraft:small_flowers", 6), new Want("minecraft:bread", 6),
            new Want("minecraft:apple", 4), new Want("#minecraft:logs", 16), new Want("minecraft:torch", 16), new Want("minecraft:sweet_berries", 8));

    /** Hunt targets that open up by rank (index = tier). A hunt picks from the player's tier and the one below. */
    private static final List<List<Prey>> HUNT = List.of(
            List.of(new Prey("minecraft:zombie", 6), new Prey("minecraft:skeleton", 5), new Prey("minecraft:spider", 5)),
            List.of(new Prey("minecraft:creeper", 4), new Prey("minecraft:husk", 5), new Prey("minecraft:drowned", 4), new Prey("minecraft:stray", 4)),
            List.of(new Prey("minecraft:witch", 2), new Prey("minecraft:enderman", 2), new Prey("minecraft:cave_spider", 6), new Prey("minecraft:pillager", 3)),
            List.of(new Prey("minecraft:vindicator", 3), new Prey("minecraft:phantom", 3), new Prey("minecraft:slime", 6)),
            List.of(new Prey("minecraft:wither_skeleton", 3), new Prey("minecraft:blaze", 4), new Prey("minecraft:evoker", 1)),
            List.of(new Prey("minecraft:evoker", 2), new Prey("minecraft:ravager", 1), new Prey("minecraft:wither_skeleton", 4)));

    /** What gathers at a nest, by tier. */
    static final List<List<String>> NEST = List.of(
            List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"),
            List.of("minecraft:husk", "minecraft:stray", "minecraft:zombie", "minecraft:spider"),
            List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:witch", "minecraft:pillager"),
            List.of("minecraft:vindicator", "minecraft:pillager", "minecraft:skeleton", "minecraft:witch"),
            List.of("minecraft:vindicator", "minecraft:wither_skeleton", "minecraft:witch", "minecraft:pillager"),
            List.of("minecraft:vindicator", "minecraft:evoker", "minecraft:wither_skeleton"));

    // ------------------------------------------------------------------ the board

    /** How many requests a villager has each day: one per job level up to three; a trader two; children none. */
    public static int slots(AbstractVillager v) {
        if (v.isBaby()) return 0;
        if (v instanceof WanderingTrader) return 2;
        if (v instanceof Villager villager) {
            VillagerProfession p = villager.getVillagerData().getProfession();
            if (p == VillagerProfession.NONE || p == VillagerProfession.NITWIT) return 1;
            return Mth.clamp(villager.getVillagerData().getLevel(), 1, 3);
        }
        return 0;
    }

    public static String profession(AbstractVillager v) {
        if (v instanceof Villager villager) return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath();
        return "trader";
    }

    /** The request in a slot today. Same for every player. */
    public static Plan plan(AbstractVillager v, NpcBoard board, int slot) {
        long seed = v.getUUID().getMostSignificantBits() * 31 + v.getUUID().getLeastSignificantBits()
                + board.day() * 0x9E3779B97F4A7C15L + board.rerolls() * 0x632BE59BD9B4E019L + slot * 0x2545F4914F6CDD1DL;
        RandomSource r = RandomSource.create(seed);
        int[] w = weights(profession(v));
        int total = 0;
        for (int x : w) total += x;
        int pick = r.nextInt(total);
        NpcQuestKind kind = NpcQuestKind.GATHER;
        for (int i = 0; i < w.length; i++) {
            pick -= w[i];
            if (pick < 0) {
                kind = NpcQuestKind.values()[i];
                break;
            }
        }
        return new Plan(kind, r.nextLong());
    }

    /** Weights of GATHER, HUNT, CLEAR, ESCORT, DELIVER by job. */
    private static int[] weights(String profession) {
        return switch (profession) {
            case "farmer", "fisherman", "shepherd", "butcher" -> new int[]{5, 2, 2, 1, 2};
            case "armorer", "weaponsmith", "toolsmith" -> new int[]{3, 3, 3, 1, 1};
            case "cleric", "leatherworker", "fletcher" -> new int[]{3, 4, 2, 1, 1};
            case "librarian", "cartographer" -> new int[]{2, 1, 1, 2, 4};
            case "mason" -> new int[]{4, 2, 2, 1, 2};
            case "trader" -> new int[]{1, 1, 0, 5, 3};
            default -> new int[]{3, 2, 2, 2, 2};
        };
    }

    // ------------------------------------------------------------------ quests

    /**
     * Builds the request in a slot for one player. With {@code accepting}, places that need the world (the ground
     * height of a destination, the nearest other village) are looked up; without, the quest is only for showing.
     */
    public static Quest build(ServerPlayer player, AbstractVillager giver, Plan plan, boolean accepting) {
        RandomSource r = RandomSource.create(plan.seed());
        int tier = SystemAPI.get(player).rank().tier();
        NpcQuestKind kind = plan.kind();
        QuestGiver who = new QuestGiver(giver.getUUID(), NpcNames.full(giver), giver.blockPosition(), giver.level().dimension(), kind.turnIn());
        QuestBuilder b = QuestBuilder.of(kind.template(), QuestCategory.NPC, player).giver(who);
        Component name = NpcNames.name(giver);
        int variant = r.nextInt(3);
        String key = "npc." + kind.name().toLowerCase() + "." + variant;
        CompoundTag extra = new CompoundTag();
        extra.putString("token", accepting ? UUID.randomUUID().toString() : "preview");
        extra.putInt("variant", variant);

        switch (kind) {
            case GATHER -> {
                List<Want> pool = GATHER.getOrDefault(profession(giver), GATHER_ANYONE);
                Want first = pool.get(r.nextInt(pool.size()));
                b.text(key, name);
                b.deliver(first.item(), first.count());
                if (tier >= 2 && pool.size() > 1 && r.nextFloat() < 0.5f) {
                    Want second = pool.get(r.nextInt(pool.size()));
                    if (!second.item().equals(first.item())) b.deliver(second.item(), Math.max(1, second.count() / 2));
                }
                b.timeLimit(2 * DAY).xp(70);
            }
            case HUNT -> {
                int from = Math.max(0, tier - 1);
                List<Prey> pool = new java.util.ArrayList<>(HUNT.get(Math.min(tier, HUNT.size() - 1)));
                if (from != tier) pool.addAll(HUNT.get(from));
                Prey prey = pool.get(r.nextInt(pool.size()));
                Component mob = Targets.name(ObjectiveType.KILL, prey.entity());
                b.text(key, name, mob);
                b.kill(prey.entity(), prey.count());
                b.timeLimit(2 * DAY).xp(100);
            }
            case CLEAR -> {
                // out past the houses, further for higher ranks
                int dist = 46 + r.nextInt(16) + 6 * tier;
                float angle = r.nextFloat() * Mth.TWO_PI;
                BlockPos center = offset(giver, dist, angle, accepting);
                b.text(key, name, dist, direction(angle));
                b.area(new QuestArea(giver.level().dimension(), center, 16, 0));
                int count = RankScaling.count(6, tier);
                b.objective(ObjectiveType.KILL, Targets.HOSTILE, count, true, Component.translatable("quest_objective.beyondsolo.clear_nest"));
                b.timeLimit(DAY).xp(140);
                extra.putInt("nest", count + 1);
            }
            case ESCORT -> {
                int dist = Math.min(190, 70 + 20 * tier + r.nextInt(20));
                float angle = r.nextFloat() * Mth.TWO_PI;
                BlockPos dest = offset(giver, dist, angle, accepting);
                String traveller = NpcNames.firstName(new UUID(plan.seed(), plan.seed() * 31));
                extra.putString("traveller", traveller);
                b.text(key, Component.literal(traveller), dist, direction(angle));
                b.area(new QuestArea(giver.level().dimension(), dest, 6, 0));
                b.custom(escortKey(extra), 1, Component.translatable("quest_objective.beyondsolo.escort", traveller));
                b.timeLimit(12 * MINUTE + 2 * MINUTE * tier).xp(150);
            }
            case DELIVER -> {
                int dist = 140 + 25 * tier + r.nextInt(30);
                float angle = r.nextFloat() * Mth.TWO_PI;
                BlockPos dest = null;
                if (accepting && giver.level() instanceof ServerLevel level) {
                    dest = otherVillage(level, giver.blockPosition(), angle);
                    if (dest != null) {
                        dist = (int) Math.sqrt(dest.distSqr(giver.blockPosition()));
                        angle = (float) Math.atan2(dest.getZ() - giver.getZ(), dest.getX() - giver.getX());
                        extra.putBoolean("village", true);
                    }
                }
                if (dest == null) dest = offset(giver, dist, angle, accepting);
                b.text(key, name, dist, direction(angle));
                b.area(new QuestArea(giver.level().dimension(), dest, 12, 0));
                b.custom(deliverKey(extra), 1, Component.translatable("quest_objective.beyondsolo.deliver_parcel"));
                b.timeLimit(DAY).xp(120);
            }
        }

        // pay: emeralds always, sometimes a remedy, and a stat point for the dangerous ones
        int emeralds = new int[]{3, 4, 6, 6, 5}[kind.ordinal()] + 2 * tier + r.nextInt(2);
        b.item(new ItemStack(Items.EMERALD, emeralds));
        if (tier >= 2 && r.nextFloat() < 0.35f) {
            b.item(new ItemStack(tier >= 4 ? ModItems.QUENCHING_ELIXIR.get() : r.nextBoolean() ? ModItems.ANTIDOTE.get() : ModItems.CLOTTING_TONIC.get()));
        }
        b.penalty(12, 15);
        if ((kind == NpcQuestKind.CLEAR || kind == NpcQuestKind.ESCORT) && r.nextFloat() < 0.3f) b.fixed().statPoints(1);
        return b.extra(extra).build();
    }

    public static String escortKey(CompoundTag extra) {
        return "beyondsolo:escort/" + extra.getString("token");
    }

    public static String deliverKey(CompoundTag extra) {
        return "beyondsolo:deliver/" + extra.getString("token");
    }

    /** A spot {@code dist} blocks from the villager towards {@code angle}, on the ground if {@code ground}. */
    private static BlockPos offset(AbstractVillager v, int dist, float angle, boolean ground) {
        int x = Mth.floor(v.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(v.getZ() + Math.sin(angle) * dist);
        int y = ground ? v.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : v.getBlockY();
        return new BlockPos(x, y, z);
    }

    /** The nearest village that isn't this one, looked for in the given direction first. Null if none is close. */
    private static BlockPos otherVillage(ServerLevel level, BlockPos home, float angle) {
        for (int i = 0; i < 3; i++) {
            float a = angle + i * 2.1f;
            BlockPos probe = home.offset(Mth.floor(Math.cos(a) * 260), 0, Mth.floor(Math.sin(a) * 260));
            BlockPos found;
            try {
                found = level.findNearestMapStructure(StructureTags.VILLAGE, probe, 12, false);
            } catch (Exception e) {
                return null;
            }
            if (found == null) continue;
            if (found.distSqr(home) < 120 * 120 || found.distSqr(home) > 700 * 700) continue;
            return new BlockPos(found.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ()), found.getZ());
        }
        return null;
    }

    /** Compass direction for the quest text. Angle as in atan2(z, x): +x east, +z south. */
    static Component direction(float angle) {
        String[] dirs = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
        int i = Math.floorMod(Math.round(angle / (Mth.TWO_PI / 8)), 8);
        return Component.translatable("npc.beyondsolo.dir." + dirs[i]);
    }

    private NpcQuests() {}
}
