package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.BuildupData;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.system.SystemData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** Per-player System state. Survives death. */
    public static final Supplier<AttachmentType<SystemData>> SYSTEM = ATTACHMENT_TYPES.register("system",
            () -> AttachmentType.serializable(SystemData::new).copyOnDeath().build());

    /** Element buildup (blood, poison, ...) on any living entity. Cleared on death. */
    public static final Supplier<AttachmentType<BuildupData>> BUILDUP = ATTACHMENT_TYPES.register("buildup",
            () -> AttachmentType.serializable(BuildupData::new).build());

    /** Per-player quests (quest module). Survives death. */
    public static final Supplier<AttachmentType<QuestData>> QUESTS = ATTACHMENT_TYPES.register("quests",
            () -> AttachmentType.serializable(QuestData::new).copyOnDeath().build());

    /** A villager's request board (NPC module). Kept on the villager. */
    public static final Supplier<AttachmentType<com.beyondsolo.npc.NpcBoard>> NPC_BOARD = ATTACHMENT_TYPES.register("npc_board",
            () -> AttachmentType.serializable(com.beyondsolo.npc.NpcBoard::new).build());

    private ModAttachments() {}
}
