package com.beyondsolo.chronicle.journal;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.ChronicleModule;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.enemy.GateMonsterEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.redgate.RedWorld;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.tower.CitadelLordEntity;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.worldboss.ColossalBoss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Fills the journal in. A creature's or a boss's page when the hunter first lays eyes on one (within
 * {@link #SIGHT} blocks, in the open) and every time they slay one; a boss's forms (the rank it was met at, and
 * ascended) as they are met; a realm's page on setting foot in it; a weapon's when it is first in their pack. Works for
 * hunters without the System too (the pages wait for them), but only hunters with it see the journal.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class JournalService {
    /** How far a creature can be seen from for its page (blocks). */
    public static final double SIGHT = 24;
    private static final int SCAN_EVERY = 20;
    private static final int PACK_EVERY = 40;

    private static Map<Item, String> weapons;

    /** The page of a creature or a boss, or null if it has none. */
    public static @Nullable String pageOf(LivingEntity e) {
        if (e instanceof ShadowEntity) return null;
        if (e instanceof GateMonsterEntity m) return "creature:" + m.kind().id;
        if (e instanceof GateBossEntity b) return "boss:" + b.kind().id;
        if (e instanceof ArbiterEntity) return "boss:" + Journal.ARBITER;
        if (e instanceof CitadelLordEntity) return "boss:" + Journal.CITADEL_LORD;
        if (e instanceof ColossalBoss c) return "boss:" + c.kind().asset();
        return null;
    }

    /** Fills a page in (if the journal has it). True if it is new. */
    public static boolean discover(ServerPlayer p, String id) {
        if (Journal.get(id) == null) return false;
        boolean fresh = ChronicleModule.data(p).see(id, p.serverLevel().getGameTime());
        if (fresh) TitleService.markDirty(p);
        return fresh;
    }

    /** The hunter saw this creature: its page, and a boss's form. */
    public static boolean saw(ServerPlayer p, LivingEntity e) {
        String id = pageOf(e);
        if (id == null) return false;
        boolean changed = discover(p, id);
        changed |= form(p, id, e);
        return changed;
    }

    /** The hunter (or their shadow) slew it: one more on its page. */
    public static void slain(ServerPlayer p, LivingEntity e) {
        String id = pageOf(e);
        if (id == null || Journal.get(id) == null) return;
        ChronicleModule.data(p).kill(id, p.serverLevel().getGameTime());
        form(p, id, e);
        TitleService.markDirty(p);
    }

    private static boolean form(ServerPlayer p, String id, LivingEntity e) {
        if (!(e instanceof GateBossEntity b)) return false;
        ChronicleData c = ChronicleModule.data(p);
        long now = p.serverLevel().getGameTime();
        boolean changed = c.addForm(id, JournalEntry.formBit(b.rank()), now);
        if (b.isAscended()) changed |= c.addForm(id, JournalEntry.ASCENDED_BIT, now);
        if (changed) TitleService.markDirty(p);
        return changed;
    }

    /** The realm (or place) the hunter stands in, as a page id, or null. */
    public static @Nullable String realmAt(ServerPlayer p) {
        Level level = p.level();
        if (GateService.isDungeon(level)) {
            GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
            return g == null ? null : "realm:" + g.realm().id();
        }
        ResourceKey<Level> dim = level.dimension();
        if (dim.equals(RedWorld.DIMENSION)) return "realm:" + Journal.RED_WORLD;
        if (dim.equals(TowerRun.DIMENSION)) return "realm:" + Journal.CITADEL;
        if (dim.equals(PenaltyZone.DIMENSION)) return "realm:" + Journal.WASTES;
        return null;
    }

    /** Looks around: creatures in sight and the realm underfoot. */
    public static void scan(ServerPlayer p) {
        if (p.isSpectator()) return;
        AABB box = p.getBoundingBox().inflate(SIGHT);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && pageOf(e) != null)) {
            if (p.hasLineOfSight(e)) saw(p, e);
        }
        String realm = realmAt(p);
        if (realm != null) discover(p, realm);
    }

    /** Weapons in the pack. */
    public static void pack(ServerPlayer p) {
        Map<Item, String> map = weapons();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = map.get(s.getItem());
            if (id != null) discover(p, id);
        }
    }

    private static synchronized Map<Item, String> weapons() {
        if (weapons == null) {
            Map<Item, String> m = new HashMap<>();
            for (JournalEntry e : Journal.of(JournalCategory.ARSENAL)) {
                if (e.item() != null) m.put(BuiltInRegistries.ITEM.get(e.item()), e.id());
            }
            weapons = m;
        }
        return weapons;
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.tickCount % SCAN_EVERY == 3) scan(p);
        if (p.tickCount % PACK_EVERY == 13) pack(p);
    }

    private JournalService() {}
}
