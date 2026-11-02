package org.pokesplash.hunt.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value checks of config.json (HUNT-008): errors reject the config, warnings replace the value.
 */
class ConfigValidatorTest {

	private static final String[] TIERS = {"common", "uncommon", "rare", "ultraRare"};

	/** The default config.json with one change applied. */
	private static String with(Consumer<JsonObject> change) {
		JsonObject root = JsonParser.parseString(new Config().toJson()).getAsJsonObject();
		change.accept(root);
		return root.toString();
	}

	private static String error(Consumer<JsonObject> change) {
		String json = with(change);
		return assertThrows(ConfigLoadException.class, () -> Config.parse(json)).getMessage();
	}

	private static Config parse(Consumer<JsonObject> change) throws ConfigLoadException {
		return Config.parse(with(change));
	}

	private static void assertMentions(String text, String... parts) {
		for (String part : parts) {
			assertTrue(text.contains(part), "\"" + part + "\" not in: " + text);
		}
	}

	private static void assertWarned(Config config, String part) {
		assertTrue(config.getWarnings().stream().anyMatch(w -> w.contains(part)),
				"no warning with \"" + part + "\" in " + config.getWarnings());
	}

	private static JsonObject tier(JsonObject root, String tier) {
		return root.getAsJsonObject("rewards").getAsJsonObject(tier);
	}

	private static JsonObject customPrice(JsonObject root, int index) {
		return root.getAsJsonArray("customPrices").get(index).getAsJsonObject();
	}

	// --- huntDuration, huntAmount, bufferDuration ---

	@ParameterizedTest
	@ValueSource(ints = {0, -1, -60})
	void huntDurationBelowOneIsRejected(int value) {
		assertMentions(error(root -> root.addProperty("huntDuration", value)), "huntDuration", "is " + value);
	}

