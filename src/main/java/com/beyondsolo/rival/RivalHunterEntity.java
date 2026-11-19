package com.beyondsolo.rival;

import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.skill.SkillFx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * One hunter of a rival party ({@link RivalParty}): a hunter like a guild's, in their class's armour with their class's
 * weapon, with their guild's name in its colour over their head. They walk where their party goes (to the gate, into
 * it, away from it, through the cave to its boss: {@link #march}), fighting whatever creature they meet on the way;
 * they never fight a hunter, and a hunter can't hurt them. Never saved: {@link RivalParties} brings them out while
 * someone is near enough to see them.
 */
public class RivalHunterEntity extends GuildHunterEntity {
    private static final EntityDataAccessor<Integer> GUILD = SynchedEntityData.defineId(RivalHunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LEADER = SynchedEntityData.defineId(RivalHunterEntity.class, EntityDataSerializers.BOOLEAN);

    /** Server: whose party it is and which of its hunters. */
    @Nullable private UUID party;
    private int member;
    /** Server: where it walks, in order, and how far along it is. */
    private final List<Vec3> route = new ArrayList<>();
    private int routeAt;
    /** Server: how fast it walks the route (1: a walk, more to hurry). */
    private double pace = 1.0;
    /** Server: it has been stuck this many checks in a row. */
    private int stuck;
    @Nullable private Vec3 lastCheck;

    public RivalHunterEntity(EntityType<? extends RivalHunterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(GUILD, RivalGuild.INDEPENDENT.ordinal());
        b.define(LEADER, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(6, new MarchGoal());
    }

    /** Makes this the body of a party's hunter: looks, gear, strength, and the guild's name over its head. */
    void setup(RivalParty p, int index) {
        RivalParty.Member m = p.members.get(index);
        this.party = p.id;
        this.member = index;
        entityData.set(GUILD, p.guild.ordinal());
        entityData.set(LEADER, index == 0);
        serve(RivalParties.CAUSE, new RaidState.Hired(p.id, m.hunter, m.name, m.cls, m.level, m.skin), p.guild.color, "");
        Component name = Component.literal("[").append(p.guild.displayName()).append("] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(p.guild.color & 0xFFFFFF)))
                .append(Component.literal(m.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        setCustomName(name);
        setCustomNameVisible(true);
    }

    @Nullable
    public UUID party() {
        return party;
    }

    public int member() {
        return member;
    }

    public RivalGuild guild() {
        return RivalGuild.byIndex(entityData.get(GUILD));
    }

    public boolean leader() {
        return entityData.get(LEADER);
    }

    // ------------------------------------------------------------------ walking

    /** Sends it along {@code points} (replacing any route it had), at {@code pace}. */
    void march(List<Vec3> points, double pace) {
        route.clear();
        route.addAll(points);
        routeAt = 0;
        this.pace = pace;
        stuck = 0;
        lastCheck = null;
        getNavigation().stop();
    }

    /**
     * Walks on at about {@code blocksPerTick}. A mob's walk grows with the square of its pace (its move control sets
     * both its speed and its forward push to speed x pace, and the push it gets is their product), so on open ground it
     * covers about 2.16 (speed x pace)^2 blocks a tick.
     */
    void walkAt(double blocksPerTick) {
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        double want = speed <= 0 ? 1 : Mth.clamp(Math.sqrt(Math.max(0, blocksPerTick) / 2.16) / speed, 0.3, 1.3);
        if (Math.abs(want - pace) < 0.02) return;
        pace = want;
        getNavigation().setSpeedModifier(want);
    }

    /** Sends it along {@code points} from the point nearest ahead of where it stands. */
    void marchFrom(List<Vec3> points, int from, double pace) {
        march(points, pace);
        routeAt = Math.max(0, Math.min(points.size(), from));
    }

    /** How far along its route it is (the index of the point it walks to; the route's length once there). */
    int routeAt() {
        return routeAt;
    }

    int routeSize() {
        return route.size();
    }

    /** True once it has walked its whole route (or has none). */
    boolean arrived() {
        return routeAt >= route.size();
    }

    @Nullable
    Vec3 goal() {
        return routeAt < route.size() ? route.get(routeAt) : null;
    }

    /** Steps out of the air at a point of its route (stuck, or left behind): a puff where it was and where it lands. */
    void slip(Vec3 to) {
        if (level() instanceof ServerLevel server) {
            SkillFx.play(this, SkillFxPayload.Kind.BLINK, guild().color & 0xFFFFFF, position(), 1f, 0);
            to = RivalParties.footing(server, to);
        }
        moveTo(to.x, to.y, to.z, getYRot(), 0f);
        getNavigation().stop();
        stuck = 0;
        lastCheck = position();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        // every two seconds on the march: one that hasn't got anywhere is stuck, and twice in a row it slips ahead
        if (tickCount % 40 == 0 && goal() != null && getTarget() == null) {
            Vec3 now = position();
            // half of what it would walk in the two seconds, at most a block and a half (a slow stroll is not stuck)
            double step = pace * getAttributeValue(Attributes.MOVEMENT_SPEED);
            double least = Math.min(1.5, 0.5 * 2.16 * step * step * 40);
            if (lastCheck != null && now.distanceToSqr(lastCheck) < least * least) {
                if (++stuck >= 2) {
                    Vec3 g = goal();
                    if (g != null) {
                        slip(g);
                        routeAt++;
                    }
                }
            } else {
                stuck = 0;
            }
            lastCheck = now;
        }
    }

    // ------------------------------------------------------------------ talking

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer p) RivalParties.talk(p, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Walks its route a point at a time; fighting (and mending) comes first. */
    private final class MarchGoal extends Goal {
        private int recompute;

        MarchGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return goal() != null && getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return goal() != null && getTarget() == null;
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 g = goal();
            if (g == null) return;
            double dx = g.x - getX(), dz = g.z - getZ();
            double flat = dx * dx + dz * dz;
            if (flat < 2.2 * 2.2 && Math.abs(g.y - getY()) < 3.5) {
                routeAt++;
                recompute = 0;
                Vec3 next = goal();
                if (next == null) {
                    getNavigation().stop();
                    return;
                }
                g = next;
            }
            getLookControl().setLookAt(g.x, g.y + 1.4, g.z, 10, getMaxHeadXRot());
            if (--recompute > 0 && !getNavigation().isDone()) return;
            recompute = 12;
            // a point far off is walked to a stretch at a time (a path is only found so far)
            double far = Math.sqrt((g.x - getX()) * (g.x - getX()) + (g.z - getZ()) * (g.z - getZ()));
            Vec3 step = far <= 20 ? g : new Vec3(getX() + (g.x - getX()) / far * 18, getY(), getZ() + (g.z - getZ()) / far * 18);
            BlockPos at = BlockPos.containing(step);
            getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, pace);
        }
    }
}
