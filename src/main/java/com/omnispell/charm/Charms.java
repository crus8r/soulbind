package com.omnispell.charm;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.soulbind.SoulBind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charms: a gem enchanted at a table that has a Runic Bookshelf beside it, then
 * set into a piece of armor on an anvil. Each kind of gem leans towards certain
 * charms, and the rarest gems tend to take more than one.
 */
public final class Charms {
	public record Charm(String id, String name, String effect) {
	}

	/** enchantability: how well the gem takes a charm; second/third: chance of extra charms; favoured: charms it leans to. */
	public record Gem(String name, int enchantability, double second, double third, List<String> favoured) {
	}

	public record Set(String gem, Map<String, Integer> charms) {
	}

	public static final Map<String, Charm> ALL = new LinkedHashMap<>();
	public static final Map<Item, Gem> GEMS = new LinkedHashMap<>();
	public static final String ATTUNEMENT = "omnispell:attunement";
	public static final int MAX_LEVEL = 3;
	/** However many pieces carry the same charm, it never counts for more than this many levels. */
	public static final int CAP = 10;
	private static final String DATA = "omni_charms";
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	static {
		charm("vigor", "Vigor", "+1 heart of health per level");
		charm("fleetfoot", "Fleetfoot", "+3% movement speed per level");
		charm("stoneskin", "Stoneskin", "+1 armor per level");
		charm("warding", "Warding", "-3% damage taken per level");
		charm("ember_ward", "Ember Ward", "-8% fire damage taken per level");
		charm("featherstep", "Featherstep", "-15% fall damage per level");
		charm("renewal", "Renewal", "slowly mends your wounds, faster per level");
		charm("fortune", "Fortune", "+1 luck per level");
		charm("deepwell", "Deepwell", "+6% to every mana reserve per level");
		charm("flow", "Flow", "+8% mana refill speed per level");
		charm("quickening", "Quickening", "-3% spell cooldown per level");
		charm("potency", "Potency", "+4% spell damage per level");
		charm("leech", "Leech", "heals you for 3% of spell damage dealt per level");

		GEMS.put(Items.AMETHYST_SHARD, new Gem("Amethyst", 10, 0.08, 0.0, List.of("deepwell", "flow")));
		GEMS.put(Items.LAPIS_LAZULI, new Gem("Lapis", 12, 0.10, 0.0, List.of("quickening", "fortune")));
		GEMS.put(Items.QUARTZ, new Gem("Quartz", 10, 0.10, 0.0, List.of("potency", "ember_ward")));
		GEMS.put(Items.PRISMARINE_CRYSTALS, new Gem("Prismarine", 12, 0.12, 0.0, List.of("renewal", "featherstep")));
		GEMS.put(Items.EMERALD, new Gem("Emerald", 14, 0.20, 0.02, List.of("fortune", "vigor", "fleetfoot")));
		GEMS.put(Items.ECHO_SHARD, new Gem("Echo", 16, 0.30, 0.05, List.of("leech", "warding")));
		GEMS.put(Items.DIAMOND, new Gem("Diamond", 18, 0.55, 0.15, List.of("warding", "stoneskin", "vigor")));
		GEMS.put(Items.TOTEM_OF_UNDYING, new Gem("Totem", 20, 0.65, 0.25, List.of("vigor", "renewal", "warding")));
		GEMS.put(Items.NETHERITE_INGOT, new Gem("Netherite", 22, 0.80, 0.40, List.of("potency", "stoneskin", "ember_ward")));
	}

	private Charms() {
	}

	private static void charm(String id, String name, String effect) {
		ALL.put(id, new Charm(id, name, effect));
	}

