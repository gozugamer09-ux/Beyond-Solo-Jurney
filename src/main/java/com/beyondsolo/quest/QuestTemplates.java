package com.beyondsolo.quest;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quest templates the engine knows, by id. Built-in ones: the System quest line ({@link #SET_LINE}), the
 * daily quest, and the surprise quests the triggers start. Other modules add theirs with {@link #register}.
 */
public final class QuestTemplates {
    private static final Map<String, QuestTemplate> TEMPLATES = new LinkedHashMap<>();

    private static final int MINUTE = 20 * 60;

    public static final String PROOF_OF_STRENGTH = id("set/proof_of_strength");
    public static final String PROVISIONS = id("set/provisions");
    public static final String GROWTH = id("set/growth");
    public static final String TRIAL_OF_DEPTHS = id("set/trial_of_depths");
    /** The System quest line, in order. Each one starts when the one before it is done. */
    public static final List<String> SET_LINE = List.of(PROOF_OF_STRENGTH, PROVISIONS, GROWTH, TRIAL_OF_DEPTHS);

    public static final String DAILY = id("daily/conditioning");
    public static final String SPAWNER_NEST = id("surprise/spawner_nest");
    public static final String AMBUSH = id("surprise/ambush");
    public static final String DEEP_VEINS = id("surprise/deep_veins");

    public static void register(String id, QuestTemplate template) {
        TEMPLATES.put(id, template);
    }

    @Nullable
    public static QuestTemplate get(String id) {
        return TEMPLATES.get(id);
    }

    public static Set<String> ids() {
        return TEMPLATES.keySet();
    }

    @Nullable
    public static Quest create(String id, ServerPlayer player, QuestTemplate.Context context) {
        QuestTemplate t = TEMPLATES.get(id);
        return t == null ? null : t.create(player, context);
    }

    /** The quest after {@code template} in the System line, or null at the end. */
    @Nullable
    public static String nextInLine(String template) {
        int i = SET_LINE.indexOf(template);
        return i < 0 || i + 1 >= SET_LINE.size() ? null : SET_LINE.get(i + 1);
    }

    static {
        // ---- the System quest line
        register(PROOF_OF_STRENGTH, (p, c) -> QuestBuilder.of(PROOF_OF_STRENGTH, QuestCategory.SET, p)
                .text("proof_of_strength")
                .kill(Targets.HOSTILE, 5)
                .xp(120).statPoints(1).item(new ItemStack(Items.BREAD, 6))
                .build());
        register(PROVISIONS, (p, c) -> QuestBuilder.of(PROVISIONS, QuestCategory.SET, p)
                .text("provisions")
                .collect("minecraft:iron_ingot", 5)
                .collect("#minecraft:logs", 16)
                .xp(180).item(new ItemStack(ModItems.ANTIDOTE.get(), 2)).item(new ItemStack(ModItems.CLOTTING_TONIC.get()))
                .build());
        register(GROWTH, (p, c) -> QuestBuilder.of(GROWTH, QuestCategory.SET, p)
                .text("growth")
                .reachLevel(5)
                .travel(600)
                .xp(260).statPoints(2).mana(50)
                .build());
        register(TRIAL_OF_DEPTHS, (p, c) -> QuestBuilder.of(TRIAL_OF_DEPTHS, QuestCategory.SET, p)
                .text("trial_of_depths")
                // the gate module advances this when a gate's boss falls (com.beyondsolo.gate.GateService.CLEAR_KEY)
                .custom(id("gate_clear"), 1, net.minecraft.network.chat.Component.translatable("quest_objective.beyondsolo.gate_clear"))
                .kill(Targets.HOSTILE, 15)
                .xp(400).statPoints(3).item(new ItemStack(ModItems.QUENCHING_ELIXIR.get()))
                .rewardTitle("Relentless")
                .build());

        // ---- daily: resets with the in-game day, failing it costs you
        register(DAILY, (p, c) -> QuestBuilder.of(DAILY, QuestCategory.DAILY, p)
                .text("daily")
                .travel(800)
                .kill(Targets.HOSTILE, 8)
                .breakBlocks("#minecraft:base_stone_overworld", 24)
                .xp(150).statPoints(1)
                .penalty(40, 30)
                .build());

        // ---- surprise quests (see quest.trigger)
        register(SPAWNER_NEST, (p, c) -> {
            String mob = c.target().isEmpty() ? Targets.HOSTILE : c.target();
            QuestBuilder b = QuestBuilder.of(SPAWNER_NEST, QuestCategory.EMERGENCY, p).text("spawner_nest");
            if (c.pos() != null) b.area(p, c.pos(), 20, 48);
            return b.killInArea(mob, 10)
                    .breakInArea("minecraft:spawner", 1)
                    .timeLimit(5 * MINUTE)
                    .xp(220).statPoints(1).item(new ItemStack(Items.EXPERIENCE_BOTTLE, 3))
                    .penalty(35, 30)
                    .build();
        });
        register(AMBUSH, (p, c) -> {
            QuestBuilder b = QuestBuilder.of(AMBUSH, QuestCategory.EMERGENCY, p).text("ambush");
            b.area(p, c.pos() != null ? c.pos() : p.blockPosition(), 28, 56);
            return b.killInArea(Targets.HOSTILE, 6)
                    .timeLimit(2 * MINUTE)
                    .xp(160).item(new ItemStack(ModItems.CLOTTING_TONIC.get()))
                    .penalty(30, 20)
                    .build();
        });
        register(DEEP_VEINS, (p, c) -> QuestBuilder.of(DEEP_VEINS, QuestCategory.SURPRISE, p)
                .text("deep_veins")
                .breakBlocks("#c:ores", 8)
                .timeLimit(10 * MINUTE)
                .xp(180).item(new ItemStack(Items.TORCH, 16))
                .build());
    }

    private static String id(String path) {
        return BeyondSolo.id(path).toString();
    }

    private QuestTemplates() {}
}
