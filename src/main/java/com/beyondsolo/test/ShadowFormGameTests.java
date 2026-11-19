package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.shadow.ShadowData;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowForm;
import com.beyondsolo.shadow.ShadowModule;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shadows out of any creature at all, ours, vanilla's or another mod's: every living kind in the game leaves a soul
 * and rises in its own shape, fliers fly, swimmers swim, giants rise smaller, and the look a shadow keeps holds what
 * the creature wore. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowFormGameTests {
    private static final String ARENA = "evaluation_zone";
    /** How many kinds rise at once in the every-creature test, and how long each lot stands in the world. */
    private static final int LOT = 6, STAND_TICKS = 5;

    /** A C-rank necromancer, army emptied, on the arena's floor. */
    private static ServerPlayer necromancer(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        SkillAPI.reset(p);
        JobAPI.reset(p);
        p.setGameMode(GameType.CREATIVE);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        QuestTestAccess.markStarted(p);
        SystemAPI.awaken(p, PlayerRank.C, Subclass.NECROMANCER.base());
        JobAPI.changeJob(p, Subclass.NECROMANCER);
        ShadowService.reset(p);
        return p;
    }

    private static List<ShadowService.Soul> souls(ServerPlayer p) {
        return ShadowService.souls().stream().filter(s -> s.owner().equals(p.getUUID())).toList();
    }

    /** The arena's floor (where a creature stands), as a world height. */
    private static double floor(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1)).getY();
    }

    /** A creature of this kind, made but not put in the world, half a block over the arena's floor. */
    private static <T extends Entity> T made(GameTestHelper helper, EntityType<T> type) {
        T e = type.create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        if (e != null) e.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
        return e;
    }

    // ================================================================ every creature in the game

    /**
     * Every living kind there is (whatever mods are loaded), but those tagged {@code shadow/no_soul}: what is made of
     * the rest is not alive (an arrow, a minecart) and leaves nothing.
     */
    private static List<EntityType<?>> livingKinds(GameTestHelper helper) {
        List<EntityType<?>> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.is(ShadowModule.NO_SOUL)) continue;
            Entity e = type.create(helper.getLevel());
            if (e instanceof LivingEntity) out.add(type);
            if (e != null) e.discard();
        }
        return out;
    }

    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void everyCreatureInTheGameRisesAsAShadow(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "legion");
        List<EntityType<?>> kinds = livingKinds(helper);
        // vanilla's creatures alone are more than seventy
        helper.assertTrue(kinds.size() > 90, "every creature in the game was found: " + kinds.size());
        raiseLot(helper, p, kinds, 0, new ArrayList<>());
    }

    /** Raises the next lot of kinds, lets them stand a few ticks, lets them go, and goes on to the next. */
    private static void raiseLot(GameTestHelper helper, ServerPlayer p, List<EntityType<?>> kinds, int from, List<String> failed) {
        if (from >= kinds.size()) {
            ShadowService.reset(p);
            helper.assertTrue(failed.isEmpty(), failed.size() + " of " + kinds.size() + " kinds failed: " + String.join("; ", failed));
            helper.succeed();
            return;
        }
        List<ShadowEntity> out = new ArrayList<>();
        for (int i = from; i < Math.min(kinds.size(), from + LOT); i++) {
            EntityType<?> type = kinds.get(i);
            try {
                ShadowEntity e = raise(helper, p, type, failed);
                if (e != null) out.add(e);
            } catch (RuntimeException | LinkageError ex) {
                failed.add(EntityType.getKey(type) + ": " + ex);
            }
        }
        helper.runAfterDelay(STAND_TICKS, () -> {
            for (ShadowEntity e : out) {
                if (!e.isAlive()) failed.add(e.source() + ": its shadow fell within " + STAND_TICKS + " ticks");
                UUID rid = e.recordId();
                if (rid != null) ShadowService.release(p, rid);
                e.discard();
            }
            raiseLot(helper, p, kinds, from + LOT, failed);
        });
    }

    /** One creature of this kind falls to the necromancer and rises as their shadow; what goes wrong is told to failed. */
    private static @Nullable ShadowEntity raise(GameTestHelper helper, ServerPlayer p, EntityType<?> type, List<String> failed) {
        String what = EntityType.getKey(type).toString();
        Entity made = made(helper, type);
        if (!(made instanceof LivingEntity victim)) {
            failed.add(what + ": could not be made");
            return null;
        }
        ShadowForm.Movement moves = ShadowForm.movementOf(victim);
        ShadowService.Soul soul = ShadowService.leaveSoul(p, victim);
        victim.discard();
        if (soul == null) {
            failed.add(what + ": left no soul");
            return null;
        }
        if (Math.abs(soul.pos().y - floor(helper)) > 0.01) failed.add(what + ": its soul hangs off the floor at " + soul.pos());
        ShadowRecord r = ShadowService.extractNow(p, soul);
        if (r == null) {
            failed.add(what + ": was not raised");
            return null;
        }
        ShadowForm f = r.form();
        if (f == null) {
            failed.add(what + ": rose with no form");
            return null;
        }
        if (f.look().sizeInBytes() > ShadowForm.MAX_LOOK_BYTES) failed.add(what + ": keeps too big a look, " + f.look().sizeInBytes());
        if (!r.isMount() && r.movement() != moves) failed.add(what + ": " + moves + " but its shadow " + r.movement());
        ShadowEntity e = ShadowService.entity(p, r.id());
        if (e == null) e = ShadowService.summonAt(p, r, soul.pos(), false);
        if (e == null) {
            failed.add(what + ": did not rise");
            return null;
        }
        float health = ShadowService.maxHealth(r, SystemAPI.get(p));
        if (Math.abs(e.getMaxHealth() - health) > health * 0.005f + 0.01f) {
            failed.add(what + ": health " + e.getMaxHealth() + ", not the army's " + health);
        }
        float k = e.sizeScale();
        if (e.getBbWidth() > ShadowForm.MAX_WIDTH * k + 0.01f || e.getBbHeight() > ShadowForm.MAX_HEIGHT * k + 0.01f) {
            failed.add(what + ": rose too big, " + e.getBbWidth() + " x " + e.getBbHeight());
        }
        if (Math.abs(e.getBbWidth() - f.dimensions().width() * k) > 0.01f || Math.abs(e.getBbHeight() - f.dimensions().height() * k) > 0.01f) {
            failed.add(what + ": rose " + e.getBbWidth() + " x " + e.getBbHeight() + ", not in its shape " + f.dimensions());
        }
        boolean flies = !r.isMount() && r.movement().airborne();
        if (e.airborne() != flies || e.isNoGravity() != flies) failed.add(what + ": its shadow " + (flies ? "can't" : "shouldn't") + " fly");
        return e;
    }

    // ================================================================ souls

    @GameTest(template = ARENA)
    public static void aCreatureThatBurnsToDeathStillLeavesItsSoul(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "pyre");
        Zombie struck = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        // struck by the necromancer, finished by the fire (a fall, the water, a pet): Minecraft gives them the kill
        struck.setLastHurtByPlayer(p);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(struck, struck.damageSources().onFire()));
        struck.discard();
        helper.assertTrue(souls(p).size() == 1, "a zombie the necromancer struck, burned to death, leaves them its soul");
        Zombie untouched = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(untouched, untouched.damageSources().onFire()));
        untouched.discard();
        helper.assertTrue(souls(p).size() == 1, "one they never touched leaves them nothing");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aFliersSoulLingersOnTheGroundUnderIt(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "fowler");
        Bat bat = made(helper, EntityType.BAT);
        bat.setPos(bat.getX(), floor(helper) + 3.2, bat.getZ());
        ShadowService.Soul s = ShadowService.leaveSoul(p, bat);
        bat.discard();
        helper.assertTrue(s != null && Math.abs(s.pos().y - floor(helper)) < 0.01,
                "a bat shot down leaves its soul on the floor under it, in reach: " + (s == null ? null : s.pos()));
        helper.assertTrue(s.form().move() == ShadowForm.Movement.FLY, "and the soul remembers it flew");
        ShadowService.reset(p);
        helper.succeed();
    }

    // ================================================================ looks

    @GameTest(template = ARENA)
    public static void aShadowKeepsWhatItsCreatureWore(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "tailor");
        Zombie z = made(helper, EntityType.ZOMBIE);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        z.setCustomName(Component.literal("Gerald"));
        z.setRemainingFireTicks(100);
        CompoundTag look = ShadowForm.of(z).look();
        helper.assertTrue(look.contains("ArmorItems") && look.contains("HandItems"), "it keeps the zombie's helmet and sword");
        for (String gone : new String[]{"CustomName", "Health", "Pos", "Motion", "UUID", "Fire", "Brain", "attributes"}) {
            helper.assertFalse(look.contains(gone), "it leaves " + gone + " behind");
        }
        // the look reads back into a fresh creature of its kind, as the client draws it
        Zombie back = made(helper, EntityType.ZOMBIE);
        back.load(look.copy());
        helper.assertTrue(back.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && back.getMainHandItem().is(Items.IRON_SWORD),
                "a zombie made from the look wears the helmet and holds the sword");
        helper.assertTrue(back.getCustomName() == null, "and has no name");
        back.discard();

        Sheep sheep = made(helper, EntityType.SHEEP);
        sheep.setColor(DyeColor.LIME);
        helper.assertTrue(ShadowForm.of(sheep).look().getByte("Color") == DyeColor.LIME.getId(), "a sheep's shadow keeps its colour");
        // a bat is made hanging asleep: its shadow doesn't hang upside down over its master's head
        helper.assertFalse(ShadowForm.of(made(helper, EntityType.BAT)).look().contains("BatFlags"), "a bat's shadow is awake");

        // the army sent to the client with every change leaves the looks behind; the one saved keeps them
        Skeleton archer = made(helper, EntityType.SKELETON);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        ShadowRecord r = ShadowService.extractNow(p, ShadowService.leaveSoul(p, archer));
        helper.assertTrue(r != null && r.role() == ShadowRecord.Role.CASTER, "a skeleton with its bow casts");
        helper.assertTrue(r.save(true).getCompound("form").contains("look") && !r.save(false).getCompound("form").contains("look"),
                "its look is saved with the army but not sent with every change");
        ShadowRecord saved = ShadowRecord.load(r.save(true));
        helper.assertTrue(saved.form() != null && saved.form().look().contains("HandItems") && saved.movement() == ShadowForm.Movement.WALK,
                "the look and the way it moves survive a save");
        ShadowService.reset(p);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rolesFollowWhatTheCreatureDid(GameTestHelper helper) {
        necromancer(helper, "marshal");
        helper.assertTrue(ShadowForm.roleOf(EntityType.SNOW_GOLEM, made(helper, EntityType.SNOW_GOLEM)) == ShadowRecord.Role.CASTER,
                "a snow golem throws: its shadow casts");
        Piglin blade = made(helper, EntityType.PIGLIN);
        blade.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        Piglin bolt = made(helper, EntityType.PIGLIN);
        bolt.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        helper.assertTrue(ShadowForm.roleOf(EntityType.PIGLIN, blade) == ShadowRecord.Role.WARRIOR, "a piglin with its sword fights up close");
        helper.assertTrue(ShadowForm.roleOf(EntityType.PIGLIN, bolt) == ShadowRecord.Role.CASTER, "one with its crossbow casts");
        helper.assertTrue(ShadowForm.roleOf(EntityType.WITHER_SKELETON, made(helper, EntityType.WITHER_SKELETON)) == ShadowRecord.Role.WARRIOR,
                "a wither skeleton swings its blade, even one that lost it");
        helper.assertTrue(ShadowForm.roleOf(EntityType.CAMEL, made(helper, EntityType.CAMEL)) == ShadowRecord.Role.MOUNT, "a camel carries");
        helper.succeed();
    }

    // ================================================================ getting about

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void fliersFlyAndSwimmersSwim(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "falconer");
        for (EntityType<?> t : List.of(EntityType.BAT, EntityType.PHANTOM, EntityType.GHAST, EntityType.BLAZE, EntityType.ENDER_DRAGON,
                EntityType.WITHER, EntityType.PARROT, EntityType.BEE, EntityType.ALLAY, EntityType.VEX)) {
            ShadowRecord r = ShadowService.give(p, t, ShadowRank.SOLDIER, false);
            helper.assertTrue(r.movement() == ShadowForm.Movement.FLY, EntityType.getKey(t) + "'s shadow flies, not " + r.movement());
        }
        for (EntityType<?> t : List.of(EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH, EntityType.SQUID,
                EntityType.GLOW_SQUID, EntityType.DOLPHIN, EntityType.TADPOLE, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN)) {
            ShadowRecord r = ShadowService.give(p, t, ShadowRank.SOLDIER, false);
            helper.assertTrue(r.movement() == ShadowForm.Movement.SWIM, EntityType.getKey(t) + "'s shadow swims, not " + r.movement());
        }
        for (EntityType<?> t : List.of(EntityType.ZOMBIE, EntityType.DROWNED, EntityType.SPIDER, EntityType.IRON_GOLEM, EntityType.HORSE)) {
            ShadowRecord r = ShadowService.give(p, t, ShadowRank.SOLDIER, false);
            helper.assertTrue(r.movement() == ShadowForm.Movement.WALK, EntityType.getKey(t) + "'s shadow walks, not " + r.movement());
        }
        ShadowData army = ShadowService.army(p);
        ShadowRecord batR = find(army, EntityType.BAT), zombieR = find(army, EntityType.ZOMBIE), codR = find(army, EntityType.COD);
        double floor = floor(helper);
        BlockPos high = helper.absolutePos(new BlockPos(2, 4, 4));
        ShadowEntity bat = ShadowService.summonAt(p, batR, Vec3.atBottomCenterOf(high), false);
        ShadowEntity zombie = ShadowService.summonAt(p, zombieR, Vec3.atBottomCenterOf(high.east(2)), false);
        ShadowEntity cod = ShadowService.summonAt(p, codR, Vec3.atBottomCenterOf(high.south(-2).east(2)), false);
        helper.assertTrue(bat.airborne() && bat.isNoGravity() && bat.getNavigation() instanceof FlyingPathNavigation, "a bat's shadow flies");
        helper.assertTrue(cod.airborne() && cod.isNoGravity(), "a cod's shadow swims through the air");
        helper.assertFalse(zombie.airborne() || zombie.isNoGravity(), "a zombie's walks");
        // (just past their rising: long enough to fall, too soon to go after another test's creatures)
        helper.runAfterDelay(ShadowEntity.RISE_TICKS + 4, () -> {
            helper.assertTrue(bat.isAlive() && bat.getY() > floor + 0.9, "the bat's shadow stays up in the air: " + (bat.getY() - floor));
            // down off the two blocks it rose at (it may already be on its way, up on the crystal's dais)
            helper.assertTrue(zombie.isAlive() && zombie.getY() - floor < 1, "the zombie's came down to the floor: " + (zombie.getY() - floor));
            helper.assertTrue(cod.isAlive() && cod.getHealth() >= cod.getMaxHealth(), "the cod's doesn't choke out of the water");
            ShadowService.reset(p);
            helper.succeed();
        });
    }

    private static ShadowRecord find(ShadowData army, EntityType<?> type) {
        for (ShadowRecord r : army.shadows()) if (r.type().equals(EntityType.getKey(type))) return r;
        throw new IllegalStateException("no shadow of " + EntityType.getKey(type));
    }

    // ================================================================ size

    @GameTest(template = ARENA)
    public static void hugeCreaturesRiseSmaller(GameTestHelper helper) {
        ShadowForm dragon = ShadowForm.probe(helper.getLevel(), EntityType.ENDER_DRAGON);
        helper.assertTrue(Math.abs(dragon.fit() - 0.25f) < 0.001f && dragon.dimensions().width() <= ShadowForm.MAX_WIDTH + 0.001f,
                "a dragon's shadow rises a quarter its size: " + dragon.fit());
        ShadowForm giant = ShadowForm.probe(helper.getLevel(), EntityType.GIANT);
        helper.assertTrue(giant.fit() < 1 && giant.dimensions().height() <= ShadowForm.MAX_HEIGHT + 0.001f,
                "a giant's shadow rises no taller than " + ShadowForm.MAX_HEIGHT + ": " + giant.dimensions().height());
        ShadowForm zombie = ShadowForm.probe(helper.getLevel(), EntityType.ZOMBIE);
        helper.assertTrue(zombie.fit() == 1 && Math.abs(zombie.dimensions().height() - EntityType.ZOMBIE.getHeight()) < 0.001f,
                "a zombie's shadow is a zombie's size");
        Zombie baby = made(helper, EntityType.ZOMBIE);
        baby.setBaby(true);
        helper.assertTrue(ShadowForm.of(baby).height() < zombie.height() * 0.6f, "a baby's shadow is a baby's size");
        Slime slime = made(helper, EntityType.SLIME);
        slime.setSize(4, true);
        helper.assertTrue(ShadowForm.of(slime).width() > 2, "a big slime's shadow is big: " + ShadowForm.of(slime).width());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void shadowsRaisedBeforeFormsTakeTheirKindsShape(GameTestHelper helper) {
        ServerPlayer p = necromancer(helper, "elder");
        ShadowRecord r = ShadowService.give(p, EntityType.BAT, ShadowRank.SOLDIER, false);
        CompoundTag saved = ShadowService.army(p).save(true);
        ListTag list = saved.getList("shadows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) list.getCompound(i).remove("form");
        ShadowData army = ShadowService.army(p);
        army.deserializeNBT(helper.getLevel().registryAccess(), saved);
        ShadowRecord old = army.get(r.id());
        helper.assertTrue(old != null && old.form() == null, "a shadow raised before forms has none");
        ShadowEntity e = ShadowService.summonAt(p, old, p.position(), false);
        helper.assertTrue(old.form() != null && old.movement() == ShadowForm.Movement.FLY && e != null && e.airborne(),
                "called out, it takes its kind's shape: a bat's shadow flies");
        ShadowService.reset(p);
        helper.succeed();
    }
}
