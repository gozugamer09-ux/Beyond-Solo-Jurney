package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a skill effect to draw in the world.
 *
 * @param entityId the caster (or, for MARK, the marked creature)
 * @param kind     which effect
 * @param color    RGB tint (usually the skill's colour)
 * @param x        a second point the effect needs (bolt end, dash start, meteor landing...)
 * @param size     radius or length in blocks
 * @param duration how long a lasting effect stays (ticks); 0 for one-off bursts. A few one-off kinds use it to pick a look
 */
public record SkillFxPayload(int entityId, Kind kind, int color, float x, float y, float z, float size, int duration)
        implements CustomPacketPayload {
    public enum Kind {
        /** The magic circle that flares under every cast. */
        CAST,
        /** A crescent of light swept in front of the caster. */
        SLASH,
        /** Afterimages from (x,y,z) to the caster. */
        DASH,
        /** A wave rolling out along the ground. Duration 1 is a quiet ripple without motes, for waves that keep coming. */
        PULSE,
        /** Shock rings at chest height and a flash (war cry, provoke). */
        ROAR,
        /** A ground slam: cracked ring, debris, dust. */
        SLAM,
        /** Vanished at (x,y,z), appeared at the caster. Duration picks the look: 0 smoke and shadow, 1 motes of light. */
        BLINK,
        /** A coloured aura around the caster for a while (buffs). */
        AURA,
        /** A bolt of light from the caster's hand to (x,y,z). */
        BOLT,
        /** A ring rushing out from the caster. Duration picks what it throws: 0 flames, 1 sparks, 2 spray, 3 motes of light, 4 soul fire. */
        NOVA,
        /** A burning star falling onto (x,y,z). */
        METEOR,
        /** A shield bubble around the caster for a while. */
        DOME,
        /** A column of light and rising sparks (heals, cures). */
        HEAL,
        /** A lasting circle on the ground at (x,y,z) (sanctuary, arrow rain). */
        ZONE,
        /** A sigil hovering over the marked creature. */
        MARK,
        /** A streak of light shot from the caster to (x,y,z). */
        SHOT,
        /** Smoke and shadow swallowing the caster. */
        VEIL,
        /**
         * Something rolling along the ground from the caster to (x,y,z), {@code size} wide. Duration picks the look:
         * 0 fire, 1 rock spikes, 2 a charge's wake, 3 a crescent of wind flying at chest height.
         */
        WAVE,
        /** Blades of light circling the caster for {@code duration} ticks. */
        SPIN,
        /** Streaks thrown in a fan from the caster towards (x,y,z). */
        FAN,
        /** A burst at (x,y,z). Duration picks the look: 0 water, 1 an impact. */
        SPLASH,
        /** A whirlwind standing at (x,y,z) for {@code duration} ticks. */
        VORTEX,
        /** A column from the sky onto (x,y,z); duration is the warning before it lands (0 = a lightning strike at once). */
        PILLAR,
        /** A ring of petals thrown out from the caster. */
        BLOSSOM,
        /** Thorns and chains of shadow holding the ground at (x,y,z) for {@code duration} ticks. */
        BIND,
        /** Streaks of light raining onto the circle at (x,y,z) for {@code duration} ticks. */
        RAIN,
        /** The element a creature is carrying: a mote circling it for {@code duration} ticks (entity = the creature). */
        ELEMENT,
        /** An element reaction at (x,y,z); duration is the {@code SkillReaction} ordinal (entity = the creature). */
        REACTION,
        /** A giant blade of light falling onto (x,y,z); size = blade length, duration = ticks until it lands. */
        CLEAVE,
        /** A war banner of light standing at (x,y,z) for {@code duration} ticks; size = the radius it commands. */
        BANNER,
        /** Spectral blades circling the entity at radius {@code size} for {@code duration} ticks. */
        BLADES,
        /** Glowing fissures tearing outwards over the ground from (x,y,z) to radius {@code size}. */
        CRACK,
        /** A fountain bursting up from (x,y,z) to height {@code size}. Duration picks what flies up: 0 embers, 1 the ground itself, 2 motes of light, 3 water, 4 souls. */
        GEYSER,
        /** Shaking ground: rings rolling outwards from (x,y,z) to radius {@code size} for {@code duration} ticks. */
        QUAKE,
        /** A steady beam from the entity to (x,y,z) for {@code duration} ticks; size = its width. */
        BEAM,
        /** A sphere of light hanging at (x,y,z) for {@code duration} ticks; size = its radius. */
        ORB,
        /** A thunderbolt from the sky onto (x,y,z); size = the radius it scorches. */
        THUNDER,
        /** A shield of light held in front of the entity for {@code duration} ticks; size = its radius. */
        SHIELD,
        /** A chain of links from the entity to (x,y,z) for {@code duration} ticks; size = the size of a link. */
        CHAIN,
        /** A hammer of light thrown end over end from the entity to (x,y,z) in {@code duration} ticks; size 1 is hand-sized, big ones are lobbed high. */
        HAMMER,
        /** A thrown shield: a spinning disc from the entity to (x,y,z) in {@code duration} ticks; size = its scale. */
        DISCUS,
        /** A ring of light over the entity's head for {@code duration} ticks; size = its scale. */
        HALO,
        /** Wings of light on the entity's back for {@code duration} ticks; size = their scale. */
        WINGS,
        /** A bubble around the entity for {@code duration} ticks; size = its radius. */
        BUBBLE,
        /** Drops flowing from (x,y,z) into the entity for {@code duration} ticks; size = the size of a drop. */
        STREAM,
        /**
         * Small lights circling the entity for {@code duration} ticks; size = how many. A new one of the same colour
         * replaces the old one, and size 0 only takes it away.
         */
        ORBIT,
        /** A low bank of fog lying around (x,y,z) for {@code duration} ticks; size = its radius. */
        MIST,
        /**
         * An arrow of light shot from the entity to (x,y,z); size = its scale. With a {@code duration} it stays
         * stuck where it landed for that many ticks.
         */
        ARROW,
        /** An arrow of light falling out of the sky onto (x,y,z), landing after {@code duration} ticks; size = its scale. */
        SKYSHOT,
        /** An arrow that curves from over the entity's shoulder onto (x,y,z) in {@code duration} ticks; size = its scale. */
        SEEKER,
        /**
         * A thin sight line from the entity's eye for {@code duration} ticks. Size 0 follows their gaze, size 1
         * holds on (x,y,z), and a negative size only takes the gaze line away.
         */
        SIGHT,
        /** An arrow from the entity to (x,y,z) that drags the wind with it: rings opening down the line; size = their radius. */
        GALE,
        /** Wind spiralling up around the entity for {@code duration} ticks; size = its radius. */
        UPDRAFT,
        /**
         * Arrows of light hovering in a fan behind the entity's shoulders for {@code duration} ticks; size = how
         * many. A new one replaces the old one, and size 0 only takes it away.
         */
        QUIVER,
        /** A wall of rising wind across the caster's facing, centred on (x,y,z), for {@code duration} ticks; size = its width. */
        WINDWALL,
        /**
         * A beast of starlight. Duration is {@code ticks * 10 + species} (see {@link #BEAST_HAWK} and the rest): a
         * hawk stoops from behind the caster onto (x,y,z) in that many ticks, a wolf or a boar runs the way the
         * caster faces to end at (x,y,z), a bear rears at (x,y,z) and comes down on it, a howling wolf sits by the
         * caster that long. Size = its scale.
         */
        BEAST,
        /** Three claw marks torn through the air at (x,y,z); size = their length. */
        CLAW,
        /** Small lights swarming around the entity for {@code duration} ticks; size = how far out they fly. */
        SWARM,
        /**
         * Iron jaws on the ground at (x,y,z); size = their radius. With a {@code duration} they lie open that long.
         * Duration 0 snaps them shut there and takes the open ones at that spot away; size 0 only takes them away.
         */
        TRAP,
        /**
         * A wire between two stakes across the caster's facing, centred on (x,y,z), for {@code duration} ticks;
         * size = its length. Size 0 takes the wire at that spot away.
         */
        WIRE,
        /** A net thrown from the entity onto (x,y,z), lying there for {@code duration} ticks; size = its radius. */
        NET,
        /** A sprung pole standing at (x,y,z) that holds the entity up by a rope for {@code duration} ticks. */
        ROPE,
        /** A storm cloud hanging over (x,y,z) for {@code duration} ticks; size = its radius. */
        CLOUD,
        /** A ball of lightning drifting from the entity to (x,y,z) in {@code duration} ticks; size = its radius. */
        BALL,
        /** Arcs of electricity crawling over the entity, or around (x,y,z) without one, for {@code duration} ticks; size = how far they reach. */
        STATIC
    }

    /** The species a {@link Kind#BEAST} can be. */
    public static final int BEAST_HAWK = 0, BEAST_WOLF = 1, BEAST_BOAR = 2, BEAST_BEAR = 3, BEAST_HOWL = 4;

    public static final Type<SkillFxPayload> TYPE = new Type<>(BeyondSolo.id("skill_fx"));
    public static final StreamCodec<ByteBuf, SkillFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeVarInt(p.entityId);
                f.writeVarInt(p.kind.ordinal());
                f.writeInt(p.color);
                f.writeFloat(p.x);
                f.writeFloat(p.y);
                f.writeFloat(p.z);
                f.writeFloat(p.size);
                f.writeVarInt(p.duration);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                int id = f.readVarInt();
                Kind kind = Kind.values()[Math.floorMod(f.readVarInt(), Kind.values().length)];
                return new SkillFxPayload(id, kind, f.readInt(), f.readFloat(), f.readFloat(), f.readFloat(), f.readFloat(), f.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
