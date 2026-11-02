package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.config.CustomPrice;
import org.pokesplash.hunt.config.RewardConfig;
import org.pokesplash.hunt.util.Placeholders;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SingleHunt {

	private final UUID id; // Unique ID to reference hunt by.
	private final UUID owner; // Player who owns the hunt
	private double price; // hunt prize amount.
	private ArrayList<String> commands; // Commands for completing the hunt.
	private Pokemon pokemon; // Pokemon being hunted.
	private final long endtime; // The end date for the hunt.
	private Bucket bucket; // The spawn bucket of the Pokemon.

	/**
	 * Creates a new hunt for the given species and form.
	 * @param owner the player who owns the hunt, or null for global hunts.
	 * @param target the species and form to hunt.
	 * @param bucket the rarity bucket of the target.
	 */
	public SingleHunt(UUID owner, HuntTarget target, Bucket bucket) {
		id = UUID.randomUUID();
		this.owner = owner;
		this.bucket = bucket;

		pokemon = new Pokemon();
		pokemon.setSpecies(target.species());
		// Forms are driven by aspects, so forcing the form's aspects gives e.g. an Alolan Vulpix.
		if (!target.form().getAspects().isEmpty()) {
			pokemon.setForcedAspects(new HashSet<>(target.form().getAspects()));
		}

		RewardConfig reward = Hunt.config.getRewards().get(bucket);
		price = reward.getPrice();
		commands = reward.getCommands();

		CustomPrice customPrice = findCustomPrice(pokemon);
		if (customPrice != null) {
			price = customPrice.getReward().getPrice();
			commands = customPrice.getReward().getCommands();
		}

		pokemon.rollAbility();
		pokemon.checkGender();

		// The hunt ends after the configured duration (in minutes).
		endtime = System.currentTimeMillis() + Hunt.config.getHuntDuration() * 60_000L;
	}

	/**
	 * Restores a saved hunt.
	 */
	private SingleHunt(UUID id, UUID owner, Pokemon pokemon, Bucket bucket, double price,
	                   ArrayList<String> commands, long endtime) {
		this.id = id;
		this.owner = owner;
		this.pokemon = pokemon;
		this.bucket = bucket;
		this.price = price;
		this.commands = commands;
		this.endtime = endtime;
	}

	/**
	 * Finds the custom price for a Pokemon. An entry for its exact form wins over an entry for the
	 * whole species (no form given).
	 */
	private static CustomPrice findCustomPrice(Pokemon pokemon) {
		String species = pokemon.getSpecies().getName().trim();
		String form = pokemon.getForm().getName().trim();
		CustomPrice speciesMatch = null;

		for (CustomPrice item : Hunt.config.getCustomPrices()) {
			if (!item.getSpecies().trim().equalsIgnoreCase(species)) {
				continue;
			}
			String itemForm = item.getForm() == null ? "" : item.getForm().trim();
			if (itemForm.equalsIgnoreCase(form)) {
				return item;
			}
			if (itemForm.isEmpty() && speciesMatch == null) {
				speciesMatch = item;
			}
		}
		return speciesMatch;
	}

	/**
	 * Saves the hunt to JSON.
	 */
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id.toString());
		if (owner != null) {
			json.addProperty("owner", owner.toString());
		}
		json.addProperty("bucket", bucket.name());
		json.addProperty("price", price);
		JsonArray commandArray = new JsonArray();
		if (commands != null) {
			commands.forEach(commandArray::add);
		}
		json.add("commands", commandArray);
		json.addProperty("endtime", endtime);
		json.add("pokemon", savePokemon(pokemon));
		return json;
	}

	/**
	 * Saves only what a hunt needs (what's matched and displayed), not the whole Pokemon:
	 * about 100 bytes instead of 1-2 KB per hunt.
	 */
	private static JsonObject savePokemon(Pokemon pokemon) {
		JsonObject json = new JsonObject();
		json.addProperty("species", pokemon.getSpecies().getResourceIdentifier().toString());
		JsonArray aspects = new JsonArray();
		pokemon.getForcedAspects().forEach(aspects::add); // Forced aspects select the form.
		json.add("aspects", aspects);
		json.addProperty("ability", pokemon.getAbility().getName());
		json.addProperty("gender", pokemon.getGender().name());
		json.addProperty("nature", pokemon.getNature().getName().toString());
		json.addProperty("shiny", pokemon.getShiny());
		return json;
	}

	/**
	 * Rebuilds a Pokemon saved with {@link #savePokemon}.
	 */
	private static Pokemon loadPokemon(JsonObject json) {
		Species species = PokemonSpecies.getByIdentifier(ResourceLocation.parse(json.get("species").getAsString()));
		if (species == null) {
			throw new IllegalArgumentException("Unknown species " + json.get("species").getAsString());
		}

		Pokemon pokemon = new Pokemon();
		pokemon.setSpecies(species);

		Set<String> aspects = new HashSet<>();
		json.getAsJsonArray("aspects").forEach(aspect -> aspects.add(aspect.getAsString()));
		if (!aspects.isEmpty()) {
			pokemon.setForcedAspects(aspects);
		}

		pokemon.setGender(Gender.valueOf(json.get("gender").getAsString()));
		pokemon.setShiny(json.get("shiny").getAsBoolean());

		Nature nature = Natures.getNature(ResourceLocation.parse(json.get("nature").getAsString()));
		if (nature != null) {
			pokemon.setNature(nature);
		}

		AbilityTemplate ability = Abilities.get(json.get("ability").getAsString());
		if (ability != null) {
			pokemon.updateAbility(ability.create(false, Priority.LOWEST));
		} else {
			pokemon.rollAbility();
		}
		return pokemon;
	}

	/**
	 * Loads a hunt saved with {@link #toJson}.
	 * @throws RuntimeException if the data is invalid, e.g. the species no longer exists.
	 */
	public static SingleHunt fromJson(JsonObject json) {
		UUID id = UUID.fromString(json.get("id").getAsString());
		UUID owner = json.has("owner") ? UUID.fromString(json.get("owner").getAsString()) : null;
		Bucket bucket = Bucket.valueOf(json.get("bucket").getAsString());
		double price = json.get("price").getAsDouble();
		ArrayList<String> commands = new ArrayList<>();
		json.getAsJsonArray("commands").forEach(element -> commands.add(element.getAsString()));
		long endtime = json.get("endtime").getAsLong();
		Pokemon pokemon = loadPokemon(json.getAsJsonObject("pokemon"));
		return new SingleHunt(id, owner, pokemon, bucket, price, commands, endtime);
	}

	/**
	 * Checks that the hunt is still valid.
	 */
	public void check() {

		if (endtime > System.currentTimeMillis()) {
			return;
		}

		CurrentHunts hunts = Hunt.config.isIndividualHunts() ? Hunt.manager.getPlayerHunts(owner) : Hunt.hunts;
		if (hunts != null) {
			hunts.replaceHunt(id, true);
		}
	}

	/**
	 * Getters
	 */
	public UUID getOwner() {
		return owner;
	}

	public UUID getId() {
		return id;
	}

	public double getPrice() {
		return price;
	}

	public String getPriceAsString() {
		return Placeholders.formatPrice(price);
	}

	public Pokemon getPokemon() {
		return pokemon;
	}

	public long getEndtime() {
		return endtime;
	}

	public ArrayList<String> getCommands() {
		return commands;
	}

	public Bucket getBucket() {
		return bucket;
	}

	/**
	 * Checks that a given pokemon matches the one in the listing.
	 * @param pokemon The pokemon to check.
	 * @return true if the pokemon matches the hunt, or false if it doesn't.
	 */

	public boolean matches(Pokemon pokemon) {
		// Checks the species and form match.
		if (!pokemon.getSpecies().getName().equalsIgnoreCase(this.pokemon.getSpecies().getName())) {
			return false;
		}
		if (!pokemon.getForm().getName().equalsIgnoreCase(this.getPokemon().getForm().getName())) {
			return false;
		}

		// Checks for ability, if enabled.
		if (Hunt.config.getMatchProperties().isAbility()) {
			if (!pokemon.getAbility().getName().equalsIgnoreCase(this.pokemon.getAbility().getName())) {
				return false;
			}
		}

		// Checks gender, if enabled.
		if (Hunt.config.getMatchProperties().isGender()) {
			if (!pokemon.getGender().name().equalsIgnoreCase(this.pokemon.getGender().name())) {
				return false;
			}
		}

		// Checks nature, if enabled.
		if (Hunt.config.getMatchProperties().isNature()) {
			if (!pokemon.getNature().getName().getPath().equalsIgnoreCase(this.pokemon.getNature().getName().getPath())) {
				return false;
			}
		}

		// Checks shiny, if enabled.
		if (Hunt.config.getMatchProperties().isShiny()) {
			return pokemon.getShiny() == this.pokemon.getShiny();
		}
		return true;
	}

}
