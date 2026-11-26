package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The Wizard's Robe and Boots as worn. The shapes and textures are the "Arcane"
 * armour of the Zero Day mod (MIT licence; see ROBE_TEXTURE_CREDITS.txt): an
 * ordinary armour body with bell sleeves, and below it a skirt and leg cuffs.
 * The textures are split so the dye colours the cloth and the gold stays gold.
 * The robe draws its skirt whenever the leg slot is empty, so it hangs to the boots.
 */
public final class WizardRobeLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier UPPER = Omnispell.id("textures/entity/wizard_robe_upper.png");
	private static final Identifier UPPER_TRIM = Omnispell.id("textures/entity/wizard_robe_upper_trim.png");
	private static final Identifier LOWER = Omnispell.id("textures/entity/wizard_robe_lower.png");
	private static final Identifier LOWER_TRIM = Omnispell.id("textures/entity/wizard_robe_lower_trim.png");
	private static final int UNDYED = 0xFFB928FA;
	private static final ModelLayerLocation OUTER = new ModelLayerLocation(Omnispell.id("wizard_robe"), "outer");
	private static final ModelLayerLocation INNER = new ModelLayerLocation(Omnispell.id("wizard_robe"), "inner");
	private static HumanoidModel<HumanoidRenderState> robe, skirt, boots;

	public WizardRobeLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	/** The upper robe and the boots: an armour body with a bell sleeve on each arm. */
	static LayerDefinition outer() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0f), 0f);
		PartDefinition root = mesh.getRoot();
		root.getChild("right_arm").addOrReplaceChild("wizard_sleeve_right", CubeListBuilder.create().texOffs(0, 57).addBox(-3.5f, 5f, -2.5f, 5, 3, 5, new CubeDeformation(0.3f)), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("wizard_sleeve_left", CubeListBuilder.create().texOffs(0, 57).addBox(-1.5f, 5f, -2.5f, 5, 3, 5, new CubeDeformation(0.3f)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The lower robe: a skirt at the waist and a cuff on each leg. */
	static LayerDefinition inner() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5f), 0f);
		PartDefinition root = mesh.getRoot();
		root.getChild("body").addOrReplaceChild("wizard_skirt", CubeListBuilder.create().texOffs(20, 51).addBox(-4.5f, 10f, -2.5f, 9, 6, 5, new CubeDeformation(0.2f)), PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild("wizard_cuff_right", CubeListBuilder.create().texOffs(48, 45).addBox(-2.5f, 5f, -2.5f, 4, 3, 4, new CubeDeformation(0.15f)), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild("wizard_cuff_left", CubeListBuilder.create().texOffs(48, 45).addBox(-1.5f, 5f, -2.5f, 4, 3, 4, new CubeDeformation(0.15f)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static HumanoidModel<HumanoidRenderState> only(HumanoidModel<HumanoidRenderState> model, boolean body, boolean arms, boolean legs) {
		model.head.visible = false;
		model.hat.visible = false;
		model.body.visible = body;
		model.rightArm.visible = arms;
		model.leftArm.visible = arms;
		model.rightLeg.visible = legs;
		model.leftLeg.visible = legs;
		return model;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(OUTER, WizardRobeLayer::outer);
		ModelLayerRegistry.registerModelLayer(INNER, WizardRobeLayer::inner);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (robe == null) {
				robe = only(new HumanoidModel<>(context.bakeLayer(OUTER)), true, true, false);
				skirt = only(new HumanoidModel<>(context.bakeLayer(INNER)), true, false, true);
				boots = only(new HumanoidModel<>(context.bakeLayer(OUTER)), false, false, true);
			}
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				@SuppressWarnings({"unchecked", "rawtypes"})
				RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent = (RenderLayerParent) renderer;
				helper.register(new WizardRobeLayer(parent));
			}
		});
	}

	private static int color(ItemStack stack) {
		DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
		return dyed == null ? UNDYED : 0xFF000000 | dyed.rgb();
	}

	private void draw(HumanoidModel<HumanoidRenderState> model, Identifier base, Identifier trim, PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, int color) {
		renderColoredCutoutModel(model, base, pose, collector, light, state, color, 1);
		renderColoredCutoutModel(model, trim, pose, collector, light, state, -1, 2);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
		if (robe == null) return;
		if (state.chestEquipment != null && state.chestEquipment.is(OmniItems.WIZARD_ROBE)) {
			int color = color(state.chestEquipment);
			draw(robe, UPPER, UPPER_TRIM, pose, collector, light, state, color);
			if (state.legsEquipment == null || state.legsEquipment.isEmpty()) draw(skirt, LOWER, LOWER_TRIM, pose, collector, light, state, color);
		}
	}
}
