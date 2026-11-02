package org.pokesplash.hunt.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.pokesplash.hunt.enumeration.Economy;
import org.pokesplash.hunt.hunts.Bucket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks the values of a parsed config. Problems that would pay the wrong rewards or break hunts are
 * errors (the config is rejected), small ones are warnings and get a safe value. Has no Minecraft
 * dependencies, so it can be tested without a server.
 */
public final class ConfigValidator {
	/** Highest number of hunts the GUI can show. */
	public static final int MAX_HUNT_AMOUNT = 28;
	static final String DEFAULT_PACK_ICON_ITEM = "minecraft:gray_dye";
	static final String LEGACY_FORMAT = "\"economy\" is missing: this looks like a config from Hunt 1.2.x"
			+ " (it has \"impactorCurrencyName\"), which is no longer migrated. Add \"economy\" and \"currencyName\""
			+ " (see the README)";

	private ConfigValidator() {
	}

	/**
	 * @param errors problems that make the config unusable.
	 * @param warnings problems that were replaced with a safe value.
	 */
	public record Result(List<String> errors, List<String> warnings) {
	}

	/**
	 * Checks the config and replaces small problems with safe values.
	 * @param cfg the config parsed from root.
	 * @param root the JSON the config was parsed from, to tell a missing key from a default value.
	 */
	static Result validate(Config cfg, JsonObject root) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		// Sections that decide what is paid: a missing key would silently give the default rewards.
		JsonElement economy = root.get("economy");
		if (isMissing(economy)) {
			if (root.has("impactorCurrencyName")) {
				// Hunt 1.2.x had impactorCurrencyName instead of economy and currencyName. It's no longer migrated.
				errors.add(LEGACY_FORMAT);
			} else {
				errors.add(missing("economy"));
			}
		} else if (cfg.getEconomy() == null) {
			errors.add("economy must be one of " + Arrays.toString(Economy.values()) + " (is " + economy + ")");
		}
		boolean rarityOk = requirePresent(root, "rarity", errors);
		boolean rewardsOk = requirePresent(root, "rewards", errors);
		boolean customPricesOk = requirePresent(root, "customPrices", errors);
		optionalSection(root, "matchProperties", "the default match properties are used", errors, warnings);
		boolean blacklistOk = optionalSection(root, "blacklist", "nothing is blacklisted", errors, warnings);

		if (cfg.getHuntDuration() < 1) {
			errors.add("huntDuration must be at least 1 minute (is " + cfg.getHuntDuration() + ")");
		}
		if (cfg.getHuntAmount() < 1) {
			errors.add("huntAmount must be at least 1 (is " + cfg.getHuntAmount() + ")");
		} else if (cfg.getHuntAmount() > MAX_HUNT_AMOUNT) {
			warnings.add("huntAmount can't be higher than " + MAX_HUNT_AMOUNT + " (is " + cfg.getHuntAmount()
					+ "), using " + MAX_HUNT_AMOUNT);
			cfg.setHuntAmount(MAX_HUNT_AMOUNT);
		}
		if (cfg.getBufferDuration() < 0) {
			warnings.add("bufferDuration can't be negative (is " + cfg.getBufferDuration() + "), using 0");
			cfg.setBufferDuration(0);
		}
		if (root.has("packIconItem") && (cfg.getPackIconItem() == null || cfg.getPackIconItem().isBlank())) {
			warnings.add("packIconItem is empty, using " + DEFAULT_PACK_ICON_ITEM);
			cfg.setPackIconItem(DEFAULT_PACK_ICON_ITEM);
		}
		if (usesCurrencyName(cfg) && (cfg.getCurrencyName() == null || cfg.getCurrencyName().isBlank())) {
			errors.add("currencyName must be set when economy is " + cfg.getEconomy()
					+ (cfg.getEconomy() == Economy.IMPACTOR ? " and useImpactorDefaultCurrency is false" : ""));
		}

