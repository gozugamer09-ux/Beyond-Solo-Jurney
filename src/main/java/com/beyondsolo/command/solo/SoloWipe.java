package com.beyondsolo.command.solo;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.armory.SetPerks;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.depths.WeaknessLog;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.EconomyModule;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.JobModule;
import com.beyondsolo.job.JobTrial;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.situation.Situations;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.spawn.FirstSpawnHandler;
import com.beyondsolo.system.Exhaustion;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.util.ServerScheduler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * /solo wipe: {@code system} takes a player's System back to the very start (they keep their things and where they
 * stand), {@code all} makes them a brand-new player (inventory, ender chest, effects, XP and spawn point too, and a new
 * random start). Without either it asks which, with buttons.
 *
 * <p>The System part resets every per-player record the mod keeps (every data attachment of the {@code beyondsolo}
 * namespace), so a module added later is wiped without changes here. A module that keeps state elsewhere (a running
 * session, a map in memory) hooks its cancel into {@link #progress}.</p>
 */
final class SoloWipe {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("wipe")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(SoloWipe::prompt, false);
                    return 1;
                })
                .then(Solo.targets(Commands.literal("system"), (ctx, p) -> {
                    system(p);
                    return Solo.t("wipe.system.done", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("all"), (ctx, p) -> {
                    all(p);
                    return Solo.t("wipe.all.done", p.getDisplayName());
                })));
    }

    /** The question: two buttons, each saying what it takes. */
    private static Component prompt() {
        MutableComponent text = Solo.t("wipe.prompt").withStyle(ChatFormatting.GOLD);
        text.append("\n").append(button("system", ChatFormatting.AQUA)).append("  ").append(Solo.t("wipe.system.what").withStyle(ChatFormatting.GRAY));
        text.append("\n").append(button("all", ChatFormatting.RED)).append("  ").append(Solo.t("wipe.all.what").withStyle(ChatFormatting.GRAY));
        return text;
    }

    private static Component button(String which, ChatFormatting color) {
        String command = "/solo wipe " + which;
        return Component.literal("[").append(Solo.t("wipe." + which + ".button")).append("]")
                .withStyle(s -> s.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    // ---------------------------------------------------------------- the wipes

    /** Every bit of System progress goes; the player keeps their things and where they stand. */
    static void system(ServerPlayer p) throws CommandSyntaxException {
        progress(p);
        restart(p);
    }

    /** A brand-new player: all System progress, everything they carry, their effects, XP and spawn point. */
    static void all(ServerPlayer p) throws CommandSyntaxException {
        progress(p);
        // out of any cave they stand in (the cave's own goodbye), then off to a new start
        GateInstance cave = GateService.isDungeon(p.level()) ? GateData.get(p.server).bySlotPos(p.blockPosition()) : null;
        if (cave != null) GateService.leave(p, cave);
        p.getPersistentData().remove(GateService.IN_GATE_TAG);
        p.getInventory().clearContent();
        p.inventoryMenu.getCraftSlots().clearContent();
        p.containerMenu.setCarried(ItemStack.EMPTY);
        p.getEnderChestInventory().clearContent();
        p.removeAllEffects();
        p.clearFire();
        p.setExperienceLevels(0);
        p.setExperiencePoints(0);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.setAirSupply(p.getMaxAirSupply());
        p.setDeltaMovement(Vec3.ZERO);
        ArmorSets.refresh(p);
        FirstSpawnHandler.startOver(p);
        restart(p);
    }

    /** Takes the System's side of the player back to how a new player starts. */
    private static void progress(ServerPlayer p) throws CommandSyntaxException {
        if (EvaluationManager.isBeingEvaluated(p)) throw Solo.error("evaluating", p.getDisplayName());
        // first out of whatever is running for them, so nothing waits for them or sends them back later
        TowerRun.cancel(p);
        PenaltyZone.cancel(p);
        JobTrial.cancel(p);
        RedGates.leaveNow(p);
        ShadowService.reset(p);
        AwakeningService.reset(p);
        Exhaustion.recover(p, 0);
        Situations.clear(p);
        // every record the mod keeps on the player back to its defaults (the System's own keeps "first join handled")
        List<AttachmentType<?>> ours = new ArrayList<>();
        for (var e : NeoForgeRegistries.ATTACHMENT_TYPES.entrySet()) {
            if (e.getKey().location().getNamespace().equals(BeyondSolo.MOD_ID)) ours.add(e.getValue());
        }
        for (AttachmentType<?> type : ours) {
            if (type != ModAttachments.SYSTEM.get() && p.hasData(type)) p.removeData(type);
        }
        SystemAPI.reset(p);
        forgetTags(p);
        // everything that hangs off those records, and the client's picture of them
        SkillAPI.reset(p);
        QuestEngine.sync(p);
        JobAPI.sync(p);
        Economy.sync(p);
        Runes.sync(p);
        DungeonKeys.sync(p);
        TowerAPI.sync(p);
        ShadowService.sync(p);
        Reevaluation.sync(p);
        WeaknessLog.sync(p);
        for (MobEffectInstance effect : List.copyOf(p.getActiveEffects())) {
            if (effect.getEffect().unwrapKey().map(k -> k.location().getNamespace().equals(BeyondSolo.MOD_ID)).orElse(false)) {
                p.removeEffect(effect.getEffect());
            }
        }
        SetPerks.reset(p);
        ArmorSets.refresh(p);
        // what the System handed them as its own: the license and the job change's key
        p.getInventory().clearOrCountMatchingItems(s -> s.is(EconomyModule.HUNTER_LICENSE.get()) || s.is(JobModule.ASCENSION_KEY.get()),
                -1, p.inventoryMenu.getCraftSlots());
    }

    /** The mod's marks on the player (what it told them once, where it last saw them), except the cave they stand in. */
    private static void forgetTags(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        for (String key : List.copyOf(root.getAllKeys())) {
            if (key.startsWith(BeyondSolo.MOD_ID) && !key.equals(GateService.IN_GATE_TAG)) root.remove(key);
        }
        if (root.contains(Player.PERSISTED_NBT_TAG)) {
            CompoundTag kept = root.getCompound(Player.PERSISTED_NBT_TAG);
            for (String key : List.copyOf(kept.getAllKeys())) if (key.startsWith(BeyondSolo.MOD_ID)) kept.remove(key);
        }
    }

    /**
     * The start again: with the awakening trial on, the player is ordinary until the gates change them (the hint they
     * got on their first login comes again); with it off, the System binds at once and says its first words.
     */
    private static void restart(ServerPlayer p) {
        AwakeningData d = AwakeningService.data(p);
        if (!Config.AWAKENING_TRIAL.get()) {
            d.setStage(AwakeningData.Stage.DONE);
            SystemAPI.grantSystem(p);
            FirstSpawnHandler.systemIntro(p, 40);
            return;
        }
        d.setHinted(true);
        ServerScheduler.schedule(60, () -> {
            if (!p.hasDisconnected() && !SystemAPI.hasSystem(p)) {
                p.sendSystemMessage(Component.translatable("message.beyondsolo.presystem.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        });
    }

    private SoloWipe() {}
}