	/** Gems can go in an enchanting table. Vanilla has no enchantment that fits them, so only charms ever land. */
	public static void register() {
		net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Map.Entry<Item, Gem> gem : GEMS.entrySet()) {
				context.modify(gem.getKey(), builder -> builder.set(DataComponents.ENCHANTABLE, new Enchantable(gem.getValue().enchantability())));
			}
		});
	}

	public static boolean isGem(ItemStack stack) {
		return !stack.isEmpty() && GEMS.containsKey(stack.getItem());
	}

	/** The charms an enchanted gem carries. Empty for a plain gem. */
	public static Map<String, Integer> charmsOn(ItemStack gem) {
		Map<String, Integer> out = new LinkedHashMap<>();
		if (!isGem(gem)) return out;
		ItemEnchantments enchantments = gem.getEnchantments();
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			Identifier id = Identifier.parse(holder.getRegisteredName());
			if (id.getNamespace().equals(Omnispell.MOD_ID) && ALL.containsKey(id.getPath())) out.put(id.getPath(), enchantments.getLevel(holder));
		}
		return out;
	}

	/** True if a Runic Bookshelf stands within reach of the enchanting table. */
	public static boolean runicShelfNear(Level level, BlockPos table) {
		for (BlockPos pos : BlockPos.betweenClosed(table.offset(-2, 0, -2), table.offset(2, 1, 2))) {
			if (level.getBlockState(pos).is(OmniItems.RUNIC_BOOKSHELF)) return true;
		}
		return false;
	}

	/** What the enchanting table offers for a gem at a given cost. */
	public static List<EnchantmentInstance> roll(RegistryAccess registries, RandomSource random, ItemStack stack, int cost) {
		List<EnchantmentInstance> out = new ArrayList<>();
		Gem gem = GEMS.get(stack.getItem());
		if (gem == null || cost <= 0) return out;
		var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		List<String> pool = new ArrayList<>(ALL.keySet());
		int count = 1;
		if (random.nextDouble() < gem.second() + cost * 0.005) count++;
		if (count == 2 && random.nextDouble() < gem.third() + cost * 0.003) count++;
		for (int n = 0; n < count && !pool.isEmpty(); n++) {
			int total = 0;
			for (String id : pool) total += gem.favoured().contains(id) ? 8 : 1;
			int pick = random.nextInt(total);
			String chosen = pool.get(0);
			for (String id : pool) {
				pick -= gem.favoured().contains(id) ? 8 : 1;
				if (pick < 0) {
					chosen = id;
					break;
				}
			}
			pool.remove(chosen);
			int level = cost >= 24 ? 3 : cost >= 10 ? 2 : 1;
			if (level > 1 && random.nextInt(3) == 0) level--;
			if (n > 0 && level > 1) level--; // extra charms come in weaker
			var holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, Omnispell.id(chosen)));
			if (holder.isPresent()) out.add(new EnchantmentInstance(holder.get(), level));
		}
		return out;
	}

	// ----------------------------------------------------------------- armor

	public static boolean isArmor(ItemStack stack) {
		if (stack.isEmpty()) return false;
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().isArmor() && equippable.slot() != EquipmentSlot.BODY;
	}

	/** One slot, plus one for each level of Attunement. */
	public static int slots(ItemStack armor) {
		return isArmor(armor) ? 1 + SoulBind.enchantmentLevel(armor, ATTUNEMENT) : 0;
	}

	/** The charms set into a piece of armor, oldest first. Stored as gem|charm:level,charm:level;gem|... */
	public static List<Set> stored(ItemStack armor) {
		List<Set> out = new ArrayList<>();
		CustomData data = armor.get(DataComponents.CUSTOM_DATA);
		if (data == null) return out;
		String text = data.copyTag().getStringOr(DATA, "");
		for (String part : text.split(";")) {
			int bar = part.indexOf('|');
			if (bar <= 0) continue;
			Map<String, Integer> charms = new LinkedHashMap<>();
			for (String pair : part.substring(bar + 1).split(",")) {
				String[] kv = pair.split(":");
				if (kv.length == 2 && ALL.containsKey(kv[0])) {
					try {
						charms.put(kv[0], Integer.parseInt(kv[1]));
					} catch (NumberFormatException ignored) {
					}
				}
			}
			if (!charms.isEmpty()) out.add(new Set(part.substring(0, bar), charms));
		}
		return out;
	}

	private static void store(ItemStack armor, List<Set> sets) {
		StringBuilder text = new StringBuilder();
		for (Set set : sets) {
			if (!text.isEmpty()) text.append(';');
			text.append(set.gem()).append('|');
			boolean first = true;
			for (Map.Entry<String, Integer> e : set.charms().entrySet()) {
				if (!first) text.append(',');
				text.append(e.getKey()).append(':').append(e.getValue());
				first = false;
			}
		}
		CustomData.update(DataComponents.CUSTOM_DATA, armor, (CompoundTag tag) -> tag.putString(DATA, text.toString()));
	}

	/**
	 * Armor on the left of an anvil, an enchanted gem on the right. Returns the
	 * armor with the charm set in, or nothing if these two do not go together.
	 * When every slot is taken the oldest charm is pushed out.
	 */
	public static ItemStack merge(ItemStack armor, ItemStack gem) {
		Map<String, Integer> charms = charmsOn(gem);
		int slots = slots(armor);
		if (charms.isEmpty() || slots == 0) return ItemStack.EMPTY;
		ItemStack result = armor.copy();
		List<Set> sets = stored(result);
		sets.add(new Set(GEMS.get(gem.getItem()).name(), charms));
		while (sets.size() > slots) sets.remove(0);
		store(result, sets);
		return result;
	}

	public static int mergeCost(ItemStack gem) {
		int cost = 2;
		for (int level : charmsOn(gem).values()) cost += level;
		return cost;
	}

	// --------------------------------------------------------------- effects

	/** Total level of a charm across everything worn. */
	public static int level(LivingEntity entity, String id) {
		int total = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = entity.getItemBySlot(slot);
			if (piece.isEmpty() || !piece.has(DataComponents.CUSTOM_DATA)) continue;
			for (Set set : stored(piece)) total += set.charms().getOrDefault(id, 0);
		}
		return Math.min(CAP, total);
	}

	/** Called once a second: keeps the worn charms' steady effects in step with what is worn. */
	public static void everySecond(ServerPlayer player) {
		attribute(player, Attributes.MAX_HEALTH, "vigor", 2.0 * level(player, "vigor"), AttributeModifier.Operation.ADD_VALUE);
		attribute(player, Attributes.MOVEMENT_SPEED, "fleetfoot", 0.03 * level(player, "fleetfoot"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		attribute(player, Attributes.ARMOR, "stoneskin", level(player, "stoneskin"), AttributeModifier.Operation.ADD_VALUE);
		attribute(player, Attributes.LUCK, "fortune", level(player, "fortune"), AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
		int renewal = level(player, "renewal");
		if (renewal > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) player.heal(0.1f * renewal);
	}

	private static void attribute(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) return;
		Identifier id = Omnispell.id("charm_" + name);
		AttributeModifier current = instance.getModifier(id);
		if (amount <= 0) {
			if (current != null) instance.removeModifier(id);
		} else if (current == null || current.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Warding, Ember Ward and Featherstep take the edge off a hit. */
	public static float incoming(ServerPlayer player, DamageSource source, float amount) {
		if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		double percent = 3.0 * level(player, "warding");
		if (source.is(DamageTypeTags.IS_FIRE)) percent += 8.0 * level(player, "ember_ward");
		if (source.is(DamageTypeTags.IS_FALL)) percent += 15.0 * level(player, "featherstep");
		return percent <= 0 ? amount : (float) (amount * (1.0 - Math.min(80.0, percent) / 100.0));
	}

	/** Potency strengthens the wearer's spells, and Leech gives a little of the damage back. */
	public static float outgoing(DamageSource source, float amount) {
		if (amount <= 0 || !(source.getEntity() instanceof ServerPlayer player)) return amount;
		if (!source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) return amount;
		float dealt = (float) (amount * (1.0 + 0.04 * level(player, "potency")));
		int leech = level(player, "leech");
		if (leech > 0 && player.isAlive()) player.heal(dealt * 0.03f * leech);
		return dealt;
	}

	public static double manaPercent(ServerPlayer player) {
		return 6.0 * level(player, "deepwell");
	}

	public static double regenPercent(ServerPlayer player) {
		return 8.0 * level(player, "flow");
	}

	public static double cooldownPercent(ServerPlayer player) {
		return 3.0 * level(player, "quickening");
	}

	public static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> String.valueOf(level);
		};
	}

	/** The gem's item, for showing its name. */
	public static String gemName(ItemStack stack) {
		Gem gem = GEMS.get(stack.getItem());
		return gem == null ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() : gem.name();
	}
}
