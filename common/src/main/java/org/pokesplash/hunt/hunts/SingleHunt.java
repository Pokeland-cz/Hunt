package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.config.CustomPrice;
import org.pokesplash.hunt.config.RewardConfig;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
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
	 * Creates a new hunt for the given species.
	 * @param owner the player who owns the hunt, or null for global hunts.
	 * @param species the species to hunt.
	 * @param bucket the rarity bucket of the species.
	 */
	public SingleHunt(UUID owner, Species species, Bucket bucket) {
		id = UUID.randomUUID();
		this.owner = owner;
		this.bucket = bucket;

		pokemon = new Pokemon();
		pokemon.setSpecies(species);

		RewardConfig reward = Hunt.config.getRewards().get(bucket);
		price = reward.getPrice();
		commands = reward.getCommands();

		// Checks for a custom price.
		List<CustomPrice> customPrices = Hunt.config.getCustomPrices();
		for (CustomPrice item : customPrices) {
			// If species match
			if (item.getSpecies().trim().equalsIgnoreCase(pokemon.getSpecies().getName().trim())) {
				// If no form is given or the form matches, use price.
				if (item.getForm().trim().equalsIgnoreCase("") ||
						item.getForm().trim().equalsIgnoreCase(pokemon.getForm().getName().trim())) {
					price = item.getReward().getPrice();
					commands = item.getReward().getCommands();
					break;
				}
			}
		}

		pokemon.rollAbility();
		pokemon.checkGender();

		// The hunt ends after the configured duration (in minutes).
		endtime = System.currentTimeMillis() + Hunt.config.getHuntDuration() * 60_000L;
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
		DecimalFormat df = new DecimalFormat("0.##");
		return df.format(price);
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
