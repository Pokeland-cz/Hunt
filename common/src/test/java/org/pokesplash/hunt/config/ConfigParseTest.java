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
		assertTrue(config.getWarnings().stream().anyMatch(w -> w.contains("huntAmount")), config.getWarnings().toString());
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

	/** Step 8: a 1.2.x config is no longer migrated. It is rejected (Hunt pauses) instead of paying in a guessed currency. */
	@Test
	void legacyConfigIsRejected() {
		ConfigLoadException e = assertThrows(ConfigLoadException.class, () -> Config.parse(defaultJsonWith(root -> {
			root.addProperty("version", "1.2.2");
			root.remove("economy");
			root.remove("currencyName");
			root.addProperty("impactorCurrencyName", "impactor:gold");
		})));
		assertTrue(e.getMessage().contains("1.2.x"), e.getMessage());
		assertTrue(e.getMessage().contains("economy"), e.getMessage());
	}

	@Test
	void missingVersionIsMigrated() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> root.remove("version")));
		assertTrue(config.isUpgraded());
		assertEquals(Hunt.CONFIG_VERSION, config.getVersion());
		assertEquals(Hunt.CONFIG_VERSION,
				JsonParser.parseString(config.toJson()).getAsJsonObject().get("version").getAsString());
	}

	/** HUNT-032: a current-format config that only lacks "version" keeps its economy and currency. */
	@Test
	void missingVersionKeepsEconomyAndCurrency() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.remove("version");
			root.addProperty("economy", "PEBBLES");
			root.addProperty("currencyName", "impactor:gold");
		}));
		assertEquals(Economy.PEBBLES, config.getEconomy());
		assertEquals("impactor:gold", config.getCurrencyName());
		assertTrue(config.isUpgraded());
		assertEquals(Hunt.CONFIG_VERSION, config.getVersion());
		assertTrue(config.getWarnings().stream().anyMatch(w -> w.contains("version")), config.getWarnings().toString());
	}

	@Test
	void oldVersionWithEconomyIsNotMigrated() throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> {
			root.addProperty("version", "1.2.2");
			root.addProperty("economy", "BLANKET");
			root.addProperty("currencyName", "blanket:coin");
		}));
		assertEquals(Economy.BLANKET, config.getEconomy());
		assertEquals("blanket:coin", config.getCurrencyName());
		assertTrue(config.isUpgraded());
	}

	/** HUNT-031: a missing section key no longer silently gives the default rewards. */
	@ParameterizedTest
	@ValueSource(strings = {"economy", "rarity", "rewards", "customPrices"})
	void missingSectionIsRejected(String key) {
		String json = defaultJsonWith(root -> root.remove(key));
		ConfigLoadException e = assertThrows(ConfigLoadException.class, () -> Config.parse(json));
		assertTrue(e.getMessage().contains(key), e.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"matchProperties", "blacklist"})
	void missingOptionalSectionGetsDefaultsWithWarning(String key) throws ConfigLoadException {
		Config config = Config.parse(defaultJsonWith(root -> root.remove(key)));
		assertTrue(config.getWarnings().stream().anyMatch(w -> w.contains(key)), config.getWarnings().toString());
	}

	@Test
	void defaultConfigHasNoWarnings() throws ConfigLoadException {
		assertTrue(Config.parse(defaultJson()).getWarnings().isEmpty());
	}

	@Test
	void lenientSyntaxStillParses() {
		String json = defaultJson();
		String lenient = "{\n// edited by hand\n" + json.substring(1).replace("\"huntAmount\"", "huntAmount");
		Config config = assertDoesNotThrow(() -> Config.parse(lenient));
		assertEquals(7, config.getHuntAmount());
	}
}
