package com.beyondsolo.crafting;

import com.beyondsolo.combat.CombatFx;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Mana stones, mined from the glowing veins in gate caves. Hold use to draw the mana out of one (it refills the
 * System's mana bar), or keep them for crafting. Three sizes: shard, stone, greater stone.
 */
public class ManaStoneItem extends Item {
    /** Colour of the light it gives off when absorbed. */
    public static final int COLOR = 0x5FD0FF;
    private final float mana;

    public ManaStoneItem(float mana, Properties props) {
        super(props);
        this.mana = mana;
    }

    public float mana() {
        return mana;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SystemAPI.hasSystem(player)) return InteractionResultHolder.pass(stack);
        if (SystemAPI.get(player).mana() >= SystemAPI.get(player).maxMana() - 0.5f) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.beyondsolo.mana_stone.full").withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p && SystemAPI.hasSystem(p)) {
            SystemAPI.restoreMana(p, mana);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.5f);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4f, 1.8f);
            CombatFx.play(p, CombatFxPayload.Kind.ABSORB, COLOR);
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.beyondsolo.mana_stone.desc", Math.round(mana)).withStyle(s -> s.withColor(COLOR)));
        lines.add(Component.translatable("craft.beyondsolo.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
