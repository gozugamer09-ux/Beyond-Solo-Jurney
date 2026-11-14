package com.beyondsolo.skill;

import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A skill tome. A named tome teaches one skill (or raises it a level if known); an unread tome rolls a skill the
 * reader's class can learn and doesn't know yet, better grades more likely from higher gates.
 * Advanced (job change) skills never come from tomes.
 */
public class SkillTomeItem extends Item {
    public SkillTomeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Skill skill) {
        ItemStack stack = new ItemStack(ModItems.SKILL_TOME.get());
        stack.set(ModComponents.SKILL.get(), skill.path());
        return stack;
    }

    /** An unread tome. */
    public static ItemStack unread() {
        return new ItemStack(ModItems.SKILL_TOME.get());
    }

    public static @Nullable Skill skillOf(ItemStack stack) {
        String path = stack.get(ModComponents.SKILL.get());
        return path == null ? null : Skills.get(path);
    }

    @Override
    public Component getName(ItemStack stack) {
        Skill s = skillOf(stack);
        return s == null ? Component.translatable("item.beyondsolo.skill_tome.unread")
                : Component.translatable("item.beyondsolo.skill_tome.named", s.displayName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return skillOf(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Skill s = skillOf(stack);
        if (s == null) {
            tooltip.add(Component.translatable("item.beyondsolo.skill_tome.unread.tip").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("skills.beyondsolo.grade", s.grade().name()).withStyle(ChatFormatting.GOLD));
            tooltip.add(s.owner() == null ? Component.translatable("skills.beyondsolo.common").withStyle(ChatFormatting.AQUA)
                    : s.owner().displayName().copy().withStyle(ChatFormatting.AQUA));
            tooltip.add(s.flavour().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (read(sp, stack)) {
            stack.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.1f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    /** Reads a tome. Returns true if it taught or raised something (and should be used up). */
    public static boolean read(ServerPlayer player, ItemStack stack) {
        SystemData sys = SystemAPI.get(player);
        if (!sys.isAwakened()) {
            player.displayClientMessage(Component.translatable("skills.beyondsolo.tome.unreadable"), true);
            return false;
        }
        Skill s = skillOf(stack);
        if (s == null) s = roll(player, player.getRandom());
        if (s == null) {
            player.displayClientMessage(Component.translatable("skills.beyondsolo.tome.nothing"), true);
            return false;
        }
        if (s.advanced() || !s.fits(sys.playerClass())) {
            player.displayClientMessage(Component.translatable("skills.beyondsolo.tome.wrong_class"), true);
            return false;
        }
        boolean ok;
        if (SkillAPI.knows(player, s)) {
            ok = SkillAPI.addLevels(player, s, 1) > 0;
            if (!ok) player.displayClientMessage(Component.translatable("skills.beyondsolo.tome.mastered"), true);
        } else {
            ok = SkillAPI.learn(player, s, 1, SkillEvent.Source.BOOK);
        }
        if (ok) SkillFx.play(player, SkillFxPayload.Kind.HEAL, s.color(), player.position(), 1.2f, 0);
        return ok;
    }

    /** What an unread tome teaches this player: an unknown fitting skill (better grades rarer), else +1 on a known one. */
    static @Nullable Skill roll(ServerPlayer player, RandomSource r) {
        SystemData sys = SystemAPI.get(player);
        PlayerClass c = sys.playerClass();
        List<Skill> unknown = new ArrayList<>();
        List<Skill> raisable = new ArrayList<>();
        for (Skill s : Skills.forClass(c)) {
            if (s.advanced()) continue;
            if (!SkillAPI.knows(player, s)) unknown.add(s);
            else if (SkillAPI.level(player, s) < Skill.MAX_LEVEL) raisable.add(s);
        }
        List<Skill> pool = unknown.isEmpty() ? raisable : unknown;
        if (pool.isEmpty()) return null;
        int total = 0;
        for (Skill s : pool) total += weight(s.grade(), sys.rank());
        int pick = r.nextInt(Math.max(1, total));
        for (Skill s : pool) {
            pick -= weight(s.grade(), sys.rank());
            if (pick < 0) return s;
        }
        return pool.get(0);
    }

    private static int weight(PlayerRank grade, PlayerRank rank) {
        int gap = grade.tier() - rank.tier();
        return gap <= 0 ? 6 : Math.max(1, 6 - gap * 2);
    }
}
