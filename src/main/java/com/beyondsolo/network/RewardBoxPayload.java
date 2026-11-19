package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.reward.BoxCurse;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.BoxReward;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server -> client: a random box opened ({@link #OPENED}: its kind and rank, what it gave and, for a cursed box, the
 * curse and a number for it) or a blessed box's three offers to pick from ({@link #OFFER}: the hand it is in and the
 * seed they were rolled from, which the pick sends back). Built by {@code reward.RewardBoxes}, read by
 * {@code client.reward.RewardClient}.
 */
public record RewardBoxPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final int OPENED = 0, OFFER = 1;

    public static final Type<RewardBoxPayload> TYPE = new Type<>(BeyondSolo.id("reward_box"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewardBoxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, RewardBoxPayload::tag,
            RewardBoxPayload::new);

    public static RewardBoxPayload opened(BoxKind kind, PlayerRank rank, BoxReward reward, @Nullable BoxCurse curse, long detail,
                                          HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("mode", OPENED);
        t.putInt("kind", kind.ordinal());
        t.putInt("rank", rank.tier());
        t.put("reward", reward.save(provider));
        t.putInt("curse", curse == null ? -1 : curse.ordinal());
        t.putLong("detail", detail);
        return new RewardBoxPayload(t);
    }

    public static RewardBoxPayload offer(int hand, long seed, PlayerRank rank, List<BoxReward> offers, HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("mode", OFFER);
        t.putInt("kind", BoxKind.BLESSED.ordinal());
        t.putInt("rank", rank.tier());
        t.putInt("hand", hand);
        t.putLong("seed", seed);
        ListTag list = new ListTag();
        for (BoxReward r : offers) list.add(r.save(provider));
        t.put("offers", list);
        return new RewardBoxPayload(t);
    }

    public int mode() {
        return tag.getInt("mode");
    }

    public BoxKind kind() {
        return BoxKind.byIndex(tag.getInt("kind"));
    }

    public PlayerRank rank() {
        return PlayerRank.values()[Math.max(0, Math.min(PlayerRank.values().length - 1, tag.getInt("rank")))];
    }

    @Nullable
    public BoxCurse curse() {
        int c = tag.contains("curse") ? tag.getInt("curse") : -1;
        return c < 0 ? null : BoxCurse.byIndex(c);
    }

    public long detail() {
        return tag.getLong("detail");
    }

    public BoxReward reward(HolderLookup.Provider provider) {
        return BoxReward.load(tag.getCompound("reward"), provider);
    }

    public int hand() {
        return tag.getInt("hand");
    }

    public long seed() {
        return tag.getLong("seed");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
