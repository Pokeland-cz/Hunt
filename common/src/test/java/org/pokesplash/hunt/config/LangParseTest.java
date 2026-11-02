package org.pokesplash.hunt.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pokesplash.hunt.Hunt;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lang.parse must reject unreadable files and migrate old ones from their own texts (HUNT-016).
 */
class LangParseTest {
	private static final String CUSTOM_BROADCAST = "§d{player} chytil {pokemon}!";

	private static String langJsonWith(Consumer<JsonObject> change) {
		JsonObject root = JsonParser.parseString(new Lang().toJson()).getAsJsonObject();
		change.accept(root);
		return root.toString();
	}

	@Test
	void defaultLangParses() throws ConfigLoadException {
		Lang lang = Lang.parse(new Lang().toJson());
		assertFalse(lang.isUpgraded());
		assertEquals(Hunt.LANG_VERSION, lang.getVersion());
	}

	// 1.3.4: a 1.3.3 lang.json gets the new message written in, its own texts stay.
	@Test
	void fileWithoutPayFailedMessageIsUpgraded() throws ConfigLoadException {
		Lang lang = Lang.parse(langJsonWith(root -> {
			root.remove("payFailedMessage");
			root.addProperty("captureHuntBroadcast", CUSTOM_BROADCAST);
		}));
		assertTrue(lang.isUpgraded());
		assertEquals(new Lang().getPayFailedMessage(), lang.getPayFailedMessage());
		assertEquals(CUSTOM_BROADCAST, lang.getCaptureHuntBroadcast());
		assertTrue(JsonParser.parseString(lang.toJson()).getAsJsonObject().has("payFailedMessage"));
	}

	@Test
	void fileWithoutStatsAmbiguousIsUpgraded() throws ConfigLoadException {
		Lang lang = Lang.parse(langJsonWith(root -> {
			root.remove("statsAmbiguous");
			root.addProperty("captureHuntBroadcast", CUSTOM_BROADCAST);
		}));
		assertTrue(lang.isUpgraded());
		assertEquals(new Lang().getStatsAmbiguous(), lang.getStatsAmbiguous());
		assertEquals(CUSTOM_BROADCAST, lang.getCaptureHuntBroadcast());
		assertTrue(JsonParser.parseString(lang.toJson()).getAsJsonObject().has("statsAmbiguous"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "[]", "\"text\"", "{\"title\": \"Hunt\" \"reward\": \"x\"}", "{\"title\": "})
	void unreadableFileIsRejected(String json) {
		assertThrows(ConfigLoadException.class, () -> Lang.parse(json));
	}

	@Test
	void oldVersionKeepsCustomTexts() throws ConfigLoadException {
		Lang lang = Lang.parse(langJsonWith(root -> {
			root.addProperty("version", "0.9");
			root.addProperty("captureHuntBroadcast", CUSTOM_BROADCAST);
		}));
		assertTrue(lang.isUpgraded());
		assertEquals(Hunt.LANG_VERSION, lang.getVersion());
		assertEquals(CUSTOM_BROADCAST, lang.getCaptureHuntBroadcast());
	}

	@Test
	void fileWithoutVersionOrStatsIsMigrated() throws ConfigLoadException {
		Lang lang = Lang.parse(langJsonWith(root -> {
			root.remove("version");
			root.addProperty("captureHuntBroadcast", CUSTOM_BROADCAST);
			for (String key : root.keySet().toArray(new String[0])) {
				if (key.startsWith("stats") || key.startsWith("top")) {
					root.remove(key);
				}
			}
		}));
		assertTrue(lang.isUpgraded());
		assertEquals(Hunt.LANG_VERSION, lang.getVersion());
		assertEquals(CUSTOM_BROADCAST, lang.getCaptureHuntBroadcast());
		assertEquals(new Lang().getStatsHeader(), lang.getStatsHeader());
		assertEquals(new Lang().getTopEntry(), lang.getTopEntry());

		// The rewritten file keeps the custom text and the new keys.
		JsonObject written = JsonParser.parseString(lang.toJson()).getAsJsonObject();
		assertEquals(CUSTOM_BROADCAST, written.get("captureHuntBroadcast").getAsString());
		assertEquals(Hunt.LANG_VERSION, written.get("version").getAsString());
		assertTrue(written.has("statsHeader"));
	}

	@Test
	void missingStatsKeysMarkUpgraded() throws ConfigLoadException {
		assertTrue(Lang.parse(langJsonWith(root -> root.remove("statsHeader"))).isUpgraded());
	}
}
