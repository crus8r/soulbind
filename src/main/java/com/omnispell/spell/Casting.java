package com.omnispell.spell;

import com.google.gson.JsonArray;
import com.omnispell.Omnispell;
import com.omnispell.item.WandItem;
import com.omnispell.item.WandParts;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Casting a spell from a wand: cooldowns, dark-magic corruption, and handing over to the right effects. */
public final class Casting {
	private record Cooldown(long start, long ready) {
	}

	private static final Map<UUID, Map<String, Cooldown>> COOLDOWNS = new HashMap<>();

	private Casting() {
	}

	private static final Map<UUID, Long> BOLT_READY = new HashMap<>();
	/** Until when a player's kills count as made by fire magic, so the meat comes off cooked. */
	private static final Map<UUID, Long> FIRE_UNTIL = new HashMap<>();

	public static boolean fireMagicActive(ServerPlayer player) {
		return FIRE_UNTIL.getOrDefault(player.getUUID(), 0L) > Tasks.now();
	}
	/** Force mana per bolt: a full starting reserve is twelve bolts, then about one every ten seconds. */
	public static final double BOLT_COST = 5.0;

	/**
	 * Left-click with a wand: cast the selected spell if it is ready. If the slot is
	 * empty or the spell is still cooling, the wand throws a plain arcane bolt
	 * instead, so a wizard always has something to hit with.
	 */
	public static void primary(ServerPlayer player, ItemStack wand) {
		// A wand made into a weapon strikes in melee until its enchantment fades.
		if (Weapons.imbued(player)) {
			Weapons.swing(player, wand);
			return;
		}
		int slot = WandItem.selected(wand);
		Spell spell = slot < 0 ? null : WandItem.spell(wand, slot);
		long now = Tasks.now();
		Cooldown cd = spell == null ? null : COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).get(spell.id);
		if (spell != null && (cd == null || cd.ready <= now)) {
			cast(player, wand, slot);
			return;
		}
		if (BOLT_READY.getOrDefault(player.getUUID(), 0L) > now) return;
		// The bolt is a convenience, not a free weapon: each one is paid for from the Force reserve.
		if (!player.getAbilities().instabuild && com.omnispell.player.Mana.available(player, com.omnispell.player.ManaPool.FORCE) < BOLT_COST) {
			player.sendOverlayMessage(Component.literal("Not enough Force mana for a bolt.").withStyle(ChatFormatting.GRAY));
			BOLT_READY.put(player.getUUID(), now + 12);
			return;
		}
		if (!player.getAbilities().instabuild) com.omnispell.player.Mana.spend(player, com.omnispell.player.ManaPool.FORCE, BOLT_COST);
		BOLT_READY.put(player.getUUID(), now + 12);
		ServerLevel level = player.level();
		com.omnispell.item.WandParts.Core core = com.omnispell.item.WandParts.core(WandItem.core(wand));
		float damage = 2.5f + Math.max(0, core.words - 3) * 0.25f;
		net.minecraft.world.phys.Vec3 from = Targeting.hand(player);
		net.minecraft.world.entity.LivingEntity target = Targeting.entity(player, 20);
		net.minecraft.world.phys.Vec3 to = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : Targeting.point(player, 20);
		net.minecraft.world.phys.Vec3 step = to.subtract(from);
		int points = Math.max(2, (int) (step.length() * 2));
		for (int i = 1; i <= points; i++) {
			net.minecraft.world.phys.Vec3 at = from.add(step.scale(i / (double) points));
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.7f, 1.6f);
		if (target != null) target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
	}

	public static void cast(ServerPlayer player, ItemStack wand, int slot) {
		Spell spell = WandItem.spell(wand, slot);
		if (spell == null) {
			player.sendOverlayMessage(Component.literal("No spell in slot " + (slot + 1) + ". Open your book to equip one."));
			return;
		}
		long now = Tasks.now();
		Map<String, Cooldown> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		Cooldown cd = cooldowns.get(spell.id);
		if (cd != null && cd.ready > now) {
			player.sendOverlayMessage(Component.literal(spell.label() + " — " + String.format("%.1fs", (cd.ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}

		double cost = manaCost(spell);
		com.omnispell.player.ManaPool pool = poolOf(spell);
		if (cost > 0 && pool != null && !player.getAbilities().instabuild && com.omnispell.player.Mana.available(player, pool) < cost) {
			player.sendOverlayMessage(Component.literal("Not enough " + pool.label + " mana for " + spell.label() + " (" + (int) Math.ceil(cost) + " needed).").withStyle(ChatFormatting.GRAY));
			return;
		}

		Cast cast = new Cast(player, wand, spell);
		if (spell.dark && !corruption(cast)) {
			setCooldown(cast, cooldowns, now, 1.0);
			PlayerStore.syncVitals(player);
			return;
		}

		Modifiers.noteCast(player);
		if ("fire".equals(spell.code.element) || "fire".equals(spell.element)) FIRE_UNTIL.put(player.getUUID(), now + (spell.isExploration() ? 500 : 100));
		boolean done;
		if (infinity(spell)) com.omnispell.player.Mana.DISCOUNT.set(com.omnispell.player.Mana.INFINITY_DISCOUNT);
		try {
			done = spell.isExploration() ? ExploreEffects.cast(cast) : Weapons.cast(cast) || CombatEffects.cast(cast);
		} catch (Exception e) {
			Omnispell.LOG.error("Spell {} failed", spell.id, e);
			cast.failure = "The spell unravels. (An error was logged.)";
			done = false;
		} finally {
			com.omnispell.player.Mana.DISCOUNT.set(1.0);
		}
		if (!done) {
			player.sendOverlayMessage(Component.literal(cast.failure == null ? "Nothing happens." : cast.failure).withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.6f);
			return;
		}
		if (cost > 0 && pool != null && !player.getAbilities().instabuild) com.omnispell.player.Mana.spend(player, pool, cost);
		setCooldown(cast, cooldowns, now, cast.cooldownFactor);
		String screen = spell.visual.screenEffect;
		if (screen != null && !screen.equals("none")) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
					new com.omnispell.net.OmniNet.ScreenFx(screen, spell.visual.intensity, Fx.color(spell.visual.color1)));
		}
		PlayerStore.syncVitals(player);
	}

	/** The website's cooldown in turns for a spell of this tier and type. */
	/** The reserve a spell mostly draws on. Shown as a coloured pip on its square. Null for spells that only have a cooldown. */
	public static com.omnispell.player.ManaPool poolOf(Spell spell) {
		if (spell == null || spell.type == null) return null;
		if (spell.isExploration()) {
			return switch (spell.toggle == null ? "" : spell.toggle) {
				case "invisibility", "fade", "camouflage", "silent_step", "teleport", "phase" -> com.omnispell.player.ManaPool.PHASE;
				case "stun", "trap", "golem", "guardian" -> com.omnispell.player.ManaPool.SPIRIT;
				default -> explorePool(spell.toggle);
			};
		}
		// Attacks of the body's elements (fire, ice, storm, stone, nature) draw on Force; attacks of the unseen ones (shadow, light, void, blood, arcane) draw on Spirit.
		if (spell.type.equals("attack")) return spiritElement(spell.code.element) ? com.omnispell.player.ManaPool.SPIRIT : com.omnispell.player.ManaPool.FORCE;
		return switch (spell.code.type == null ? "" : spell.code.type) {
			case "heal" -> com.omnispell.player.ManaPool.RESTORATION;
			case "shield" -> com.omnispell.player.ManaPool.BARRIER;
			case "debuff", "status" -> com.omnispell.player.ManaPool.SPIRIT;
			case "damage" -> com.omnispell.player.ManaPool.FORCE;
			default -> null;
		};
	}

	/** Every exploration spell and cantrip shows the reserve it mostly leans on, even when it pays as it goes. */
	private static com.omnispell.player.ManaPool explorePool(String toggle) {
		if (toggle == null || toggle.isEmpty()) return null; // a fizzle draws on nothing
		return switch (toggle) {
			case "force_field", "barrier_wall", "armor", "deflect", "anchor", "ward_element", "ring", "dome", "raise_wall", "solidify" -> com.omnispell.player.ManaPool.BARRIER;
			case "heal", "nourish", "salve", "growth", "mend_land", "thicket", "clear", "calm" -> com.omnispell.player.ManaPool.RESTORATION;
			case "dash", "leap", "grapple", "tether", "current", "mount", "gust", "blink", "shade", "decoy", "mirage", "phantom", "smoke_screen", "blackout", "bridge", "platform" -> com.omnispell.player.ManaPool.PHASE;
			case "reveal", "scout", "true_sight", "track", "charm", "restraint", "entangle", "polymorph", "transmute", "prospect", "glimmer", "illuminate", "weather", "attune" -> com.omnispell.player.ManaPool.SPIRIT;
			default -> com.omnispell.player.ManaPool.FORCE;
		};
	}

	public static boolean spiritElement(String element) {
		return switch (element == null ? "" : element.toLowerCase()) {
			case "shadow", "dark", "darkness", "light", "holy", "radiant", "void", "blood", "arcane", "spirit", "psychic", "death", "necrotic", "ethereal" -> true;
			default -> false;
		};
	}

	/** What a cast costs up front. Only attack spells have a fixed price; the other kinds pay as their effects are used. */
	public static double manaCost(Spell spell) {
		if (spell == null || !"attack".equals(spell.type)) return 0;
		double cost = 4 + 3 * Math.max(1, Math.min(5, spell.tier));
		if (spell.hasCharge("Minima")) cost *= 0.6;
		if (infinity(spell)) cost *= com.omnispell.player.Mana.INFINITY_DISCOUNT;
		return cost;
	}

	/** Infinity is the moderators' word: its spells cost almost nothing and come back almost at once. */
	public static boolean infinity(Spell spell) {
		return spell != null && spell.words != null && spell.words.contains("base:Infinity");
	}

	static double cooldownTurns(Spell spell) {
		int tier = spell.tier;
		double cd = tier <= 1 ? 0 : tier <= 2 ? 1 : tier <= 4 ? 2 : 3;
		if (spell.type.equals("conjuration")) cd += 1;
		if (spell.type.equals("defense")) {
			int shield = spell.code.duration > 0 ? spell.code.duration : Math.max(1, Math.min(tier, 3));
			cd = Math.max(cd, shield + 1);
		}
		return cd;
	}

	private static void setCooldown(Cast cast, Map<String, Cooldown> cooldowns, long now, double factor) {
		ServerPlayer player = cast.player;
		Spell spell = cast.spell;
		double seconds;
		if (spell.isExploration()) {
			seconds = ExploreEffects.cooldownSeconds(spell.toggle == null ? "" : spell.toggle);
			if (spell.hasCharge("Rapidus")) seconds *= 0.7;
			if (spell.hasCharge("Maxima")) seconds *= 1.3;
		} else {
			double turns = cooldownTurns(spell);
			turns -= Modifiers.value(player, "cooldown_reduction") + Modifiers.value(player, "mana_efficiency");
			seconds = Math.max(0, turns) * Omnispell.CONFIG.turnSeconds;
			if (spell.type.equals("attack")) seconds *= 0.7; // attack spells are paid for in Force mana now, so they come back sooner
		}
		seconds *= 1.0 - Math.min(60.0, cast.core.haste + com.omnispell.player.Mana.alacrityPercent(player)) / 100.0;
		seconds *= factor;
		if (infinity(spell)) seconds *= 0.15;
		seconds = Math.max(Omnispell.CONFIG.minimumCooldownSeconds, seconds);
		if (Modifiers.consume(player, "haste") != 0 && !spell.code.modifiers.toString().contains("haste")) {
			seconds = 0.25; // Haste: the next spell comes at once
		}
		long ticks = Math.round(seconds * 20);
		cooldowns.put(spell.id, new Cooldown(now, now + ticks));
	}

	/**
	 * Dark magic builds corruption. The more there is, the more likely a dark spell
	 * fizzles or turns on its caster. Returns false if this cast was lost to it.
	 */
	private static boolean corruption(Cast cast) {
		ServerPlayer player = cast.player;
		PlayerData data = PlayerStore.get(player);
		int tier = cast.spell.tier;
		double max = 100;
		double pct = data.corruption / max * 100;
		double backfire = pct >= 100 ? 100 : pct >= 75 ? 40 : pct >= 50 ? 20 : pct >= 25 ? 8 : 0;
		double selfDamage = pct >= 100 ? 0.5 : pct >= 75 ? 0.3 : pct >= 50 ? 0.2 : 0.1;
		double fizzle = pct >= 100 ? 30 : pct >= 75 ? 15 : pct >= 50 ? 5 : 0;
		data.corruption = Math.min(max, data.corruption + 5 + tier * 5 + (tier >= 4 ? (tier - 3) * 5 : 0));

		double roll = player.getRandom().nextDouble() * 100;
		if (roll < fizzle) {
			cast.tell("The corruption swallows your spell.");
			player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
			return false;
		}
		if (player.getRandom().nextDouble() * 100 < backfire) {
			float hurt = (float) (Math.max(10, cast.spell.code.primaryValue) * cast.scale * selfDamage);
			player.hurtServer(cast.level, cast.level.damageSources().magic(), hurt);
			cast.tell("The dark magic turns on you.");
			player.level().sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
		}
		return true;
	}

	/** Spell Vampirism: knocks time off one of the caster's running cooldowns. */
	public static void shortenRandomCooldown(ServerPlayer player, int ticks) {
		Map<String, Cooldown> cooldowns = COOLDOWNS.get(player.getUUID());
		if (cooldowns == null) return;
		long now = Tasks.now();
		List<String> running = new ArrayList<>();
		for (Map.Entry<String, Cooldown> e : cooldowns.entrySet()) {
			if (e.getValue().ready > now) running.add(e.getKey());
		}
		if (running.isEmpty()) return;
		String id = running.get(player.getRandom().nextInt(running.size()));
		Cooldown cd = cooldowns.get(id);
		cooldowns.put(id, new Cooldown(cd.start, Math.max(now, cd.ready - ticks)));
	}

	/** Silence: every spell the player has on cooldown waits longer, and ready spells get a short wait. */
	public static void lengthenCooldowns(ServerPlayer player, int ticks) {
		Map<String, Cooldown> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		long now = Tasks.now();
		ItemStack wand = WandItem.heldWand(player);
		if (!wand.isEmpty()) {
			for (int i = 0; i < WandItem.SLOTS; i++) {
				Spell spell = WandItem.spell(wand, i);
				if (spell == null) continue;
				Cooldown cd = cooldowns.get(spell.id);
				long ready = Math.max(now, cd == null ? now : cd.ready) + ticks;
				cooldowns.put(spell.id, new Cooldown(now, ready));
			}
		}
		PlayerStore.syncVitals(player);
	}

	/** For the HUD: for each wand slot, [ticks left, total ticks]. */
	public static JsonArray cooldownsJson(ServerPlayer player) {
		JsonArray array = new JsonArray();
		ItemStack wand = WandItem.heldWand(player);
		Map<String, Cooldown> cooldowns = COOLDOWNS.get(player.getUUID());
		long now = Tasks.now();
		for (int i = 0; i < WandItem.SLOTS; i++) {
			JsonArray pair = new JsonArray();
			Spell spell = wand.isEmpty() ? null : WandItem.spell(wand, i);
			Cooldown cd = spell == null || cooldowns == null ? null : cooldowns.get(spell.id);
			if (cd != null && cd.ready > now) {
				pair.add(cd.ready - now);
				pair.add(Math.max(1, cd.ready - cd.start));
			} else {
				pair.add(0);
				pair.add(1);
			}
			array.add(pair);
		}
		return array;
	}

	public static void forget(ServerPlayer player) {
		COOLDOWNS.remove(player.getUUID());
	}

	/** Core of a wand, for things outside a cast that want its bonuses. */
	public static WandParts.Core coreOf(ItemStack wand) {
		return WandParts.core(WandItem.core(wand));
	}
}
