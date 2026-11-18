package com.beyondsolo.chronicle.title;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalService;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.network.ChronicleSyncPayload;
import com.beyondsolo.network.TitleEarnedPayload;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Titles: earning them, wearing one, and what the one worn does.
 *
 * <ul>
 *   <li><b>Earning.</b> A title with a feat is earned when its number reaches the goal; every hunter with the System is
 *   checked every two seconds (and when they log in), so feats kept by other features need no hooks there. A title a
 *   feature gives itself comes through {@code SystemAPI.setTitle} ({@link SystemEvent.TitleGranted}). Either way it is
 *   kept, the System announces it (the TITLE ACQUIRED moment) and it is put on if the hunter wears none, or wears one
 *   the System picked and the new one is rarer. A title the hunter picked themselves is never swapped for them.</li>
 *   <li><b>Wearing.</b> Only an earned title can be worn. Its perks hold while it is worn: attribute perks as
 *   modifiers on the hunter (put back on login and respawn), the rest read here when XP, gold or damage happens.</li>
 *   <li><b>Counting.</b> The feats the chronicle keeps itself: creatures and bosses slain (by the hunter or their
 *   shadows), by family, the ascended, the giants, the clutch kills, deaths, dungeon breaks, hidden and trap rooms,
 *   daily and surprise quests, element reactions and the longest combo.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class TitleService {
    /** How often every hunter is checked for titles (ticks). */
    public static final int CHECK_EVERY = 40;
    /** A boss slain below this share of the hunter's health is a clutch kill. */
    public static final float CLUTCH_BELOW = 0.15f;
    /** Damage taken is never cut by more than this, whatever the title. */
    public static final float MAX_GUARD = 0.5f;

    /** Hunters whose chronicle changed since it was last sent (sent on their next half second). */
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    public static ChronicleData data(Player p) {
        return ChronicleModule.data(p);
    }

    // ------------------------------------------------------------------ earning

    /**
     * Gives a title (its lang key). False if they had it. Announced, kept, and worn if the hunter wears none (or a
     * System-picked one of a lower tier).
     */
    public static boolean grant(ServerPlayer p, String key) {
        if (key == null || key.isEmpty()) return false;
        ChronicleData c = data(p);
        if (!c.addTitle(key, p.serverLevel().getGameTime())) return false;
        boolean wear = shouldWear(p, c, key);
        if (wear) wear(p, key);
        PacketDistributor.sendToPlayer(p, new TitleEarnedPayload(key, wear));
        sync(p);
        return true;
    }

    private static boolean shouldWear(ServerPlayer p, ChronicleData c, String key) {
        String worn = SystemAPI.get(p).title();
        if (worn.isEmpty() || !c.hasTitle(worn)) return true;
        if (c.chosen()) return false;
        return Titles.tierOf(key).ordinal() > Titles.tierOf(worn).ordinal();
    }

    /** Takes a title away (commands and tests). Taken off too if it was worn. */
    public static boolean revoke(ServerPlayer p, String key) {
        ChronicleData c = data(p);
        if (!c.removeTitle(key)) return false;
        if (SystemAPI.get(p).title().equals(key)) wear(p, "");
        sync(p);
        return true;
    }

    /**
     * Every title whose feat is done and that the hunter does not have yet. Needs the System. How far along a title
     * is never leaves the server: the hunter learns what earns a title only once it is theirs.
     */
    public static int check(ServerPlayer p) {
        if (!SystemAPI.hasSystem(p)) return 0;
        ChronicleData c = data(p);
        int earned = 0;
        for (TitleDef d : Titles.all()) {
            if (d.feat() == null || c.hasTitle(d.key())) continue;
            if (d.feat().value(p, c) >= d.goal() && grant(p, d.key())) earned++;
        }
        return earned;
    }

    // ------------------------------------------------------------------ wearing

    /** Wears an earned title ("" takes it off). {@code byHand}: the hunter picked it on the CHRONICLE page. */
    public static boolean equip(ServerPlayer p, String key, boolean byHand) {
        ChronicleData c = data(p);
        String k = key == null ? "" : key;
        if (!k.isEmpty() && !c.hasTitle(k)) return false;
        if (byHand) c.setChosen(!k.isEmpty());
        if (!k.isEmpty()) c.markRead(k);
        wear(p, k);
        sync(p);
        return true;
    }

    private static void wear(ServerPlayer p, String key) {
        SystemAPI.equipTitle(p, key);
        applyPerks(p);
    }

    /** The perks of the title a player wears (works on both sides: the client has the System data too). */
    public static List<PerkLine> perks(Player p) {
        TitleDef d = Titles.byKey(SystemAPI.get(p).title());
        return d == null ? List.of() : d.perks();
    }

    /** The total of one perk on the worn title. */
    public static float bonus(Player p, TitlePerk perk) {
        float v = 0;
        for (PerkLine l : perks(p)) if (l.perk() == perk) v += l.value();
        return v;
    }

    private static float familyBonus(Player p, @Nullable CreatureFamily family) {
        if (family == null) return 0;
        float v = 0;
        for (PerkLine l : perks(p)) if (l.perk() == TitlePerk.FAMILY_DAMAGE && l.family() == family) v += l.value();
        return v;
    }

    /** Puts the worn title's attribute perks on the hunter (and takes off any left from another title). */
    public static void applyPerks(ServerPlayer p) {
        for (TitlePerk perk : TitlePerk.values()) {
            Holder<Attribute> attribute = perk.attribute();
            if (attribute == null) continue;
            AttributeInstance inst = p.getAttribute(attribute);
            if (inst == null) continue;
            ResourceLocation id = modifierId(perk);
            inst.removeModifier(id);
            float v = bonus(p, perk);
            if (v != 0) inst.addTransientModifier(new AttributeModifier(id, v, perk.operation()));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static ResourceLocation modifierId(TitlePerk perk) {
        return BeyondSolo.id("title_" + perk.id());
    }

    /** Gold from kills, quests, gates and the Citadel, with the worn title's share added ({@code Economy.add}). */
    public static long gold(ServerPlayer p, long amount, Economy.Reason reason) {
        if (amount <= 0) return amount;
        if (reason != Economy.Reason.KILL && reason != Economy.Reason.QUEST && reason != Economy.Reason.GATE && reason != Economy.Reason.TOWER) return amount;
        float bonus = bonus(p, TitlePerk.GOLD);
        return bonus <= 0 ? amount : Math.round(amount * (1.0 + bonus));
    }

    /** How much more damage the hunter's worn title adds against this creature (0.1 = 10%). */
    public static float damageBonus(Player p, LivingEntity victim) {
        float v = 0;
        if (HiddenLevel.has(victim)) {
            v += bonus(p, TitlePerk.CREATURE_DAMAGE);
            if (victim instanceof DungeonBoss || victim instanceof ColossalBoss) v += bonus(p, TitlePerk.BOSS_DAMAGE);
            v += familyBonus(p, CreatureFamily.of(victim));
            float giant = bonus(p, TitlePerk.GIANT_DAMAGE);
            if (giant > 0 && HiddenLevel.threat(victim, p) == HiddenLevel.Threat.FAR_STRONGER) v += giant;
        }
        if (lastStand(p)) v += bonus(p, TitlePerk.LAST_STAND);
        return v;
    }

    /** How much less damage the hunter takes (0.1 = 10%). */
    public static float guard(Player p) {
        float v = bonus(p, TitlePerk.GUARD);
        if (lastStand(p)) v += bonus(p, TitlePerk.LAST_STAND);
        return Math.min(MAX_GUARD, v);
    }

    private static boolean lastStand(Player p) {
        return p.getHealth() < p.getMaxHealth() * TitlePerk.LAST_STAND_BELOW;
    }

    // ------------------------------------------------------------------ the client's copy

    /** The chronicle changed: sent within half a second (many kills in a row are sent once). */
    public static void markDirty(ServerPlayer p) {
        DIRTY.add(p.getUUID());
    }

    public static void sync(ServerPlayer p) {
        DIRTY.remove(p.getUUID());
        if (p instanceof FakePlayer) return;
        ChronicleData c = data(p);
        CompoundTag tag = c.serializeNBT(p.registryAccess());
        tag.putInt("pages", Journal.size());
        PacketDistributor.sendToPlayer(p, new ChronicleSyncPayload(tag));
    }

    /** The hunter looked at a title or a journal page. */
    public static void read(ServerPlayer p, String id) {
        data(p).markRead(id);
    }

    // ------------------------------------------------------------------ events

    /** {@code SystemAPI.setTitle}: a feature gives a title. */
    @SubscribeEvent
    static void granted(SystemEvent.TitleGranted event) {
        grant(event.getPlayer(), event.getTitle());
    }

    @SubscribeEvent
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ChronicleData c = data(p);
        if (!c.migrated()) {
            // the title worn before the chronicle was earned once: keep it, quietly
            String worn = SystemAPI.get(p).title();
            if (!worn.isEmpty() && c.addTitle(worn, p.serverLevel().getGameTime())) c.markRead(worn);
            c.setMigrated();
        }
        applyPerks(p);
        check(p);
        sync(p);
    }

    @SubscribeEvent
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            applyPerks(p);
            sync(p);
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.tickCount % CHECK_EVERY == 7) check(p);
        if (p.tickCount % 10 == 5 && DIRTY.contains(p.getUUID())) sync(p);
    }

    /** The worn title's XP perk. */
    @SubscribeEvent
    static void xp(SystemEvent.XpGain event) {
        float bonus = bonus(event.getPlayer(), TitlePerk.XP);
        if (bonus > 0) event.setAmount(Math.round(event.getAmount() * (1.0 + bonus)));
    }

    /** Feats that show as XP: a dungeon break suppressed, a hidden room found, a trap room beaten. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void xpFeats(SystemEvent.XpGain event) {
        ResourceLocation source = event.getSource();
        if (source == null || !source.getNamespace().equals(BeyondSolo.MOD_ID)) return;
        Feat feat = switch (source.getPath()) {
            case "dungeon_break" -> Feat.BREAKS;
            case "hidden_found" -> Feat.HIDDEN_ROOMS;
            case "trap_cleared" -> Feat.TRAP_ROOMS;
            default -> null;
        };
        if (feat != null) data(event.getPlayer()).add(feat.id(), 1);
    }

    /** Damage the worn title adds (dealt) or takes away (taken). */
    @SubscribeEvent
    static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p && p != victim && !(victim instanceof Player)) {
            float bonus = damageBonus(p, victim);
            if (bonus != 0) event.setAmount(event.getAmount() * (1 + bonus));
        }
        if (victim instanceof ServerPlayer p) {
            float guard = guard(p);
            if (guard > 0) event.setAmount(event.getAmount() * (1 - guard));
        }
    }

    /** Kills and deaths. A death the game refused (cancelled) never gets here. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void died(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer dead) {
            data(dead).add(Feat.DEATHS.id(), 1);
            return;
        }
        ServerPlayer hunter = killer(event.getSource().getEntity());
        if (hunter == null || !HiddenLevel.has(victim)) return;
        ChronicleData c = data(hunter);
        c.add(Feat.KILLS.id(), 1);
        CreatureFamily family = CreatureFamily.of(victim);
        if (family != null) c.add(Feat.of(family).id(), 1);
        if (victim instanceof DungeonBoss || victim instanceof ColossalBoss) {
            c.add(Feat.BOSSES.id(), 1);
            if (victim instanceof GateBossEntity b && b.isAscended()) c.add(Feat.ASCENDED.id(), 1);
            if (hunter.isAlive() && hunter.getHealth() < hunter.getMaxHealth() * CLUTCH_BELOW) c.add(Feat.CLUTCH.id(), 1);
        }
        if (HiddenLevel.threat(victim, hunter) == HiddenLevel.Threat.FAR_STRONGER) c.add(Feat.GIANTS.id(), 1);
        JournalService.slain(hunter, victim);
        markDirty(hunter);
    }

    /** Who a kill counts for: the hunter, or the master of the shadow that made it. */
    public static @Nullable ServerPlayer killer(@Nullable Entity source) {
        if (source instanceof ServerPlayer p) return p;
        if (source instanceof ShadowEntity s) return s.master();
        return null;
    }

    @SubscribeEvent
    static void quest(QuestEvent.Completed event) {
        QuestCategory category = event.getQuest().category();
        if (category == QuestCategory.DAILY) data(event.getPlayer()).add(Feat.DAILIES.id(), 1);
        else if (category == QuestCategory.SURPRISE || category == QuestCategory.EMERGENCY) data(event.getPlayer()).add(Feat.SURPRISES.id(), 1);
    }

    @SubscribeEvent
    static void reaction(SkillEvent.Reaction event) {
        data(event.getPlayer()).add(Feat.REACTIONS.id(), 1);
    }

    @SubscribeEvent
    static void combo(SkillEvent.Combo event) {
        data(event.getPlayer()).max(Feat.COMBO.id(), event.getCount());
    }

    private TitleService() {}
}
