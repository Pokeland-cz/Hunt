package org.pokesplash.hunt.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholdersTest {
	private static final String UUID = "5f8d2b1e-0000-4000-8000-000000000001";

	private Locale defaultLocale;

	@BeforeEach
	void rememberLocale() {
		defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.US);
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(defaultLocale);
	}

	// HUNT-002: $ and \ used to throw inside String.replaceAll and crash the server.
	@ParameterizedTest
	@ValueSource(strings = {"Normal_1", "@a", "Ash$1", "a$b", "Ash\\", "x;y", "a\"b", "@e[type=player]",
			"Pokémon", "a b", "$0", "\\$1"})
	void nameIsInsertedLiterally(String name) {
		String filled = assertDoesNotThrow(
				() -> Placeholders.fill("[{player}] give {player} diamond", name, UUID, (String) null, -1));
		assertEquals("[" + name + "] give " + name + " diamond", filled);
	}

	// HUNT-001: a name like @a must never reach a command through {player}.
	@ParameterizedTest
	@ValueSource(strings = {"@a", "@e[type=player]", "Ash$1", "Ash\\", "x;y", "a\"b", "a b", "Pokémon", ""})
	void playerCommandIsBlockedForUnsafeName(String name) {
		assertFalse(Placeholders.commandAllowed("give {player} minecraft:diamond 1", name), name);
	}

	@Test
	void playerCommandIsBlockedForNullName() {
		assertFalse(Placeholders.commandAllowed("give {player} minecraft:diamond 1", null));
	}

	@Test
	void playerCommandRunsForSafeName() {
		assertTrue(Placeholders.commandAllowed("give {player} minecraft:diamond 1", "Normal_1"));
	}

	@Test
	void commandWithoutPlayerRunsForAnyName() {
		assertAll(
				() -> assertTrue(Placeholders.commandAllowed("give {uuid} minecraft:diamond 1", "@a")),
				() -> assertTrue(Placeholders.commandAllowed("say {pokemon} caught", "Ash$1")),
				() -> assertTrue(Placeholders.commandAllowed("say hello", null)));
	}

	@Test
	void uuidIsFilledIn() {
		assertEquals("give " + UUID + " minecraft:diamond 1",
				Placeholders.fill("give {uuid} minecraft:diamond 1", "@a", UUID, (String) null, -1));
	}

	@Test
	void allPlaceholdersAreFilledIn() {
		assertEquals("Ash caught Pikachu for 100 (" + UUID + ")",
				Placeholders.fill("{player} caught {pokemon} for {price} ({uuid})", "Ash", UUID, "Pikachu", 100));
	}

	@Test
	void repeatedPlaceholdersAreAllFilledIn() {
		assertEquals("Pikachu Pikachu 5 5",
				Placeholders.fill("{pokemon} {pokemon} {price} {price}", null, null, "Pikachu", 5));
	}

	@Test
	void nullTemplateGivesEmptyString() {
		assertEquals("", Placeholders.fill(null, "Ash", UUID, "Pikachu", 1));
	}

	@Test
	void missingValuesLeavePlaceholders() {
		assertEquals("{player} {uuid} {pokemon} {price}",
				Placeholders.fill("{player} {uuid} {pokemon} {price}", null, null, (String) null, -1));
	}

	@Test
	void pokemonNameIsInsertedLiterally() {
		assertEquals("Mr. Mime $1 \\", Placeholders.fill("{pokemon}", null, null, "Mr. Mime $1 \\", -1));
	}

	@Test
	void wholePricesHaveNoSeparators() {
		assertAll(
				() -> assertEquals("0", Placeholders.fill("{price}", null, null, (String) null, 0)),
				() -> assertEquals("100", Placeholders.fill("{price}", null, null, (String) null, 100)),
				() -> assertEquals("1000", Placeholders.fill("{price}", null, null, (String) null, 1000)),
				() -> assertEquals("1234567", Placeholders.fill("{price}", null, null, (String) null, 1234567)));
	}

	@Test
	void decimalPricesHaveAtMostTwoDigits() {
		assertAll(
				() -> assertEquals("1.5", Placeholders.fill("{price}", null, null, (String) null, 1.5)),
				() -> assertEquals("1.26", Placeholders.fill("{price}", null, null, (String) null, 1.257)),
				() -> assertEquals("2", Placeholders.fill("{price}", null, null, (String) null, 2.001)));
	}

	/**
	 * HUNT-030: {price} always uses a dot, whatever the server's locale, so a command like
	 * "eco give {uuid} {price}" can read it.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"cs-CZ", "de-DE", "fr-FR", "ar-EG", "hi-IN-u-nu-deva"})
	void decimalSeparatorIsAlwaysDot(String locale) {
		Locale.setDefault(Locale.forLanguageTag(locale));
		assertEquals("1.5", Placeholders.fill("{price}", null, null, (String) null, 1.5));
		assertEquals("1234567.25", Placeholders.fill("{price}", null, null, (String) null, 1234567.25));
		assertEquals("500", Placeholders.formatPrice(500));
	}

	// HUNT-020: species id and form for commands, inserted literally like the other values.
	@Test
	void speciesIdAndFormAreFilledIn() {
		Placeholders.PokemonText vulpix = new Placeholders.PokemonText("Vulpix the Peppy", "vulpix", "alola");
		assertEquals("pokegive abc vulpix form=alola (Vulpix the Peppy)",
				Placeholders.fill("pokegive {uuid} {species_id} form={form} ({pokemon})", "Ash", "abc", vulpix, -1));
	}

	@Test
	void speciesIdAndFormStayWithoutValues() {
		assertAll(
				() -> assertEquals("{species_id} {form} Pikachu",
						Placeholders.fill("{species_id} {form} {pokemon}", null, null, "Pikachu", -1)),
				() -> assertEquals("{species_id} {form} {pokemon}",
						Placeholders.fill("{species_id} {form} {pokemon}", null, null,
								(Placeholders.PokemonText) null, -1)),
				() -> assertEquals("x {form}", Placeholders.fill("{species_id} {form}", null, null,
						new Placeholders.PokemonText(null, "x", null), -1)));
	}

	@Test
	void speciesIdValueIsLiteral() {
		assertEquals("a$1\\ b", Placeholders.fill("{species_id} {form}", null, null,
				new Placeholders.PokemonText(null, "a$1\\", "b"), -1));
	}

	@Test
	void speciesIdFollowsCobblemonsFormat() {
		assertAll(
				() -> assertEquals("mrmime", Placeholders.speciesId("cobblemon", "mrmime")),
				() -> assertEquals("othermod:foo", Placeholders.speciesId("othermod", "foo")));
	}
}
