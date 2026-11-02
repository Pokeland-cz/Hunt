package org.pokesplash.hunt.hunts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.util.FileStore;
import org.pokesplash.hunt.util.Utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saves the current hunts to config/hunt/hunts.json so they survive a restart.
 * Changes only mark the data as dirty; it's written at most every 30 seconds on a background
 * thread, and synchronously when the server stops.
 */
public class HuntStorage {
	private static final String FILE = "hunts.json";
	private static final int VERSION = 1;

	private boolean dirty = false;
	// False if hunts.json exists but couldn't be read: then the file must not be overwritten.
	private boolean writable = true;
	private boolean lockLogged = false;

	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (dirty && canSave(false)) {
			dirty = false;
			FileStore.writeAsync(FILE, serialize());
		}
	}

	/**
	 * Queues hunts.json to be written right away, without waiting for the 30 second interval.
	 * Used after a hunt is completed, so a crash can't bring the completed hunt back.
	 */
	public void saveSoon() {
		if (canSave(false)) {
			dirty = false;
			FileStore.writeAsync(FILE, serialize());
		}
	}

	public void saveNow() {
		if (!canSave(true)) {
			return;
		}
		dirty = false;
		FileStore.writeSync(FILE, serialize());
	}

	/**
	 * @return false if hunts.json couldn't be read, so it isn't overwritten. Logs that once per run,
	 * or every time if always is true.
	 */
	private boolean canSave(boolean always) {
		if (!writable && (always || !lockLogged)) {
			lockLogged = true;
			Hunt.LOGGER.error("Hunts were not saved: " + FILE + " could not be read at startup.");
		}
		return writable;
	}

	private String serialize() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);

		JsonArray global = new JsonArray();
		for (SingleHunt hunt : Hunt.hunts.getHuntValues()) {
			global.add(hunt.toJson());
		}
		root.add("global", global);

		JsonObject players = new JsonObject();
		for (UUID player : Hunt.manager.getPlayers()) {
			JsonArray playerHunts = new JsonArray();
			for (SingleHunt hunt : Hunt.manager.getPlayerHunts(player).getHuntValues()) {
				playerHunts.add(hunt.toJson());
			}
			players.add(player.toString(), playerHunts);
		}
		root.add("players", players);

		return Utils.newGson().toJson(root);
	}

	/**
	 * Loads saved hunts into Hunt.hunts and Hunt.manager. Hunts that can't be loaded
	 * (e.g. their species was removed) are skipped, and so are hunts the payout journal says were
	 * already completed (hunts.json may not have been saved before a crash).
	 * @param completed ids of hunts that were already completed.
	 */
	public void load(Set<UUID> completed) {
		writable = true;
		lockLogged = false;
		String data;
		switch (FileStore.read(FILE)) {
			case FileStore.Ok ok -> data = ok.content();
			case FileStore.Missing missing -> {
				return;
			}
			case FileStore.Failed failed -> {
				writable = false;
				Hunt.LOGGER.error(FILE + " could not be read (" + failed.error() + "). New hunts are generated, "
						+ "but they will NOT be saved this run, so the file isn't overwritten. "
						+ "Fix the file (permissions) and restart the server.");
				return;
			}
		}

		JsonObject root;
		try {
			root = JsonParser.parseString(data).getAsJsonObject();
		} catch (Exception e) {
			Hunt.LOGGER.error("Could not parse " + FILE + ", starting with new hunts.", e);
			FileStore.backupBroken(FILE);
			return;
		}

		int loaded = 0;
		int skipped = 0;
		int alreadyCompleted = 0;

		if (root.has("global")) {
			for (JsonElement element : root.getAsJsonArray("global")) {
				SingleHunt hunt = parse(element);
				if (hunt != null && completed.contains(hunt.getId())) {
					alreadyCompleted++;
				} else if (hunt != null && Hunt.hunts.restoreHunt(hunt)) {
					loaded++;
				} else {
					skipped++;
				}
			}
		}

		if (root.has("players")) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("players").entrySet()) {
				UUID player;
				try {
					player = UUID.fromString(entry.getKey());
				} catch (IllegalArgumentException e) {
					continue;
				}

				CurrentHunts playerHunts = new CurrentHunts(player);
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					SingleHunt hunt = parse(element);
					if (hunt != null && completed.contains(hunt.getId())) {
						alreadyCompleted++;
					} else if (hunt != null && playerHunts.restoreHunt(hunt)) {
						loaded++;
					} else {
						skipped++;
					}
				}
				Hunt.manager.restorePlayerHunts(player, playerHunts);
			}
		}

		if (skipped > 0 || alreadyCompleted > 0) {
			// Some saved hunts were dropped, so save the cleaned-up state.
			markDirty();
		}
		if (alreadyCompleted > 0) {
			Hunt.LOGGER.warn("Dropped " + alreadyCompleted + " saved hunts that the payout log says were already "
					+ "completed (" + FILE + " was not saved after they were completed).");
		}
		Hunt.LOGGER.info("Loaded " + loaded + " saved hunts" + (skipped > 0 ? " (" + skipped + " skipped)." : "."));
	}

	private static SingleHunt parse(JsonElement element) {
		try {
			return SingleHunt.fromJson(element.getAsJsonObject());
		} catch (Exception e) {
			Hunt.LOGGER.warn("Skipping a saved hunt that could not be loaded: " + e.getMessage());
			return null;
		}
	}
}
