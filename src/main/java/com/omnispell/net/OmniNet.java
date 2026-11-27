package com.omnispell.net;

import com.omnispell.Omnispell;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The messages Omnispell sends between the game client and the server. */
public final class OmniNet {
	private static final StreamCodec<ByteBuf, String> BIG_STRING = ByteBufCodecs.stringUtf8(4 * 1024 * 1024);
	private static final int BIG = 8 * 1024 * 1024;

	private OmniNet() {
	}

	// ----- server -> client -----

	/** The player's dictionary, spellbook, unlocked spell types, pending spell and defeated bosses, as JSON. */
	public record SyncState(String json) implements CustomPacketPayload {
		public static final Type<SyncState> TYPE = OmniNet.makeType("sync_state");
		public static final StreamCodec<ByteBuf, SyncState> CODEC = StreamCodec.composite(BIG_STRING, SyncState::json, SyncState::new);

		@Override
		public Type<SyncState> type() {
			return TYPE;
		}
	}

	/** Fast-changing numbers: essence, mana, cooldowns. JSON keeps this easy to extend. */
	public record SyncVitals(String json) implements CustomPacketPayload {
		public static final Type<SyncVitals> TYPE = OmniNet.makeType("sync_vitals");
		public static final StreamCodec<ByteBuf, SyncVitals> CODEC = StreamCodec.composite(BIG_STRING, SyncVitals::json, SyncVitals::new);

		@Override
		public Type<SyncVitals> type() {
			return TYPE;
		}
	}

	/** Open the book. */
	public record OpenBook(String tab) implements CustomPacketPayload {
		public static final Type<OpenBook> TYPE = OmniNet.makeType("open_book");
		public static final StreamCodec<ByteBuf, OpenBook> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenBook::tab, OpenBook::new);

