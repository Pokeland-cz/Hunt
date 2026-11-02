package org.pokesplash.hunt.config;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.enumeration.Economy;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config.parse must reject anything it can't read (so the file is never overwritten, HUNT-004)
 * and migrate older files without losing their values.
 */
class ConfigParseTest {

	private static String defaultJson() {
		return new Config().toJson();
	}

	/** The default config.json with one change applied. */
	private static String defaultJsonWith(Consumer<JsonObject> change) {
		JsonObject root = JsonParser.parseString(defaultJson()).getAsJsonObject();
		change.accept(root);
		return root.toString();
	}

	@Test
	void defaultConfigParses() throws ConfigLoadException {
		Config config = Config.parse(defaultJson());
		assertFalse(config.isUpgraded());
		assertEquals(Hunt.CONFIG_VERSION, config.getVersion());
		assertEquals(Economy.IMPACTOR, config.getEconomy());
		assertEquals(7, config.getHuntAmount());
		assertEquals(60, config.getHuntDuration());
		assertEquals(100, config.getRewards().getCommon().getPrice());
	}

	@Test
	void customValuesAreKept() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.addProperty("economy", "PEBBLES");
			root.addProperty("currencyName", "impactor:gold");
			root.addProperty("huntAmount", 9);
			root.getAsJsonObject("rewards").getAsJsonObject("rare").addProperty("price", 1234.5);
		}));
		assertFalse(config.isUpgraded());
		assertEquals(Economy.PEBBLES, config.getEconomy());
		assertEquals("impactor:gold", config.getCurrencyName());
		assertEquals(9, config.getHuntAmount());
		assertEquals(1234.5, config.getRewards().getRare().getPrice());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\n", "[]", "[{}]", "\"text\"", "42", "null"})
	void notAnObjectIsRejected(String json) {
		assertThrows(ConfigLoadException.class, () -> Config.parse(json));
	}

	@Test
	void missingCommaIsRejectedWithPosition() {
		String broken = defaultJson().replaceFirst("\"huntAmount\": 7,", "\"huntAmount\": 7");
		ConfigLoadException e = assertThrows(ConfigLoadException.class, () -> Config.parse(broken));
		assertTrue(e.getMessage().contains("line"), e.getMessage());
	}

	@Test
	void truncatedFileIsRejected() {
		String json = defaultJson();
		assertThrows(ConfigLoadException.class, () -> Config.parse(json.substring(0, json.length() / 2)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"economy", "rarity", "rewards", "matchProperties", "customPrices", "blacklist"})
	void nullSectionIsRejected(String key) {
		String json = defaultJsonWith(root -> root.add(key, JsonNull.INSTANCE));
		ConfigLoadException e = assertThrows(ConfigLoadException.class, () -> Config.parse(json));
		assertTrue(e.getMessage().contains(key), e.getMessage());
	}

	@Test
	void textInNumberFieldIsRejected() {
		assertThrows(ConfigLoadException.class,
				() -> Config.parse(defaultJsonWith(root -> root.addProperty("huntAmount", "abc"))));
	}

	@Test
	void unknownEconomyIsRejected() {
		ConfigLoadException e = assertThrows(ConfigLoadException.class,
				() -> Config.parse(defaultJsonWith(root -> root.addProperty("economy", "NONSENSE"))));
		assertTrue(e.getMessage().contains("economy"), e.getMessage());
	}

	@Test
	void sectionOfWrongTypeIsRejected() {
		assertThrows(ConfigLoadException.class,
				() -> Config.parse(defaultJsonWith(root -> root.addProperty("rewards", "lots"))));
	}

	@Test
	void huntAmountIsCappedAt28() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> root.addProperty("huntAmount", 40)));
		assertEquals(28, config.getHuntAmount());
	}

	@Test
	void blankPackIconItemGetsDefault() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> root.addProperty("packIconItem", " ")));
		assertEquals("minecraft:gray_dye", config.getPackIconItem());
	}

	@Test
	void missingPackIconOptionsMarkUpgraded() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.remove("usePackIcons");
			root.remove("packIconItem");
		}));
		assertTrue(config.isUpgraded());
		assertTrue(config.isUsePackIcons());
		assertEquals("minecraft:gray_dye", config.getPackIconItem());
	}

	@Test
	void oldVersionIsMigratedAndKeepsCurrency() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.addProperty("version", "1.2.2");
			root.remove("economy");
			root.remove("currencyName");
			root.addProperty("impactorCurrencyName", "impactor:gold");
			root.addProperty("huntDuration", 30);
		}));
		assertTrue(config.isUpgraded());
		assertEquals(Hunt.CONFIG_VERSION, config.getVersion());
		assertEquals(Economy.IMPACTOR, config.getEconomy());
		assertEquals("impactor:gold", config.getCurrencyName());
		assertEquals(30, config.getHuntDuration());
	}

	@Test
	void missingVersionIsMigrated() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> root.remove("version")));
		assertTrue(config.isUpgraded());
		assertEquals(Hunt.CONFIG_VERSION, config.getVersion());
		assertEquals(Hunt.CONFIG_VERSION,
				JsonParser.parseString(config.toJson()).getAsJsonObject().get("version").getAsString());
	}

	/**
	 * Documents current behaviour (see AUDIT.md, HUNT-032): a current-format config that only lacks
	 * "version" goes through the old-format migration, which switches the economy to Impactor and
	 * replaces currencyName with ConfigOld's default, because ConfigOld() always sets impactorCurrencyName.
	 */
	@Test
	void missingVersionResetsEconomyAndCurrency() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.remove("version");
			root.addProperty("economy", "PEBBLES");
			root.addProperty("currencyName", "impactor:gold");
		}));
		assertEquals(Economy.IMPACTOR, config.getEconomy());
		assertEquals("impactor:huntcoins", config.getCurrencyName());
	}

	/**
	 * Documents current behaviour (see AUDIT.md, HUNT-031): a missing section key is not an error,
	 * Gson keeps the default from the constructor, so the default rewards apply.
	 */
	@Test
	void missingSectionGetsDefaults() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.getAsJsonObject("rewards").getAsJsonObject("common").addProperty("price", 1);
			root.remove("rewards");
		}));
		assertEquals(100, config.getRewards().getCommon().getPrice());
		assertEquals("give {player} minecraft:diamond 1", config.getRewards().getCommon().getCommands().get(0));
	}

	@Test
	void lenientSyntaxStillParses() {
		String json = defaultJson();
		String lenient = "{\n// edited by hand\n" + json.substring(1).replace("\"huntAmount\"", "huntAmount");
		Config config = assertDoesNotThrow(() -> Config.parse(lenient));
		assertEquals(7, config.getHuntAmount());
	}
}
