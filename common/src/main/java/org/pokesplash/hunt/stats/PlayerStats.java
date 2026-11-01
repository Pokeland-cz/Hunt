package org.pokesplash.hunt.stats;

import org.pokesplash.hunt.hunts.Bucket;

import java.util.HashMap;

/**
 * Hunt statistics of a single player.
 */
public class PlayerStats {
	private String name; // Last known name, so offline players can be shown and looked up.
	private int completions;
	private double earned;
	private HashMap<Bucket, Integer> completionsByRarity;

	public PlayerStats() {
		completionsByRarity = new HashMap<>();
	}

	void recordCompletion(String name, Bucket bucket, double earned) {
		this.name = name;
		this.completions++;
		this.earned += earned;
		if (completionsByRarity == null) {
			completionsByRarity = new HashMap<>();
		}
		if (bucket != null) {
			completionsByRarity.merge(bucket, 1, Integer::sum);
		}
	}

	void addEarned(double amount) {
		earned += amount;
	}

	public String getName() {
		return name;
	}

	public int getCompletions() {
		return completions;
	}

	public double getEarned() {
		return earned;
	}

	public int getCompletions(Bucket bucket) {
		return completionsByRarity == null ? 0 : completionsByRarity.getOrDefault(bucket, 0);
	}
}