		if (rarityOk) {
			checkRarity(cfg.getRarity(), errors);
		}
		if (rewardsOk) {
			checkRewards(cfg.getRewards(), root.getAsJsonObject("rewards"), errors, warnings);
		}
		if (customPricesOk) {
			checkCustomPrices(cfg.getCustomPrices(), root.getAsJsonArray("customPrices"), errors, warnings);
		}
		if (blacklistOk && cfg.getBlacklist().removeIf(name -> name == null || name.isBlank())) {
			warnings.add("blacklist contains empty entries, they are ignored");
		}
		checkExcludedLabels(cfg, warnings);
		return new Result(errors, warnings);
	}

	/**
	 * excludedLabels is optional: a missing key gets the defaults (and is written to the file), null gets the
	 * defaults with a warning. Labels are compared in lower case.
	 */
	private static void checkExcludedLabels(Config cfg, List<String> warnings) {
		if (cfg.getExcludedLabels() == null) {
			warnings.add("excludedLabels is null, using " + Config.DEFAULT_EXCLUDED_LABELS);
			cfg.setExcludedLabels(new ArrayList<>(Config.DEFAULT_EXCLUDED_LABELS));
			return;
		}
		ArrayList<String> labels = new ArrayList<>();
		boolean empty = false;
		for (String label : cfg.getExcludedLabels()) {
			if (label == null || label.isBlank()) {
				empty = true;
			} else {
				labels.add(label.trim().toLowerCase(Locale.ROOT));
			}
		}
		if (empty) {
			warnings.add("excludedLabels contains empty entries, they are ignored");
		}
		cfg.setExcludedLabels(labels);
	}

	private static boolean usesCurrencyName(Config cfg) {
		return cfg.getEconomy() == Economy.BLANKET
				|| (cfg.getEconomy() == Economy.IMPACTOR && !cfg.isUseImpactorDefaultCurrency());
	}

	private static void checkRarity(RarityConfig rarity, List<String> errors) {
		int total = 0;
		for (Bucket bucket : Bucket.values()) {
			int weight = rarity.getWeight(bucket);
			if (weight < 0) {
				errors.add("rarity." + key(bucket) + " can't be negative (is " + weight + ")");
			} else {
				total += weight;
			}
		}
		if (total == 0) {
			errors.add("rarity: at least one weight must be above 0, otherwise there are no hunts");
		}
	}

	private static void checkRewards(RewardsConfig rewards, JsonObject json, List<String> errors,
									 List<String> warnings) {
		for (Bucket bucket : Bucket.values()) {
			String path = "rewards." + key(bucket);
			JsonElement element = json.get(key(bucket));
			if (isMissing(element)) {
				errors.add(missing(path));
				continue;
			}
			checkReward(path, rewards.get(bucket), element.getAsJsonObject(), errors, warnings);
		}
	}

	private static void checkCustomPrices(List<CustomPrice> prices, JsonArray json, List<String> errors,
										  List<String> warnings) {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < prices.size(); i++) {
			String path = "customPrices[" + i + "]";
			CustomPrice price = prices.get(i);
			JsonElement element = json.get(i);
			if (price == null || isMissing(element)) {
				errors.add(path + " is null");
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			// Gson fills missing keys from CustomPrice(), which would be a Magikarp with a default reward.
			if (isMissing(entry.get("species")) || price.getSpecies() == null || price.getSpecies().isBlank()) {
				errors.add(path + ".species is missing or empty");
				continue;
			}
			path += " (" + price.getSpecies().trim() + ")";
			if (isMissing(entry.get("reward"))) {
				errors.add(missing(path + ".reward"));
				continue;
			}
			checkReward(path + ".reward", price.getReward(), entry.getAsJsonObject("reward"), errors, warnings);

			String form = price.getForm() == null ? "" : price.getForm().trim();
			String key = price.getSpecies().trim().toLowerCase(Locale.ROOT) + "|" + form.toLowerCase(Locale.ROOT);
			if (!seen.add(key)) {
				warnings.add(path + (form.isEmpty() ? "" : " form " + form)
						+ " is listed more than once, only the first entry is used");
			}
		}
	}

	private static void checkReward(String path, RewardConfig reward, JsonObject json, List<String> errors,
									List<String> warnings) {
		// RewardConfig has no default constructor, so a missing price would silently be 0.
		if (isMissing(json.get("price"))) {
			errors.add(missing(path + ".price"));
		} else {
			double price = reward.getPrice();
			if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
				errors.add(path + ".price must be a number of at least 0 (is " + json.get("price") + ")");
			}
		}

		if (reward.getCommands() == null) {
			warnings.add(path + ".commands is missing, no commands will run");
			reward.setCommands(new ArrayList<>());
		} else if (reward.getCommands().removeIf(command -> command == null || command.isBlank())) {
			warnings.add(path + ".commands contains empty entries, they are ignored");
		}
	}

	/**
	 * Finds customPrices and blacklist entries that name a species or form that doesn't exist. They do
	 * nothing, which is usually a typo, so they're reported as warnings.
	 * @param cfg the config.
	 * @param catalog every species name in lower case, with the lower case names of its forms.
	 */
	public static List<String> checkSpecies(Config cfg, Map<String, Set<String>> catalog) {
		List<String> warnings = new ArrayList<>();
		for (CustomPrice price : cfg.getCustomPrices()) {
			String species = price.getSpecies().trim();
			Set<String> forms = catalog.get(species.toLowerCase(Locale.ROOT));
			if (forms == null) {
				warnings.add("customPrices: unknown species \"" + species + "\", the entry is ignored");
				continue;
			}
			String form = price.getForm() == null ? "" : price.getForm().trim();
			if (!form.isEmpty() && !forms.contains(form.toLowerCase(Locale.ROOT))) {
				warnings.add("customPrices: " + species + " has no form \"" + form + "\", the entry is ignored");
			}
		}
		for (String entry : cfg.getBlacklist()) {
			if (!blacklistEntryExists(entry.trim().toLowerCase(Locale.ROOT), catalog)) {
				warnings.add("blacklist: unknown species or form \"" + entry
						+ "\" (use \"Species\" or \"Species-Form\"), the entry is ignored");
			}
		}
		return warnings;
	}

	/**
	 * A blacklist entry is a species name or "Species-Form". Species names can contain "-" themselves
	 * (Ho-Oh), so every split is tried.
	 */
	private static boolean blacklistEntryExists(String entry, Map<String, Set<String>> catalog) {
		if (catalog.containsKey(entry)) {
			return true;
		}
		for (int i = entry.indexOf('-'); i >= 0; i = entry.indexOf('-', i + 1)) {
			Set<String> forms = catalog.get(entry.substring(0, i));
			if (forms != null && forms.contains(entry.substring(i + 1))) {
				return true;
			}
		}
		return false;
	}

	private static boolean requirePresent(JsonObject root, String key, List<String> errors) {
		if (isMissing(root.get(key))) {
			errors.add(missing(key));
			return false;
		}
		return true;
	}

	/**
	 * A missing optional section keeps its default with a warning; an explicit null is still an error.
	 */
	private static boolean optionalSection(JsonObject root, String key, String effect, List<String> errors,
										   List<String> warnings) {
		if (!root.has(key)) {
			warnings.add("\"" + key + "\" is missing, " + effect);
			return true;
		}
		if (root.get(key).isJsonNull()) {
			errors.add(missing(key));
			return false;
		}
		return true;
	}

	private static boolean isMissing(JsonElement element) {
		return element == null || element.isJsonNull();
	}

	private static String missing(String path) {
		return "\"" + path + "\" is missing or null";
	}

	private static String key(Bucket bucket) {
		return switch (bucket) {
			case COMMON -> "common";
			case UNCOMMON -> "uncommon";
			case RARE -> "rare";
			case ULTRA_RARE -> "ultraRare";
		};
	}
}
