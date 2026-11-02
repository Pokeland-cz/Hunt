package org.pokesplash.hunt.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.hunts.Bucket;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUNT-013: deleting stats in memory. Nothing here touches the files.
 */
class HuntStatsTest {
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private final HuntStats stats = new HuntStats();

	private HuntStats withPlayers() {
		stats.put(STEVE, "Steve", Bucket.RARE, 300);
		stats.put(STEVE, "Steve", Bucket.COMMON, 100);
		stats.put(ALEX, "Alex", Bucket.COMMON, 50);
		return stats;
	}

	@Test
	void removeReturnsTheDeletedStats() {
		withPlayers();
		PlayerStats steve = stats.get(STEVE);
		assertSame(steve, stats.remove(STEVE));
		assertNull(stats.get(STEVE));
		assertEquals(1, stats.size());
		assertTrue(stats.isDirty());
	}

	@Test
	void removingUnknownPlayerChangesNothing() {
		withPlayers();
		stats.clearDirty();
		assertNull(stats.remove(UUID.randomUUID()));
		assertEquals(2, stats.size());
		assertFalse(stats.isDirty());
	}

	@Test
	void removedPlayerLeavesTheTopAndTheLookup() {
		withPlayers();
		stats.remove(STEVE);
		assertEquals(1, stats.top(HuntStats.Sort.COMPLETIONS, 10).size());
		assertEquals(ALEX, stats.top(HuntStats.Sort.EARNED, 10).getFirst().getKey());
		assertInstanceOf(StatsLookup.NotFound.class, stats.lookup("Steve", Map.of()));
	}

	@Test
	void clearDeletesEveryone() {
		withPlayers();
		stats.clearDirty();
		assertEquals(2, stats.clear());
		assertEquals(0, stats.size());
		assertTrue(stats.top(HuntStats.Sort.COMPLETIONS, 10).isEmpty());
		assertTrue(stats.isDirty());
	}

	@Test
	void snapshotHasEveryPlayerInTheFileFormat() {
		withPlayers();
		JsonObject root = JsonParser.parseString(stats.snapshot()).getAsJsonObject();
		assertEquals(1, root.get("version").getAsInt());
		JsonObject players = root.getAsJsonObject("players");
		assertEquals(2, players.size());
		assertEquals(2, players.getAsJsonObject(STEVE.toString()).get("completions").getAsInt());
	}

	@Test
	void totalsForThePreview() {
		withPlayers();
		assertEquals(3, stats.totalCompletions());
		assertEquals(java.util.List.of("Steve", "Alex"), stats.knownNames().stream().sorted(
				java.util.Comparator.reverseOrder()).toList());
	}

	@Test
	void logJsonIsOneLine() {
		withPlayers();
		String json = HuntStats.toJson(stats.get(STEVE));
		assertFalse(json.contains("\n"));
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		assertEquals("Steve", parsed.get("name").getAsString());
		assertEquals(400.0, parsed.get("earned").getAsDouble());
		assertEquals(1, parsed.getAsJsonObject("completionsByRarity").get("RARE").getAsInt());
	}
}
