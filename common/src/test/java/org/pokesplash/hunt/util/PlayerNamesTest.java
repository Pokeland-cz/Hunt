package org.pokesplash.hunt.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNamesTest {

	@ParameterizedTest
	@ValueSource(strings = {"a", "Normal_1", "ABCDEFGHIJKLMNOP", "_", "0123456789"})
	void mojangStyleNamesAreSafe(String name) {
		assertTrue(PlayerNames.isCommandSafe(name), name);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "ABCDEFGHIJKLMNOPQ", "a b", "§cRed", "Pokémon", "@a", "@e[type=player]",
			"Ash$1", "a$b", "Ash\\", "x;y", "a\"b", "a'b", "a\nb", "a-b", "a.b"})
	void otherNamesAreNotSafe(String name) {
		assertFalse(PlayerNames.isCommandSafe(name), name);
	}

	@Test
	void nullIsNotSafe() {
		assertFalse(PlayerNames.isCommandSafe(null));
	}
}
