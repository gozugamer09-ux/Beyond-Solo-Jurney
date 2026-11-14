package com.beyondsolo.item.arsenal;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.SystemWeaponItem;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.beyondsolo.item.ItemGrade.*;
import static com.beyondsolo.item.arsenal.Trait.Kind.*;
import static com.beyondsolo.item.arsenal.WeaponKind.*;

/**
 * The arsenal: every graded System weapon, from starter knives to the six divine weapons (one per class). Designs
 * borrow the feel of the hunters' world (fangs of gate beasts, a knight-killing dagger, a storm lord's sword, a
 * dragon's fang...) but every name and sprite is our own. Sprites come from {@code tools/gen_arsenal_assets.py}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Arsenal {
    private static final List<DeferredItem<? extends Item>> ALL = new ArrayList<>();
    private static final Map<ItemGrade, List<DeferredItem<? extends Item>>> BY_GRADE = new EnumMap<>(ItemGrade.class);

    private static final Map<ItemGrade, Tier> TIERS = new EnumMap<>(ItemGrade.class);

    static {
        TIERS.put(COMMON, new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 350, 6f, 2.0f, 14, () -> Ingredient.of(Items.IRON_INGOT)));
        TIERS.put(RARE, new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 900, 8f, 3.0f, 15, () -> Ingredient.of(Items.DIAMOND)));
        TIERS.put(EPIC, new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1500, 8.5f, 3.5f, 16, () -> Ingredient.of(Items.DIAMOND)));
        TIERS.put(LEGENDARY, new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 9f, 4.5f, 18, () -> Ingredient.of(Items.NETHERITE_INGOT)));
        TIERS.put(MYTHIC, new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3200, 9.5f, 5.5f, 20, () -> Ingredient.of(Items.NETHERITE_INGOT)));
        TIERS.put(DIVINE, new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 5000, 10f, 7.0f, 22, () -> Ingredient.of(Items.NETHER_STAR)));
    }

    // ---------------------------------------------------------------- common: starter gear
    public static final DeferredItem<SystemWeaponItem> LYCAN_FANG_KNIFE = w("lycan_fang_knife", DAGGER, COMMON).element(DamageElement.BLOOD, 10).melee();
    public static final DeferredItem<SystemWeaponItem> GOBLIN_SHIV = w("goblin_shiv", DAGGER, COMMON).element(DamageElement.POISON, 12).favored(PlayerClass.ASSASSIN).melee();
    public static final DeferredItem<SystemWeaponItem> APPRENTICE_STAFF = w("apprentice_staff", STAFF, COMMON).spell(0.10f).favored(PlayerClass.MAGE).melee();
    public static final DeferredItem<SystemWeaponItem> ACOLYTE_MACE = w("acolyte_mace", MACE, COMMON).trait(MENDING, 0.15f).favored(PlayerClass.HEALER).melee();
    public static final DeferredItem<SystemWeaponItem> IRONCLAD_WARHAMMER = w("ironclad_warhammer", HAMMER, COMMON).trait(BULWARK, 0.05f).favored(PlayerClass.GUARDIAN).melee();
    public static final DeferredItem<SystemBowItem> HUNTERS_SHORTBOW = w("hunters_shortbow", BOW, COMMON).bowPower(1.0f).favored(PlayerClass.RANGER).bow();

    // ---------------------------------------------------------------- rare
    public static final DeferredItem<SystemWeaponItem> KNIGHTSBANE = w("knightsbane", DAGGER, RARE).trait(ARMOR_BREAKER, 0.03f).favored(PlayerClass.ASSASSIN).melee();
    public static final DeferredItem<SystemWeaponItem> FROSTWARDEN_DAGGER = w("frostwarden_dagger", DAGGER, RARE).trait(FROSTBITE, 2).favored(PlayerClass.ASSASSIN).melee();
    public static final DeferredItem<SystemWeaponItem> WARCHIEF_CLEAVER = w("warchief_cleaver", HEAVY_AXE, RARE).element(DamageElement.BLOOD, 18).trait(EXECUTIONER, 0.3f).favored(PlayerClass.WARRIOR).melee();
    public static final DeferredItem<SystemWeaponItem> SILVERMOON_RAPIER = w("silvermoon_rapier", RAPIER, RARE).trait(LIFESTEAL, 0.05f).melee();
    public static final DeferredItem<SystemWeaponItem> NAGA_GLAIVE = w("naga_glaive", GLAIVE, RARE).element(DamageElement.POISON, 18).melee();
    public static final DeferredItem<SystemWeaponItem> WINTER_BREATH_STAFF = w("winter_breath_staff", STAFF, RARE).spell(0.2f).trait(FROSTBITE, 2).favored(PlayerClass.MAGE).melee();
    public static final DeferredItem<SystemBowItem> WINDRUNNER_LONGBOW = w("windrunner_longbow", BOW, RARE).bowPower(1.15f).favored(PlayerClass.RANGER).bow();
    public static final DeferredItem<SystemWeaponItem> MENDING_SCEPTER = w("mending_scepter", MACE, RARE).trait(MENDING, 0.25f).favored(PlayerClass.HEALER).melee();

    // ---------------------------------------------------------------- epic
    public static final DeferredItem<SystemWeaponItem> THUNDERFANG_LONGSWORD = w("thunderfang_longsword", SWORD, EPIC).trait(THUNDERCLAP, 0.25f).favored(PlayerClass.WARRIOR).melee();
    public static final DeferredItem<SystemWeaponItem> CRIMSON_COMMANDER_BLADE = w("crimson_commander_blade", CLAYMORE, EPIC).element(DamageElement.CRIMSON, 22).trait(LIFESTEAL, 0.08f).favored(PlayerClass.WARRIOR).melee();
    public static final DeferredItem<SystemWeaponItem> SWARM_KING_TALONS = w("swarm_king_talons", CLAW, EPIC).element(DamageElement.POISON, 16).trait(EXECUTIONER, 0.35f).favored(PlayerClass.ASSASSIN).melee();
    public static final DeferredItem<SystemWeaponItem> FROSTBEAR_GAUNTLETS = w("frostbear_gauntlets", FIST, EPIC).trait(FROSTBITE, 3).trait(BULWARK, 0.05f).favored(PlayerClass.GUARDIAN).melee();
    public static final DeferredItem<SystemWeaponItem> DAWNPETAL_RAPIER = w("dawnpetal_rapier", RAPIER, EPIC).trait(RHYTHM, 2.0f).melee();
    public static final DeferredItem<SystemWeaponItem> EMBER_SAGE_STAFF = w("ember_sage_staff", STAFF, EPIC).element(DamageElement.FIRE, 18).spell(0.3f).favored(PlayerClass.MAGE).melee();
    public static final DeferredItem<SystemWeaponItem> LIFEBLOOM_STAFF = w("lifebloom_staff", STAFF, EPIC).trait(MENDING, 0.35f).spell(0.15f).favored(PlayerClass.HEALER).melee();
    public static final DeferredItem<SystemBowItem> SERPENTSTING_BOW = w("serpentsting_bow", BOW, EPIC).bowPower(1.25f).element(DamageElement.POISON, 18).favored(PlayerClass.RANGER).bow();

    // ---------------------------------------------------------------- legendary
    public static final DeferredItem<SystemWeaponItem> STORMLORD_LONGSWORD = w("stormlord_longsword", SWORD, LEGENDARY).trait(THUNDERCLAP, 0.4f).favored(PlayerClass.WARRIOR).melee();
    public static final DeferredItem<SystemWeaponItem> TITANS_KNUCKLES = w("titans_knuckles", FIST, LEGENDARY).trait(BULWARK, 0.12f).trait(ARMOR_BREAKER, 0.02f).favored(PlayerClass.GUARDIAN).melee();
    public static final DeferredItem<SystemWeaponItem> HELLHOUND_HALBERD = w("hellhound_halberd", HALBERD, LEGENDARY).element(DamageElement.FIRE, 28).trait(EXECUTIONER, 0.25f).melee();
    public static final DeferredItem<SystemWeaponItem> STARSEER_STAFF = w("starseer_staff", STAFF, LEGENDARY).spell(0.45f).trait(THUNDERCLAP, 0.2f).favored(PlayerClass.MAGE).melee();
    public static final DeferredItem<SystemWeaponItem> SAINTS_SUNSTAFF = w("saints_sunstaff", STAFF, LEGENDARY).trait(MENDING, 0.45f).spell(0.2f).favored(PlayerClass.HEALER).melee();
    public static final DeferredItem<SystemBowItem> GALEBREAKER_WARBOW = w("galebreaker_warbow", BOW, LEGENDARY).bowPower(1.4f).trait(SPLIT_SHOT, 2).favored(PlayerClass.RANGER).bow();
    public static final DeferredItem<SystemWeaponItem> NIGHTVEIL_KRIS = w("nightveil_kris", DAGGER, LEGENDARY).trait(BACKSTAB, 0.6f).trait(EXECUTIONER, 0.3f).favored(PlayerClass.ASSASSIN).melee();
    public static final DeferredItem<SystemWeaponItem> IRONBLOOD_CLAYMORE = w("ironblood_claymore", CLAYMORE, LEGENDARY).element(DamageElement.BLOOD, 26).trait(LIFESTEAL, 0.06f).favored(PlayerClass.WARRIOR).melee();

    // ---------------------------------------------------------------- mythic: very scarce, any class
    public static final DeferredItem<SystemWeaponItem> HELLSOVEREIGN_FANG = w("hellsovereign_fang", DAGGER, MYTHIC).element(DamageElement.FIRE, 30).trait(TWIN, 0.35f).signature(Signature.INFERNAL_REND).melee();
    public static final DeferredItem<SystemWeaponItem> WINTERBORNE_REQUIEM = w("winterborne_requiem", SPEAR, MYTHIC).trait(FROSTBITE, 3).signature(Signature.ABSOLUTE_ZERO).melee();
    public static final DeferredItem<SystemWeaponItem> STORMCALLER_EDICT = w("stormcaller_edict", HAMMER, MYTHIC).trait(THUNDERCLAP, 0.45f).signature(Signature.HEAVENS_VERDICT).melee();

    // ---------------------------------------------------------------- divine: one per class, bound to it
    public static final DeferredItem<SystemWeaponItem> RUINBRINGER = w("ruinbringer", CLAYMORE, DIVINE).element(DamageElement.FIRE, 30).trait(LIFESTEAL, 0.05f).favored(PlayerClass.WARRIOR).signature(Signature.CATACLYSM_CLEAVE).melee();
    public static final DeferredItem<SystemWeaponItem> CALAMITY_FANG = w("calamity_fang", DAGGER, DIVINE).element(DamageElement.CRIMSON, 30).trait(BACKSTAB, 0.5f).trait(TWIN, 0.3f).favored(PlayerClass.ASSASSIN).signature(Signature.DRAGONS_WRATH).melee();
    public static final DeferredItem<SystemWeaponItem> EYE_OF_THE_INSATIABLE = w("eye_of_the_insatiable", STAFF, DIVINE).spell(0.8f).trait(THUNDERCLAP, 0.25f).favored(PlayerClass.MAGE).signature(Signature.DEVOURING_STAR).melee();
    public static final DeferredItem<SystemWeaponItem> COLOSSUS_AEGIS = w("colossus_aegis", HAMMER, DIVINE).trait(BULWARK, 0.2f).trait(ARMOR_BREAKER, 0.02f).favored(PlayerClass.GUARDIAN).signature(Signature.COLOSSUS_STANCE).melee();
    public static final DeferredItem<SystemWeaponItem> WORLD_TREE_BRANCH = w("world_tree_branch", STAFF, DIVINE).trait(MENDING, 0.5f).spell(0.3f).favored(PlayerClass.HEALER).signature(Signature.VERDANT_BLESSING).melee();
    public static final DeferredItem<SystemBowItem> STARFALL_LONGBOW = w("starfall_longbow", BOW, DIVINE).bowPower(1.6f).trait(SPLIT_SHOT, 2).trait(THUNDERCLAP, 0.35f).favored(PlayerClass.RANGER).signature(Signature.STARFALL).bow();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = ModItems.TABS.register("arsenal", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.arsenal"))
            .icon(() -> new ItemStack(RUINBRINGER.get()))
            .withTabsBefore(ModItems.TAB.getId())
            .displayItems((params, out) -> everything().stream()
                    .sorted(Comparator.comparingInt(Arsenal::tierOf))
                    .forEach(item -> out.accept(item.get())))
            .build());

    /** Loads the class, so every item above is queued for registration. */
    public static void init() {}

    // ---------------------------------------------------------------- lookups

    /**
     * Every graded weapon that drops in the world: the original eight combat weapons and the whole arsenal. The
     * awakening trial's own legendaries ({@link ModItems#LEGENDARIES}) are kept out of this pool: they only come
     * from killing the Arbiter.
     */
    public static List<DeferredItem<? extends Item>> all() {
        List<DeferredItem<? extends Item>> out = new ArrayList<>(ModItems.WEAPONS);
        out.addAll(ALL);
        return out;
    }

    /** {@link #all()} plus the awakening trial's legendaries. */
    public static List<DeferredItem<? extends Item>> everything() {
        List<DeferredItem<? extends Item>> out = all();
        out.addAll(ModItems.LEGENDARIES);
        return out;
    }

    public static List<DeferredItem<? extends Item>> ofGrade(ItemGrade grade) {
        List<DeferredItem<? extends Item>> out = new ArrayList<>();
        for (var item : all()) if (tierOf(item) == grade.tier()) out.add(item);
        return out;
    }

    private static int tierOf(DeferredItem<? extends Item> item) {
        return item.get() instanceof ArsenalWeapon w ? w.baseGrade().tier() : 0;
    }

    /** The divine weapon bound to a class. */
    public static @Nullable DeferredItem<? extends Item> divineFor(PlayerClass cls) {
        for (var item : BY_GRADE.getOrDefault(DIVINE, List.of())) {
            if (item.get() instanceof ArsenalWeapon w && w.favoredClass() == cls) return item;
        }
        return null;
    }

    /** A random weapon of a grade, or null if there is none. */
    public static @Nullable ItemStack random(ItemGrade grade, RandomSource random) {
        List<DeferredItem<? extends Item>> pool = ofGrade(grade);
        return pool.isEmpty() ? null : new ItemStack(pool.get(random.nextInt(pool.size())).get());
    }

    /** Every System weapon and armor piece gets its grade as a default component. */
    @SubscribeEvent
    static void defaultGrades(ModifyDefaultComponentsEvent event) {
        for (var item : everything()) {
            if (item.get() instanceof ArsenalWeapon w) event.modify(item.get(), b -> b.set(ModComponents.GRADE.get(), w.baseGrade()));
        }
        for (var item : ModItems.ARMOR) {
            SystemArmorItem armor = item.get();
            event.modify(armor, b -> b.set(ModComponents.GRADE.get(), armor.grade()));
        }
    }

    // ---------------------------------------------------------------- building

    public static Tier tier(ItemGrade grade) {
        return TIERS.get(grade);
    }

    private static Rarity rarity(ItemGrade grade) {
        return switch (grade) {
            case COMMON -> Rarity.COMMON;
            case RARE -> Rarity.UNCOMMON;
            case EPIC -> Rarity.RARE;
            default -> Rarity.EPIC;
        };
    }

    private static Builder w(String id, WeaponKind kind, ItemGrade grade) {
        return new Builder(id, kind, grade);
    }

    private static final class Builder {
        final String id;
        final WeaponKind kind;
        final ItemGrade grade;
        DamageElement element = DamageElement.PHYSICAL;
        float buildup;
        float spell;
        float bowPower = 1f;
        final List<Trait> traits = new ArrayList<>();
        @Nullable PlayerClass favored;
        @Nullable Signature signature;

        Builder(String id, WeaponKind kind, ItemGrade grade) {
            this.id = id;
            this.kind = kind;
            this.grade = grade;
        }

        Builder element(DamageElement element, float buildup) {
            this.element = element;
            this.buildup = buildup;
            return this;
        }

        Builder spell(float power) {
            this.spell = power;
            return this;
        }

        Builder bowPower(float power) {
            this.bowPower = power;
            return this;
        }

        Builder trait(Trait.Kind kind, float value) {
            traits.add(Trait.of(kind, value));
            return this;
        }

        Builder favored(PlayerClass cls) {
            this.favored = cls;
            return this;
        }

        Builder signature(Signature signature) {
            this.signature = signature;
            return this;
        }

        Item.Properties props() {
            Item.Properties p = new Item.Properties().rarity(rarity(grade));
            if (grade == DIVINE) p.component(DataComponents.UNBREAKABLE, new Unbreakable(false));
            if (grade.atLeast(LEGENDARY)) p.fireResistant();
            return p;
        }

        DeferredItem<SystemWeaponItem> melee() {
            Tier tier = TIERS.get(grade);
            DeferredItem<SystemWeaponItem> item = ModItems.ITEMS.register(id, () -> {
                ItemAttributeModifiers attributes = SwordItem.createAttributes(tier, kind.damage(), kind.speed());
                if (spell > 0) {
                    attributes = attributes.withModifierAdded(ModAttributes.SPELL_POWER,
                            new AttributeModifier(BeyondSolo.id("weapon.spell_power"), spell, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND);
                }
                return new SystemWeaponItem(tier, attributes, element, buildup, kind.weight(), grade, kind, traits, favored, signature, props());
            });
            add(item);
            return item;
        }

        DeferredItem<SystemBowItem> bow() {
            int durability = 384 + grade.tier() * 300;
            DeferredItem<SystemBowItem> item = ModItems.ITEMS.register(id,
                    () -> new SystemBowItem(grade, bowPower, element, buildup, traits, favored, signature, props().durability(durability)));
            add(item);
            return item;
        }

        private void add(DeferredItem<? extends Item> item) {
            ALL.add(item);
            BY_GRADE.computeIfAbsent(grade, g -> new ArrayList<>()).add(item);
        }
    }

    private Arsenal() {}
}