		@Override
		public Type<OpenBook> type() {
			return TYPE;
		}
	}

	/** A line of feedback for the book screen (crafting progress, errors). */
	public record BookMessage(boolean ok, String text) implements CustomPacketPayload {
		public static final Type<BookMessage> TYPE = OmniNet.makeType("book_message");
		public static final StreamCodec<ByteBuf, BookMessage> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, BookMessage::ok, ByteBufCodecs.STRING_UTF8, BookMessage::text, BookMessage::new);

		@Override
		public Type<BookMessage> type() {
			return TYPE;
		}
	}

	/** Where the diamond on the experience bar points: the player's Reliquary, or the portal toward it. */
	public record CacheTarget(boolean active, double x, double y, double z) implements CustomPacketPayload {
		public static final Type<CacheTarget> TYPE = OmniNet.makeType("cache_target");
		public static final StreamCodec<ByteBuf, CacheTarget> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, CacheTarget::active, ByteBufCodecs.DOUBLE, CacheTarget::x,
				ByteBufCodecs.DOUBLE, CacheTarget::y, ByteBufCodecs.DOUBLE, CacheTarget::z, CacheTarget::new);

		@Override
		public Type<CacheTarget> type() {
			return TYPE;
		}
	}

	/** How corrupted a player is (0 to 100), so everyone can see their veins. */
	public record CorruptionLevel(int entityId, int percent) implements CustomPacketPayload {
		public static final Type<CorruptionLevel> TYPE = OmniNet.makeType("corruption_level");
		public static final StreamCodec<ByteBuf, CorruptionLevel> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, CorruptionLevel::entityId, ByteBufCodecs.VAR_INT, CorruptionLevel::percent, CorruptionLevel::new);

		@Override
		public Type<CorruptionLevel> type() {
			return TYPE;
		}
	}

	/** A conjured creature's colour and the element whose markings it wears, so a fire wolf looks like one. */
	public record ConjureTint(int entityId, int color, String element) implements CustomPacketPayload {
		public static final Type<ConjureTint> TYPE = OmniNet.makeType("conjure_tint");
		public static final StreamCodec<ByteBuf, ConjureTint> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ConjureTint::entityId, ByteBufCodecs.INT, ConjureTint::color, ByteBufCodecs.STRING_UTF8, ConjureTint::element, ConjureTint::new);

		@Override
		public Type<ConjureTint> type() {
			return TYPE;
		}
	}

	/** A screen flash or shake for a spell, from the spell's visual manifest. */
	/** A player is fully shrouded (armour and held items hidden too) for this many ticks; 0 ends it. */
	public record Shroud(int entityId, int ticks) implements CustomPacketPayload {
		public static final Type<Shroud> TYPE = OmniNet.makeType("shroud");
		public static final StreamCodec<ByteBuf, Shroud> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Shroud::entityId, ByteBufCodecs.VAR_INT, Shroud::ticks, Shroud::new);

		@Override
		public Type<Shroud> type() {
			return TYPE;
		}
	}

	public record ScreenFx(String kind, int intensity, int color) implements CustomPacketPayload {
		public static final Type<ScreenFx> TYPE = OmniNet.makeType("screen_fx");
		public static final StreamCodec<ByteBuf, ScreenFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ScreenFx::kind, ByteBufCodecs.VAR_INT, ScreenFx::intensity, ByteBufCodecs.INT, ScreenFx::color, ScreenFx::new);

		@Override
		public Type<ScreenFx> type() {
			return TYPE;
		}
	}

	/** Open the rename / recolour screen for a waypoint pair. */
	public record OpenWaypoint(String pairId, String name, int color) implements CustomPacketPayload {
		public static final Type<OpenWaypoint> TYPE = OmniNet.makeType("open_waypoint");
		public static final StreamCodec<ByteBuf, OpenWaypoint> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenWaypoint::pairId, ByteBufCodecs.STRING_UTF8, OpenWaypoint::name, ByteBufCodecs.VAR_INT, OpenWaypoint::color, OpenWaypoint::new);

		@Override
		public Type<OpenWaypoint> type() {
			return TYPE;
		}
	}

	// ----- client -> server -----

	/** Rename and recolour a waypoint pair. */
	public record EditWaypoint(String pairId, String name, int color) implements CustomPacketPayload {
		public static final Type<EditWaypoint> TYPE = OmniNet.makeType("edit_waypoint");
		public static final StreamCodec<ByteBuf, EditWaypoint> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, EditWaypoint::pairId, ByteBufCodecs.STRING_UTF8, EditWaypoint::name, ByteBufCodecs.VAR_INT, EditWaypoint::color, EditWaypoint::new);

		@Override
		public Type<EditWaypoint> type() {
			return TYPE;
		}
	}


	/** Cast the spell in this wand slot (0-4), or -1 for the selected slot. */
	public record CastSpell(int slot) implements CustomPacketPayload {
		public static final Type<CastSpell> TYPE = OmniNet.makeType("cast_spell");
		public static final StreamCodec<ByteBuf, CastSpell> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, c -> c.slot() + 1, i -> new CastSpell(i - 1));

		@Override
		public Type<CastSpell> type() {
			return TYPE;
		}
	}

	/** Craft a spell of this type from these word ids (comma separated). */
	public record CraftSpell(String spellType, String words) implements CustomPacketPayload {
		public static final Type<CraftSpell> TYPE = OmniNet.makeType("craft_spell");
		public static final StreamCodec<ByteBuf, CraftSpell> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, CraftSpell::spellType, ByteBufCodecs.STRING_UTF8, CraftSpell::words, CraftSpell::new);

		@Override
		public Type<CraftSpell> type() {
			return TYPE;
		}
	}

	/**
	 * A small action from the book: "save" / "discard" the pending spell,
	 * "forget" a spell (arg = spell id), "equip" (arg = slot + ":" + spell id, empty id clears),
	 * "select" (arg = slot).
	 */
	public record BookAction(String action, String arg) implements CustomPacketPayload {
		public static final Type<BookAction> TYPE = OmniNet.makeType("book_action");
		public static final StreamCodec<ByteBuf, BookAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, BookAction::action, ByteBufCodecs.STRING_UTF8, BookAction::arg, BookAction::new);

		@Override
		public Type<BookAction> type() {
			return TYPE;
		}
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> makeType(String name) {
		return new CustomPacketPayload.Type<>(Omnispell.id(name));
	}

	/** Registers the message formats. Runs on both sides. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().registerLarge(SyncState.TYPE, SyncState.CODEC, BIG);
		PayloadTypeRegistry.clientboundPlay().register(SyncVitals.TYPE, SyncVitals.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenBook.TYPE, OpenBook.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BookMessage.TYPE, BookMessage.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScreenFx.TYPE, ScreenFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Shroud.TYPE, Shroud.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConjureTint.TYPE, ConjureTint.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CorruptionLevel.TYPE, CorruptionLevel.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CacheTarget.TYPE, CacheTarget.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenWaypoint.TYPE, OpenWaypoint.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EditWaypoint.TYPE, EditWaypoint.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CastSpell.TYPE, CastSpell.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CraftSpell.TYPE, CraftSpell.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BookAction.TYPE, BookAction.CODEC);
	}
}
