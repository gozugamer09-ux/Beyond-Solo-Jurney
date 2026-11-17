package com.beyondsolo.client.rune;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.job.JobClient;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.network.RuneSyncPayload;
import com.beyondsolo.rune.RuneData;
import com.beyondsolo.rune.RuneModule;
import com.beyondsolo.rune.Runes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** The local player's rune data as last sent by the server, and where rune messages from the server go. */
public final class RuneClient {
    private static RuneData data = new RuneData();
    private static CompoundTag lastTag = new CompoundTag();
    private static LocalPlayer appliedTo;
    /** When the local player's last rune shattered into them (the Skill Acquired moment waits for the light). */
    private static double shatterAt = -100;
    /** How long the light takes to pour in before the System speaks. */
    private static final float POUR = 1.1f;

    /** Hooks the shared rune rules up to what the client knows. Called once at client setup. */
    static void setup() {
        Runes.clientCheck = (player, stack) -> Runes.refusal(ClientSystemData.get(), JobClient.subclass(), ClientSkills.data(), stack);
        Runes.clientEngrave = (hand, engraving) -> Minecraft.getInstance().setScreen(new RuneEngraveScreen(hand, engraving));
    }

    public static RuneData data() {
        // the local player entity is recreated on respawn and dimension change: copy the data across
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player != appliedTo) {
            player.getData(RuneModule.RUNES).deserializeNBT(player.registryAccess(), lastTag);
            appliedTo = player;
        }
        return data;
    }

    public static void onSync(RuneSyncPayload p) {
        lastTag = p.data();
        RuneData fresh = new RuneData();
        fresh.deserializeNBT(null, lastTag);
        data = fresh;
        appliedTo = null;
        data();
    }

    public static void onFx(RuneFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        boolean self = e != null && e == mc.player;
        switch (p.kind()) {
            case FIZZLE -> {
                // the server said no: stop the channel the client started
                if (mc.player != null && mc.player.isUsingItem() && Runes.isRune(mc.player.getUseItem())) mc.player.stopUsingItem();
                RuneOverlay.fizzle(p.color());
            }
            case SHATTER -> {
                if (self) {
                    shatterAt = Anim.now();
                    RuneOverlay.shatter(p);
                }
                RuneAnimator.release(e);
            }
            case ENGRAVED -> {
                if (self) {
                    RuneOverlay.engraved(p);
                    if (mc.screen instanceof RuneEngraveScreen screen) screen.engraved();
                }
            }
            case ENGRAVE_REFUSED -> {
                if (mc.screen instanceof RuneEngraveScreen screen) screen.refused();
            }
            default -> {
            }
        }
        RuneWorldFx.play(p);
    }

    /** True while the light of a rune that just shattered is still pouring into the player. */
    public static boolean absorbing() {
        return Anim.since(shatterAt) < POUR;
    }

    /** Seconds since the local player's last rune shattered. */
    static float sinceShatter() {
        return Anim.since(shatterAt);
    }

    static void clear() {
        data = new RuneData();
        lastTag = new CompoundTag();
        appliedTo = null;
        shatterAt = -100;
    }

    private RuneClient() {}
}
