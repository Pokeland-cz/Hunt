package org.pokesplash.hunt.hunts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Picks the target of a new hunt. Has no Minecraft dependencies, so it can be tested.
 */
public final class TargetChooser {
	private TargetChooser() {
	}

	/**
	 * @param bucket the rarity of the target.
	 * @param target the chosen target.
	 */
	public record Choice<T>(Bucket bucket, T target) {
	}

	/**
	 * Picks a rarity, then a target of that rarity that isn't hunted yet. The excluded target (the hunt that
	 * was just completed or ended) is avoided, so the same species isn't hunted again right away (HUNT-026),
	 * unless nothing else is left.
	 * @param rarity picks a random rarity among those the predicate accepts, or returns null if there is none.
	 * @param eligible the targets that can be hunted in a rarity.
	 * @param keyOf the key of a target, as used in taken and excludedKey.
	 * @param taken keys of targets that are already hunted, never picked.
	 * @param excludedKey key of a target to avoid if possible, or null.
	 * @return the choice, or null if no target is left.
	 */
	public static <T> Choice<T> choose(Function<Predicate<Bucket>, Bucket> rarity,
									   Function<Bucket, List<T>> eligible, Function<T, String> keyOf,
									   Set<String> taken, String excludedKey, RandomGenerator random) {
		if (excludedKey != null && !taken.contains(excludedKey)) {
			Set<String> avoid = new HashSet<>(taken);
			avoid.add(excludedKey);
			Choice<T> choice = pick(rarity, eligible, keyOf, avoid, random);
			if (choice != null) {
				return choice;
			}
		}
		return pick(rarity, eligible, keyOf, taken, random);
	}

	private static <T> Choice<T> pick(Function<Predicate<Bucket>, Bucket> rarity,
									  Function<Bucket, List<T>> eligible, Function<T, String> keyOf,
									  Set<String> avoid, RandomGenerator random) {
		Bucket bucket = rarity.apply(b -> eligible.apply(b).stream().anyMatch(t -> !avoid.contains(keyOf.apply(t))));
		if (bucket == null) {
			return null;
		}
		List<T> candidates = eligible.apply(bucket).stream()
				.filter(t -> !avoid.contains(keyOf.apply(t)))
				.toList();
		if (candidates.isEmpty()) {
			return null;
		}
		return new Choice<>(bucket, candidates.get(random.nextInt(candidates.size())));
	}
}