	@Test
	void huntDurationOfOneIsAccepted() throws ConfigLoadException {
		assertEquals(1, parse(root -> root.addProperty("huntDuration", 1)).getHuntDuration());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -3})
	void huntAmountBelowOneIsRejected(int value) {
		assertMentions(error(root -> root.addProperty("huntAmount", value)), "huntAmount");
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 28})
	void huntAmountInRangeIsKeptWithoutWarning(int value) throws ConfigLoadException {
		Config config = parse(root -> root.addProperty("huntAmount", value));
		assertEquals(value, config.getHuntAmount());
		assertTrue(config.getWarnings().isEmpty(), config.getWarnings().toString());
	}

	@Test
	void huntAmountAbove28IsCappedWithWarning() throws ConfigLoadException {
		Config config = parse(root -> root.addProperty("huntAmount", 29));
		assertEquals(28, config.getHuntAmount());
		assertWarned(config, "huntAmount");
	}

	@Test
	void negativeBufferDurationBecomesZero() throws ConfigLoadException {
		Config config = parse(root -> root.addProperty("bufferDuration", -1));
		assertEquals(0, config.getBufferDuration());
		assertWarned(config, "bufferDuration");
	}

	@Test
	void zeroBufferDurationIsKept() throws ConfigLoadException {
		Config config = parse(root -> root.addProperty("bufferDuration", 0));
		assertEquals(0, config.getBufferDuration());
		assertTrue(config.getWarnings().isEmpty(), config.getWarnings().toString());
	}

	// --- rarity ---

	@Test
	void allRarityWeightsZeroIsRejected() {
		assertMentions(error(root -> {
			for (String tier : TIERS) {
				root.getAsJsonObject("rarity").addProperty(tier, 0);
			}
		}), "rarity");
	}

	@Test
	void negativeRarityWeightIsRejected() {
		assertMentions(error(root -> root.getAsJsonObject("rarity").addProperty("rare", -1)), "rarity.rare");
	}

	@Test
	void singlePositiveRarityWeightIsAccepted() throws ConfigLoadException {
		Config config = parse(root -> {
			for (String tier : TIERS) {
				root.getAsJsonObject("rarity").addProperty(tier, 0);
			}
			root.getAsJsonObject("rarity").addProperty("ultraRare", 1);
		});
		assertEquals(1, config.getRarity().getUltraRare());
	}

	// --- reward prices ---

	@ParameterizedTest
	@ValueSource(strings = {"-1", "-0.01", "NaN", "Infinity", "-Infinity"})
	void invalidTierPriceIsRejected(String price) {
		String json = with(root -> tier(root, "rare").addProperty("price", 0))
				.replace("\"price\":0", "\"price\":" + price);
		String message = assertThrows(ConfigLoadException.class, () -> Config.parse(json)).getMessage();
		assertMentions(message, "rewards.rare.price");
	}

	@Test
	void zeroPriceIsAccepted() throws ConfigLoadException {
		assertEquals(0, parse(root -> tier(root, "common").addProperty("price", 0)).getRewards().getCommon().getPrice());
	}

	@ParameterizedTest
	@ValueSource(strings = {"common", "uncommon", "rare", "ultraRare"})
	void missingTierIsRejected(String tier) {
		assertMentions(error(root -> root.getAsJsonObject("rewards").remove(tier)), "rewards." + tier);
	}

	@Test
	void nullTierIsRejected() {
		assertMentions(error(root -> root.getAsJsonObject("rewards").add("rare", JsonNull.INSTANCE)), "rewards.rare");
	}

	@Test
	void missingTierPriceIsRejected() {
		assertMentions(error(root -> tier(root, "uncommon").remove("price")), "rewards.uncommon.price");
	}

	@Test
	void missingTierCommandsBecomeEmptyWithWarning() throws ConfigLoadException {
		Config config = parse(root -> tier(root, "rare").remove("commands"));
		assertTrue(config.getRewards().getRare().getCommands().isEmpty());
		assertWarned(config, "rewards.rare.commands");
	}

	@Test
	void emptyCommandsAreDroppedWithWarning() throws ConfigLoadException {
		Config config = parse(root -> {
			JsonArray commands = tier(root, "common").getAsJsonArray("commands");
			commands.add(JsonNull.INSTANCE);
			commands.add(" ");
		});
		assertEquals(List.of("give {player} minecraft:diamond 1"), config.getRewards().getCommon().getCommands());
		assertWarned(config, "rewards.common.commands");
	}

	// --- customPrices ---

	@Test
	void customPriceWithoutSpeciesIsRejected() {
		assertMentions(error(root -> customPrice(root, 0).remove("species")), "customPrices[0].species");
	}

	@Test
	void customPriceWithBlankSpeciesIsRejected() {
		assertMentions(error(root -> customPrice(root, 0).addProperty("species", " ")), "customPrices[0].species");
	}

	@Test
	void customPriceWithoutRewardIsRejected() {
		assertMentions(error(root -> customPrice(root, 0).remove("reward")), "customPrices[0]", "reward");
	}

	@Test
	void customPriceWithoutPriceIsRejected() {
		assertMentions(error(root -> customPrice(root, 0).getAsJsonObject("reward").remove("price")),
				"customPrices[0] (magikarp).reward.price");
	}

	@Test
	void negativeCustomPriceIsRejected() {
		assertMentions(error(root -> customPrice(root, 0).getAsJsonObject("reward").addProperty("price", -5)),
				"customPrices[0] (magikarp).reward.price");
	}

	@Test
	void nullCustomPriceIsRejected() {
		assertMentions(error(root -> root.getAsJsonArray("customPrices").add(JsonNull.INSTANCE)), "customPrices[1]");
	}

	@Test
	void customPriceWithoutCommandsGetsEmptyList() throws ConfigLoadException {
		Config config = parse(root -> customPrice(root, 0).getAsJsonObject("reward").remove("commands"));
		assertTrue(config.getCustomPrices().get(0).getReward().getCommands().isEmpty());
		assertWarned(config, "customPrices[0] (magikarp).reward.commands");
	}

	@Test
	void duplicateCustomPriceIsWarned() throws ConfigLoadException {
		Config config = parse(root -> {
			JsonObject copy = customPrice(root, 0).deepCopy();
			copy.addProperty("species", "MAGIKARP ");
			root.getAsJsonArray("customPrices").add(copy);
		});
		assertWarned(config, "more than once");
	}

	@Test
	void sameSpeciesWithOtherFormIsNotADuplicate() throws ConfigLoadException {
		Config config = parse(root -> {
			JsonObject copy = customPrice(root, 0).deepCopy();
			copy.addProperty("form", "galar");
			root.getAsJsonArray("customPrices").add(copy);
		});
		assertTrue(config.getWarnings().isEmpty(), config.getWarnings().toString());
	}

	// --- economy and currency ---

	@Test
	void unknownEconomyListsTheOptions() {
		assertMentions(error(root -> root.addProperty("economy", "VAULT")), "economy", "IMPACTOR", "PEBBLES", "BLANKET");
	}

	@Test
	void blankCurrencyIsRejectedForImpactorWithoutDefaultCurrency() {
		assertMentions(error(root -> {
			root.addProperty("useImpactorDefaultCurrency", false);
			root.addProperty("currencyName", "");
		}), "currencyName");
	}

	@Test
	void blankCurrencyIsRejectedForBlanket() {
		assertMentions(error(root -> {
			root.addProperty("economy", "BLANKET");
			root.addProperty("currencyName", " ");
		}), "currencyName");
	}

	@Test
	void blankCurrencyIsFineWhenItIsNotUsed() throws ConfigLoadException {
		parse(root -> root.addProperty("currencyName", ""));
		parse(root -> {
			root.addProperty("economy", "PEBBLES");
			root.addProperty("useImpactorDefaultCurrency", false);
			root.add("currencyName", JsonNull.INSTANCE);
		});
	}

	// --- blacklist and pack icon ---

	@Test
	void emptyBlacklistEntriesAreDropped() throws ConfigLoadException {
		Config config = parse(root -> {
			JsonArray blacklist = new JsonArray();
			blacklist.add("Mew");
			blacklist.add(JsonNull.INSTANCE);
			blacklist.add(new JsonPrimitive(""));
			root.add("blacklist", blacklist);
		});
		assertEquals(List.of("Mew"), config.getBlacklist());
		assertWarned(config, "blacklist");
		// Used to throw a NullPointerException for a null entry.
		assertTrue(config.blacklistContains("mew"));
	}

	@Test
	void blankPackIconItemIsWarned() throws ConfigLoadException {
		Config config = parse(root -> root.addProperty("packIconItem", ""));
		assertEquals("minecraft:gray_dye", config.getPackIconItem());
		assertWarned(config, "packIconItem");
	}

	// --- several errors ---

	@Test
	void allErrorsAreReportedAtOnce() {
		assertMentions(error(root -> {
			root.addProperty("huntAmount", 0);
			root.addProperty("huntDuration", 0);
			tier(root, "rare").addProperty("price", -5);
			root.remove("customPrices");
		}), "huntAmount", "huntDuration", "rewards.rare.price", "customPrices");
	}

	// --- species check ---

	private static final Map<String, Set<String>> CATALOG = Map.of(
			"magikarp", Set.of("normal"),
			"vulpix", Set.of("normal", "alola"),
			"ho-oh", Set.of("normal"),
			"mew", Set.of("normal"));

	private static Config speciesConfig(String[] customPrices, String... blacklist) throws ConfigLoadException {
		return parse(root -> {
			JsonArray prices = new JsonArray();
			for (String entry : customPrices) {
				JsonObject price = customPrice(root, 0).deepCopy();
				String[] parts = entry.split("/", 2);
				price.addProperty("species", parts[0]);
				price.addProperty("form", parts.length > 1 ? parts[1] : "");
				prices.add(price);
			}
			root.add("customPrices", prices);
			JsonArray list = new JsonArray();
			for (String name : blacklist) {
				list.add(name);
			}
			root.add("blacklist", list);
		});
	}

	@Test
	void knownSpeciesAndFormsGiveNoWarnings() throws ConfigLoadException {
		Config config = speciesConfig(new String[]{"Magikarp", " vulpix /Alola", "MEW/normal"},
				"Mew", "vulpix-alola", "Ho-Oh", "ho-oh-normal", "VULPIX");
		assertEquals(List.of(), ConfigValidator.checkSpecies(config, CATALOG));
	}

	@Test
	void unknownSpeciesInCustomPricesIsWarned() throws ConfigLoadException {
		List<String> warnings = ConfigValidator.checkSpecies(speciesConfig(new String[]{"magikrap"}), CATALOG);
		assertEquals(1, warnings.size(), warnings.toString());
		assertMentions(warnings.get(0), "customPrices", "magikrap");
	}

	@Test
	void unknownFormInCustomPricesIsWarned() throws ConfigLoadException {
		List<String> warnings = ConfigValidator.checkSpecies(speciesConfig(new String[]{"magikarp/alola"}), CATALOG);
		assertEquals(1, warnings.size(), warnings.toString());
		assertMentions(warnings.get(0), "magikarp", "alola");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Mewtwo", "Vulpix-Galar", "Ho", "Ho-Oh-Alola", "-mew", "mew-"})
	void unknownBlacklistEntryIsWarned(String entry) throws ConfigLoadException {
		List<String> warnings = ConfigValidator.checkSpecies(speciesConfig(new String[]{"mew"}, entry), CATALOG);
		assertEquals(1, warnings.size(), warnings.toString());
		assertMentions(warnings.get(0), "blacklist", entry);
	}
}
