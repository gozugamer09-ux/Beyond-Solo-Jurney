package com.beyondsolo.shadow;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One shadow in a necromancer's army, whether it is out in the world or resting in its master's shadow.
 * The numbers it was extracted with (base health, damage and speed) are kept, so it does not depend on the
 * creature's type staying the same.
 */
public final class ShadowRecord {
    /** What a shadow does in a fight. */
    public enum Role {
        /** Fights up close. */
        WARRIOR,
        /** Hurls bolts of shadow from range. */
        CASTER,
        /** Carries its master. Still fights when nobody rides it. */
        MOUNT;

        public String key() { return name().toLowerCase(); }

        public Component displayName() {
            return Component.translatable("shadow_role.beyondsolo." + key());
        }

        public static Role byOrdinal(int i) {
            Role[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, i))];
        }
    }

    private UUID id;
    private String type;
    private String name = "";
    /** The name is one its master gave it (not a boss's own, nor the first steed's): it is bound closer, and stronger. */
    private boolean bestowed;
    /** The name it came with (a boss's own, the first steed's) while one its master gave stands over it, else "". */
    private String born = "";
    private ShadowRank rank = ShadowRank.HUSK;
    private int level = 1;
    private int xp;
    private Role role = Role.WARRIOR;
    /** Extracted from a dungeon boss (or a vanilla boss): a named elite with its own skill. */
    private boolean boss;
    /** The rank of the gate whose boss it was (PlayerRank ordinal), or -1. */
    private int bossRank = -1;
    private float baseHealth;
    private float baseDamage;
    private float baseSpeed;
    /** Fell in battle: can't be called out again until this game time. */
    private long recoverUntil;
    private int kills;
    private long raisedAt;
    /** Standing guard: the world it guards in and the spot it keeps to, or null while it follows its master. */
    private @Nullable String postDimension;
    private @Nullable net.minecraft.world.phys.Vec3 post;
    /**
     * The shape of the creature it was: how it gets about, its size and its look ({@link ShadowForm}). Null for a shadow
     * raised before forms were kept, until its master next comes in ({@link ShadowService#ensureForm}).
     */
    private @Nullable ShadowForm form;

    ShadowRecord() {}

    public ShadowRecord(UUID id, ResourceLocation type, Role role, ShadowRank rank, float baseHealth, float baseDamage, float baseSpeed, long raisedAt) {
        this.id = id;
        this.type = type.toString();
        this.role = role;
        this.rank = rank;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.raisedAt = raisedAt;
    }

    public UUID id() { return id; }
    public ResourceLocation type() { return ResourceLocation.parse(type); }
    public @Nullable EntityType<?> entityType() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(type()).orElse(null);
    }
    public String name() { return name; }
    public boolean named() { return !name.isEmpty(); }
    /** Its master gave it its name ({@link ShadowPromotions#name}): {@link ShadowPromotions#BOND} stronger. */
    public boolean bonded() { return bestowed && !name.isEmpty(); }
    public ShadowRank rank() { return rank; }
    public int level() { return level; }
    public int xp() { return xp; }
    public Role role() { return role; }
    public boolean isMount() { return role == Role.MOUNT; }
    public boolean boss() { return boss; }
    public int bossRank() { return bossRank; }
    public float baseHealth() { return baseHealth; }
    public float baseDamage() { return baseDamage; }
    public float baseSpeed() { return baseSpeed; }
    public long recoverUntil() { return recoverUntil; }
    public boolean recovering(long now) { return recoverUntil > now; }
    public int kills() { return kills; }
    public long raisedAt() { return raisedAt; }
    /** The shape of the creature it was, or null (an old shadow not seen since, or the client before the first sync). */
    public @Nullable ShadowForm form() { return form; }
    /** How it gets about: walks, flies or swims. */
    public ShadowForm.Movement movement() { return form == null ? ShadowForm.Movement.WALK : form.move(); }

    /** True while it stands guard somewhere instead of following its master. */
    public boolean posted() { return post != null && postDimension != null; }
    /** The spot it guards, or null. */
    public @Nullable net.minecraft.world.phys.Vec3 post() { return post; }
    /** The world it guards in ({@code minecraft:overworld}...), or null. */
    public @Nullable ResourceLocation postDimension() { return postDimension == null ? null : ResourceLocation.tryParse(postDimension); }

    /** True if it guards a spot in this world. */
    public boolean postedIn(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return posted() && dimension.location().toString().equals(postDimension);
    }

    /** The shadow's name: its own if it has one, otherwise the creature it was. */
    public Component displayName() {
        return displayName(wireName());
    }

    /** The shadow's name as one string to send to a client: its own stored name, or its creature type id. */
    public String wireName() {
        return named() ? name : type;
    }

    /** Reads {@link #wireName()} back into the name, in the reader's language (stored names never hold a ':'). */
    public static Component displayName(String wire) {
        if (wire.indexOf(':') < 0) return ShadowNames.display(wire);
        ResourceLocation id = ResourceLocation.tryParse(wire);
        EntityType<?> t = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        Component base = t != null ? t.getDescription() : Component.literal(wire);
        return Component.translatable("shadow.beyondsolo.of", base);
    }

    /** XP to fill the current level. */
    public int xpToNext() {
        return xpToNext(level, rank);
    }

    public static int xpToNext(int level, ShadowRank rank) {
        return Math.round((20 + 10 * level) * (1 + 0.5f * rank.ordinal()));
    }

    /** The shadow can't grow any more under a master whose shadows stop at {@code top} ({@link ShadowService#top}). */
    public boolean maxed(ShadowRank top) {
        return rank.ordinal() >= top.ordinal() && level >= ShadowRank.LEVEL_CAP;
    }

    /**
     * At the top of its rank, waiting for its master to promote it ({@link ShadowPromotions#promote}): the next rank is
     * one only a promotion reaches ({@link ShadowRank#byPromotion}), and one its master's shadows can reach at all.
     */
    public boolean ready(ShadowRank top) {
        return level >= ShadowRank.LEVEL_CAP && rank.ordinal() < top.ordinal() && rank.next().byPromotion();
    }

    /** Takes no XP for now: fully grown, or waiting for a promotion. */
    public boolean stalled(ShadowRank top) {
        return maxed(top) || ready(top);
    }

    // ---- changes (through ShadowService)

    void setName(String name) { this.name = name == null ? "" : name; }
    void setForm(@Nullable ShadowForm form) { this.form = form; }

    /**
     * Its master gives it a name ({@link ShadowPromotions#name}), or with "" takes theirs back: then it is known again by
     * the name it came with, if it had one, else by its creature.
     */
    void bestow(String given) {
        if (given == null || given.isEmpty()) {
            if (bestowed) name = born;
            born = "";
            bestowed = false;
            return;
        }
        if (!bestowed) born = name;
        name = given;
        bestowed = true;
    }
    void setBoss(int bossRank) {
        this.boss = true;
        this.bossRank = bossRank;
    }
    void setRank(ShadowRank rank) { this.rank = rank; }
    void setLevel(int level) { this.level = Math.max(1, Math.min(ShadowRank.LEVEL_CAP, level)); }
    void setXp(int xp) { this.xp = Math.max(0, xp); }
    void setRecoverUntil(long t) { this.recoverUntil = t; }
    void addKill() { kills++; }

    /** Sets it to guard a spot ({@code at} null: back to following). */
    void setPost(@Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, @Nullable net.minecraft.world.phys.Vec3 at) {
        if (dimension == null || at == null) {
            this.postDimension = null;
            this.post = null;
        } else {
            this.postDimension = dimension.location().toString();
            this.post = at;
        }
    }

    /**
     * Adds XP, climbing no higher than {@code top} and no further than the top of a rank its master has to promote it
     * out of ({@link #ready}). Returns how many ranks it climbed (0 if none); levels inside a rank are applied on the way.
     */
    int addXp(int amount, ShadowRank top) {
        if (stalled(top) || amount <= 0) return 0;
        int ranks = 0;
        xp += amount;
        while (!stalled(top) && xp >= xpToNext()) {
            xp -= xpToNext();
            if (level < ShadowRank.LEVEL_CAP) {
                level++;
            } else {
                level = rank.entryLevel(level);
                rank = rank.next();
                ranks++;
            }
        }
        if (stalled(top)) xp = 0;
        return ranks;
    }

    // ---- save

    public CompoundTag save() {
        return save(true);
    }

    /** Saved; without its look ({@link ShadowForm#save}) for the army sent to the client. */
    public CompoundTag save(boolean withLook) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type);
        if (!name.isEmpty()) t.putString("name", name);
        if (bestowed) t.putBoolean("bestowed", true);
        if (!born.isEmpty()) t.putString("born", born);
        t.putInt("rank", rank.ordinal());
        t.putInt("level", level);
        t.putInt("xp", xp);
        t.putInt("role", role.ordinal());
        t.putBoolean("boss", boss);
        t.putInt("bossRank", bossRank);
        t.putFloat("health", baseHealth);
        t.putFloat("damage", baseDamage);
        t.putFloat("speed", baseSpeed);
        t.putLong("recover", recoverUntil);
        t.putInt("kills", kills);
        t.putLong("raised", raisedAt);
        if (posted()) {
            t.putString("postDim", postDimension);
            t.putDouble("postX", post.x);
            t.putDouble("postY", post.y);
            t.putDouble("postZ", post.z);
        }
        if (form != null) t.put("form", form.save(withLook));
        return t;
    }

    public static ShadowRecord load(CompoundTag t) {
        ShadowRecord r = new ShadowRecord();
        r.id = t.hasUUID("id") ? t.getUUID("id") : UUID.randomUUID();
        r.type = t.getString("type");
        r.name = t.getString("name");
        r.bestowed = t.getBoolean("bestowed");
        r.born = t.getString("born");
        r.rank = ShadowRank.byOrdinal(t.getInt("rank"));
        r.level = Math.max(1, t.getInt("level"));
        r.xp = t.getInt("xp");
        r.role = Role.byOrdinal(t.getInt("role"));
        r.boss = t.getBoolean("boss");
        r.bossRank = t.contains("bossRank") ? t.getInt("bossRank") : -1;
        r.baseHealth = t.getFloat("health");
        r.baseDamage = t.getFloat("damage");
        r.baseSpeed = t.getFloat("speed");
        r.recoverUntil = t.getLong("recover");
        r.kills = t.getInt("kills");
        r.raisedAt = t.getLong("raised");
        if (t.contains("postDim")) {
            r.postDimension = t.getString("postDim");
            r.post = new net.minecraft.world.phys.Vec3(t.getDouble("postX"), t.getDouble("postY"), t.getDouble("postZ"));
        }
        if (t.contains("form", net.minecraft.nbt.Tag.TAG_COMPOUND)) r.form = ShadowForm.load(t.getCompound("form"));
        return r;
    }
}
