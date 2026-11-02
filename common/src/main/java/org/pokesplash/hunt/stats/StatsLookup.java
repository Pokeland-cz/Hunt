package org.pokesplash.hunt.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Finds the player an admin or player means by a name or UUID (HUNT-028). Has no Minecraft dependencies.
 * <ol>
 *     <li>A UUID is used as it is.</li>
 *     <li>An online player with exactly that name.</li>
 *     <li>A player whose last known name in the stats is exactly that name.</li>
 *     <li>Any online player or stats name that matches ignoring case.</li>
 * </ol>
 * If a step finds several players, the result is {@link Ambiguous}: offline mode allows "Steve" and "steve"
 * to be two accounts, and a name can move to another UUID.
 */
public final class StatsLookup {

	public sealed interface Result permits Found, NotFound, Ambiguous {
	}

	/**
	 * @param name the player's name, or null if a UUID was given and no name is known.
	 */
	public record Found(UUID uuid, String name) implements Result {
	}

	public record NotFound() implements Result {
	}

	public record Ambiguous(List<Match> matches) implements Result {
	}

	public record Match(String name, UUID uuid) {
	}

	private StatsLookup() {
	}

	/**
	 * @param query a player name or UUID.
	 * @param stats the stats by UUID; entries may have no name.
	 * @param online the online players, by name.
	 */
	public static Result find(String query, Map<UUID, PlayerStats> stats, Map<String, UUID> online) {
		String trimmed = query == null ? "" : query.trim();
		if (trimmed.isEmpty()) {
			return new NotFound();
		}

		UUID uuid = parseUuid(trimmed);
		if (uuid != null) {
			return new Found(uuid, knownName(uuid, stats, online));
		}

		for (Map.Entry<String, UUID> player : online.entrySet()) {
			if (trimmed.equals(player.getKey())) {
				return new Found(player.getValue(), player.getKey());
			}
		}

		Map<UUID, String> exact = new LinkedHashMap<>();
		for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
			if (trimmed.equals(entry.getValue().getName())) {
				exact.put(entry.getKey(), entry.getValue().getName());
			}
		}
		if (!exact.isEmpty()) {
			return result(exact);
		}

		Map<UUID, String> similar = new LinkedHashMap<>();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, UUID> player : online.entrySet()) {
			if (lower.equals(player.getKey().toLowerCase(Locale.ROOT))) {
				similar.put(player.getValue(), player.getKey());
			}
		}
		for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
			String name = entry.getValue().getName();
			if (name != null && lower.equals(name.toLowerCase(Locale.ROOT))) {
				similar.putIfAbsent(entry.getKey(), name);
			}
		}
		return similar.isEmpty() ? new NotFound() : result(similar);
	}

	private static Result result(Map<UUID, String> matches) {
		if (matches.size() == 1) {
			Map.Entry<UUID, String> only = matches.entrySet().iterator().next();
			return new Found(only.getKey(), only.getValue());
		}
		List<Match> list = new ArrayList<>();
		matches.forEach((uuid, name) -> list.add(new Match(name, uuid)));
		return new Ambiguous(List.copyOf(list));
	}

	private static String knownName(UUID uuid, Map<UUID, PlayerStats> stats, Map<String, UUID> online) {
		for (Map.Entry<String, UUID> player : online.entrySet()) {
			if (uuid.equals(player.getValue())) {
				return player.getKey();
			}
		}
		PlayerStats record = stats.get(uuid);
		return record == null ? null : record.getName();
	}

	/**
	 * @return the UUID, or null if the text isn't one in the usual 8-4-4-4-12 form.
	 */
	static UUID parseUuid(String text) {
		if (text.length() != 36) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
