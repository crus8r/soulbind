package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The Wizard's Robe as worn: the robe of "Spells and whatnot" by Qsenja (MIT
 * licence; see ROBE_TEXTURE_CREDITS.txt), its body and sleeves over the body and
 * arms and its long skirt down the legs to the boots. The textures are split so
 * the dye colours the cloth while the gem, belt and fringe keep their colours.
 * The skirt is drawn whenever the leg slot is empty.
 */
public final class WizardRobeLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier UPPER = Omnispell.id("textures/entity/wizard_robe_upper.png");
	private static final Identifier UPPER_TRIM = Omnispell.id("textures/entity/wizard_robe_upper_trim.png");
	private static final Identifier LOWER = Omnispell.id("textures/entity/wizard_robe_lower.png");
	private static final Identifier LOWER_TRIM = Omnispell.id("textures/entity/wizard_robe_lower_trim.png");
	private static final int UNDYED = 0xFF9FC997;
	private static final ModelLayerLocation OUTER = new ModelLayerLocation(Omnispell.id("wizard_robe"), "outer");
	private static final ModelLayerLocation INNER = new ModelLayerLocation(Omnispell.id("wizard_robe"), "inner");
	private static HumanoidModel<HumanoidRenderState> robe, skirt;

	public WizardRobeLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	/** The upper robe: an armour body and sleeves. */
	static LayerDefinition outer() {
		return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.0f), 0f), 64, 64);
	}

	/** The lower robe: a long skirt piece over each leg. */
	static LayerDefinition inner() {
		return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.55f), 0f), 64, 64);
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
				skirt = only(new HumanoidModel<>(context.bakeLayer(INNER)), false, false, true);
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
