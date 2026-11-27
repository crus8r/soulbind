package com.omnispell.player;

import com.omnispell.Omnispell;
import com.omnispell.net.OmniNet;
import com.omnispell.spell.Spell;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Corruption: the price of forbidden magic. Every forbidden spell fills the bar; it drains
 * away on its own over about ten minutes, and is gone entirely after ten minutes without a
 * forbidden cast.
 *
 * As it fills, it shows. From half full, dark veins creep over the body and a faint darkness
 * clings to you. From 60%, your hearts turn purple: you lose health, down to half your hearts
 * when it is full, but purple hearts shrug off much of any harm that is not a blow struck by
 * hand (arrows, falls, fire, magic, blasts). When it is full, forbidden spells no longer need
 * their own reserve: they tear what they lack from every other one, a tenth of each at a time.
 */
public final class Corruption {
	public static final double MAX = 100;
	/** Ten minutes of world time. */
	private static final long QUIET_RESET = 12000;
	private static final Identifier HEALTH_ID = Omnispell.id("corruption_health");
	/** When purple hearts begin, and veins. */
	public static final double HEARTS_FROM = 60, VEINS_FROM = 50;

	private Corruption() {
	}

	/** Who has been told, this life, that their corruption is full. */
	private static final java.util.Set<java.util.UUID> WARNED = new java.util.HashSet<>();

	public static void respawned(ServerPlayer player) {
		WARNED.remove(player.getUUID());
	}

	public static double of(ServerPlayer player) {
		return PlayerStore.get(player).corruption;
	}

	public static boolean full(ServerPlayer player) {
		return of(player) >= MAX - 0.5;
	}

	/** How far into the purple hearts: 0 below 60%, 1 when full. */
	private static double heartDepth(double corruption) {
		return Math.max(0, Math.min(1, (corruption - HEARTS_FROM) / (MAX - HEARTS_FROM)));
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	/** A forbidden spell was cast: the bar fills. */
	public static void onCast(ServerPlayer player, Spell spell) {
		PlayerData data = PlayerStore.get(player);
		int tier = Math.max(1, Math.min(5, spell.tier));
		data.corruption = Math.min(MAX, data.corruption + 5 + tier * 5 + (tier >= 4 ? (tier - 3) * 5 : 0));
		data.lastForbiddenAt = now(player);
		apply(player, data.corruption);
		broadcast(player, data.corruption);
		if (data.corruption >= MAX - 0.5 && WARNED.add(player.getUUID())) {
			// Said once a life; it fades from the screen on its own.
			player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Corruption is full. Your power can feed on other reserves.").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
		}
	}

	/** Once a second: it drains away, and is gone after ten quiet minutes. Its marks show. */
	public static void everySecond(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		if (data.corruption <= 0) {
			if (hasModifier(player)) apply(player, 0);
			return;
		}
		double before = data.corruption;
		if (now(player) - data.lastForbiddenAt >= QUIET_RESET) data.corruption = 0;
		else data.corruption = Math.max(0, data.corruption - MAX / 600.0); // about ten minutes from full to nothing
		apply(player, data.corruption);
		if ((int) before / 5 != (int) data.corruption / 5 || data.corruption == 0 || player.tickCount % 100 < 20) broadcast(player, data.corruption);
		marks(player, data.corruption);
	}

	private static boolean hasModifier(ServerPlayer player) {
		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		return max != null && max.hasModifier(HEALTH_ID);
	}

	/** Purple hearts: health falls away, down to half when the bar is full. */
	private static void apply(ServerPlayer player, double corruption) {
		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		if (max == null) return;
		double depth = heartDepth(corruption);
		if (depth <= 0) {
			if (max.hasModifier(HEALTH_ID)) max.removeModifier(HEALTH_ID);
			return;
		}
		max.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, -0.5 * depth, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	/** The faint darkness that clings to the corrupted. */
	private static void marks(ServerPlayer player, double corruption) {
		if (corruption < VEINS_FROM) return;
		ServerLevel level = player.level();
		int count = 1 + (int) ((corruption - VEINS_FROM) / 12);
		level.sendParticles(new DustParticleOptions(0x4A1270, 0.8f), player.getX(), player.getY() + 1.0, player.getZ(), count, 0.3, 0.5, 0.3, 0.0);
		if (corruption >= HEARTS_FROM && player.getRandom().nextInt(3) == 0) level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
		if (corruption >= MAX - 0.5 && player.getRandom().nextInt(2) == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.4, player.getZ(), 1, 0.25, 0.3, 0.25, 0.01);
	}

	/** Purple hearts shrug off much of any harm that is not a blow struck by hand. */
	public static float incoming(ServerPlayer player, DamageSource source, float amount) {
		double depth = heartDepth(of(player));
		if (depth <= 0 || amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		boolean byHand = source.getDirectEntity() instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
		if (byHand) return amount;
		return (float) (amount * (1.0 - 0.5 * depth));
	}

	/**
	 * When the bar is full and a forbidden spell's own reserve runs short, it takes from every
	 * other one: a tenth of each, whatever it needs. Returns how much it found.
	 */
	public static double overdraw(ServerPlayer player, ManaPool short_) {
		PlayerData data = PlayerStore.get(player);
		double found = 0;
		for (ManaPool other : ManaPool.values()) {
			if (other == short_) continue;
			double take = Math.min(data.mana[other.ordinal()], Mana.max(player, other) * 0.10);
			data.mana[other.ordinal()] -= take;
			found += take;
		}
		data.mana[short_.ordinal()] = Math.min(Mana.max(player, short_), data.mana[short_.ordinal()] + found);
		if (found > 0) {
			player.level().sendParticles(new DustParticleOptions(0x2A0838, 1.2f), player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.7, 0.5, 0.0);
			player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK, net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.6f);
		}
		return found;
	}

	/** Tells everyone in the world how corrupted this player is, so their veins show to others too. */
	public static void broadcast(ServerPlayer player, double corruption) {
		OmniNet.CorruptionLevel packet = new OmniNet.CorruptionLevel(player.getId(), (int) Math.round(corruption));
		for (ServerPlayer viewer : player.level().players()) ServerPlayNetworking.send(viewer, packet);
	}
}
