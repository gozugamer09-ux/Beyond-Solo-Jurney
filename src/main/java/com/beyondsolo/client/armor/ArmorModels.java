package com.beyondsolo.client.armor;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.armory.ArmoryModule;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.registry.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's worn armor models: the vanilla armor boxes plus each set's own extra parts (pauldrons, brims, crests, horns,
 * wings, capes, tassets, toe caps...), read from {@code assets/beyondsolo/models/armor/<set>.json} as written by
 * {@code tools/gen_armory_assets.py}.
 *
 * <p>The textures are 128 pixels wide: the model's UV space is 64 units wide at two pixels a unit, the vanilla boxes
 * in their usual places and the extra parts packed below them; a set with many parts gets a taller texture (the file
 * gives each layer's UV height). Extra parts on the right arm or leg are mirrored onto the left one here; parts on the
 * head and body come with their own mirrored twins in the file.
 *
 * <p>Every armor item of the mod renders with its set's model (found from its material's texture name). NeoForge copies
 * the vanilla armor model's pose and part visibility onto it, so the parts on the head only show with the helmet, those
 * on the body and arms with the chestplate, and so on.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class ArmorModels {
    private static final String DIR = "models/armor";
    /** Set name: outer model (helmet, chestplate, boots: layer 1) and inner model (leggings: layer 2). */
    private static volatile Map<String, HumanoidModel<LivingEntity>[]> models = Map.of();
    private static final Map<Item, String> SET_OF = new IdentityHashMap<>();

    private ArmorModels() {
    }

    static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            HumanoidModel<LivingEntity>[] pair = models.get(setOf(stack.getItem()));
            if (pair == null) return original;
            return pair[slot == EquipmentSlot.LEGS ? 1 : 0];
        }
    };

    /** The set an armor item belongs to, by its material's texture: textures/models/armor/{set}_layer_1.png. */
    static String setOf(Item item) {
        String known = SET_OF.get(item);
        if (known != null) return known;
        String set = "";
        if (item instanceof ArmorItem armor) {
            List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
            if (!layers.isEmpty()) {
                String path = layers.get(0).texture(false).getPath();
                int a = path.lastIndexOf('/') + 1;
                int b = path.lastIndexOf("_layer_");
                if (b > a) set = path.substring(a, b);
            }
        }
        SET_OF.put(item, set);
        return set;
    }

    /** The model for a set, outer (false) or inner (true); null when the set has no model file. */
    public static @Nullable HumanoidModel<LivingEntity> model(String set, boolean inner) {
        HumanoidModel<LivingEntity>[] pair = models.get(set);
        return pair == null ? null : pair[inner ? 1 : 0];
    }

    @SubscribeEvent
    static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new Loader());
    }

    @SubscribeEvent
    static void extensions(RegisterClientExtensionsEvent event) {
        List<Item> items = new ArrayList<>();
        ArmoryModule.all().forEach(piece -> items.add(piece.get()));
        GearModule.SET_PIECES.values().forEach(pieces -> pieces.forEach(piece -> items.add(piece.get())));
        ModItems.ARMOR.forEach(piece -> items.add(piece.get()));
        event.registerItem(EXTENSIONS, items.toArray(Item[]::new));
    }

    // ------------------------------------------------------------------------------------------------ loading

    static final class Loader extends SimplePreparableReloadListener<Map<String, JsonObject>> {
        @Override
        protected Map<String, JsonObject> prepare(ResourceManager resources, ProfilerFiller profiler) {
            Map<String, JsonObject> out = new HashMap<>();
            Map<ResourceLocation, Resource> found = resources.listResources(DIR,
                    id -> id.getNamespace().equals(BeyondSolo.MOD_ID) && id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                String path = e.getKey().getPath();
                String name = path.substring(DIR.length() + 1, path.length() - ".json".length());
                try (Reader reader = e.getValue().openAsReader()) {
                    out.put(name, GsonHelper.parse(reader));
                } catch (Exception ex) {
                    BeyondSolo.LOGGER.warn("Could not read armor model {}: {}", e.getKey(), ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<String, JsonObject> files, ResourceManager resources, ProfilerFiller profiler) {
            Map<String, HumanoidModel<LivingEntity>[]> built = new HashMap<>();
            files.forEach((name, json) -> {
                try {
                    built.put(name, build(json));
                } catch (Exception ex) {
                    BeyondSolo.LOGGER.warn("Could not build armor model {}: {}", name, ex.toString());
                }
            });
            models = built;
        }
    }

    @SuppressWarnings("unchecked")
    static HumanoidModel<LivingEntity>[] build(JsonObject json) {
        JsonObject heights = GsonHelper.getAsJsonObject(json, "texture_height", new JsonObject());
        JsonArray parts = GsonHelper.getAsJsonArray(json, "parts", new JsonArray());
        HumanoidModel<LivingEntity>[] out = new HumanoidModel[2];
        for (int layer = 1; layer <= 2; layer++) {
            MeshDefinition mesh = vanilla(new CubeDeformation(layer == 1 ? 1.0f : 0.5f));
            PartDefinition root = mesh.getRoot();
            for (JsonElement el : parts) {
                JsonObject part = el.getAsJsonObject();
                if (GsonHelper.getAsInt(part, "layer", 1) == layer) add(root, part);
            }
            int height = GsonHelper.getAsInt(heights, String.valueOf(layer), 64);
            out[layer - 1] = new HumanoidModel<>(LayerDefinition.create(mesh, 64, height).bakeRoot());
        }
        return out;
    }

    /** The vanilla armor boxes (HumanoidModel's mesh with the armor legs), arms a hair bigger than the body so their
     * fronts win where the two overlap at the shoulder instead of flickering. */
    static MeshDefinition vanilla(CubeDeformation d) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, d), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, d.extend(0.5f)), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        CubeDeformation arm = d.extend(0.02f);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, arm),
                PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4, arm),
                PartPose.offset(5, 2, 0));
        CubeDeformation leg = d.extend(-0.1f);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, leg),
                PartPose.offset(-1.9f, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4, leg),
                PartPose.offset(1.9f, 12, 0));
        return mesh;
    }

    /** One extra part, hung on its body part; on the right arm or leg, also its mirror image on the left one. */
    static void add(PartDefinition root, JsonObject p) {
        String name = "x_" + GsonHelper.getAsString(p, "name");
        String on = GsonHelper.getAsString(p, "part");
        float[] o = floats(p, "origin");
        float[] s = floats(p, "size");
        float[] uv = floats(p, "uv");
        float inflate = GsonHelper.getAsFloat(p, "inflate", 0f);
        float[] pivot = p.has("pivot") && !p.get("pivot").isJsonNull() ? floats(p, "pivot") : null;
        float[] r = p.has("rotation") ? floats(p, "rotation") : new float[3];
        boolean mirror = GsonHelper.getAsBoolean(p, "mirror", false);
        attach(root.getChild(on), name, o, s, uv, inflate, pivot, r, mirror);
        String twin = switch (on) {
            case "right_arm" -> "left_arm";
            case "right_leg" -> "left_leg";
            default -> null;
        };
        if (twin != null) {
            attach(root.getChild(twin), name, new float[]{-o[0] - s[0], o[1], o[2]}, s, uv, inflate,
                    pivot == null ? null : new float[]{-pivot[0], pivot[1], pivot[2]}, new float[]{r[0], -r[1], -r[2]}, !mirror);
        }
    }

    static void attach(PartDefinition parent, String name, float[] o, float[] s, float[] uv, float inflate, @Nullable float[] pivot,
                       float[] r, boolean mirror) {
        CubeListBuilder cube = CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1]).mirror(mirror)
                .addBox(o[0], o[1], o[2], s[0], s[1], s[2], new CubeDeformation(inflate));
        PartPose pose = pivot == null ? PartPose.ZERO
                : PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], r[0] * Mth.DEG_TO_RAD, r[1] * Mth.DEG_TO_RAD, r[2] * Mth.DEG_TO_RAD);
        parent.addOrReplaceChild(name, cube, pose);
    }

    static float[] floats(JsonObject o, String key) {
        JsonArray a = GsonHelper.getAsJsonArray(o, key);
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }
}
