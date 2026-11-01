package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import org.pokesplash.hunt.Hunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

/**
 * Loads and stores the rarity for each Pokemon to be referenced.
 */
public class SpawnRates {

	// Stores all rarities to easily reference for a price.
	private final HashMap<String, Bucket> rarity;
	private final HashMap<Bucket, Float> buckets;
	// Species that can be picked for a hunt in each bucket (no legendaries, no blacklisted species).
	private final EnumMap<Bucket, List<Species>> eligible;

	public SpawnRates() {
		rarity = new HashMap<>();
		buckets = new HashMap<>();
		eligible = new EnumMap<>(Bucket.class);
	}

	public void init() {
		// Clear data from a previous load so /hunt reload doesn't keep stale entries.
		rarity.clear();
		buckets.clear();
		eligible.clear();

		// Cobblemon 1.8 replaced the SpawnBucket object with the bucket id as a
		// plain String, and split the single bucket list into one map per spawner
		// (world, fishing, habitats, PokeSnack). Hunts are priced off wild world
		// spawns, so that is the map to read.
		BestSpawner.INSTANCE.getConfig().getWorldBuckets().forEach((name, weight) -> {

			Bucket bucket = getBucket(name);

			if (bucket != null) {
				buckets.put(bucket, weight);
			}
		});

		ArrayList<SpawnDetail> spawnDetails = new ArrayList<>(CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails());

		spawnDetails.forEach(spawnDetail -> {

			// 1.8 added a "boss" bucket for the alpha herds, which has no rarity
			// tier here. Skipping it keeps the four tiers behaving exactly as
			// before; without this an alpha entry would overwrite the species'
			// real rarity with null and quietly drop it from every hunt.
			if (getBucket(spawnDetail.getBucket()) == null) {
				return;
			}

			if (!isRarerBucket(spawnDetail)) {
				rarity.put(spawnDetail.getName().getString(), getBucket(spawnDetail.getBucket()));
			}
		});

		// Precompute the species that can be hunted per bucket, so generating a hunt is a single pick
		// instead of creating random Pokemon until one happens to fit.
		for (Species species : PokemonSpecies.getImplemented()) {
			Bucket bucket = rarity.get(species.getName());
			if (bucket == null || isLegendary(species) || Hunt.config.blacklistContains(species.getName())) {
				continue;
			}
			eligible.computeIfAbsent(bucket, b -> new ArrayList<>()).add(species);
		}

		for (Bucket bucket : Bucket.values()) {
			if (getEligibleSpecies(bucket).isEmpty()) {
				Hunt.LOGGER.warn("No eligible species found for hunt rarity " + bucket
						+ ". Hunts of this rarity will not be generated.");
			}
		}
	}

	private static boolean isLegendary(Species species) {
		return species.getStandardForm().getLabels().stream()
				.anyMatch(label -> label.equalsIgnoreCase(CobblemonPokemonLabels.LEGENDARY));
	}

	/**
	 * Gets the species that can be used for a hunt of the given bucket.
	 * @param bucket the bucket to get the species of.
	 * @return unmodifiable list of species, empty if there are none.
	 */
	public List<Species> getEligibleSpecies(Bucket bucket) {
		List<Species> list = eligible.get(bucket);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	private boolean isRarerBucket(SpawnDetail spawnDetail) {

		if (!rarity.containsKey(spawnDetail.getName().getString())) {
			return false;
		}

		Float oldWeight = buckets.get(rarity.get(spawnDetail.getName().getString()));
		Float newWeight = buckets.get(getBucket(spawnDetail.getBucket()));

		if (oldWeight == null || newWeight == null) {
			return false;
		}

		return newWeight < oldWeight;
	}

	private Bucket getBucket(String spawnBucket) {
        return switch (spawnBucket) {
            case "common" -> Bucket.COMMON;
            case "uncommon" -> Bucket.UNCOMMON;
            case "rare" -> Bucket.RARE;
            case "ultra-rare" -> Bucket.ULTRA_RARE;
            default -> null;
        };
	}

	public Bucket getBucket(Pokemon pokemon) {
		return rarity.get(pokemon.getSpecies().getName());
	}
}
