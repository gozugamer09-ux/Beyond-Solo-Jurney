package com.beyondsolo.light;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A hunter's road in the light: whether they answered the Envoy of Dawn instead of the Arbiter (and woke sworn to the
 * light), their call to the Investiture and their tries at its vigil, and, once invested, the mantle they took up and
 * what they have done as a ruler of light. Kept through death. Change it through {@link LightAPI}.
 */
public final class LightData implements INBTSerializable<CompoundTag> {
    /** They woke sworn to the light (the covenant in the hidden chapel, or a command). */
    private boolean sworn;
    private long swornAt;
    /** The Envoy was turned away at least once before they woke (the covenant refused, then the Arbiter faced). */
    private boolean refused;
    /** The mantle taken up at the Investiture, or null before it. */
    private @Nullable Mantle mantle;
    private long investedAt;
    /** The light has called them to the Investiture (the quest was given). */
    private boolean called;
    /** No new vigil before this game time (after a failed one). */
    private long retryAt;
    private int attempts;
    /** Times they used their authority. */
    private int authorities;
    /** Monarchs from beyond the gates that fell while this one fought them, sworn. */
    private int monarchsSlain;
    /** Foes their Judgement has struck. */
    private int judged;

    public boolean sworn() { return sworn; }
    public long swornAt() { return swornAt; }
    public boolean refused() { return refused; }
    public boolean invested() { return sworn && mantle != null; }
    public @Nullable Mantle mantle() { return mantle; }
    public long investedAt() { return investedAt; }
    public boolean called() { return called; }
    public long retryAt() { return retryAt; }
    public int attempts() { return attempts; }
    public int authorities() { return authorities; }
    public int monarchsSlain() { return monarchsSlain; }
    public int judged() { return judged; }

    void swear(boolean sworn, long at) {
        this.sworn = sworn;
        this.swornAt = sworn ? at : 0;
        if (!sworn) {
            mantle = null;
            investedAt = 0;
            called = false;
        }
    }

    void setRefused(boolean refused) { this.refused = refused; }

    void invest(@Nullable Mantle mantle, long at) {
        this.mantle = mantle;
        this.investedAt = mantle == null ? 0 : at;
    }

    void setCalled(boolean called) { this.called = called; }
    void setRetryAt(long retryAt) { this.retryAt = retryAt; }
    void addAttempt() { attempts++; }
    void addAuthority() { authorities++; }
    void addMonarchSlain() { monarchsSlain++; }
    void addJudged(int n) { judged += n; }

    void reset() {
        sworn = false;
        swornAt = 0;
        refused = false;
        mantle = null;
        investedAt = 0;
        called = false;
        retryAt = 0;
        attempts = 0;
        authorities = 0;
        monarchsSlain = 0;
        judged = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("sworn", sworn);
        tag.putLong("swornAt", swornAt);
        tag.putBoolean("refused", refused);
        if (mantle != null) tag.putString("mantle", mantle.id());
        tag.putLong("investedAt", investedAt);
        tag.putBoolean("called", called);
        tag.putLong("retryAt", retryAt);
        tag.putInt("attempts", attempts);
        tag.putInt("authorities", authorities);
        tag.putInt("monarchsSlain", monarchsSlain);
        tag.putInt("judged", judged);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        sworn = tag.getBoolean("sworn");
        swornAt = tag.getLong("swornAt");
        refused = tag.getBoolean("refused");
        mantle = tag.contains("mantle") ? Mantle.byId(tag.getString("mantle")) : null;
        investedAt = tag.getLong("investedAt");
        called = tag.getBoolean("called");
        retryAt = tag.getLong("retryAt");
        attempts = tag.getInt("attempts");
        authorities = tag.getInt("authorities");
        monarchsSlain = tag.getInt("monarchsSlain");
        judged = tag.getInt("judged");
    }
}
