package com.omnispell.familiar;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WandItem;
import com.omnispell.mixin.MobGoalsAccessor;
import com.omnispell.realm.Realms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Familiars: found as eggs in the Enchanted Forest, shaped by what the egg is
 * soaked in, hatched into a companion that follows and fights for its owner and
 * can be carried in a pocket.
 */
public final class Familiars {
	/** The four things a familiar can be good at, in the order they are stored. */
	public enum Stat {
		POWER("Power", "Strong", 0xD9483B, "hits harder"),
		VITALITY("Vitality", "Hardy", 0x58B368, "has more health"),
		SWIFTNESS("Swiftness", "Swift", 0xE8C547, "moves and strikes faster"),
		SPIRIT("Spirit", "Wise", 0x5B8DEF, "refills your mana faster while it is out");

		public final String label, leaning, benefit;
		public final int color;

		Stat(String label, String leaning, int color, String benefit) {
			this.label = label;
			this.leaning = leaning;
			this.color = color;
			this.benefit = benefit;
		}

		public static Stat of(String name) {
			for (Stat s : values()) if (s.name().equalsIgnoreCase(name)) return s;
			return null;
		}
	}

	/** bodies: the creatures that can play this familiar, best first; ones from mods that are not installed are skipped. */
	public record Species(String id, String name, Stat gift, double scale, List<String> bodies) {
	}

