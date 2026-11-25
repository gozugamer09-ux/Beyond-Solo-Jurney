package com.beyondsolo.client.duel;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.association.AssociationClient;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.fx.Outline;
import com.beyondsolo.duel.DuelistEntity;
import com.beyondsolo.duel.NationalHunter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A national-level hunter: a person in the player's shape (the skins come from {@code tools/gen_duel_assets.py}, slim
 * arms for those drawn so) with their own weapon, posed by what they are doing (a guard before the bell, the move they
 * are winding up, on one knee once they yield). Unleashed, their power burns off their outline in their own colour, a
 * flame of light like the rulers' ({@link Aura}); the iron giant is simply bigger (their scale). Veiled, the shade is a
 * shimmer and nothing in their hands shows.
 */
public class DuelistRenderer extends HumanoidMobRenderer<DuelistEntity, PlayerModel<DuelistEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[NationalHunter.values().length];
    private static final Aura.Style[] LOOKS = new Aura.Style[NationalHunter.values().length];

    static {
        for (NationalHunter h : NationalHunter.values()) {
            SKINS[h.ordinal()] = BeyondSolo.id("textures/entity/duel/" + h.id + ".png");
            LOOKS[h.ordinal()] = switch (h.style) {
                case TIDE -> new Aura.Style().hue(h.color).light().rim(0.5f, 0.06f).seams(0.25f).flame(0.3f).crest(1.4f).glow(0.55f).lean(0.4f);
                case TITAN -> new Aura.Style().hue(h.color).light().rim(0.6f, 0.07f).seams(0.35f).flame(0.38f).crest(1.6f).glow(0.5f).lean(0.5f);
                case SUN -> new Aura.Style().hue(h.color).light().rim(0.55f, 0.06f).seams(0.25f).flame(0.34f).crest(1.5f).glow(0.6f).lean(0.35f);
                case SHADE -> new Aura.Style().hue(h.color).light().rim(0.5f, 0.05f).seams(0.2f).flame(0.28f).crest(1.3f).glow(0.45f).lean(0.7f);
            };
        }
    }

    private final DuelistModel wide, slim;
    /** Their own outline: the burn is read off the body as it is drawn. */
    private final Outline outline = Outline.create();

    public DuelistRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DuelistModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = (DuelistModel) model;
        slim = new DuelistModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // nothing in their hands shows while a shade is veiled
        layers.removeIf(l -> l instanceof ItemInHandLayer);
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, DuelistEntity e, float limbSwing, float limbSwingAmount,
                               float partial, float age, float netHeadYaw, float headPitch) {
                if (!e.veiled()) super.render(pose, buffers, light, e, limbSwing, limbSwingAmount, partial, age, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(DuelistEntity e) {
        return SKINS[e.who().ordinal()];
    }

    @Override
    protected boolean shouldShowName(DuelistEntity e) {
        // over their head out in the world, not on the portrait in their window (the window writes it under it)
        return !AssociationClient.drawingPortrait() && super.shouldShowName(e);
    }

    @Override
    public void render(DuelistEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = e.who().slim ? slim : wide;
        boolean real = e.level().getEntity(e.getId()) == e;
        boolean burns = real && e.unleashed() && !e.isInvisible() && !AssociationClient.drawingPortrait();
        MultiBufferSource drawn = buffers;
        if (burns) {
            RenderType body = model.renderType(getTextureLocation(e));
            drawn = outline.reset().tap(buffers, type -> type == body);
        }
        super.render(e, yaw, partial, pose, drawn, light);
        if (burns && outline.quads() > 0) {
            float time = (e.tickCount + partial) % 7200;
            Aura.burnWorld(outline, buffers, LOOKS[e.who().ordinal()], e, partial, time, 1f, Math.max(1f, e.getScale()), light);
        }
    }

    /** The player's shape, posed for a duel. */
    static final class DuelistModel extends PlayerModel<DuelistEntity> {
        DuelistModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(DuelistEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
            DuelistEntity.Move m = e.currentMove();
            boolean bow = m != null && switch (m) {
                case SUN_ARROW, TRIPLE_SHOT, SKYFALL, RAIN_OF_DAWN, SUNLANCE -> true;
                default -> false;
            };
            rightArmPose = bow ? HumanoidModel.ArmPose.BOW_AND_ARROW : e.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            leftArmPose = HumanoidModel.ArmPose.EMPTY;
            super.setupAnim(e, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
            byte state = e.state();
            float sway = Mth.sin(age * 0.25f) * 0.08f;
            if (state == DuelistEntity.READY) {
                // a guard before the bell
                rightArm.xRot = -0.55f + sway * 0.3f;
                rightArm.yRot = -0.25f;
                leftArm.xRot = -0.35f;
                leftArm.yRot = 0.3f;
            } else if (state == DuelistEntity.YIELDED) {
                // on one knee, head bowed
                head.xRot = 0.65f;
                rightArm.xRot = -0.15f;
                rightArm.zRot = 0.1f;
                leftArm.xRot = -0.75f;
                leftArm.yRot = 0.35f;
            } else if (state == DuelistEntity.STANDING) {
                // standing over the hunter, weapon lowered
                rightArm.xRot = 0.15f;
                rightArm.zRot = 0.08f;
                leftArm.xRot = 0.05f;
            } else if (state == DuelistEntity.FIGHTING && m != null) {
                pose(m, age, sway);
            }
            hat.copyFrom(head);
            jacket.copyFrom(body);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);
            leftPants.copyFrom(leftLeg);
        }

        /** The move under way, wound up. */
        private void pose(DuelistEntity.Move m, float age, float sway) {
            switch (m) {
                case QUAKE_SLAM, TITAN_STOMP, BOULDER -> {
                    rightArm.xRot = -2.9f + sway;
                    leftArm.xRot = -2.9f + sway;
                    rightArm.zRot = 0.18f;
                    leftArm.zRot = -0.18f;
                }
                case IRON_CHARGE -> {
                    rightArm.xRot = 0.55f;
                    leftArm.xRot = 0.55f;
                    head.xRot = 0.3f;
                }
                case IRON_SKIN, THOUSAND_CUTS -> {
                    rightArm.xRot = -1.25f;
                    rightArm.yRot = -0.7f;
                    leftArm.xRot = -1.25f;
                    leftArm.yRot = 0.7f;
                }
                case TIDE_BOLT -> {
                    rightArm.xRot = -1.5f + head.xRot;
                    rightArm.yRot = head.yRot;
                }
                case RIPTIDE, THUNDERCALL -> {
                    rightArm.xRot = -2.6f + sway;
                    leftArm.xRot = -2.6f - sway;
                    rightArm.zRot = 0.35f;
                    leftArm.zRot = -0.35f;
                }
                case KNIVES -> {
                    rightArm.xRot = -1.6f;
                    rightArm.yRot = -0.35f;
                    leftArm.xRot = -1.4f;
                    leftArm.yRot = 0.35f;
                }
                case SMOKE -> leftArm.xRot = -1.3f;
                case SHADOW_STEP -> {
                    rightArm.xRot = 0.6f;
                    leftArm.xRot = 0.6f;
                }
                case UNLEASH -> {
                    rightArm.zRot = 1.15f + sway;
                    leftArm.zRot = -1.15f - sway;
                    rightArm.xRot = -0.2f;
                    leftArm.xRot = -0.2f;
                    head.xRot = -0.35f;
                }
                default -> {
                }
            }
        }
    }
}
