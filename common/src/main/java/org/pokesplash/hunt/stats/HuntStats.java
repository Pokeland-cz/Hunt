package org.pokesplash.hunt.stats;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.util.FileStore;
import org.pokesplash.hunt.util.Utils;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks completed hunts per player in config/hunt/stats.json.
 * Changes are saved at most every 30 seconds, and on server stop.
 */
public class HuntStats {
	private static final String FILE = "stats.json";
	private static final String LEGACY_FILE = "logs.json";
	private static final Type PLAYERS_TYPE = new TypeToken<HashMap<UUID, PlayerStats>>() {}.getType();
	private static final Type LEGACY_TYPE = new TypeToken<HashMap<UUID, Double>>() {}.getType();

	public enum Sort {
		COMPLETIONS(Comparator.comparingInt(PlayerStats::getCompletions)),
		EARNED(Comparator.comparingDouble(PlayerStats::getEarned));

		private final Comparator<PlayerStats> comparator;

		Sort(Comparator<PlayerStats> comparator) {
			this.comparator = comparator;
		}
	}

	private final HashMap<UUID, PlayerStats> players = new HashMap<>();
	private boolean dirty = false;

	/**
	 * Loads the stats. If there's no stats file yet, imports the money totals from the old logs.json.
	 */
	public void init() {
		players.clear();
		Gson gson = Utils.newGson();

		String data = FileStore.read(FILE);
		if (data != null) {
			try {
				JsonObject root = JsonParser.parseString(data).getAsJsonObject();
				HashMap<UUID, PlayerStats> loaded = gson.fromJson(root.get("players"), PLAYERS_TYPE);
				if (loaded != null) {
					players.putAll(loaded);
				}
			} catch (Exception e) {
				Hunt.LOGGER.error("Could not parse " + FILE + ", starting with empty stats.", e);
				FileStore.backupBroken(FILE);
			}
			return;
		}

		String legacy = FileStore.read(LEGACY_FILE);
		if (legacy != null) {
			try {
				JsonObject root = JsonParser.parseString(legacy).getAsJsonObject();
				HashMap<UUID, Double> earned = gson.fromJson(root.get("data"), LEGACY_TYPE);
				if (earned != null && !earned.isEmpty()) {
					earned.forEach((uuid, amount) ->
							players.computeIfAbsent(uuid, u -> new PlayerStats()).addEarned(amount));
					dirty = true;
					Hunt.LOGGER.info("Imported money totals of " + earned.size() + " players from " + LEGACY_FILE + ".");
				}
			} catch (Exception e) {
				Hunt.LOGGER.warn("Could not import " + LEGACY_FILE + ": " + e.getMessage());
			}
		}
	}

	public void recordCompletion(ServerPlayer player, Bucket bucket, double earned) {
		players.computeIfAbsent(player.getUUID(), u -> new PlayerStats())
				.recordCompletion(player.getName().getString(), bucket, earned);
		dirty = true;
	}

	public PlayerStats get(UUID player) {
		return players.get(player);
	}

	/**
	 * Finds a player's stats by their last known name (case-insensitive).
	 */
	public Optional<Map.Entry<UUID, PlayerStats>> findByName(String name) {
		return players.entrySet().stream()
				.filter(entry -> name.equalsIgnoreCase(entry.getValue().getName()))
				.findFirst();
	}

	/**
	 * Gets the best players, best first. Players with no completions are left out.
	 */
	public List<Map.Entry<UUID, PlayerStats>> top(Sort sort, int limit) {
		return players.entrySet().stream()
				.filter(entry -> entry.getValue().getCompletions() > 0 || entry.getValue().getEarned() > 0)
				.sorted(Map.Entry.<UUID, PlayerStats>comparingByValue(sort.comparator).reversed())
				.limit(limit)
				.toList();
	}

	/**
	 * Gets a display name for a player: their last known name, or the server's profile cache.
	 */
	public String displayName(UUID player, PlayerStats stats) {
		if (stats != null && stats.getName() != null) {
			return stats.getName();
		}
		GameProfileCache cache = Hunt.server == null ? null : Hunt.server.getProfileCache();
		if (cache != null) {
			Optional<GameProfile> profile = cache.get(player);
			if (profile.isPresent()) {
				return profile.get().getName();
			}
		}
		return player.toString();
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
		Gson gson = Utils.newGson();
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("players", gson.toJsonTree(players, PLAYERS_TYPE));
		return gson.toJson(root);
	}
}
