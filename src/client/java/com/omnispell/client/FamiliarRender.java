package com.omnispell.client;

import com.omnispell.Omnispell;
import com.omnispell.familiar.FamiliarEntity;
import com.omnispell.familiar.Familiars;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * How a familiar looks. Every species shares one skeleton (body, head, four
 * legs, wings, tail, and a few extras); each shows the parts it needs, in its
 * own proportions, under its own skin. The texture layout here must match
 * tools/gen_assets.py.
 */
public final class FamiliarRender {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Omnispell.id("familiar"), "main");

	private FamiliarRender() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, FamiliarRender::layer);
		EntityRendererRegistry.register(Familiars.TYPE, Renderer::new);
	}

	public static final class State extends LivingEntityRenderState {
		public String species = "griffin";
	}

	static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -2.5f, -5f, 6f, 5f, 10f), PartPose.offset(0f, 14.5f, 0f));
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-2.5f, -2.5f, -5f, 5f, 5f, 5f), PartPose.offset(0f, 11f, -5f));
		head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(20, 16).addBox(-1.5f, 0f, -8f, 3f, 2f, 3f), PartPose.ZERO);
		head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(32, 16).addBox(-1f, -0.5f, -8f, 2f, 2f, 3f), PartPose.ZERO);
		head.addOrReplaceChild("ears", CubeListBuilder.create().texOffs(44, 16).addBox(-2.5f, -4.5f, -2f, 2f, 2f, 1f).addBox(0.5f, -4.5f, -2f, 2f, 2f, 1f), PartPose.ZERO);
		head.addOrReplaceChild("horns", CubeListBuilder.create().texOffs(52, 16).addBox(-2.5f, -6.5f, -1f, 1f, 4f, 1f).addBox(1.5f, -6.5f, -1f, 1f, 4f, 1f), PartPose.rotation(-0.35f, 0f, 0f));
		head.addOrReplaceChild("antlers", CubeListBuilder.create().texOffs(56, 16).addBox(-4.5f, -7.5f, -1f, 2f, 5f, 1f).addBox(2.5f, -7.5f, -1f, 2f, 5f, 1f), PartPose.ZERO);
		head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(20, 22).addBox(-0.5f, -5.5f, -3f, 1f, 3f, 4f), PartPose.ZERO);
		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 27).addBox(-1f, 0f, -1f, 2f, 7f, 2f);
		root.addOrReplaceChild("leg_front_left", leg, PartPose.offset(2f, 17f, -4f));
		root.addOrReplaceChild("leg_front_right", leg, PartPose.offset(-2f, 17f, -4f));
		root.addOrReplaceChild("leg_back_left", leg, PartPose.offset(2f, 17f, 4f));
		root.addOrReplaceChild("leg_back_right", leg, PartPose.offset(-2f, 17f, 4f));
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 37).addBox(0f, -0.5f, -3f, 9f, 1f, 7f), PartPose.offset(3f, 12.5f, -1f));
		root.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 37).mirror().addBox(-9f, -0.5f, -3f, 9f, 1f, 7f), PartPose.offset(-3f, 12.5f, -1f));
		root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 0).addBox(-1f, -1f, 0f, 2f, 2f, 7f), PartPose.offset(0f, 13f, 5f));
		root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 46).addBox(-4f, -4.5f, -5.5f, 8f, 4f, 11f), PartPose.offset(0f, 14.5f, 0f));
		root.addOrReplaceChild("orb", CubeListBuilder.create().texOffs(40, 28).addBox(-3f, -3f, -3f, 6f, 6f, 6f), PartPose.offset(0f, 10f, 0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static final class Model extends EntityModel<State> {
		private final ModelPart body, head, snout, beak, ears, horns, antlers, crest, wingLeft, wingRight, tail, shell, orb;
		private final ModelPart[] legs;

		public Model(ModelPart root) {
			super(root);
			body = root.getChild("body");
			head = root.getChild("head");
			snout = head.getChild("snout");
			beak = head.getChild("beak");
			ears = head.getChild("ears");
			horns = head.getChild("horns");
			antlers = head.getChild("antlers");
			crest = head.getChild("crest");
			legs = new ModelPart[]{root.getChild("leg_front_left"), root.getChild("leg_front_right"), root.getChild("leg_back_left"), root.getChild("leg_back_right")};
			wingLeft = root.getChild("wing_left");
			wingRight = root.getChild("wing_right");
			tail = root.getChild("tail");
			shell = root.getChild("shell");
			orb = root.getChild("orb");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			resetPose();
			String species = state.species == null ? "griffin" : state.species;
			boolean wisp = species.equals("wisp");
			boolean bird = species.equals("phoenix");
			boolean winged = species.equals("griffin") || bird || species.equals("drake");

			body.visible = !wisp;
			head.visible = !wisp;
			tail.visible = !wisp && !species.equals("kodama");
			orb.visible = wisp;
			shell.visible = species.equals("tortoise");
			wingLeft.visible = winged;
			wingRight.visible = winged;
			snout.visible = species.equals("direwolf") || species.equals("stag") || species.equals("drake");
			beak.visible = species.equals("griffin") || bird;
			ears.visible = species.equals("direwolf") || species.equals("griffin") || species.equals("stag");
			horns.visible = species.equals("drake");
			antlers.visible = species.equals("stag");
			crest.visible = bird;
			for (int i = 0; i < 4; i++) legs[i].visible = !wisp && !(bird && i < 2);

			// Proportions: how long the legs are decides how high everything else rides.
			float legScale = switch (species) {
				case "stag" -> 1.45f;
				case "kodama" -> 0.6f;
				case "tortoise" -> 0.45f;
				default -> 1f;
			};
			float lift = (legScale - 1f) * 7f; // pixels the body rises (negative: sinks)
			for (ModelPart part : new ModelPart[]{body, head, wingLeft, wingRight, tail, shell}) part.y -= lift;
			for (ModelPart part : legs) {
				part.y -= lift;
				part.yScale = legScale;
			}
			switch (species) {
				case "kodama" -> {
					head.xScale = head.yScale = head.zScale = 1.4f;
					head.y -= 1f;
					body.zScale = 0.7f;
					head.z += 1.5f;
					for (int i = 0; i < 4; i++) legs[i].z *= 0.7f;
				}
				case "tortoise" -> {
					head.xScale = head.yScale = head.zScale = 0.8f;
					head.y += 1.5f;
					tail.zScale = 0.35f;
					for (ModelPart part : legs) part.xScale = part.zScale = 1.3f;
				}
				case "phoenix" -> {
					tail.zScale = 1.9f;
					tail.xScale = 1.6f;
					legs[2].z = 1f;
					legs[3].z = 1f;
					body.xRot = -0.25f;
					wingLeft.xScale = wingRight.xScale = 1.15f;
				}
				case "drake" -> {
					tail.zScale = 1.7f;
					wingLeft.xScale = wingRight.xScale = 0.85f;
					head.zScale = 1.15f;
				}
				case "stag" -> tail.zScale = 0.35f;
				case "griffin" -> wingLeft.xScale = wingRight.xScale = 1.2f;
				default -> {
				}
			}

			// Movement.
			float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.2f * Math.min(1f, state.walkAnimationSpeed);
			legs[0].xRot = swing;
			legs[1].xRot = -swing;
			legs[2].xRot = -swing;
			legs[3].xRot = swing;
			head.yRot = state.yRot * Mth.DEG_TO_RAD;
			head.xRot = state.xRot * Mth.DEG_TO_RAD;
			float moving = Math.min(1f, state.walkAnimationSpeed);
			float flap = Mth.sin(state.ageInTicks * (0.15f + 0.35f * moving));
			float fold = bird ? 0.35f : 0.9f; // a griffin and a drake hold their wings half folded; a phoenix holds them out
			wingLeft.zRot = -fold * (1f - moving) - flap * (0.15f + 0.45f * moving) - (bird ? 0.2f : 0f);
			wingRight.zRot = -wingLeft.zRot;
			tail.xRot = (species.equals("direwolf") ? -0.2f : bird ? 0.35f : -0.45f) + Mth.sin(state.ageInTicks * 0.1f) * 0.06f;
			tail.yRot = Mth.sin(state.ageInTicks * (species.equals("direwolf") ? 0.4f : 0.12f)) * (species.equals("direwolf") ? 0.35f : 0.12f);
			if (wisp) {
				orb.y += Mth.sin(state.ageInTicks * 0.12f) * 1.5f - 3f;
				orb.yRot = state.ageInTicks * 0.05f;
			}
		}
	}

	public static final class Renderer extends MobRenderer<FamiliarEntity, State, Model> {
		public Renderer(EntityRendererProvider.Context context) {
			super(context, new Model(context.bakeLayer(LAYER)), 0.35f);
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(FamiliarEntity entity, State state, float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.species = entity.species();
		}

		@Override
		public Identifier getTextureLocation(State state) {
			String species = Familiars.SPECIES.containsKey(state.species) ? state.species : "griffin";
			return Omnispell.id("textures/entity/familiar/" + species + ".png");
		}
	}
}
