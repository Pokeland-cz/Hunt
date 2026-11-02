package org.pokesplash.hunt.config;

import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.util.Utils;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityConfigTest {

	/** Returns a fixed roll and remembers the bound it was asked for. */
	private static final class FixedRoll implements RandomGenerator {
		private final int roll;
		private int bound = -1;

		FixedRoll(int roll) {
			this.roll = roll;
		}

		@Override
		public int nextInt(int bound) {
			this.bound = bound;
			if (roll < 0 || roll >= bound) {
				throw new IllegalArgumentException("roll " + roll + " outside bound " + bound);
			}
			return roll;
		}

		@Override
		public long nextLong() {
			throw new UnsupportedOperationException();
		}
	}

	/** Builds the rarity section the way config.json is loaded. */
	private static RarityConfig rarity(int common, int uncommon, int rare, int ultraRare) {
		return Utils.newGson().fromJson("{\"common\": " + common + ", \"uncommon\": " + uncommon
				+ ", \"rare\": " + rare + ", \"ultraRare\": " + ultraRare + "}", RarityConfig.class);
	}

	private static Bucket roll(RarityConfig config, int roll) {
		return config.getRandomRarity(bucket -> true, new FixedRoll(roll));
	}

	@Test
	void defaultWeights() {
		RarityConfig config = new RarityConfig();
		assertEquals(60, config.getWeight(Bucket.COMMON));
		assertEquals(30, config.getWeight(Bucket.UNCOMMON));
		assertEquals(9, config.getWeight(Bucket.RARE));
		assertEquals(1, config.getWeight(Bucket.ULTRA_RARE));
	}

	@Test
	void bucketBoundariesWithDefaultWeights() {
		RarityConfig config = new RarityConfig();
		assertEquals(Bucket.COMMON, roll(config, 0));
		assertEquals(Bucket.COMMON, roll(config, 59));
		assertEquals(Bucket.UNCOMMON, roll(config, 60));
		assertEquals(Bucket.UNCOMMON, roll(config, 89));
		assertEquals(Bucket.RARE, roll(config, 90));
		assertEquals(Bucket.RARE, roll(config, 98));
		assertEquals(Bucket.ULTRA_RARE, roll(config, 99));
	}

	@Test
	void rollsOverTheSumOfWeights() {
		FixedRoll random = new FixedRoll(0);
		new RarityConfig().getRandomRarity(bucket -> true, random);
		assertEquals(100, random.bound);
	}

	@Test
	void allWeightsZeroGivesNull() {
		assertNull(rarity(0, 0, 0, 0).getRandomRarity());
	}

	@Test
	void negativeWeightsCountAsZero() {
		RarityConfig config = rarity(-50, 10, -1, 0);
		FixedRoll random = new FixedRoll(9);
		assertEquals(Bucket.UNCOMMON, config.getRandomRarity(bucket -> true, random));
		assertEquals(10, random.bound);
		assertNull(rarity(-1, -1, -1, -1).getRandomRarity());
	}

	@Test
	void filterExcludingEverythingGivesNull() {
		assertNull(new RarityConfig().getRandomRarity(bucket -> false));
	}

	@Test
	void filterExcludingPositiveBucketsGivesNull() {
		assertNull(rarity(10, 0, 0, 0).getRandomRarity(bucket -> bucket != Bucket.COMMON));
	}

	@Test
	void filteredBucketIsSkipped() {
		RarityConfig config = new RarityConfig();
		FixedRoll first = new FixedRoll(0);
		assertEquals(Bucket.UNCOMMON, config.getRandomRarity(bucket -> bucket != Bucket.COMMON, first));
		assertEquals(40, first.bound);
		assertEquals(Bucket.ULTRA_RARE,
				config.getRandomRarity(bucket -> bucket != Bucket.COMMON, new FixedRoll(39)));
		for (int i = 0; i < 1000; i++) {
			assertNotEquals(Bucket.COMMON, config.getRandomRarity(bucket -> bucket != Bucket.COMMON));
		}
	}

	@Test
	void singlePositiveBucketIsAlwaysPicked() {
		RarityConfig config = rarity(0, 0, 5, 0);
		for (int i = 0; i < 1000; i++) {
			assertEquals(Bucket.RARE, config.getRandomRarity());
		}
	}

	@Test
	void distributionFollowsWeights() {
		RarityConfig config = new RarityConfig();
		Random random = new Random(42);
		int draws = 100_000;
		Map<Bucket, Integer> counts = new EnumMap<>(Bucket.class);
		for (int i = 0; i < draws; i++) {
			counts.merge(config.getRandomRarity(bucket -> true, random), 1, Integer::sum);
		}
		for (Bucket bucket : Bucket.values()) {
			double share = 100.0 * counts.getOrDefault(bucket, 0) / draws;
			double expected = config.getWeight(bucket);
			assertTrue(Math.abs(share - expected) < 1.0,
					bucket + ": " + share + " % instead of " + expected + " %");
		}
	}
}
