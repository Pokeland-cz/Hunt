package org.pokesplash.hunt.stats;

import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.stats.StatsLookup.Ambiguous;
import org.pokesplash.hunt.stats.StatsLookup.Found;
import org.pokesplash.hunt.stats.StatsLookup.Match;
import org.pokesplash.hunt.stats.StatsLookup.NotFound;
import org.pokesplash.hunt.stats.StatsLookup.Result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HUNT-028: which player /hunt stats and /hunt admin stats reset mean by a name or UUID.
 */
class StatsLookupTest {
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID STEVE_LOWER = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private static final UUID LEGACY = UUID.fromString("00000000-0000-0000-0000-000000000004");

	private final Map<UUID, PlayerStats> stats = new LinkedHashMap<>();
	private final Map<String, UUID> online = new LinkedHashMap<>();

	private static PlayerStats withName(String name) {
		PlayerStats stats = new PlayerStats();
		stats.recordCompletion(name, Bucket.COMMON, 10);
		return stats;
	}

	private Result find(String query) {
		return StatsLookup.find(query, stats, online);
	}

	private static Found found(Result result) {
		return assertInstanceOf(Found.class, result);
	}

	private static Set<UUID> uuids(Result result) {
		return assertInstanceOf(Ambiguous.class, result).matches().stream().map(Match::uuid).collect(Collectors.toSet());
	}

	@Test
	void uuidIsUsedAsItIs() {
		stats.put(STEVE, withName("Steve"));
		assertEquals(new Found(STEVE, "Steve"), find(STEVE.toString()));
		assertEquals(new Found(STEVE, "Steve"), find(STEVE.toString().toUpperCase(Locale.ROOT)));
		assertEquals(new Found(STEVE, "Steve"), find("  " + STEVE + " "));
	}

	@Test
	void uuidWithoutStatsIsFoundWithoutName() {
		assertEquals(new Found(ALEX, null), find(ALEX.toString()));
	}

	@Test
	void uuidTakesNameOfOnlinePlayer() {
		online.put("Alex", ALEX);
		assertEquals(new Found(ALEX, "Alex"), find(ALEX.toString()));
	}

	@Test
	void uuidLikeTextIsAName() {
		assertInstanceOf(NotFound.class, find("1-1-1-1-1"));
		assertNull(StatsLookup.parseUuid("1-1-1-1-1"));
	}

	@Test
	void exactStatsNameWinsOverOtherCase() {
		stats.put(STEVE, withName("Steve"));
		stats.put(STEVE_LOWER, withName("steve"));
		assertEquals(STEVE, found(find("Steve")).uuid());
		assertEquals(STEVE_LOWER, found(find("steve")).uuid());
	}

	@Test
	void otherCaseWithSeveralMatchesIsAmbiguous() {
		stats.put(STEVE, withName("Steve"));
		stats.put(STEVE_LOWER, withName("steve"));
		assertEquals(Set.of(STEVE, STEVE_LOWER), uuids(find("STEVE")));
	}

	@Test
	void sameExactNameOnTwoUuidsIsAmbiguous() {
		stats.put(STEVE, withName("Steve"));
		stats.put(STEVE_LOWER, withName("Steve"));
		assertEquals(Set.of(STEVE, STEVE_LOWER), uuids(find("Steve")));
	}

	@Test
	void exactOnlineNameWinsOverStats() {
		stats.put(STEVE, withName("Steve"));
		online.put("Steve", STEVE_LOWER);
		assertEquals(new Found(STEVE_LOWER, "Steve"), find("Steve"));
	}

	@Test
	void exactStatsNameWinsOverOnlineOtherCase() {
		stats.put(STEVE, withName("Steve"));
		online.put("steve", STEVE_LOWER);
		assertEquals(STEVE, found(find("Steve")).uuid());
	}

	@Test
	void otherCaseIsFoundWhenUnique() {
		stats.put(ALEX, withName("Alex"));
		assertEquals(new Found(ALEX, "Alex"), find("aLEX"));
		online.put("Steve", STEVE);
		assertEquals(new Found(STEVE, "Steve"), find("steve"));
	}

	@Test
	void onlineAndStatsWithSameUuidAreOneMatch() {
		stats.put(STEVE, withName("Steve"));
		online.put("Steve", STEVE);
		assertEquals(new Found(STEVE, "Steve"), find("STEVE"));
	}

	@Test
	void onlineAndStatsWithDifferentUuidsAreAmbiguous() {
		stats.put(STEVE, withName("Steve"));
		online.put("STEVE", STEVE_LOWER);
		assertEquals(Set.of(STEVE, STEVE_LOWER), uuids(find("steve")));
	}

	@Test
	void statsWithoutNameAreOnlyFoundByUuid() {
		stats.put(LEGACY, new PlayerStats());
		assertInstanceOf(NotFound.class, find("null"));
		assertEquals(new Found(LEGACY, null), find(LEGACY.toString()));
	}

	@Test
	void unknownAndEmptyAreNotFound() {
		stats.put(STEVE, withName("Steve"));
		assertInstanceOf(NotFound.class, find("Alex"));
		assertInstanceOf(NotFound.class, find(""));
		assertInstanceOf(NotFound.class, find("   "));
		assertInstanceOf(NotFound.class, find(null));
	}

	@Test
	void ambiguousListsNames() {
		stats.put(STEVE, withName("Steve"));
		stats.put(STEVE_LOWER, withName("steve"));
		List<Match> matches = assertInstanceOf(Ambiguous.class, find("STEVE")).matches();
		assertEquals(List.of(new Match("Steve", STEVE), new Match("steve", STEVE_LOWER)), matches);
	}
}
