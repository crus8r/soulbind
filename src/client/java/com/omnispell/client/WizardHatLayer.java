package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The Wizard's Hat as it is worn: a wide brim and a tall cone that leans back a
 * little, sitting on top of the head. The cone takes the hat's dye; the band and
 * stars stay gold. The texture layout here must match tools/gen_assets.py.
 */
public final class WizardHatLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Omnispell.id("wizard_hat"), "main");
	private static final Identifier BASE = Omnispell.id("textures/entity/wizard_hat.png");
	private static final Identifier TRIM = Omnispell.id("textures/entity/wizard_hat_trim.png");
	public static final int UNDYED = 0xFF5E8F74; // a green to sit with the robe's, so an undyed set matches
	private static Model<Object> model;

	public WizardHatLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation snug = new CubeDeformation(0.05f);
		root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -8.8f, -7f, 14, 1, 14, snug), PartPose.ZERO);
		PartDefinition cone = root.addOrReplaceChild("cone", CubeListBuilder.create().texOffs(0, 15).addBox(-4.5f, -4f, -4.5f, 9, 4, 9, snug), PartPose.offsetAndRotation(0f, -8.8f, 0f, -0.06f, 0f, 0f));
		PartDefinition mid = cone.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(0, 29).addBox(-3.5f, -4f, -3.5f, 7, 4, 7), PartPose.offsetAndRotation(0f, -4f, 0.3f, -0.14f, 0f, 0f));
		PartDefinition upper = mid.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(28, 29).addBox(-2.5f, -4f, -2.5f, 5, 4, 5), PartPose.offsetAndRotation(0f, -4f, 0.4f, -0.22f, 0f, 0f));
		PartDefinition top = upper.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 41).addBox(-1.5f, -4f, -1.5f, 3, 4, 3), PartPose.offsetAndRotation(0f, -4f, 0.4f, -0.32f, 0f, 0f));
		top.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(14, 41).addBox(-0.5f, -3f, -0.5f, 1, 3, 1), PartPose.offsetAndRotation(0f, -4f, 0.3f, -0.5f, 0f, 0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, WizardHatLayer::layer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (model == null) model = new Model<>(context.bakeLayer(LAYER), RenderTypes::entityCutout) { };
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				@SuppressWarnings({"unchecked", "rawtypes"})
				RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent = (RenderLayerParent) renderer;
				helper.register(new WizardHatLayer(parent));
			}
		});
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
		if (state.headEquipment == null || !state.headEquipment.is(OmniItems.WIZARD_HAT) || model == null) return;
		DyedItemColor dyed = state.headEquipment.get(DataComponents.DYED_COLOR);
		int color = dyed == null ? UNDYED : 0xFF000000 | dyed.rgb();
		pose.pushPose();
		getParentModel().getHead().translateAndRotate(pose);
		renderColoredCutoutModel(model, BASE, pose, collector, light, state, color, 1);
		renderColoredCutoutModel(model, TRIM, pose, collector, light, state, -1, 2);
		pose.popPose();
	}
}
