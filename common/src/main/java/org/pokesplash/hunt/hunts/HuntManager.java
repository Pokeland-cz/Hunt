package org.pokesplash.hunt.hunts;

import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Holds the hunts of each player when individual hunts are enabled.
 * All methods must be called from the server thread.
 */
public class HuntManager {
	private final HashMap<UUID, CurrentHunts> playerHunts;

	public HuntManager() {
		playerHunts = new HashMap<>();
	}

	/**
	 * Gets the hunts of a player.
	 * @param player the player's UUID.
	 * @return the player's hunts, or null if they don't have any yet.
	 */
	public CurrentHunts getPlayerHunts(UUID player) {
		return playerHunts.get(player);
	}

	/**
	 * Gets the hunts of a player, creating them if they don't exist yet.
	 * @param player the player's UUID.
	 * @return the player's hunts, or null if individual hunts are disabled.
	 */
	public CurrentHunts getOrCreatePlayerHunts(UUID player) {
		if (!playerHunts.containsKey(player)) {
			addPlayer(player);
		}
		return playerHunts.get(player);
	}

	/**
	 * Puts saved hunts back for a player.
	 */
	public void restorePlayerHunts(UUID player, CurrentHunts hunts) {
		playerHunts.put(player, hunts);
	}

	public HashSet<UUID> getPlayers() {
		return new HashSet<>(playerHunts.keySet());
	}

	public ArrayList<SingleHunt> getHunts() {
		ArrayList<SingleHunt> hunts = new ArrayList<>();
		for (CurrentHunts current : playerHunts.values()) {
			hunts.addAll(current.getHunts().values());
		}
		return hunts;
	}

	/**
	 * Replaces expired hunts of online players. Offline players' hunts are left alone
	 * and get replaced once they are back online.
	 */
	public void checkOnlinePlayers() {
		for (UUID uuid : getPlayers()) {
			if (Hunt.server.getPlayerList().getPlayer(uuid) != null) {
				playerHunts.get(uuid).getHuntValues().forEach(SingleHunt::check);
			}
		}
	}

	/**
	 * Gives a player their hunts: creates them for a new player, or trims / tops up the hunts of an
	 * existing player to the configured amount (it may have changed since they were made).
	 */
	public void addPlayer(UUID player) {
		if (!Hunt.config.isIndividualHunts()) {
			return;
		}
		CurrentHunts hunts = playerHunts.get(player);
		if (hunts == null) {
			hunts = new CurrentHunts(player);
			// Register before generating, so the player has hunts even if generation partially fails.
			playerHunts.put(player, hunts);
		} else {
			hunts.trimToLimit();
		}
		hunts.init();
	}

	public void init() {
		for (ServerPlayer player : Hunt.server.getPlayerList().getPlayers()) {
			addPlayer(player.getUUID());
		}
	}
}
