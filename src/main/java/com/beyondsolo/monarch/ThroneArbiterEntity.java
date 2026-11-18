package com.beyondsolo.monarch;

import com.beyondsolo.awakening.ArbiterEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Arbiter Unchained: the same hollow giant that judged the hunter at their awakening, waiting at the foot of the
 * empty throne. This time its chains are already broken and its halo burns violet: it is the last thing between the
 * hunter and the crown. It fights as it did at the awakening (cleave, sunder, judgment), on the numbers of a boss made
 * for the hunter's level and far above it, and falling to it only throws the hunter out of the hall
 * ({@link ThroneTrial}).
 */
public class ThroneArbiterEntity extends ArbiterEntity {
    public ThroneArbiterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /** The throne trial watches for its fall; nothing of the awakening happens. */
    @Override
    protected void slain(ServerLevel server, DamageSource source) {
    }

    @Override
    public int barColor() {
        return isEnraged() ? 0xFF2E6A : 0xB57BFF;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.beyondsolo.throne_arbiter");
    }
}
