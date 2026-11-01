package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import org.pokesplash.hunt.Hunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

/**
 * Loads and stores the rarity of each species and form that spawns in the world, to be referenced.
 */
public class SpawnRates {

	// Rarity of each target, keyed by HuntTarget#key.
	private final HashMap<String, Bucket> rarity;
	private final HashMap<String, HuntTarget> targets;
	private final HashMap<Bucket, Float> buckets;
	// Targets that can be picked for a hunt in each bucket (no legendaries, no blacklisted species).
	private final EnumMap<Bucket, List<HuntTarget>> eligible;

	public SpawnRates() {
		rarity = new HashMap<>();
		targets = new HashMap<>();
		buckets = new HashMap<>();
		eligible = new EnumMap<>(Bucket.class);
	}

	public void init() {
		// Clear data from a previous load so /hunt reload doesn't keep stale entries.
		rarity.clear();
		targets.clear();
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

		for (SpawnDetail spawnDetail : new ArrayList<>(CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails())) {
			// 1.8 added a "boss" bucket for the alpha herds, which has no rarity
			// tier here. Skipping it keeps the four tiers behaving exactly as before.
			Bucket bucket = getBucket(spawnDetail.getBucket());
			if (bucket == null) {
				continue;
			}

			for (PokemonProperties properties : getSpawnedPokemon(spawnDetail)) {
				HuntTarget target = resolve(properties);
				if (target != null) {
					addSpawn(target, bucket);
				}
			}
		}

		// Precompute the targets that can be hunted per bucket, so generating a hunt is a single pick
		// instead of creating random Pokemon until one happens to fit.
		rarity.forEach((key, bucket) -> {
			HuntTarget target = targets.get(key);
			if (!isLegendary(target.form()) && !isBlacklisted(target)) {
				eligible.computeIfAbsent(bucket, b -> new ArrayList<>()).add(target);
			}
		});

		for (Bucket bucket : Bucket.values()) {
			if (getEligibleTargets(bucket).isEmpty()) {
				Hunt.LOGGER.warn("No eligible species found for hunt rarity " + bucket
						+ ". Hunts of this rarity will not be generated.");
			}
		}
	}

	/**
	 * Gets the Pokemon a spawn detail can spawn: a single Pokemon, or every member of a herd.
	 */
	private static List<PokemonProperties> getSpawnedPokemon(SpawnDetail spawnDetail) {
		if (spawnDetail instanceof PokemonSpawnDetail pokemonDetail) {
			return List.of(pokemonDetail.getPokemon());
		}
		if (spawnDetail instanceof PokemonHerdSpawnDetail herdDetail) {
			List<PokemonProperties> pokemon = new ArrayList<>();
			herdDetail.getHerdablePokemon().forEach(herdable -> pokemon.add(herdable.getPokemon()));
			return pokemon;
		}
		return List.of();
	}

	/**
	 * Works out which species and form the spawn properties describe.
	 * @return the target, or null if the species is unknown, random or not implemented.
	 */
	private static HuntTarget resolve(PokemonProperties properties) {
		String speciesName = properties.getSpecies();
		if (speciesName == null || speciesName.equalsIgnoreCase("random")) {
			return null;
		}

		ResourceLocation id = speciesName.contains(":")
				? ResourceLocation.tryParse(speciesName.toLowerCase())
				: ResourceLocation.tryBuild("cobblemon", speciesName.toLowerCase());
		Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
		if (species == null || !species.getImplemented()) {
			return null;
		}

		// Forms are either named directly (form=alola) or selected by aspects (alolan).
		FormData form;
		if (properties.getForm() != null) {
			form = species.getForms().stream()
					.filter(f -> f.formOnlyShowdownId().equalsIgnoreCase(properties.getForm()))
					.findFirst()
					.orElse(species.getStandardForm());
		} else {
			form = species.getForm(properties.getAspects());
		}
		return new HuntTarget(species, form);
	}

	/**
	 * Records that a target spawns in a bucket. A target that spawns in several buckets keeps the
	 * most common one, as before.
	 */
	private void addSpawn(HuntTarget target, Bucket bucket) {
		String key = target.key();
		Bucket current = rarity.get(key);

		if (current != null) {
			Float oldWeight = buckets.get(current);
			Float newWeight = buckets.get(bucket);
			if (oldWeight != null && newWeight != null && newWeight < oldWeight) {
				return; // Rarer than the bucket we already have.
			}
		}

		rarity.put(key, bucket);
		targets.put(key, target);
	}

	private static boolean isLegendary(FormData form) {
		return form.getLabels().stream()
				.anyMatch(label -> label.equalsIgnoreCase(CobblemonPokemonLabels.LEGENDARY));
	}

	/**
	 * A blacklist entry can be a species name (blocks every form) or "Species-Form", e.g. "Vulpix-Alola".
	 */
	private static boolean isBlacklisted(HuntTarget target) {
		String species = target.species().getName();
		return Hunt.config.blacklistContains(species)
				|| Hunt.config.blacklistContains(species + "-" + target.form().getName());
	}

	/**
	 * Gets the targets that can be used for a hunt of the given bucket.
	 * @param bucket the bucket to get the targets of.
	 * @return unmodifiable list of targets, empty if there are none.
	 */
	public List<HuntTarget> getEligibleTargets(Bucket bucket) {
		List<HuntTarget> list = eligible.get(bucket);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
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

	/**
	 * Gets the bucket a Pokemon's species and form spawn in.
	 * @return the bucket, or null if it doesn't spawn in the world.
	 */
	public Bucket getBucket(Pokemon pokemon) {
		return rarity.get(HuntTarget.key(pokemon.getSpecies(), pokemon.getForm()));
	}
}
