package com.beyondsolo.test;

import com.beyondsolo.armory.ArmorCatalog;
import com.beyondsolo.armory.ArmoryModule;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.light.LightAPI;
import com.beyondsolo.light.LightSanctum;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchDomain;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * A bench for the balance tests: hunters built the way a player of a level would be (their rank's bonus, three points
 * a level spent the way their class leans, the best armour of their class they may wear and their class's weapon),
 * crowned or invested on demand, and the blows between them and whatever they fight, read off the real damage
 * pipeline with every hook on.
 */
public final class BalanceLab {
    /**
     * A hunter that takes blows (the fake players the factory hands out never do) and swings at full strength. It is
     * not added to the level: nothing ticks it, so its equipment is put on by hand ({@link #wear}).
     */
    public static final class Hunter extends FakePlayer {
        Hunter(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "lab"));
            // a joining player's three safe seconds count down in its tick, which never comes
            try {
                var spawnSafe = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                spawnSafe.setAccessible(true);
                spawnSafe.setInt(this, 0);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("can't end the spawn safety", ex);
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return isRemoved();
        }

        /** Other hunters may hurt it where the server allows fights between hunters (fake players never may). */
        @Override
        public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            var team = getTeam();
            return getServer().isPvpAllowed() && (team == null || !team.isAlliedTo(other.getTeam()) || team.isAllowFriendlyFire());
        }

        /** The next swing lands at full strength, and nobody tires on the bench. */
        public void charge() {
            attackStrengthTicker = 10_000;
            com.beyondsolo.system.Exhaustion.recover(this, 0);
        }
    }

    /** Free stat points a level from quests, chapters and rewards on top of the three a level brings. */
    public static final double REWARD_POINTS_PER_LEVEL = 0.5;

    /**
     * A hunter of {@code level}: the rank made for that level, its bonus and the level's points spent as the class
     * leans, the best non-divine armour of the class the rank may wear (divine from level 60 when {@code divine}) and
     * the class's weapon for the rank.
     */
    public static Hunter hunter(GameTestHelper helper, PlayerClass cls, int level, boolean divine) {
        Hunter p = new Hunter(helper.getLevel());
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        PlayerRank rank = Balance.rankFor(level);
        SystemAPI.awaken(p, rank, cls);
        SystemAPI.setLevel(p, level);
        SystemAPI.addStatPoints(p, (int) Math.round(REWARD_POINTS_PER_LEVEL * (level - 1)));
        spend(p, cls);
        SystemAPI.setRank(p, rank);
        gear(p, cls, rank, divine && level >= 60);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** Spends every free point the way the class leans (its stat weights), leftovers on its main stat. */
    public static void spend(Hunter p, PlayerClass cls) {
        int points = SystemAPI.get(p).statPoints();
        int total = 0;
        for (Stat s : Stat.values()) total += cls.statWeight(s);
        int given = 0;
        Stat main = Stat.STR;
        for (Stat s : Stat.values()) {
            int share = points * cls.statWeight(s) / total;
            if (share > 0) SystemAPI.allocateStats(p, s, share);
            given += share;
            if (cls.statWeight(s) > cls.statWeight(main)) main = s;
        }
        if (points > given) SystemAPI.allocateStats(p, main, points - given);
    }

    /** The class's armour (the best it may wear at the rank, or its divine set) and its weapon for the rank. */
    public static void gear(Hunter p, PlayerClass cls, PlayerRank rank, boolean divine) {
        ArmorSets.Set best = divine ? ArmorCatalog.divine(cls) : null;
        if (best == null) {
            for (ArmorCatalog.Def d : ArmorCatalog.ALL) {
                ArmorSets.Set s = d.set();
                if (s.playerClass != cls || s.divine() || s.tier < 0 || s.tier > rank.tier()) continue;
                if (best == null || s.tier > best.tier) best = s;
            }
        }
        if (best != null) {
            var pieces = ArmoryModule.piecesOf(best);
            if (pieces != null) for (var piece : pieces) {
                ItemStack stack = new ItemStack(piece.get());
                p.setItemSlot(p.getEquipmentSlotForItem(stack), stack);
            }
        }
        Item weapon = GuildHunterEntity.classWeapon(cls, rank);
        if (weapon != null) p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        wear(p);
    }

    private static Method detect;

    /** Puts on what the hunter holds and wears (the attribute modifiers, set bonuses), as a tick would. */
    public static void wear(LivingEntity e) {
        try {
            if (detect == null) {
                detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
                detect.setAccessible(true);
            }
            detect.invoke(e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("can't put the equipment on", ex);
        }
    }

    /** Crowns the hunter and calls up their domain and crown form at full strength. */
    public static void crown(Hunter p, Dominion d) {
        MonarchAPI.crown(p, d);
        MonarchDomain.open(p, d, 20 * 60, Skill.MAX_LEVEL);
        MonarchDomain.crown(p, d, 20 * 60, Skill.MAX_LEVEL);
        p.setHealth(p.getMaxHealth());
    }

    /** Invests the hunter with a mantle and raises their sanctum and seraph form at full strength. */
    public static void invest(Hunter p, Mantle m) {
        LightAPI.invest(p, m);
        LightSanctum.open(p, m, 20 * 60, Skill.MAX_LEVEL);
        LightSanctum.seraph(p, m.color(), 20 * 60, Skill.MAX_LEVEL);
        p.setHealth(p.getMaxHealth());
    }

    /** Takes the crown, the mantle and every form back. */
    public static void release(Hunter p) {
        MonarchDomain.end(p);
        LightSanctum.end(p);
        MonarchAPI.reset(p);
        LightAPI.reset(p);
        SystemAPI.reset(p);
        p.discard();
    }

    /** No lucky blows either way: no critical hits and no dodges (Apothic gives every creature some). */
    public static void steady(LivingEntity e) {
        AttributeInstance crit = e.getAttribute(ALObjects.Attributes.CRIT_CHANCE);
        if (crit != null) crit.setBaseValue(0);
        AttributeInstance dodge = e.getAttribute(ALObjects.Attributes.DODGE_CHANCE);
        if (dodge != null) dodge.setBaseValue(0);
    }

    /**
     * What a full swing of the hunter does to {@code target} on average over {@code n} swings (crits, dodges and
     * every hook on), the target back to full health before each.
     */
    public static double swing(Hunter p, LivingEntity target, int n) {
        double total = 0;
        for (int i = 0; i < n; i++) {
            target.setHealth(target.getMaxHealth());
            target.setAbsorptionAmount(0);
            target.invulnerableTime = 0;
            float before = target.getHealth();
            p.charge();
            p.attack(target);
            total += before - target.getHealth();
        }
        target.setHealth(target.getMaxHealth());
        return total / n;
    }

    /** What {@code n} blows of {@code amount} from {@code source} do to {@code target} on average, from full health. */
    public static double blow(LivingEntity target, DamageSource source, float amount, int n) {
        double total = 0;
        for (int i = 0; i < n; i++) {
            target.setHealth(target.getMaxHealth());
            target.setAbsorptionAmount(0);
            target.invulnerableTime = 0;
            float before = target.getHealth();
            target.hurt(source, amount);
            total += before - target.getHealth();
        }
        target.setHealth(target.getMaxHealth());
        return total / n;
    }

    /** The skill power a hunter's skills hit with, times a skill's own growth at full level for their rank. */
    public static double skillIndex(Hunter p) {
        var d = SystemAPI.get(p);
        return Balance.power(d) * com.beyondsolo.skill.SkillActions.mult(Skill.MAX_LEVEL, d.rank().tier());
    }

    /** One line about a hunter: class, level, health, armour, attack. */
    public static String describe(Hunter p) {
        var d = SystemAPI.get(p);
        StringBuilder b = new StringBuilder();
        b.append(d.playerClass()).append(" L").append(d.level()).append(' ').append(d.rank())
                .append(" hp=").append(Math.round(p.getMaxHealth()))
                .append(" armor=").append(String.format("%.1f", (double) p.getArmorValue()))
                .append(" tough=").append(String.format("%.1f", p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)))
                .append(" atk=").append(String.format("%.1f", p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)))
                .append(" spd=").append(String.format("%.2f", p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)))
                .append(" power=").append(String.format("%.2f", Balance.power(d)));
        for (Stat s : Stat.values()) b.append(' ').append(s.key()).append('=').append(d.stat(s));
        ArmorSets.Set set = ArmorSets.fullSet(p);
        b.append(" set=").append(set == null ? "-" : set.name);
        return b.toString();
    }

    private BalanceLab() {}
}
