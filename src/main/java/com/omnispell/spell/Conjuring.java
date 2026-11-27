package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Conjured creatures. The spell's creature word (or the creature its own name speaks
 * of) decides the body: a wolf, a bear, a serpent, a bird, a golem. The base word
 * colours it. It fights beside its caster, biting what is close and casting the
 * spell's own magic at what is not, so a fire serpent spits fire and a frost wolf
 * leaves its prey frozen.
 */
public final class Conjuring {
	private Conjuring() {
	}

	public static final net.minecraft.resources.ResourceKey<EntityType<?>> SERPENT_KEY =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Omnispell.id("conjured_serpent"));
	public static final EntityType<ConjuredSerpent> SERPENT = net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE, SERPENT_KEY,
			EntityType.Builder.<ConjuredSerpent>of(ConjuredSerpent::new, MobCategory.MISC).sized(0.9f, 0.8f).eyeHeight(0.5f).clientTrackingRange(10).build(SERPENT_KEY));

	/** Each conjured creature's colour and markings, sent again to anyone who comes near it later. */
	private static final Map<net.minecraft.world.entity.Entity, com.omnispell.net.OmniNet.ConjureTint> MARKS = new java.util.WeakHashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(SERPENT, ConjuredSerpent.attributes());
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			com.omnispell.net.OmniNet.ConjureTint mark = MARKS.get(entity);
			if (mark != null && entity.isAlive()) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, mark);
		});
	}

	/** A body: its entity type and how much bigger or smaller than usual. */
	/** A body: its entity type, its size, whether it flies, and the weapon it carries (null for none). */
	public record Body(String id, EntityType<? extends Mob> type, double scale, boolean flies, String weapon) {
	}

	private static final Map<String, Body> BODIES = new HashMap<>();

	static {
		body("serpent", SERPENT, 1.0, true, "snake", "serpent", "eel", "wyrm", "naga", "leviathan", "hydra", "worm", "dragon", "basilisk", "viper", "cobra", "python");
		body("wolf", EntityTypes.WOLF, 1.15, false, "wolf", "hound", "dog", "jackal", "coyote", "cerberus");
		body("bear", EntityTypes.POLAR_BEAR, 1.0, false, "bear", "ursine", "grizzly");
		body("fox", EntityTypes.FOX, 1.3, false, "fox", "kitsune", "vixen");
		body("cat", EntityTypes.OCELOT, 1.5, false, "cat", "panther", "lion", "tiger", "lynx", "leopard", "jaguar", "feline", "sphinx");
		body("horse", EntityTypes.HORSE, 1.0, false, "horse", "steed", "stallion", "unicorn", "mare", "pegasus", "kelpie");
		body("frog", EntityTypes.FROG, 1.6, false, "frog", "toad");
		body("bird", EntityTypes.PARROT, 1.4, true, "bird", "hawk", "falcon", "raven", "crow", "owl", "phoenix", "sparrow", "swallow", "dove");
		body("eagle", EntityTypes.PARROT, 2.0, true, "eagle", "griffin", "gryphon", "roc", "condor", "thunderbird", "talon", "stormtalon");
		body("insect", EntityTypes.BEE, 1.5, true, "insect", "bee", "wasp", "hornet", "swarm", "beetle", "moth", "locust");
		body("spider", EntityTypes.SPIDER, 1.0, false, "spider", "scorpion", "arachnid", "tarantula");
		body("golem", EntityTypes.IRON_GOLEM, 0.85, false, "golem", "construct", "statue", "sentinel", "colossus", "titan", "automaton", "gargoyle");
		warrior("archer", EntityTypes.SKELETON, 1.0, "bow", "archer", "archers", "bowman", "ranger", "marksman", "skeleton");
		warrior("swordsman", EntityTypes.SKELETON, 1.05, "sword", "swordsman", "warrior", "soldier", "knight", "legionnaire", "champion");
		warrior("duelist", EntityTypes.SKELETON, 0.95, "dagger", "assassin", "rogue", "duelist", "thief", "cutthroat");
		warrior("brute", EntityTypes.VINDICATOR, 1.0, "club", "brute", "barbarian", "berserker", "ogre", "troll");
		warrior("spearman", EntityTypes.DROWNED, 1.0, "spear", "spearman", "lancer", "hoplite", "pikeman", "harpooner");
		body("spirit", EntityTypes.VEX, 1.4, true, "spirit", "wisp", "wraith", "ghost", "specter", "spectre", "phantom", "shade", "imp", "fairy", "sprite", "familiar");
	}

	private static void body(String id, EntityType<? extends Mob> type, double scale, boolean flies, String... words) {
		Body b = new Body(id, type, scale, flies, null);
		for (String w : words) BODIES.put(w, b);
	}

	/** Armed warriors: skeletons and their kin, each fighting with its weapon. They walk; they do not fly. */
	private static void warrior(String id, EntityType<? extends Mob> type, double scale, String weapon, String... words) {
		Body b = new Body(id, type, scale, false, weapon);
		BODIES.put(id, b);
		for (String w : words) BODIES.put(w, b);
	}

	/** The weapon words make warriors: Bow and Arrow archers, Sword swordsmen, Blade duelists, Club brutes, Spear spearmen. */
	private static Body warriorFor(Spell spell) {
		for (String id : spell.words) {
			String warrior = switch (id) {
				case "attribute:Bow", "attribute:Arrow" -> "archer";
				case "attribute:Sword" -> "swordsman";
				case "attribute:Blade/Knife" -> "duelist";
				case "attribute:Club" -> "brute";
				case "attribute:Spear" -> "spearman";
				default -> null;
			};
			if (warrior != null) return BODIES.get(warrior);
		}
		return null;
	}

	/** Some bodies change with the element: a frost archer is a stray, a fire swordsman a wither skeleton. */
	private static EntityType<? extends Mob> variant(Body body, String element) {
		return switch (body.id) {
			case "archer" -> element.equals("ice") ? EntityTypes.STRAY : element.equals("nature") || element.equals("water") ? EntityTypes.BOGGED : EntityTypes.SKELETON;
			case "swordsman" -> element.equals("fire") || element.equals("shadow") || element.equals("void") || element.equals("blood") ? EntityTypes.WITHER_SKELETON : EntityTypes.SKELETON;
			case "brute" -> element.equals("fire") ? EntityTypes.PIGLIN_BRUTE : EntityTypes.VINDICATOR;
			default -> body.type;
		};
	}

	/** The body for a spell: its creature attribute word first, then its name and description, then its element. */
	public static boolean creatureWord(String english) {
		return java.util.Set.of("Bird", "Wolf", "Bear", "Fox", "Eagle", "Snake", "Horse", "Cat", "Insect", "Frog").contains(english);
	}

	/** The creature the spell's own words or the Oracle's naming ask for, or null if nothing names one. */
	public static Body namedBody(Spell spell, String name) {
		Body flyer = warriorFor(spell) != null ? flyingCreature(spell) : null;
		if (flyer != null) return flyer;
		Body armed = warriorFor(spell);
		if (armed != null) return armed;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("attribute")) {
				Body b = BODIES.get(word.english.toLowerCase(Locale.ROOT));
				if (b != null) return b;
			}
		}
		String text = ((name == null ? "" : name) + " " + (spell.name == null ? "" : spell.name) + " " + SpellCrafting.oracleText(spell)).toLowerCase(Locale.ROOT);
		Body best = null;
		int at = Integer.MAX_VALUE;
		for (Map.Entry<String, Body> e : BODIES.entrySet()) {
			int i = indexOfWord(text, e.getKey());
			if (i >= 0 && i < at) {
				at = i;
				best = e.getValue();
			}
		}
		return best;
	}

	/** Thunder and its kin strike with sound: the creature's spell is a sonic blast. */
	public static boolean sonic(Spell spell) {
		for (String id : spell.words) if (id.equals("base:Thunder") || id.equals("base:Sound") || id.equals("base:Echo") || id.equals("base:Vibration")) return true;
		return false;
	}

	/** A creature word for something that flies (bird, eagle, insect, snake), with the body it gives, or null. */
	public static Body flyingCreature(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("attribute") || !creatureWord(word.english)) continue;
			Body b = BODIES.get(word.english.toLowerCase(Locale.ROOT));
			if (b != null && b.flies) return b;
		}
		return null;
	}

	/**
	 * A flying creature can carry no warrior, so with a weapon word the creature fights with the weapon's
	 * gift instead: a bow lets its breath loose arrows too, a sword or blade gives it slashing claws, a club
	 * a heavy blow that throws foes back, a spear a spear it hurls. "" for none.
	 */
	public static String gift(Spell spell) {
		Body armed = warriorFor(spell);
		return armed != null && flyingCreature(spell) != null ? armed.weapon : "";
	}

	public static Body bodyFor(Spell spell, String name) {
		Body flyer = warriorFor(spell) != null ? flyingCreature(spell) : null;
		if (flyer != null) return flyer;
		Body armed = warriorFor(spell);
		if (armed != null) return armed;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("attribute")) {
				Body b = BODIES.get(word.english.toLowerCase(Locale.ROOT));
				if (b != null) return b;
			}
		}
		String text = ((name == null ? "" : name) + " " + (spell.name == null ? "" : spell.name) + " " + SpellCrafting.oracleText(spell)).toLowerCase(Locale.ROOT);
		Body best = null;
		int at = Integer.MAX_VALUE;
		for (Map.Entry<String, Body> e : BODIES.entrySet()) {
			int i = indexOfWord(text, e.getKey());
			if (i >= 0 && i < at) {
				at = i;
				best = e.getValue();
			}
		}
		if (best != null) return best;
		return BODIES.get(switch (AttackForms.element(spell)) {
			case "water" -> "serpent";
			case "ice" -> "bear";
			case "fire" -> "fox";
			case "stone" -> "golem";
			case "wind", "storm" -> "bird";
			case "nature" -> "frog";
			case "shadow" -> "cat";
			case "blood" -> "wolf";
			default -> "spirit";
		});
	}

	private static int indexOfWord(String text, String word) {
		int i = text.indexOf(word);
		while (i >= 0) {
			boolean start = i == 0 || !Character.isLetter(text.charAt(i - 1));
			int end = i + word.length();
			boolean stop = end >= text.length() || !Character.isLetter(text.charAt(end)) || text.charAt(end) == 's';
			if (start && stop) return i;
			i = text.indexOf(word, i + 1);
		}
		return -1;
	}

	/** The creature's colour: a colour word if the spell has one, otherwise its element's. */
	public static int tintFor(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("attribute")) continue;
			Integer c = switch (word.english) {
				case "Red" -> 0xE0403A;
				case "Orange" -> 0xF08A30;
				case "Yellow" -> 0xF0D840;
				case "Green" -> 0x58C04A;
				case "Blue" -> 0x4A78E8;
				case "Indigo" -> 0x5A48C8;
				case "Violet" -> 0xA858E0;
				default -> null;
			};
			if (c != null) return c;
		}
		return switch (AttackForms.element(spell)) {
			case "fire" -> 0xFF8A40;
			case "ice" -> 0xA8E4FF;
			case "water" -> 0x4AA8F0;
			case "wind" -> 0xE8F4F0;
			case "storm" -> 0xC8D8FF;
			case "stone" -> 0xB49A78;
			case "nature" -> 0x7CD060;
			case "light" -> 0xFFF2A8;
			case "shadow" -> 0x6A5A8A;
			case "void" -> 0x9A5AE0;
			case "blood" -> 0xD03A4A;
			default -> 0xC08AF0;
		};
	}

	private static final class Active {
		final Mob mob;
		final UUID owner;
		final Cast cast;
		final Body body;
		final double base;
		LivingEntity foe;
		int biteReady;
		int shootReady = 20;
		float bite = 4;
		final java.util.Set<String> traits = new java.util.HashSet<>();
		boolean ended;
		int boltReady = 20;
		/** A flying creature's weapon gift (see gift()), or "". */
		String gift = "";

		Active(Mob mob, UUID owner, Cast cast, Body body, double base, LivingEntity foe) {
			this.mob = mob;
			this.owner = owner;
			this.cast = cast;
			this.body = body;
			this.base = base;
			this.foe = foe;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	public static boolean isConjured(Entity entity) {
		return entity != null && ACTIVE.containsKey(entity.getUUID());
	}

	/** How many come: one, or two to three with Sparsus, or three to five with Sparsus and Maxima. */
	public static int count(Spell spell, java.util.Random random) {
		if (!spell.hasCharge("Sparsus")) return 1;
		return spell.hasCharge("Maxima") ? 3 + random.nextInt(3) : 2 + random.nextInt(2);
	}

	/** Maxima alone makes one great creature: twice the size, harder hits, a shorter stay. */
	private static boolean giant(Spell spell) {
		return spell.hasCharge("Maxima") && !spell.hasCharge("Sparsus");
	}

	/** Brings the creature (or creatures, or the masonry) into the world. Returns false if it could not. */
	public static boolean spawn(Cast cast, String name, double damage, double turns) {
		if (Masonry.structure(cast.spell) != null) return Masonry.build(cast);
		Spell spell = cast.spell;
		double life = turns;
		if (giant(spell)) life *= 0.6;
		if (spell.hasCharge("Minima")) life *= 1.3;
		if (spell.hasCharge("Duratus")) life *= 1.6;
		int n = count(spell, new java.util.Random());
		int made = 0;
		Body steed = mountFor(spell);
		for (int i = 0; i < n; i++) {
			Active rider = spawnOne(cast, name, damage, life, i, n);
			if (rider == null) continue;
			made++;
			// A weapon word with a creature word: the warrior comes riding the creature.
			if (steed != null) {
				Active mount = spawnBody(cast, "Steed of " + name, damage, life, i, n, steed);
				if (mount != null) {
					mount.mob.setCustomNameVisible(false);
					rider.mob.startRiding(mount.mob, true, true);
				}
			}
		}
		return made > 0 || cast.fail("The conjuration finds no purchase here.");
	}

	/** A defense spell's guardian: one creature that fights beside you and takes a share of your blows. */
	public static boolean guardian(Cast cast, double turns) {
		Spell spell = cast.spell;
		String name = (spell.label() == null ? "Spell" : spell.label()) + " Guardian";
		double damage = spell.code.primaryValue > 0 ? spell.code.primaryValue * 0.6 : spell.tier * 5.0;
		Active a = spawnOne(cast, name, damage, turns, 0, 1);
		if (a == null) return false;
		a.traits.add("guard");
		a.traits.add("taunt");
		return true;
	}

	/**
	 * The creature a warrior rides, when the spell has both a weapon word and a creature word that can
	 * carry one (a jockey). Birds, eagles and insects fly and carry no one; for those it is the warrior alone.
	 */
	public static Body mountFor(Spell spell) {
		if (warriorFor(spell) == null) return null;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("attribute") || !creatureWord(word.english)) continue;
			Body b = BODIES.get(word.english.toLowerCase(Locale.ROOT));
			if (b != null && !b.flies) return b;
		}
		return null;
	}

	private static Active spawnOne(Cast cast, String name, double damage, double turns, int index, int of) {
		return spawnBody(cast, name, damage, turns, index, of, bodyFor(cast.spell, name));
	}

	private static Active spawnBody(Cast cast, String name, double damage, double turns, int index, int of, Body body) {
		ServerPlayer player = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		String element = AttackForms.element(spell);
		Mob mob = variant(body, element).create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) return null;
		// Several come in an arc in front of the caster.
		double spread = of == 1 ? 0 : (index - (of - 1) / 2.0) * 1.6;
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : look.normalize();
		Vec3 at = player.position().add(look.scale(1.8)).add(-look.z * spread, 0, look.x * spread);
		mob.snapTo(at.x, player.getY() + (body.flies ? 1.2 : 0), at.z, player.getYRot(), 0);
		((com.omnispell.mixin.MobGoalsAccessor) mob).omnispell$targetSelector().removeAllGoals(goal -> true);
		mob.setTarget(null);
		mob.setPersistenceRequired();
		if (mob instanceof net.minecraft.world.entity.TamableAnimal pet) pet.tame(player);
		// A conjured bird is there to fight: it never settles on its caster's shoulder.
		((com.omnispell.mixin.MobGoalsAccessor) mob).omnispell$goalSelector().removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal);
		if (mob instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) golem.setPlayerCreated(true);
		if (mob instanceof net.minecraft.world.entity.monster.Vex vex) vex.setLimitedLife((int) Math.max(40, cast.turns(turns) + 20));
		if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) piglin.setImmuneToZombification(true);
		arm(mob, body, tintFor(spell));
		mob.setCustomName(Component.literal(name));
		mob.setCustomNameVisible(true);
		int tier = Math.max(1, Math.min(5, spell.tier));
		double size = giant(spell) ? 2.0 : spell.hasCharge("Minima") ? 0.65 : spell.hasCharge("Sparsus") && spell.hasCharge("Maxima") ? 1.1 : 1.0;
		double might = giant(spell) ? 1.6 : spell.hasCharge("Minima") ? 0.7 : 1.0;
		set(mob.getAttribute(Attributes.SCALE), body.scale * (0.85 + 0.06 * tier) * size);
		set(mob.getAttribute(Attributes.MAX_HEALTH), (14 + tier * 7) * (giant(spell) ? 2.0 : spell.hasCharge("Minima") ? 0.6 : 1.0));
		double biteDamage = Math.max(2.0, damage * cast.scale * cast.damageBonus(spell.code.element) * 0.6 * might);
		set(mob.getAttribute(Attributes.ATTACK_DAMAGE), biteDamage);
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), 32);
		if (giant(spell)) set(mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 0.8);
		mob.setHealth(mob.getMaxHealth());
		if (isInvisibleWord(spell)) mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, cast.turns(turns) + 40, 0, false, false));
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, cast.turns(turns) + 40, 0, false, false));
		Summons.track(mob, cast.turns(turns)); // before it enters the world, or it would be taken for a stray
		com.omnispell.net.OmniNet.ConjureTint mark = new com.omnispell.net.OmniNet.ConjureTint(mob.getId(), tintFor(spell), AttackForms.element(spell));
		MARKS.put(mob, mark);
		level.addFreshEntity(mob);
		for (ServerPlayer viewer : level.players()) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, mark);
		}
		Fx.play(level, spell.visual, Targeting.hand(player), mob.position());
		LivingEntity target = Targeting.entity(player, cast.range);
		double base = (spell.code.primaryValue > 0 ? spell.code.primaryValue : tier * 6.0) * might;
		Active active = new Active(mob, player.getUUID(), cast, body, base, target != null && target != mob && hostileTo(player, target) ? target : null);
		active.bite = (float) biteDamage;
		active.traits.addAll(traits(spell));
		if (body.weapon == null && body == flyingCreature(spell)) active.gift = gift(spell);
		ACTIVE.put(mob.getUUID(), active);
		return active;
	}

	/** Warriors get their weapon; the undead also get a cap in the spell's colour, which keeps the sun off them. */
	private static void arm(Mob mob, Body body, int tint) {
		if (body.weapon == null) return;
		net.minecraft.world.item.Item weapon = switch (body.weapon) {
			case "bow" -> net.minecraft.world.item.Items.BOW;
			case "sword", "dagger" -> mob instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton ? net.minecraft.world.item.Items.STONE_SWORD : net.minecraft.world.item.Items.IRON_SWORD;
			case "club" -> mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin ? net.minecraft.world.item.Items.GOLDEN_AXE : net.minecraft.world.item.Items.IRON_AXE;
			case "spear" -> net.minecraft.world.item.Items.TRIDENT;
			default -> null;
		};
		if (weapon != null) mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(weapon));
		if (mob.isInvertedHealAndHarm()) {
			net.minecraft.world.item.ItemStack cap = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET);
			cap.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(tint));
			cap.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
			mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, cap);
		}
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) mob.setDropChance(slot, 0f);
	}

	/**
	 * What the spell's own words promise beyond the creature itself. The Oracle writes the
	 * description; these are the promises it most often makes, and the creature keeps them.
	 */
	public static java.util.Set<String> traits(Spell spell) {
		java.util.Set<String> out = new java.util.HashSet<>();
		String d = ((spell.description == null ? "" : spell.description) + " " + (spell.effect == null ? "" : spell.effect)).toLowerCase(Locale.ROOT);
		boolean you = d.contains("you") || d.contains("caster") || d.contains("summoner") || d.contains("master");
		if (d.contains("health") && you && (d.contains("redirect") || d.contains("transfer") || d.contains("return") || d.contains("flow")
				|| d.contains("share") || d.contains("link") || d.contains("siphon") || d.contains("drain") || d.contains("back to"))) out.add("link");
		if ((d.contains("explod") || d.contains("detonat") || d.contains("burst") || d.contains("erupt"))
				&& (d.contains("death") || d.contains("dies") || d.contains("die ") || d.contains("destroy") || d.contains("fade") || d.contains("expire")
				|| d.contains("vanish") || d.contains("perish") || d.contains("slain") || d.contains("killed") || d.contains("dissipat"))) out.add("explode");
		if ((d.contains("heal") || d.contains("mend") || d.contains("restor")) && (d.contains("allies") || d.contains("nearby") || d.contains("companions") || d.contains("friends") || d.contains("party"))) out.add("allies");
		else if ((d.contains("heal") || d.contains("mend") || d.contains("restor")) && you && !out.contains("link")) out.add("mend");
		if ((d.contains("protect") || d.contains("guard") || d.contains("shield") || d.contains("defend") || d.contains("intercept") || d.contains("bodyguard")) && you) out.add("guard");
		if (d.contains("taunt") || d.contains("lure") || d.contains("distract") || d.contains("attention") || d.contains("draws enemies") || d.contains("aggro")) out.add("taunt");
		return out;
	}

	/** The book's account of what the conjuration really does. */
	public static String describe(Spell spell, String name) {
		if (Masonry.structure(spell) != null) return Masonry.describe(spell);
		Body body = bodyFor(spell, name);
		String what = switch (body.id) {
			case "archer" -> "an archer that keeps its distance and looses arrows tipped with its element";
			case "swordsman" -> "a swordsman";
			case "duelist" -> "a quick duelist with a blade";
			case "brute" -> "a brute with a heavy axe that knocks its foes back";
			case "spearman" -> "a spearman that hurls tridents and fights up close";
			case "serpent" -> "a serpent that swims through the air";
			case "eagle" -> "a great bird";
			case "spirit" -> "a spirit";
			case "golem" -> "a golem";
			default -> "a " + body.id;
		};
		String weaponGift = gift(spell);
		if (!weaponGift.isEmpty()) what += switch (weaponGift) {
			case "bow" -> " that looses arrows along with its breath";
			case "sword", "dagger" -> " with slashing claws that catch whatever stands beside its prey";
			case "club" -> " whose heavy blows throw its foes back";
			default -> " that hurls spears at what it hunts";
		};
		Body steed = mountFor(spell);
		if (steed != null) what += ", riding " + (steed.id.equals("cat") ? "a great cat" : "a " + steed.id);
		StringBuilder sb = new StringBuilder("It comes as " + what);
		if (spell.hasCharge("Sparsus")) sb.append(spell.hasCharge("Maxima") ? ", three to five of them at once" : ", two or three of them at once");
		else if (spell.hasCharge("Maxima")) sb.append(", twice the usual size: it hits much harder but does not stay as long");
		else if (spell.hasCharge("Minima")) sb.append(", small and weaker, but it stays longer");
		sb.append('.');
		if (body.weapon == null || !body.weapon.equals("bow")) sb.append(" It bites what is close and casts the spell's magic at what is not.");
		java.util.Set<String> t = traits(spell);
		if (t.contains("link")) sb.append(" When it falls or fades, a share of its life flows back to you, and so does a share of what it deals.");
		if (t.contains("explode")) sb.append(" When it falls or fades, it bursts.");
		if (t.contains("mend")) sb.append(" While it is near you, it mends you slowly.");
		if (t.contains("allies")) sb.append(" It slowly mends you and your allies nearby.");
		if (t.contains("guard")) sb.append(" While it is near you, it takes a share of the blows meant for you.");
		if (t.contains("taunt")) sb.append(" Hostile creatures nearby turn on it instead of you.");
		return sb.toString();
	}

	/** A guarding creature near its caster takes a share of a blow meant for them. Returns what is left for the caster. */
	public static float guard(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (ACTIVE.isEmpty() || amount <= 0) return amount;
		for (Active a : ACTIVE.values()) {
			if (!a.traits.contains("guard") || !a.owner.equals(player.getUUID()) || !a.mob.isAlive() || a.mob.distanceToSqr(player) > 8 * 8) continue;
			if (source.getEntity() == a.mob) continue;
			float share = amount * 0.35f;
			a.mob.hurtServer(player.level(), player.level().damageSources().generic(), share);
			Fx.line(player.level(), player.getBoundingBox().getCenter(), a.mob.getBoundingBox().getCenter(), tintFor(a.cast.spell), null, 2);
			return amount - share;
		}
		return amount;
	}

	private static boolean isInvisibleWord(Spell spell) {
		return spell.words.contains("attribute:Invisible");
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) attribute.setBaseValue(value);
	}

	/** Never its caster, another player, a familiar or another conjured creature. */
	private static boolean hostileTo(ServerPlayer owner, LivingEntity e) {
		if (e == null || !e.isAlive() || e instanceof Player) return false;
		if (com.omnispell.familiar.Familiars.isFamiliar(e) || isConjured(e)) return false;
		if (e instanceof net.minecraft.world.entity.OwnableEntity own && owner.getUUID().equals(own.getOwnerReference() == null ? null : own.getOwnerReference().getUUID())) return false;
		return e.level() == owner.level();
	}

	private static LivingEntity pickFoe(ServerPlayer owner, Active a) {
		if (a.foe != null && hostileTo(owner, a.foe) && a.foe.distanceToSqr(a.mob) < 28 * 28) return a.foe;
		for (LivingEntity c : new LivingEntity[]{owner.getLastHurtByMob(), owner.getLastHurtMob(), a.mob.getLastHurtByMob()}) {
			if (hostileTo(owner, c) && c.distanceToSqr(a.mob) < 24 * 24) return c;
		}
		LivingEntity nearest = null;
		double best = 12 * 12;
		for (LivingEntity e : a.mob.level().getEntitiesOfClass(LivingEntity.class, a.mob.getBoundingBox().inflate(12), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) {
			double d = e.distanceToSqr(a.mob);
			if (d < best && hostileTo(owner, e)) {
				best = d;
				nearest = e;
			}
		}
		return nearest;
	}

	/** Every tick: move, bite, cast. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (Active a : List.copyOf(ACTIVE.values())) {
			Mob mob = a.mob;
			ServerPlayer owner = server.getPlayerList().getPlayer(a.owner);
			if (mob.isRemoved() || !mob.isAlive()) {
				end(a, owner);
				ACTIVE.remove(mob.getUUID());
				continue;
			}
			if (owner == null || owner.level() != mob.level()) {
				Summons.dismiss(mob);
				ACTIVE.remove(mob.getUUID());
				continue;
			}
			ServerLevel level = owner.level();
			if (mob.getTarget() != null) mob.setTarget(null);
			if (mob instanceof net.minecraft.world.entity.animal.bee.Bee bee) bee.stopBeingAngry();
			if (a.biteReady > 0) a.biteReady--;
			if (a.boltReady > 0) a.boltReady--;
			if (mob.tickCount % 10 == 0) a.foe = pickFoe(owner, a);
			if (mob.tickCount % 8 == 0) aura(level, mob, a);
			if (mob.tickCount % 60 == 0) keepPromises(level, owner, a);
			if (a.shootReady > 0) a.shootReady--;
			String weapon = a.body.weapon == null ? "" : a.body.weapon;
			LivingEntity foe = a.foe;
			Vec3 goal;
			if (foe != null) {
				goal = foe.position().add(0, a.body.flies ? 1.5 : 0, 0);
				double reach = 1.8 + mob.getBbWidth() / 2 + foe.getBbWidth() / 2;
				double dist = Math.sqrt(mob.distanceToSqr(foe));
				mob.getLookControl().setLookAt(foe);
				if (weapon.equals("bow")) {
					// An archer stands off and shoots; it only closes in to find its range.
					if (dist <= 11 && mob.hasLineOfSight(foe)) {
						goal = null;
						mob.getNavigation().stop();
					}
					if (a.shootReady <= 0 && dist < 16 && mob.hasLineOfSight(foe)) {
						a.shootReady = Math.max(14, 30 - 3 * a.cast.spell.tier);
						shoot(a, foe);
					}
				} else {
					// A flying creature with a bow's gift looses arrows as it fights, as well as its breath.
					if (a.gift.equals("bow") && a.shootReady <= 0 && dist < 16 && mob.hasLineOfSight(foe)) {
						a.shootReady = Math.max(16, 34 - 3 * a.cast.spell.tier);
						shoot(a, foe);
					}
					if ((weapon.equals("spear") || a.gift.equals("spear")) && a.shootReady <= 0 && dist > 4 && dist < 14 && mob.hasLineOfSight(foe)) {
						a.shootReady = 50;
						throwSpear(a, foe);
					}
					String blow = weapon.isEmpty() && !a.gift.equals("bow") ? a.gift : weapon;
					if (dist <= reach && a.biteReady <= 0) {
						a.biteReady = switch (blow) {
							case "dagger" -> 10;
							case "club" -> 28;
							case "sword" -> 18;
							default -> 20;
						};
						level.broadcastEntityEvent(mob, (byte) 4); // the attack animation, for the bodies that have one
						mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
						float before = foe.getHealth();
						foe.hurtServer(level, level.damageSources().mobAttack(mob), blow.equals("dagger") ? a.bite * 0.6f : blow.equals("club") ? a.bite * 1.3f : a.gift.equals("sword") ? a.bite * 1.25f : a.bite);
						if (!a.gift.isEmpty() && (a.gift.equals("sword") || a.gift.equals("dagger"))) claw(a, foe, level, owner);
						if (mob instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton) foe.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0), mob);
						if (blow.equals("club")) {
							Vec3 away = foe.position().subtract(mob.position()).multiply(1, 0, 1);
							if (away.lengthSqr() > 0.01) foe.push(away.normalize().x * 0.9, 0.35, away.normalize().z * 0.9);
							foe.syncVelocity = true;
						}
						float bitten = Math.max(0, before - foe.getHealth());
						if (a.traits.contains("link")) owner.heal(bitten * 0.2f);
						soulLink(owner, bitten);
					}
					// The spell's own magic, cast at what it fights.
					if (a.boltReady <= 0 && dist < 14 && mob.hasLineOfSight(foe)) {
						a.boltReady = 40;
						bolt(a, foe);
					}
				}
			} else {
				Vec3 side = owner.getLookAngle().multiply(1, 0, 1);
				side = side.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : side.normalize();
				goal = owner.position().add(-side.z * 2.2, a.body.flies ? 1.8 : 0, side.x * 2.2);
			}
			if (goal != null) move(a, goal, foe != null);
		}
	}

	private static void move(Active a, Vec3 goal, boolean hunting) {
		Mob mob = a.mob;
		double far = mob.position().distanceToSqr(goal);
		if (!hunting && far > 28 * 28) {
			mob.snapTo(goal.x, goal.y, goal.z, mob.getYRot(), 0);
			return;
		}
		if (mob instanceof ConjuredSerpent || mob instanceof net.minecraft.world.entity.monster.Vex) {
			// The serpent and the spirits swim through the air straight to where they want to be.
			Vec3 to = goal.subtract(mob.position());
			double dist = to.length();
			if (mob instanceof net.minecraft.world.entity.monster.Vex vex) {
				vex.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, hunting ? 1.0 : 0.6);
				return;
			}
			Vec3 v = dist < 1.2 ? mob.getDeltaMovement().scale(0.7) : to.normalize().scale(Math.min(0.42, 0.12 + dist * 0.05));
			double wave = Math.sin(mob.tickCount * 0.25) * 0.05;
			mob.setDeltaMovement(v.x, v.y + wave, v.z);
			if (v.horizontalDistanceSqr() > 0.0004) {
				float yaw = (float) (Math.toDegrees(Math.atan2(-v.x, v.z)));
				mob.setYRot(yaw);
				mob.yBodyRot = yaw;
				mob.yHeadRot = yaw;
			}
			mob.syncVelocity = true;
			return;
		}
		if (mob.tickCount % 5 == 0 && far > 2.5 * 2.5) mob.getNavigation().moveTo(goal.x, goal.y, goal.z, hunting ? 1.35 : 1.1);
	}

	/** The creature casts its spell: the spell's look, its damage, its element and its form. */
	private static void bolt(Active a, LivingEntity foe) {
		Cast cast = a.cast;
		Mob mob = a.mob;
		Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
		if (sonic(cast.spell)) {
			// A blast of sound like the warden's, in the creature's own colour, and a good deal gentler.
			Vec3 to = foe.getBoundingBox().getCenter();
			Vec3 step = to.subtract(from);
			int n = Math.max(1, (int) Math.ceil(step.length()));
			net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(tintFor(cast.spell), 1.6f);
			// Rings of the creature's colour along the way, like the warden's shockwaves (whose own particle is always blue).
			Vec3 dir = step.normalize();
			Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 ringA = dir.cross(up).normalize();
			Vec3 ringB = dir.cross(ringA).normalize();
			for (int i = 1; i <= n; i++) {
				Vec3 q = from.add(step.scale(i / (double) n));
				double r = 0.35 + 0.25 * Math.sin(i * 0.9);
				for (int k = 0; k < 10; k++) {
					double t = k * Math.PI * 2 / 10;
					Vec3 o = q.add(ringA.scale(Math.cos(t) * r)).add(ringB.scale(Math.sin(t) * r));
					cast.level.sendParticles(dust, o.x, o.y, o.z, 1, 0, 0, 0, 0);
				}
				cast.level.sendParticles(dust, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0);
			}
			cast.level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.3f);
		} else {
			Fx.play(cast.level, cast.spell.visual, from, foe.getBoundingBox().getCenter());
			Fx.sound(cast.level, mob.position(), cast.spell.code.element, Math.max(1, cast.spell.visual.intensity - 1));
		}
		foe.setInvulnerableTime(0);
		float before = foe.getHealth();
		CombatEffects.strike(cast, foe, a.base * 0.35, false);
		ServerPlayer owner = cast.player;
		if (owner.isAlive()) soulLink(owner, Math.max(0, before - foe.getHealth()));
		Spell.EffectCode code = cast.spell.code;
		if (code.status != null && !code.status.equals("none") && !code.status.equals("null") && foe.isAlive()) CombatEffects.status(cast, foe, code.status, 1);
		if (foe.isAlive() && mob.getRandom().nextFloat() < 0.3f) AttackForms.apply(cast, foe, a.base * 0.35);
	}

	/** Slashing claws: a clear slash across the foe that catches anything hostile beside it. */
	private static void claw(Active a, LivingEntity foe, ServerLevel level, ServerPlayer owner) {
		Mob mob = a.mob;
		Vec3 at = foe.getBoundingBox().getCenter();
		Vec3 facing = foe.position().subtract(mob.position()).multiply(1, 0, 1);
		facing = facing.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : facing.normalize();
		Vec3 across = new Vec3(-facing.z, 0, facing.x);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(tintFor(a.cast.spell), 1.0f);
		for (int line = -1; line <= 1; line++) { // three claw marks, raked diagonally
			for (int k = -4; k <= 4; k++) {
				Vec3 q = at.add(across.scale(k * 0.18)).add(0, k * 0.14 + line * 0.22, 0).add(facing.scale(-0.4));
				level.sendParticles(dust, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, foe.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.2f);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(1.6), e -> e != foe && e != mob && e.isAlive() && hostileTo(owner, e))) {
			near.hurtServer(level, level.damageSources().mobAttack(mob), a.bite * 0.5f);
		}
	}

	/** An archer's arrow, tipped with the spell's element. */
	private static void shoot(Active a, LivingEntity foe) {
		Mob mob = a.mob;
		ServerLevel level = a.cast.level;
		net.minecraft.world.entity.projectile.arrow.Arrow arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, mob,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
		double dx = foe.getX() - mob.getX();
		double dy = foe.getY(0.33) - arrow.getY();
		double dz = foe.getZ() - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + flat * 0.18, dz, 1.7f, 4f);
		arrow.setBaseDamage(Math.max(1.0, a.bite * 0.45));
		arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
		AttackForms.tip(arrow, AttackForms.element(a.cast.spell), a.cast.spell.tier);
		level.addFreshEntity(arrow);
		Tasks.later(100, () -> {
			if (!arrow.isRemoved()) arrow.discard();
		});
		level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.1f);
	}

	/** A spearman's thrown trident. It is gone again a few seconds later. */
	private static void throwSpear(Active a, LivingEntity foe) {
		Mob mob = a.mob;
		ServerLevel level = a.cast.level;
		var spear = new net.minecraft.world.entity.projectile.arrow.ThrownTrident(level, mob, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
		double dx = foe.getX() - mob.getX();
		double dy = foe.getY(0.33) - spear.getY();
		double dz = foe.getZ() - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		spear.shoot(dx, dy + flat * 0.2, dz, 1.6f, 3f);
		spear.setBaseDamage(Math.max(2.0, a.bite * 0.8));
		spear.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
		level.addFreshEntity(spear);
		Tasks.later(60, () -> {
			if (!spear.isRemoved()) spear.discard();
		});
		level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.DROWNED_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.0f);
	}

	/** Every three seconds: the promises in its description that work over time. */
	private static void keepPromises(ServerLevel level, ServerPlayer owner, Active a) {
		Mob mob = a.mob;
		if (a.traits.contains("mend") && mob.distanceToSqr(owner) < 10 * 10 && owner.getHealth() < owner.getMaxHealth()) {
			owner.heal(1.0f + 0.25f * a.cast.spell.tier);
			Fx.line(level, mob.getBoundingBox().getCenter(), owner.getBoundingBox().getCenter(), 0x7CF08A, net.minecraft.core.particles.ParticleTypes.HEART, 1);
		}
		if (a.traits.contains("allies")) {
			for (LivingEntity friend : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(6), e -> e != mob && Defenses.ally(owner, e))) {
				if (friend.getHealth() < friend.getMaxHealth()) friend.heal(1.0f + 0.25f * a.cast.spell.tier);
			}
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 2, 0.5, 2, 0);
		}
		if (a.traits.contains("taunt")) {
			for (Mob hunter : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(10), e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.getTarget() == owner)) {
				if (!MobControl.immune(hunter)) hunter.setTarget(mob);
			}
		}
	}

	/** A conjured creature is gone, slain or faded. Some leave something behind. */
	private static void end(Active a, ServerPlayer owner) {
		if (a.ended) return;
		a.ended = true;
		Mob mob = a.mob;
		ServerLevel level = a.cast.level;
		if (a.traits.contains("explode")) {
			Vec3 at = mob.position();
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y + 0.8, at.z, 2, 0.4, 0.4, 0.4, 0);
			level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.2f);
			if (owner != null) {
				for (LivingEntity e : Targeting.around(owner, at, 3.5, false)) {
					if (!hostileTo(owner, e)) continue;
					CombatEffects.strike(a.cast, e, a.base * 0.5, false);
				}
			}
		}
		if (a.traits.contains("link") && owner != null && owner.isAlive() && owner.level() == level) {
			owner.heal((float) (mob.getMaxHealth() * 0.25));
			Fx.line(level, mob.getBoundingBox().getCenter(), owner.getBoundingBox().getCenter(), 0xE03A5A, net.minecraft.core.particles.ParticleTypes.HEART, 2);
		}
	}

	/** Soul Link (from the spell's toggles): a share of what your creatures deal comes back to you as health. */
	public static void soulLink(ServerPlayer owner, float dealt) {
		double share = Modifiers.value(owner, "soul_link");
		if (share > 0 && dealt > 0 && owner.isAlive()) owner.heal((float) (dealt * share / 100.0));
	}

	/** A faint haze of its element, so you can tell a fire wolf from a frost one at a glance. */
	private static void aura(ServerLevel level, Mob mob, Active a) {
		var particle = switch (AttackForms.element(a.cast.spell)) {
			case "fire" -> net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
			case "ice" -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
			case "water" -> net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER;
			case "wind" -> net.minecraft.core.particles.ParticleTypes.CLOUD;
			case "storm" -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
			case "stone" -> net.minecraft.core.particles.ParticleTypes.ASH;
			case "nature" -> net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR;
			case "light" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
			case "shadow" -> net.minecraft.core.particles.ParticleTypes.SMOKE;
			case "void" -> net.minecraft.core.particles.ParticleTypes.PORTAL;
			case "blood" -> net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR;
			default -> net.minecraft.core.particles.ParticleTypes.WITCH;
		};
		level.sendParticles(particle, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.01);
	}

	/** Conjured creatures never hurt their caster, other players, or each other. */
	public static boolean spares(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		Entity attacker = source.getEntity();
		if (!isConjured(attacker)) return false;
		return victim instanceof Player || isConjured(victim) || com.omnispell.familiar.Familiars.isFamiliar(victim);
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
