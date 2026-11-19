package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: one beat of a story scene ({@code story.StoryScenes}).
 *
 * <ul>
 *   <li>{@link #LINE}: a line of the scene at the bottom of the screen for {@code ticks}: someone speaking
 *       ({@link #SPEECH}, with {@code speaker}'s name over it in {@code color}), the hunter's own thoughts
 *       ({@link #THOUGHT}) or a quiet description of what happens ({@link #NARRATION}).</li>
 *   <li>{@link #BLINK}: the screen dips to black and back over {@code ticks} (a cut, a moment passing).</li>
 *   <li>{@link #BEGIN} / {@link #END}: a scene starts (the hide key can skip it; {@code style} 1: a held, cinematic
 *       one: the HUD steps aside and bars close in) / is over (lines fade out).</li>
 * </ul>
 */
public record StoryLinePayload(int mode, int style, Component speaker, Component text, int ticks, int color) implements CustomPacketPayload {
    public static final int LINE = 0, BLINK = 1, BEGIN = 2, END = 3;
    public static final int SPEECH = 0, THOUGHT = 1, NARRATION = 2;

    public static final Type<StoryLinePayload> TYPE = new Type<>(BeyondSolo.id("story_line"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoryLinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mode);
                buf.writeVarInt(p.style);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.speaker);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.text);
                buf.writeVarInt(p.ticks);
                buf.writeInt(p.color);
            },
            buf -> new StoryLinePayload(buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readInt()));

    public static StoryLinePayload line(int style, Component speaker, Component text, int ticks, int color) {
        return new StoryLinePayload(LINE, style, speaker, text, ticks, color);
    }

    public static StoryLinePayload blink(int ticks) {
        return new StoryLinePayload(BLINK, 0, Component.empty(), Component.empty(), ticks, 0);
    }

    public static StoryLinePayload begin(boolean cinematic) {
        return new StoryLinePayload(BEGIN, cinematic ? 1 : 0, Component.empty(), Component.empty(), 0, 0);
    }

    public static StoryLinePayload end() {
        return new StoryLinePayload(END, 0, Component.empty(), Component.empty(), 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
