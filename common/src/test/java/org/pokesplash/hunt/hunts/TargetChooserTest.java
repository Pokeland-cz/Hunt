package org.pokesplash.hunt.hunts;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TargetChooserTest {
	private final Map<Bucket, List<String>> eligible = new EnumMap<>(Bucket.class);
	private final RandomGenerator random = new Random(42);

	/** Picks the first allowed bucket, like a rarity roll that always lands on the most common one left. */
	private static Bucket firstAllowed(Predicate<Bucket> allowed) {
		for (Bucket bucket : Bucket.values()) {
			if (allowed.test(bucket)) {
				return bucket;
			}
		}
		return null;
	}

	private TargetChooser.Choice<String> choose(Set<String> taken, String excluded) {
		return TargetChooser.choose(TargetChooserTest::firstAllowed,
				b -> eligible.getOrDefault(b, List.of()), Function.identity(), taken, excluded, random);
	}

	// HUNT-026: the species that was just completed isn't hunted again right away.
	@Test
	void excludedTargetIsNotPickedWhileOthersAreLeft() {
		eligible.put(Bucket.COMMON, List.of("pikachu", "eevee", "zubat"));
		for (int i = 0; i < 1000; i++) {
			TargetChooser.Choice<String> choice = choose(Set.of("zubat"), "pikachu");
			assertEquals("eevee", choice.target());
			assertEquals(Bucket.COMMON, choice.bucket());
		}
	}

	@Test
	void excludedTargetIsPickedIfNothingElseIsLeft() {
		eligible.put(Bucket.COMMON, List.of("pikachu", "zubat"));
		TargetChooser.Choice<String> choice = choose(Set.of("zubat"), "pikachu");
		assertNotNull(choice);
		assertEquals("pikachu", choice.target());
	}

	@Test
	void takenTargetsAreNeverPicked() {
		eligible.put(Bucket.COMMON, List.of("pikachu", "eevee", "zubat"));
		for (int i = 0; i < 1000; i++) {
			assertNotEquals("zubat", choose(Set.of("zubat"), null).target());
		}
	}

	@Test
	void nothingLeftGivesNull() {
		eligible.put(Bucket.COMMON, List.of("pikachu"));
		assertNull(choose(Set.of("pikachu"), "pikachu"));
		assertNull(choose(Set.of("pikachu"), null));
		eligible.clear();
		assertNull(choose(Set.of(), null));
	}

	@Test
	void otherBucketIsUsedWhenTheExclusionEmptiesOne() {
		eligible.put(Bucket.COMMON, List.of("pikachu"));
		eligible.put(Bucket.RARE, List.of("dratini"));
		TargetChooser.Choice<String> choice = choose(Set.of(), "pikachu");
		assertEquals(Bucket.RARE, choice.bucket());
		assertEquals("dratini", choice.target());
	}

	@Test
	void everyCandidateCanBePicked() {
		eligible.put(Bucket.COMMON, List.of("pikachu", "eevee", "zubat"));
		Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < 1000; i++) {
			seen.add(choose(Set.of(), null).target());
		}
		assertEquals(Set.of("pikachu", "eevee", "zubat"), seen);
	}
}
