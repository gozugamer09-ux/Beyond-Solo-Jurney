package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.system.PlayerRank;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rune stone as it is drawn: the stone of its grade, the glyph of its skill (or the seal) and, while it is held to
 * be absorbed, the cracks spreading through it. Put together from twenty small models made by
 * {@code tools/gen_rune_assets.py} ({@code item/rune/stone_<grade>}, {@code glyph_<n>}, {@code crack_<n>}) instead of
 * baking a model for every combination. The glyph and the cracks are full-bright layers, tinted in the skill's colour
 * by {@link RuneClientEvents#colors}.
 */
final class RuneStoneModel extends BakedModelWrapper<BakedModel> {
    static final ModelResourceLocation ITEM = ModelResourceLocation.inventory(BeyondSolo.id("rune_stone"));
    private static final int GRADES = PlayerRank.values().length;
    /** Crack stages after the whole stone: a few hairlines, spreading, about to burst. */
    private static final int CRACKS = 3;
    /** How far into the hold each crack stage shows. */
    private static final float[] CRACK_AT = {0.22f, 0.5f, 0.8f};

    private final BakedModel[] stones, glyphs, cracks;
    private final Combined[] cache = new Combined[GRADES * Runes.GLYPHS * (CRACKS + 1)];
    private final ItemOverrides overrides = new ItemOverrides() {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            int crack = 0;
            if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
                float p = entity.getTicksUsingItem() / (float) Runes.USE_TICKS;
                while (crack < CRACKS && p >= CRACK_AT[crack]) crack++;
            }
            return combined(Runes.gradeOf(stack).tier(), Runes.glyphOf(stack), crack);
        }
    };

    private RuneStoneModel(BakedModel original, BakedModel[] stones, BakedModel[] glyphs, BakedModel[] cracks) {
        super(original);
        this.stones = stones;
        this.glyphs = glyphs;
        this.cracks = cracks;
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private Combined combined(int grade, int glyph, int crack) {
        grade = Mth.clamp(grade, 0, GRADES - 1);
        glyph = Mth.clamp(glyph, 0, Runes.GLYPHS - 1);
        int i = (grade * Runes.GLYPHS + glyph) * (CRACKS + 1) + crack;
        Combined c = cache[i];
        if (c == null) {
            List<BakedModel> parts = new ArrayList<>(3);
            parts.add(stones[grade]);
            parts.add(glyphs[glyph]);
            if (crack > 0) parts.add(cracks[crack - 1]);
            cache[i] = c = new Combined(stones[grade], List.copyOf(parts));
        }
        return c;
    }

    // ---------------------------------------------------------------- loading

    private static ModelResourceLocation part(String name) {
        return ModelResourceLocation.standalone(BeyondSolo.id("item/rune/" + name));
    }

    /** The part models to load besides the item's own. */
    static List<ModelResourceLocation> parts() {
        List<ModelResourceLocation> out = new ArrayList<>();
        for (int g = 0; g < GRADES; g++) out.add(part("stone_" + g));
        for (int g = 0; g < Runes.GLYPHS; g++) out.add(part("glyph_" + g));
        for (int c = 1; c <= CRACKS; c++) out.add(part("crack_" + c));
        return out;
    }

    /** Puts the composed model in place of the item's plain one, once everything is baked. */
    static void install(Map<ModelResourceLocation, BakedModel> models) {
        BakedModel base = models.get(ITEM);
        BakedModel[] stones = collect(models, "stone_", 0, GRADES);
        BakedModel[] glyphs = collect(models, "glyph_", 0, Runes.GLYPHS);
        BakedModel[] cracks = collect(models, "crack_", 1, CRACKS);
        if (base == null || stones == null || glyphs == null || cracks == null) {
            BeyondSolo.LOGGER.warn("Rune stone models are missing; rune stones keep their plain look");
            return;
        }
        models.put(ITEM, new RuneStoneModel(base, stones, glyphs, cracks));
    }

    private static BakedModel @Nullable [] collect(Map<ModelResourceLocation, BakedModel> models, String prefix, int from, int count) {
        BakedModel[] out = new BakedModel[count];
        for (int i = 0; i < count; i++) {
            out[i] = models.get(part(prefix + (from + i)));
            if (out[i] == null) return null;
        }
        return out;
    }

    /**
     * One look: the parts' quads in order (stone, glyph, cracks), each part drawn with its own render types, and the
     * stone's display transforms.
     */
    private static final class Combined extends BakedModelWrapper<BakedModel> {
        private final List<BakedModel> parts;

        Combined(BakedModel stone, List<BakedModel> parts) {
            super(stone);
            this.parts = parts;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            List<BakedQuad> out = new ArrayList<>();
            for (BakedModel m : parts) out.addAll(m.getQuads(state, side, rand));
            return out;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType type) {
            List<BakedQuad> out = new ArrayList<>();
            for (BakedModel m : parts) out.addAll(m.getQuads(state, side, rand, data, type));
            return out;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            List<BakedModel> out = new ArrayList<>();
            for (BakedModel m : parts) out.addAll(m.getRenderPasses(stack, fabulous));
            return out;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            getTransforms().getTransform(context).apply(leftHand, pose);
            return this;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
