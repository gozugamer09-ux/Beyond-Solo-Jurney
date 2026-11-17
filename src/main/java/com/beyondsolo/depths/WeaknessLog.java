package com.beyondsolo.depths;

import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.network.WeaknessPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The weaknesses a player has found: the first time one of their skills (or an infused weapon) lands on what a kind of
 * creature is weak to, the System notes it ("Weakness found: Gloomfang, Light") and from then on shows it over every
 * creature of that kind they aim at. Keyed by {@link ElementalProfile#key()}, so a frostbound hound and a plain one
 * are different finds. Kept on the player ({@link DepthsModule#WEAKNESSES}) and sent to their client.
 */
public final class WeaknessLog implements INBTSerializable<CompoundTag> {
    private final Set<String> found = new LinkedHashSet<>();

    public static String entry(String profileKey, SkillElement element) {
        return profileKey + "|" + element.name().toLowerCase(Locale.ROOT);
    }

    public boolean knows(String profileKey, SkillElement element) {
        return found.contains(entry(profileKey, element));
    }

    public Set<String> entries() {
        return Collections.unmodifiableSet(found);
    }

    public int size() {
        return found.size();
    }

    boolean add(String entry) {
        return found.add(entry);
    }

    public void clear() {
        found.clear();
    }

    public static WeaknessLog of(ServerPlayer player) {
        return player.getData(DepthsModule.WEAKNESSES);
    }

    /** A player's element hit landed on a weakness: noted, and the first time for that kind of creature, announced. */
    public static void discover(ServerPlayer player, LivingEntity target, SkillElement element) {
        ElementalProfile profile = ElementalProfile.of(target);
        if (!profile.rating(element).weak()) return;
        WeaknessLog log = of(player);
        String entry = entry(profile.key(), element);
        if (!log.add(entry)) return;
        PacketDistributor.sendToPlayer(player, new WeaknessPayload(false, List.of(entry)));
        if (!SystemAPI.hasSystem(player)) return;
        Component name = target.getType().getDescription();
        String body = profile.rating(element) == ElementalProfile.Rating.WEAK_SEVERE
                ? "depths.beyondsolo.weakness.found.body_severe" : "depths.beyondsolo.weakness.found.body";
        Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("depths.beyondsolo.weakness.found.title"),
                Component.translatable(body, name, element.displayName())));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.6f);
    }

    /** Sends a player everything they have found (on login). */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WeaknessPayload(true, new ArrayList<>(of(player).found)));
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (String s : found) list.add(StringTag.valueOf(s));
        t.put("found", list);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        found.clear();
        ListTag list = t.getList("found", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) found.add(list.getString(i));
    }
}
