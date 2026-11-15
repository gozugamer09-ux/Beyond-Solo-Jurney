package com.beyondsolo.economy;

import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a hunter license shows, stamped on the card itself (item component {@code beyondsolo:license}), so anyone
 * holding the card sees the same thing. The owner's card is brought up to date while it is in their inventory
 * ({@link HunterLicenseItem#inventoryTick}).
 *
 * @param rank     {@link PlayerRank} ordinal
 * @param cls      {@link PlayerClass} ordinal, -1 before the evaluation
 * @param subclass {@link Subclass#key()} or empty before the job change
 * @param best     highest gate rank cleared (ordinal), -1 for none
 * @param issued   in-game day the license was first issued
 */
public record LicenseInfo(UUID owner, String name, int rank, int cls, String subclass, int level, int clears, int best,
                          String number, long issued) {
    public static final Codec<LicenseInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(LicenseInfo::owner),
            Codec.STRING.fieldOf("name").forGetter(LicenseInfo::name),
            Codec.INT.fieldOf("rank").forGetter(LicenseInfo::rank),
            Codec.INT.fieldOf("cls").forGetter(LicenseInfo::cls),
            Codec.STRING.fieldOf("subclass").forGetter(LicenseInfo::subclass),
            Codec.INT.fieldOf("level").forGetter(LicenseInfo::level),
            Codec.INT.fieldOf("clears").forGetter(LicenseInfo::clears),
            Codec.INT.fieldOf("best").forGetter(LicenseInfo::best),
            Codec.STRING.fieldOf("number").forGetter(LicenseInfo::number),
            Codec.LONG.fieldOf("issued").forGetter(LicenseInfo::issued)
    ).apply(i, LicenseInfo::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LicenseInfo> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PlayerRank playerRank() {
        return PlayerRank.values()[Math.max(0, Math.min(rank, PlayerRank.values().length - 1))];
    }

    public @Nullable PlayerClass playerClass() {
        return cls < 0 || cls >= PlayerClass.values().length ? null : PlayerClass.values()[cls];
    }

    public @Nullable Subclass sub() {
        return subclass.isEmpty() ? null : Subclass.byName(subclass);
    }

    public @Nullable PlayerRank bestRank() {
        return best < 0 || best >= PlayerRank.values().length ? null : PlayerRank.values()[best];
    }

    /** The card as it should read for this player right now. */
    public static LicenseInfo of(ServerPlayer player) {
        SystemData d = SystemAPI.get(player);
        HunterData h = Economy.data(player);
        Subclass sub = JobAPI.subclass(player);
        PlayerRank best = h.bestClear();
        return new LicenseInfo(player.getUUID(), player.getGameProfile().getName(), d.rank().ordinal(),
                d.isAwakened() ? d.playerClass().ordinal() : -1, sub == null ? "" : sub.key(), d.level(),
                h.totalClears(), best == null ? -1 : best.ordinal(), number(player.getUUID()),
                Math.max(0, h.licenseIssuedDay()));
    }

    /** A stable license number made from the player's id, e.g. "0472-1938-55". */
    public static String number(UUID id) {
        long h = id.getMostSignificantBits() ^ (id.getLeastSignificantBits() * 31);
        h = Math.abs(h % 10_000_000_000L);
        String s = String.format("%010d", h);
        return s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8);
    }
}
