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

	/**
	 * Left-click with a wand: cast the selected spell if it is ready. If the slot is
	 * empty or the spell is still cooling, the wand throws a plain arcane bolt
	 * instead, so a wizard always has something to hit with.
	 */
	public static void primary(ServerPlayer player, ItemStack wand) {
		int slot = WandItem.selected(wand);
		Spell spell = WandItem.spell(wand, slot);
		long now = Tasks.now();
		Cooldown cd = spell == null ? null : COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).get(spell.id);
		if (spell != null && (cd == null || cd.ready <= now)) {
			cast(player, wand, slot);
			return;
		}
		if (BOLT_READY.getOrDefault(player.getUUID(), 0L) > now) return;
		BOLT_READY.put(player.getUUID(), now + 12);
		ServerLevel level = player.level();
		com.omnispell.item.WandParts.Core core = com.omnispell.item.WandParts.core(WandItem.core(wand));
		float damage = 3.0f + Math.max(0, core.words - 3) * 0.5f;
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
			player.sendOverlayMessage(Component.literal(spell.name + " — " + String.format("%.1fs", (cd.ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}

		Cast cast = new Cast(player, wand, spell);
		if (spell.dark && !corruption(cast)) {
			setCooldown(cast, cooldowns, now, 1.0);
			PlayerStore.syncVitals(player);
			return;
		}

		Modifiers.noteCast(player);
		boolean done;
		try {
			done = spell.isExploration() ? ExploreEffects.cast(cast) : CombatEffects.cast(cast);
		} catch (Exception e) {
			Omnispell.LOG.error("Spell {} failed", spell.id, e);
			cast.failure = "The spell unravels. (An error was logged.)";
			done = false;
		}
		if (!done) {
			player.sendOverlayMessage(Component.literal(cast.failure == null ? "Nothing happens." : cast.failure).withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.6f);
			return;
		}
		setCooldown(cast, cooldowns, now, cast.cooldownFactor);
		String screen = spell.visual.screenEffect;
		if (screen != null && !screen.equals("none")) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
					new com.omnispell.net.OmniNet.ScreenFx(screen, spell.visual.intensity, Fx.color(spell.visual.color1)));
		}
		PlayerStore.syncVitals(player);
	}

	/** The website's cooldown in turns for a spell of this tier and type. */
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
		}
		seconds *= 1.0 - Math.min(60.0, cast.core.haste + com.omnispell.player.Mana.alacrityPercent(player)) / 100.0;
		seconds *= factor;
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
