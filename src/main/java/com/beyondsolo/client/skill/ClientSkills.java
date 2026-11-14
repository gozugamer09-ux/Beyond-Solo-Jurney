package com.beyondsolo.client.skill;

import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.skill.Skills;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The local player's skills as last sent by the server. */
public final class ClientSkills {
    private static SkillData data = new SkillData();
    private static CompoundTag lastTag = new CompoundTag();

    public static SkillData data() {
        return data;
    }

    public static void update(CompoundTag tag) {
        lastTag = tag;
        SkillData fresh = new SkillData();
        fresh.deserializeNBT(null, tag);
        data = fresh;
        // keep the player's attachment in step, so SkillAPI reads work on the client too
        var player = Minecraft.getInstance().player;
        if (player != null) player.getData(ModAttachments.SKILLS).deserializeNBT(player.registryAccess(), lastTag);
    }

    public static void clear() {
        data = new SkillData();
        lastTag = new CompoundTag();
    }

    public static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    public static List<Skill> known() {
        return Skills.known(data);
    }

    public static @Nullable Skill slot(int i) {
        String path = data.slot(i);
        return path.isEmpty() ? null : Skills.get(path);
    }

    /** 0 = ready .. 1 = just started. */
    public static float cooldownFraction(Skill s, float partial) {
        int len = data.cooldownLength(s.path());
        if (len <= 0) return 0;
        float left = data.readyAt(s.path()) - (gameTime() + partial);
        return Math.max(0, Math.min(1, left / len));
    }

    public static float cooldownSeconds(Skill s, float partial) {
        return Math.max(0, (data.readyAt(s.path()) - (gameTime() + partial)) / 20f);
    }

    public static boolean hasBuff(String key) {
        return data.hasBuff(key, gameTime());
    }

    private ClientSkills() {}
}
