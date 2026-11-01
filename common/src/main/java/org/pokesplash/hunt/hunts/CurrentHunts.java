package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.broadcast.BroadcastType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CurrentHunts {
	private final UUID owner; // The owner of the current hunts.
	private HashMap<UUID, SingleHunt> hunts; // List of current hunts.
	private HashMap<UUID, Species> species; // List of species.

	/**
	 * Constructor that generates a bunch of hunts when the server starts.
	 */
	public CurrentHunts(UUID owner) {
		this.owner = owner;
		hunts = new HashMap<>();
		species = new HashMap<>();
	}

	/**
	 * Initializes hashmap with hunts.
	 */
	public void init() {
		if (owner == null && Hunt.config.isIndividualHunts()) {
			return;
		}

		while (hunts.size() < Hunt.config.getHuntAmount()) {
			if (addHunt() == null) {
				break; // No more species available.
			}
		}
	}

	/**
	 * Gets a hunt with the given UUID.
	 * @param uuid the UUID of the hunt to fetch.
	 * @return the hunt or null.
	 */
	public SingleHunt getHunt(UUID uuid) {
		return hunts.get(uuid);
	}

	/**
	 * Adds a new hunt to the current hunts.
	 * @return the new hunt, or null if the hunt limit is reached or no species are available.
	 */
	public SingleHunt addHunt() {
		// If the maximum hunt amount is reached, don't add another.
		if (hunts.size() >= Hunt.config.getHuntAmount()) {
			return null;
		}

		// Species already being hunted can't be picked again.
		Set<Species> taken = new HashSet<>(species.values());

		Bucket bucket = Hunt.config.getRarity().getRandomRarity(b -> hasAvailableSpecies(b, taken));
		if (bucket == null) {
			Hunt.LOGGER.warn("Could not create a new hunt: no eligible species are left. "
					+ "Check the blacklist and rarity settings.");
			return null;
		}

		List<Species> candidates = Hunt.spawnRates.getEligibleSpecies(bucket).stream()
				.filter(s -> !taken.contains(s))
				.toList();
		Species chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

		SingleHunt hunt = new SingleHunt(owner, chosen, bucket);

		// If the config setting is enabled, send the broadcast.
		if (Hunt.config.isIndividualHunts()) {
			ServerPlayer player = getOnlineOwner();
			if (player != null &&
					Hunt.permissions.hasPermission(player, Hunt.permissions.getPermission("HuntNotify"))) {
				Hunt.broadcaster.sendPlayerMessage(owner, BroadcastType.STARTED, null, hunt.getPokemon(),
						hunt.getPrice());
			}
		} else if (Hunt.config.isSendHuntBeginMessage()) {
			Hunt.broadcaster.sendBroadcast(BroadcastType.STARTED, null, hunt.getPokemon(), hunt.getPrice());
		}

		species.put(hunt.getId(), chosen);
		hunts.put(hunt.getId(), hunt);
		return hunt;
	}

	private static boolean hasAvailableSpecies(Bucket bucket, Set<Species> taken) {
		for (Species s : Hunt.spawnRates.getEligibleSpecies(bucket)) {
			if (!taken.contains(s)) {
				return true;
			}
		}
		return false;
	}

	private ServerPlayer getOnlineOwner() {
		if (owner == null || Hunt.server == null) {
			return null;
		}
		return Hunt.server.getPlayerList().getPlayer(owner);
	}

	/**
	 * Removes a hunt from the list.
	 * @param id the ID of the hunt to remove.
	 * @return true if successfully removed.
	 */
	public SingleHunt removeHunt(UUID id, boolean broadcast) {
		SingleHunt removedHunt = hunts.remove(id);
		if (removedHunt != null) {
			species.remove(id);

			// If broadcasts are enabled and the method call wants it broadcast, send it.
			if (Hunt.config.isIndividualHunts()) {
				ServerPlayer player = getOnlineOwner();
				if (player != null &&
						Hunt.permissions.hasPermission(player, Hunt.permissions.getPermission("HuntNotify"))) {
					Hunt.broadcaster.sendPlayerMessage(owner, BroadcastType.ENDED, null, removedHunt.getPokemon(),
							removedHunt.getPrice());
				}
			} else {
				if (Hunt.config.isSendHuntEndMessage() && broadcast) {
					Hunt.broadcaster.sendBroadcast(BroadcastType.ENDED, null, removedHunt.getPokemon(),
							removedHunt.getPrice());
				}
			}

		}
		return removedHunt;
	}

	/**
	 * Removes a hunt with the ID and adds a new one.
	 * @param id the ID of the hunt to remove.
	 * @return the new Hunt replacement.
	 */
	public ReplacedHunt replaceHunt(UUID id, boolean broadcast) {
		SingleHunt oldHunt = removeHunt(id, broadcast);

		if (oldHunt != null) {
			return new ReplacedHunt(oldHunt, addHunt());
		}
		return null;
	}

	/**
	 * Checks that a given pokemon matches one from the current hunt pool.
	 * @param pokemon The pokemon to check.
	 * @return UUID of the hunt that matches the hunt.
	 */
	public UUID matches(Pokemon pokemon) {
		for (SingleHunt hunt : hunts.values()) {
			if (hunt.matches(pokemon)) {
				return hunt.getId();
			}
		}
		return null;
	}

	/**
	 * Gets the price of the specified hunt.
	 * @param uuid The hunt UUID to check.
	 * @return The price of the hunt.
	 */
	public double getPrice(UUID uuid) {
		SingleHunt hunt = hunts.get(uuid);
		if (hunt != null) {
			return hunt.getPrice();
		}
		return -1;
	}


	/**
	 * Gets all of the hunts.
	 * @return
	 */
	public HashMap<UUID, SingleHunt> getHunts() {
		return hunts;
	}

	public ArrayList<SingleHunt> getHuntValues() {
        return new ArrayList<>(hunts.values());
	}
}
