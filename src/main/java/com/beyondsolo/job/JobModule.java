package com.beyondsolo.job;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.JobRevealPayload;
import com.beyondsolo.network.JobSyncPayload;
import com.beyondsolo.network.JobTrialPayload;
import com.beyondsolo.network.SkipJobRevealPayload;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestTemplates;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the job change module: its player data, the Key of Ascension, network messages, config and quest.
 * Called once from the mod constructor.
 */
public final class JobModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);

    /** Per-player job change state. Survives death. */
    public static final Supplier<AttachmentType<JobData>> JOB = ATTACHMENTS.register("job",
            () -> AttachmentType.serializable(JobData::new).copyOnDeath().build());

    public static final DeferredItem<AscensionKeyItem> ASCENSION_KEY = ITEMS.register("ascension_key",
            () -> new AscensionKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The job change quest: clear the Trial of Ascension. */
    public static final String QUEST_TEMPLATE = BeyondSolo.id("job/ascension").toString();
    /** Quest progress key the trial advances when it is cleared. */
    public static final String TRIAL_KEY = BeyondSolo.id("job_trial").toString();

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(JobModule::payloads);
        modBus.addListener(JobModule::creativeTab);
        container.registerConfig(ModConfig.Type.COMMON, JobConfig.SPEC, "beyondsolo-jobs.toml");
        QuestTemplates.register(QUEST_TEMPLATE, (p, c) -> QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.SET, p)
                .fixed()
                .text("job_ascension")
                .custom(TRIAL_KEY, 1, Component.translatable("quest_objective.beyondsolo.job_trial"))
                .xp(1500).statPoints(5)
                .item(new ItemStack(Items.TOTEM_OF_UNDYING))
                .build());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(JobSyncPayload.TYPE, JobSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.job.JobClient.onSync(p)));
        r.playToClient(JobTrialPayload.TYPE, JobTrialPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.job.JobClient.onTrial(p)));
        r.playToClient(JobRevealPayload.TYPE, JobRevealPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.job.JobClient.onReveal(p)));
        r.playToClient(CutscenePayload.TYPE, CutscenePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.job.JobClient.onCutscene(p)));
        r.playToServer(SkipJobRevealPayload.TYPE, SkipJobRevealPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) JobTrial.skipReveal(player);
                }));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(BeyondSolo.id("combat"))) event.accept(ASCENSION_KEY);
    }

    private JobModule() {}
}