	public static final Map<String, Species> SPECIES = new LinkedHashMap<>();
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.entity.EntityType<?>> TYPE_KEY =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Omnispell.id("familiar"));
	public static final net.minecraft.world.entity.EntityType<FamiliarEntity> TYPE = net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE, TYPE_KEY,
			net.minecraft.world.entity.EntityType.Builder.<FamiliarEntity>of(FamiliarEntity::new, net.minecraft.world.entity.MobCategory.CREATURE)
					.sized(0.7f, 0.8f).eyeHeight(0.6f).clientTrackingRange(10).build(TYPE_KEY));
	public static final int MAX_SOAKS = 8;
	public static final int SOAK_POINTS = 2;
	public static final long SOAK_INTERVAL = 6000; // five minutes
	public static final long REST = 6000;
	private static final String TAG = "omnispell_familiar";
	private static final String DATA_PREFIX = "omnifam|";

	static {
		species("griffin", "Griffin", Stat.SWIFTNESS, 1.3, "naturalist:vulture", "minecraft:parrot");
		species("phoenix", "Phoenix", Stat.SPIRIT, 1.0, "naturalist:bird", "minecraft:parrot");
		species("drake", "Drake", Stat.POWER, 1.1, "uselessreptile:river_pikehorn", "naturalist:lizard", "minecraft:ocelot");
		species("wisp", "Wisp", Stat.SPIRIT, 0.8, "friendsandfoes:glare", "minecraft:bee");
		species("stag", "Stag", Stat.SWIFTNESS, 1.2, "naturalist:deer", "minecraft:fox");
		species("kodama", "Kodama", Stat.SPIRIT, 0.8, "aerialhell:kodama", "minecraft:fox");
		species("direwolf", "Direwolf", Stat.POWER, 1.25, "minecraft:wolf");
		species("tortoise", "Tortoise", Stat.VITALITY, 1.1, "naturalist:tortoise", "minecraft:turtle");
	}

	private static void species(String id, String name, Stat gift, double scale, String... bodies) {
		SPECIES.put(id, new Species(id, name, gift, scale, List.of(bodies)));
	}

	/** A familiar that is out in the world. */
	private static final class Active {
		final Mob mob;
		final UUID owner;
		final Species species;
		final int[] stats;
		int cooldown;

		Active(Mob mob, UUID owner, Species species, int[] stats) {
			this.mob = mob;
			this.owner = owner;
			this.species = species;
			this.stats = stats;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private Familiars() {
	}

	// ------------------------------------------------------------------ eggs

	/** primary and secondary may be null: an egg with no primary is a blank egg. */
	public static ItemStack egg(String speciesId, Stat primary, Stat secondary) {
		Species species = SPECIES.getOrDefault(speciesId, SPECIES.get("griffin"));
		if (primary == null) secondary = null;
		if (secondary == primary) secondary = null;
		ItemStack stack = new ItemStack(OmniItems.FAMILIAR_EGG);
		CompoundTag tag = new CompoundTag();
		tag.putString("species", species.id());
		tag.putString("primary", primary == null ? "" : primary.name());
		tag.putString("secondary", secondary == null ? "" : secondary.name());
		tag.putString("soaks", "0,0,0,0");
		tag.putLong("last_soak", -1);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_NAME, Component.literal((primary == null ? "Blank" : primary.leaning) + " " + species.name() + " Egg"));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(),
				List.of(primary == null ? 0xECE6DA : primary.color, secondary == null ? 0x9A948A : secondary.color)));
		return stack;
	}

	/** A random egg as the forest gives them: one in four is blank, and most of the rest have a second leaning. */
	public static ItemStack randomEgg(RandomSource random) {
		List<String> ids = List.copyOf(SPECIES.keySet());
		String species = ids.get(random.nextInt(ids.size()));
		if (random.nextInt(4) == 0) return egg(species, null, null);
		Stat primary = Stat.values()[random.nextInt(4)];
		Stat secondary = random.nextInt(3) == 0 ? null : Stat.values()[random.nextInt(4)];
		return egg(species, primary, secondary);
	}

	public static boolean isEgg(ItemStack stack) {
		return stack.is(OmniItems.FAMILIAR_EGG);
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static int[] ints(String text) {
		int[] out = new int[4];
		String[] parts = text.split(",");
		for (int i = 0; i < 4 && i < parts.length; i++) {
			try {
				out[i] = Integer.parseInt(parts[i].trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return out;
	}

	private static String text(int[] values) {
		return values[0] + "," + values[1] + "," + values[2] + "," + values[3];
	}

	public static int[] soaks(ItemStack egg) {
		return ints(tag(egg).getStringOr("soaks", "0,0,0,0"));
	}

	private static int sum(int[] values) {
		return values[0] + values[1] + values[2] + values[3];
	}

	/** What the egg is worth right now: its nature, its species' gift, and every soak so far. */
	public static int[] eggStats(ItemStack egg) {
		CompoundTag tag = tag(egg);
		Species species = SPECIES.getOrDefault(tag.getStringOr("species", ""), SPECIES.get("griffin"));
		Stat primary = Stat.of(tag.getStringOr("primary", ""));
		Stat secondary = Stat.of(tag.getStringOr("secondary", ""));
		int[] stats = {1, 1, 1, 1};
		if (primary != null) stats[primary.ordinal()] += 3;
		if (secondary != null) stats[secondary.ordinal()] += 1;
		stats[species.gift().ordinal()] += 2;
		int[] soaks = soaks(egg);
		for (int i = 0; i < 4; i++) stats[i] += soaks[i] * SOAK_POINTS;
		return stats;
	}

	/**
	 * What the egg hatches with. Soaks never taken are not lost: if the egg was
	 * soaked at all they go to its highest stat; if it never was, they are split
	 * 60/40 between its primary and secondary leaning.
	 */
	public static int[] hatchStats(ItemStack egg) {
		CompoundTag tag = tag(egg);
		int[] stats = eggStats(egg);
		int used = sum(soaks(egg));
		int spare = Math.max(0, MAX_SOAKS - used) * SOAK_POINTS;
		Stat primary = Stat.of(tag.getStringOr("primary", ""));
		Stat secondary = Stat.of(tag.getStringOr("secondary", ""));
		int highest = 0;
		for (int i = 1; i < 4; i++) if (stats[i] > stats[highest]) highest = i;
		if (used == 0 && primary != null && secondary != null) {
			int first = Math.round(spare * 0.6f);
			stats[primary.ordinal()] += first;
			stats[secondary.ordinal()] += spare - first;
		} else {
			stats[used == 0 && primary != null ? primary.ordinal() : highest] += spare;
		}
		return stats;
	}

	/** Which stat a block soaks an egg in, or null if it is not something to soak in. */
	public static Stat soakOf(BlockState state) {
		if (state.is(Blocks.LAVA_CAULDRON)) return Stat.POWER;
		if (state.is(Blocks.POWDER_SNOW_CAULDRON)) return Stat.VITALITY;
		if (state.is(Blocks.WATER_CAULDRON)) return Stat.SWIFTNESS;
		if (state.is(OmniItems.ARCANE_WELL)) return Stat.SPIRIT;
		return null;
	}

	/** Soaks the egg. Returns a message for the player; changed[0] says whether the soak took. */
	public static String soak(ItemStack egg, Stat stat, long gameTime, boolean[] changed) {
		CompoundTag tag = tag(egg);
		int[] soaks = soaks(egg);
		if (sum(soaks) >= MAX_SOAKS) return "The shell has taken all it can. It is ready to hatch.";
		long last = tag.getLongOr("last_soak", -1);
		if (last >= 0 && gameTime - last < SOAK_INTERVAL && gameTime >= last) {
			long seconds = (SOAK_INTERVAL - (gameTime - last)) / 20;
			return "The shell is still drinking in the last soak. Try again in " + (seconds >= 60 ? (seconds / 60 + 1) + " minutes." : seconds + " seconds.");
		}
		soaks[stat.ordinal()]++;
		CustomData.update(DataComponents.CUSTOM_DATA, egg, t -> {
			t.putString("soaks", text(soaks));
			t.putLong("last_soak", gameTime);
		});
		changed[0] = true;
		return "The egg drinks it in. " + stat.label + " +" + SOAK_POINTS + "  (soak " + sum(soaks) + " of " + MAX_SOAKS + ")";
	}

	public static void describeEgg(ServerPlayer player, ItemStack egg) {
		int[] now = eggStats(egg);
		int[] hatch = hatchStats(egg);
		int used = sum(soaks(egg));
		player.sendSystemMessage(Component.empty().append(egg.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("  soaks " + used + "/" + MAX_SOAKS).withStyle(ChatFormatting.GRAY)));
		for (Stat stat : Stat.values()) {
			player.sendSystemMessage(Component.literal("  " + stat.label + " " + now[stat.ordinal()]).withColor(stat.color)
					.append(Component.literal(hatch[stat.ordinal()] != now[stat.ordinal()] ? "  (would hatch with " + hatch[stat.ordinal()] + ")" : "").withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	// ------------------------------------------------------------ pocketing

	/** The item a familiar becomes when it is carried. */
	public static ItemStack pocketed(Species species, int[] stats, String name, long restUntil) {
		ItemStack stack = new ItemStack(OmniItems.FAMILIAR);
		CompoundTag tag = new CompoundTag();
		tag.putString("species", species.id());
		tag.putString("stats", text(stats));
		tag.putString("name", name == null ? "" : name);
		tag.putLong("rest_until", restUntil);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_NAME, Component.literal(name == null || name.isEmpty() ? species.name() : name));
		int best = 0;
		for (int i = 1; i < 4; i++) if (stats[i] > stats[best]) best = i;
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(Stat.values()[best].color)));
		return stack;
	}

	public static int[] pocketStats(ItemStack stack) {
		return ints(tag(stack).getStringOr("stats", "1,1,1,1"));
	}

	public static Species pocketSpecies(ItemStack stack) {
		return SPECIES.getOrDefault(tag(stack).getStringOr("species", ""), SPECIES.get("griffin"));
	}

	public static void describe(ServerPlayer player, String name, int[] stats, float health, float maxHealth) {
		player.sendSystemMessage(Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal(maxHealth > 0 ? "  health " + Math.round(health) + "/" + Math.round(maxHealth) : "").withStyle(ChatFormatting.GRAY)));
		for (Stat stat : Stat.values()) {
			player.sendSystemMessage(Component.literal("  " + stat.label + " " + stats[stat.ordinal()] + "  ").withColor(stat.color)
					.append(Component.literal(stat.benefit).withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	// ------------------------------------------------------------- the body

	public static Mob familiarOf(ServerPlayer player) {
		for (Active active : ACTIVE.values()) {
			if (active.owner.equals(player.getUUID()) && active.mob.isAlive()) return active.mob;
		}
		return null;
	}

	public static boolean isFamiliar(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	/** Brings a familiar into the world beside its owner. Returns null if no body could be made. */
	public static Mob release(ServerPlayer owner, Species species, int[] stats, String name, Vec3 at) {
		ServerLevel level = owner.level();
		FamiliarEntity mob = TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) return null;
		mob.snapTo(at.x, at.y, at.z, owner.getYRot(), 0);
		mob.addTag(TAG);
		mob.addTag(DATA_PREFIX + owner.getUUID() + "|" + species.id() + "|" + text(stats));
		mob.setCustomName(Component.literal(name == null || name.isEmpty() ? owner.getGameProfile().name() + "'s " + species.name() : name).withStyle(ChatFormatting.LIGHT_PURPLE));
		mob.setPersistenceRequired();
		adopt(mob, owner.getUUID(), species, stats);
		mob.setHealth(mob.getMaxHealth());
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.5, at.z, 20, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.2f);
		return mob;
	}

	private static void adopt(Mob mob, UUID owner, Species species, int[] stats) {
		if (mob instanceof FamiliarEntity familiar) familiar.setSpecies(species.id());
		((MobGoalsAccessor) mob).omnispell$targetSelector().removeAllGoals(goal -> true);
		mob.setTarget(null);
		set(mob.getAttribute(Attributes.SCALE), species.scale());
		set(mob.getAttribute(Attributes.MAX_HEALTH), 16 + 3.0 * stats[Stat.VITALITY.ordinal()]);
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), 32);
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !mob.entityTags().contains(TAG + "_speed")) {
			mob.addTag(TAG + "_speed");
			speed.setBaseValue(Math.max(0.25, speed.getBaseValue()) * (1.0 + 0.04 * stats[Stat.SWIFTNESS.ordinal()]));
		}
		ACTIVE.put(mob.getUUID(), new Active(mob, owner, species, stats));
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) attribute.setBaseValue(value);
	}

	/** A familiar loaded from the world picks up where it left off. */
	public static void onEntityLoad(Entity entity) {
		if (!(entity instanceof Mob mob) || !isFamiliar(entity) || ACTIVE.containsKey(entity.getUUID())) return;
		for (String tag : entity.entityTags()) {
			if (!tag.startsWith(DATA_PREFIX)) continue;
			String[] parts = tag.split("\\|");
			if (parts.length < 4) continue;
			try {
				adopt(mob, UUID.fromString(parts[1]), SPECIES.getOrDefault(parts[2], SPECIES.get("griffin")), ints(parts[3]));
			} catch (IllegalArgumentException ignored) {
			}
			return;
		}
	}

	/** Turns a familiar back into something carried, and hands it to its owner. */
	public static void pocket(ServerPlayer owner, Mob mob, long restUntil) {
		Active active = ACTIVE.remove(mob.getUUID());
		if (active == null) return;
		String name = mob.getCustomName() == null ? "" : mob.getCustomName().getString();
		ItemStack stack = pocketed(active.species, active.stats, name, restUntil);
		if (mob.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY() + 0.5, mob.getZ(), 16, 0.3, 0.4, 0.3, 0.02);
		mob.discard();
		if (owner != null) {
			if (!owner.getInventory().add(stack)) owner.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		} else {
			ItemEntity item = new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), stack);
			mob.level().addFreshEntity(item);
		}
	}

	/** A familiar never truly dies: it returns to its owner, spent, and must rest before it comes out again. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		Active active = ACTIVE.remove(entity.getUUID());
		if (active == null || !(entity.level() instanceof ServerLevel level)) return;
		String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
		ItemStack stack = pocketed(active.species, active.stats, name, level.getGameTime() + REST);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(active.owner);
		if (owner != null) {
			if (!owner.getInventory().add(stack)) owner.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			owner.sendSystemMessage(Component.literal(name + " is spent and has returned to you. It must rest for five minutes.").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack));
		}
	}

	/** A familiar is never hurt by its own owner, and never hurts them. */
	public static boolean spared(LivingEntity entity, DamageSource source) {
		Active active = ACTIVE.get(entity.getUUID());
		return active != null && source.getEntity() != null && source.getEntity().getUUID().equals(active.owner);
	}

	/** Mana refill bonus from a familiar's Spirit while it is out. */
	public static double spiritPercent(ServerPlayer player) {
		for (Active active : ACTIVE.values()) {
			if (active.owner.equals(player.getUUID()) && active.mob.isAlive()) return 1.5 * active.stats[Stat.SPIRIT.ordinal()];
		}
		return 0;
	}

	/** Called every half second. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (Active active : List.copyOf(ACTIVE.values())) {
			Mob mob = active.mob;
			if (mob.isRemoved()) {
				if (!mob.isAlive() || mob.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) ACTIVE.remove(mob.getUUID());
				else ACTIVE.remove(mob.getUUID()); // it will be adopted again when its chunk loads
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(active.owner);
			if (owner == null || !owner.isAlive()) {
				mob.getNavigation().stop();
				continue;
			}
			if (owner.level() != mob.level()) {
				pocket(owner, mob, 0); // it will not be left behind in another realm
				continue;
			}
			ServerLevel level = owner.level();
			if (mob.getTarget() != null) mob.setTarget(null);
			if (active.cooldown > 0) active.cooldown -= 10;
			if (mob.tickCount % 100 < 10 && mob.getHealth() < mob.getMaxHealth()) mob.heal(1.0f);
			if (active.species.id().equals("phoenix")) {
				mob.clearFire();
				level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			} else if (active.species.id().equals("wisp")) {
				level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}

			LivingEntity foe = foe(owner, mob);
			double speed = 1.25;
			if (foe != null) {
				double reach = 2.2 + mob.getBbWidth() / 2 + foe.getBbWidth() / 2;
				if (mob.distanceToSqr(foe) <= reach * reach) {
					mob.getLookControl().setLookAt(foe);
					if (active.cooldown <= 0) {
						active.cooldown = Math.max(8, 22 - active.stats[Stat.SWIFTNESS.ordinal()]);
						float damage = (float) (2.0 + 0.6 * active.stats[Stat.POWER.ordinal()]);
						foe.hurtServer(level, level.damageSources().mobAttack(mob), damage);
						if (active.species.id().equals("phoenix")) foe.igniteForSeconds(3);
					}
				} else {
					mob.getNavigation().moveTo(foe, speed);
				}
				continue;
			}
			double far = mob.distanceToSqr(owner);
			if (far > 24 * 24) {
				mob.snapTo(owner.getX(), owner.getY(), owner.getZ(), mob.getYRot(), 0);
				mob.getNavigation().stop();
			} else if (far > 5 * 5) {
				mob.getNavigation().moveTo(owner, speed);
			}
		}
	}

	/** Whatever last hurt the owner, or whatever the owner last struck, if it is still near. */
	private static LivingEntity foe(ServerPlayer owner, Mob familiar) {
		LivingEntity[] candidates = {owner.getLastHurtByMob(), owner.getLastHurtMob()};
		for (LivingEntity candidate : candidates) {
			if (candidate == null || !candidate.isAlive() || candidate == familiar || candidate instanceof Player) continue;
			if (isFamiliar(candidate) || candidate.level() != owner.level() || candidate.distanceToSqr(owner) > 24 * 24) continue;
			return candidate;
		}
		return null;
	}

	public static void clear() {
		ACTIVE.clear();
	}

	// ------------------------------------------------------------- handlers

	public static void register() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TYPE, FamiliarEntity.attributes());
		// Soaking: an egg held against a cauldron or an arcane well.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!isEgg(held) || player.isShiftKeyDown()) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Stat stat = soakOf(state);
			if (stat == null) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
			ItemStack one = held;
			boolean[] changed = {false};
			String message = soak(one, stat, level.getGameTime(), changed);
			serverPlayer.sendOverlayMessage(Component.literal(message));
			if (changed[0]) {
				if (state.is(Blocks.WATER_CAULDRON)) LayeredCauldronBlock.lowerFillLevel(state, level, pos);
				else if (!state.is(OmniItems.ARCANE_WELL)) level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
				serverPlayer.level().sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			return InteractionResult.SUCCESS;
		});

		// A wand shows a familiar's stats; sneaking with an empty hand picks it up.
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			Active active = ACTIVE.get(entity.getUUID());
			if (active == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
			ItemStack held = player.getItemInHand(hand);
			if (WandItem.isWand(held)) {
				describe(serverPlayer, active.mob.getCustomName() == null ? active.species.name() : active.mob.getCustomName().getString(), active.stats, active.mob.getHealth(), active.mob.getMaxHealth());
				return InteractionResult.SUCCESS;
			}
			if (!active.owner.equals(player.getUUID())) return InteractionResult.PASS;
			if (player.isShiftKeyDown() && held.isEmpty()) {
				pocket(serverPlayer, active.mob, 0);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Eggs lie hidden in the faewood canopy.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!state.is(OmniItems.FAEWOOD_LEAVES) || !Realms.isForest(level) || player.getAbilities().instabuild) return;
			if (level.getRandom().nextFloat() < 0.02f) {
				ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, randomEgg(level.getRandom()));
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		});
	}
}
