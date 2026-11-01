package org.pokesplash.hunt.hunts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.util.FileStore;
import org.pokesplash.hunt.util.Utils;

import java.util.Map;
import java.util.UUID;

/**
 * Saves the current hunts to config/hunt/hunts.json so they survive a restart.
 * Changes only mark the data as dirty; it's written at most once per hunt check (every second)
 * on a background thread, and synchronously when the server stops.
 */
public class HuntStorage {
	private static final String FILE = "hunts.json";
	private static final int VERSION = 1;

	private boolean dirty = false;

	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (dirty) {
			dirty = false;
			FileStore.writeAsync(FILE, serialize());
		}
	}

	public void saveNow() {
		dirty = false;
		FileStore.writeSync(FILE, serialize());
	}

	private String serialize() {
		RegistryAccess registryAccess = Hunt.server.registryAccess();
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);

		JsonArray global = new JsonArray();
		for (SingleHunt hunt : Hunt.hunts.getHuntValues()) {
			global.add(hunt.toJson(registryAccess));
		}
		root.add("global", global);

		JsonObject players = new JsonObject();
		for (UUID player : Hunt.manager.getPlayers()) {
			JsonArray playerHunts = new JsonArray();
			for (SingleHunt hunt : Hunt.manager.getPlayerHunts(player).getHuntValues()) {
				playerHunts.add(hunt.toJson(registryAccess));
			}
			players.add(player.toString(), playerHunts);
		}
		root.add("players", players);

		return Utils.newGson().toJson(root);
	}

	/**
	 * Loads saved hunts into Hunt.hunts and Hunt.manager. Hunts that can't be loaded
	 * (e.g. their species was removed) are skipped.
	 */
	public void load() {
		String data = FileStore.read(FILE);
		if (data == null) {
			return;
		}

		JsonObject root;
		try {
			root = JsonParser.parseString(data).getAsJsonObject();
		} catch (Exception e) {
			Hunt.LOGGER.error("Could not parse " + FILE + ", starting with new hunts.", e);
			FileStore.backupBroken(FILE);
			return;
		}

		RegistryAccess registryAccess = Hunt.server.registryAccess();
		int loaded = 0;
		int skipped = 0;

		if (root.has("global")) {
			for (JsonElement element : root.getAsJsonArray("global")) {
				SingleHunt hunt = parse(element, registryAccess);
				if (hunt != null && Hunt.hunts.restoreHunt(hunt)) {
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
					SingleHunt hunt = parse(element, registryAccess);
					if (hunt != null && playerHunts.restoreHunt(hunt)) {
						loaded++;
					} else {
						skipped++;
					}
				}
				Hunt.manager.restorePlayerHunts(player, playerHunts);
			}
		}

		if (skipped > 0) {
			// Some saved hunts were dropped, so save the cleaned-up state.
			markDirty();
		}
		Hunt.LOGGER.info("Loaded " + loaded + " saved hunts" + (skipped > 0 ? " (" + skipped + " skipped)." : "."));
	}

	private static SingleHunt parse(JsonElement element, RegistryAccess registryAccess) {
		try {
			return SingleHunt.fromJson(element.getAsJsonObject(), registryAccess);
		} catch (Exception e) {
			Hunt.LOGGER.warn("Skipping a saved hunt that could not be loaded: " + e.getMessage());
			return null;
		}
	}
}
