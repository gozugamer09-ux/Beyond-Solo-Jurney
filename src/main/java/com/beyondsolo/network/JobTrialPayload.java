package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: what the job change trial's banner shows.
 *
 * @param phase    one of the constants ({@link #NONE} hides the banner)
 * @param title    the phase's name
 * @param subtitle what to do now
 * @param progress foes defeated so far (or the boss's health in percent)
 * @param goal     foes to defeat (or 100)
 * @param ticks    ticks the phase has run, for animations
 */
public record JobTrialPayload(int phase, Component title, Component subtitle, int progress, int goal, int ticks) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int INTRO = 1;
    public static final int WAVE = 2;
    public static final int BOSS = 3;
    public static final int VERDICT = 4;
    public static final int FAILED = 5;

    public static final Type<JobTrialPayload> TYPE = new Type<>(BeyondSolo.id("job_trial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobTrialPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobTrialPayload::phase,
            ComponentSerialization.TRUSTED_STREAM_CODEC, JobTrialPayload::title,
            ComponentSerialization.TRUSTED_STREAM_CODEC, JobTrialPayload::subtitle,
            ByteBufCodecs.VAR_INT, JobTrialPayload::progress,
            ByteBufCodecs.VAR_INT, JobTrialPayload::goal,
            ByteBufCodecs.VAR_INT, JobTrialPayload::ticks,
            JobTrialPayload::new);

    public static JobTrialPayload none() {
        return new JobTrialPayload(NONE, Component.empty(), Component.empty(), 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
