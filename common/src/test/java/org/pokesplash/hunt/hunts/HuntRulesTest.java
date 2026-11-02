package org.pokesplash.hunt.hunts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuntRulesTest {
	private static final List<String> DEFAULTS = List.of("legendary", "mythical", "ultra_beast", "paradox");

	// HUNT-022: not only legendaries are excluded.
	@Test
	void excludedLabelsAreSkipped() {
		assertTrue(HuntRules.isExcluded(Set.of("gen1", "legendary"), DEFAULTS));
		assertTrue(HuntRules.isExcluded(Set.of("mythical"), DEFAULTS));
		assertTrue(HuntRules.isExcluded(Set.of("ultra_beast"), DEFAULTS));
		assertTrue(HuntRules.isExcluded(Set.of("paradox", "gen9"), DEFAULTS));
	}

	@Test
	void otherLabelsAreHunted() {
		assertFalse(HuntRules.isExcluded(Set.of("gen1", "starter", "alolan_form"), DEFAULTS));
		assertFalse(HuntRules.isExcluded(Set.of(), DEFAULTS));
		assertFalse(HuntRules.isExcluded(Set.of("legendary"), List.of()));
	}

	@Test
	void labelCaseDoesNotMatter() {
		assertTrue(HuntRules.isExcluded(Set.of("Legendary"), DEFAULTS));
	}

	// HUNT-021: shiny=true means only a shiny catch counts.
	@Test
	void shinyRequired() {
		assertTrue(HuntRules.shinyMatches(true, true));
		assertFalse(HuntRules.shinyMatches(true, false));
	}

	@Test
	void shinyNotRequired() {
		assertTrue(HuntRules.shinyMatches(false, true));
		assertTrue(HuntRules.shinyMatches(false, false));
	}
}
