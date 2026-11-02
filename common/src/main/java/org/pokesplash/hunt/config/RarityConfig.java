package org.pokesplash.hunt.config;

import org.pokesplash.hunt.hunts.Bucket;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

public class RarityConfig {
	private int common; // What rarity is classed as common.
	private int uncommon; // What rarity is classed as uncommon.
	private int rare; // What rarity is classed as rare.
	private int ultraRare;

	public RarityConfig() {
		common = 60;
		uncommon = 30;
		rare = 9;
		ultraRare = 1;
	}

	public int getCommon() {
		return common;
	}

	public int getUncommon() {
		return uncommon;
	}

	public int getRare() {
		return rare;
	}

	public int getUltraRare() {
		return ultraRare;
	}

	public int getWeight(Bucket bucket) {
		return switch (bucket) {
			case COMMON -> common;
			case UNCOMMON -> uncommon;
			case RARE -> rare;
			case ULTRA_RARE -> ultraRare;
		};
	}

	/**
	 * Picks a random bucket using the configured weights.
	 * @return the bucket, or null if every weight is zero.
	 */
	public Bucket getRandomRarity() {
		return getRandomRarity(bucket -> true);
	}

	/**
	 * Picks a random bucket using the configured weights, only considering buckets that pass the filter.
	 * @param allowed filter for the buckets that can be picked.
	 * @return the bucket, or null if no allowed bucket has a positive weight.
	 */
	public Bucket getRandomRarity(Predicate<Bucket> allowed) {
		return getRandomRarity(allowed, ThreadLocalRandom.current());
	}

	/**
	 * Picks a random bucket using the configured weights, only considering buckets that pass the filter.
	 * @param allowed filter for the buckets that can be picked.
	 * @param random the source of randomness.
	 * @return the bucket, or null if no allowed bucket has a positive weight.
	 */
	public Bucket getRandomRarity(Predicate<Bucket> allowed, RandomGenerator random) {
		int total = 0;
		for (Bucket bucket : Bucket.values()) {
			if (allowed.test(bucket)) {
				total += Math.max(0, getWeight(bucket));
			}
		}

		if (total <= 0) {
			return null;
		}

		int roll = random.nextInt(total);
		for (Bucket bucket : Bucket.values()) {
			if (!allowed.test(bucket)) {
				continue;
			}
			roll -= Math.max(0, getWeight(bucket));
			if (roll < 0) {
				return bucket;
			}
		}
		return null;
	}
}
