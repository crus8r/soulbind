package com.omnispell.block;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An arcane well. Drawing essence works like fishing: use the well, wait for
 * the surge (a chime and a burst of light), then use it again before the surge
 * fades. Each catch gives essence. With your essence full and a glass bottle in
 * hand, three catches fill the bottle into an Arcane Potion instead.
 */
public class ArcaneWellBlock extends Block {
	private static final int WINDOW_TICKS = 18;

	private static final class Session {
		BlockPos pos;
		long surgeAt;   // when the surge begins
		long token;     // identifies the current wait, so an old scheduled surge is ignored
		int bottlePulls;
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** True while this player's surge is active: the moment to use the well again. */
	public static boolean surging(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		long now = Tasks.now();
		return session != null && now >= session.surgeAt && now <= session.surgeAt + WINDOW_TICKS;
	}

	public ArcaneWellBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		// Whatever is in hand, using the well draws from it.
		return draw(level, pos, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return draw(level, pos, player);
	}

	private InteractionResult draw(Level level, BlockPos pos, Player user) {
		if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		long now = Tasks.now();
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.pos.equals(pos) || now > session.surgeAt + WINDOW_TICKS + 40) {
			int pulls = session != null && session.pos.equals(pos) && now <= session.surgeAt + 200 ? session.bottlePulls : 0;
			session = new Session();
			session.pos = pos.immutable();
			session.bottlePulls = pulls;
			SESSIONS.put(player.getUUID(), session);
			begin(server, player, session, "You reach into the well. Wait for the surge...");
			return InteractionResult.SUCCESS;
		}
		if (now < session.surgeAt) {
			begin(server, player, session, "Too soon. The essence slips away.");
			server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.5f);
			return InteractionResult.SUCCESS;
		}
		if (now > session.surgeAt + WINDOW_TICKS) {
			begin(server, player, session, "Too late. Wait for the next surge...");
			return InteractionResult.SUCCESS;
		}
		caught(server, player, session);
		return InteractionResult.SUCCESS;
	}

	private static void begin(ServerLevel level, ServerPlayer player, Session session, String message) {
		long now = Tasks.now();
		int wait = 25 + player.getRandom().nextInt(50);
		session.surgeAt = now + wait;
		session.token = session.surgeAt;
		long token = session.token;
		BlockPos pos = session.pos;
		player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
		level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.3);
		Tasks.later(wait, () -> {
			Session current = SESSIONS.get(player.getUUID());
			if (current != session || session.token != token) return;
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.6f);
			level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 0.6f);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.25, 0.4, 0.25, 0.08);
			player.sendOverlayMessage(Component.literal("✦ Now! ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		});
	}

	private static void caught(ServerLevel level, ServerPlayer player, Session session) {
		PlayerData data = PlayerStore.get(player);
		BlockPos pos = session.pos;
		int cap = Omnispell.CONFIG.essenceCap;
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);

		if (data.essence < cap) {
			data.essence = Math.min(cap, data.essence + Omnispell.CONFIG.essencePerWellPull);
			session.bottlePulls = 0;
			PlayerStore.save(player);
			PlayerStore.syncVitals(player);
			String more = data.essence >= cap ? "Your essence is full. Hold a glass bottle to fill it." : "Wait for the next surge...";
			begin(level, player, session, "Essence " + data.essence + " / " + cap + ". " + more);
			return;
		}

		InteractionHand hand = player.getMainHandItem().is(Items.GLASS_BOTTLE) ? InteractionHand.MAIN_HAND
				: player.getOffhandItem().is(Items.GLASS_BOTTLE) ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			SESSIONS.remove(player.getUUID());
			player.sendOverlayMessage(Component.literal("Your essence is full. Hold a glass bottle to fill it at the well.").withStyle(ChatFormatting.AQUA));
			return;
		}
		session.bottlePulls++;
		int needed = Omnispell.CONFIG.wellPullsPerPotion;
		if (session.bottlePulls < needed) {
			begin(level, player, session, "The bottle fills... " + session.bottlePulls + " / " + needed);
			return;
		}
		session.bottlePulls = 0;
		player.getItemInHand(hand).shrink(1);
		ItemStack potion = new ItemStack(OmniItems.ARCANE_POTION);
		if (!player.getInventory().add(potion)) player.drop(potion, false, net.minecraft.util.Prediction.SERVER_ONLY);
		begin(level, player, session, "An Arcane Potion! Keep going, or walk away.");
	}
}
