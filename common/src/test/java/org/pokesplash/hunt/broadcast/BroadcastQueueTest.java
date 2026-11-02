package org.pokesplash.hunt.broadcast;

import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.util.Placeholders.PokemonText;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastQueueTest {
	private static final long WINDOW = 5_000;
	private static final String BEGUN = "The hunt for {pokemon} has begun!";
	private static final String ENDED = "The hunt for {pokemon} has ended!";

	private final Map<BroadcastType, String> templates = new EnumMap<>(Map.of(
			BroadcastType.STARTED, BEGUN, BroadcastType.ENDED, ENDED, BroadcastType.CAPTURED, "x"));
	private final BroadcastQueue queue = new BroadcastQueue();

	private static PokemonText pokemon(String name) {
		return new PokemonText(name, name.toLowerCase(), "normal");
	}

	private List<String> due(long now) {
		return queue.due(now, WINDOW, templates::get);
	}

	@Test
	void singleBroadcastUsesTheTemplate() {
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		assertEquals(List.of("The hunt for Pikachu has begun!"), due(WINDOW));
	}

	// HUNT-018: all global hunts expire at once; only one of them used to be announced.
	@Test
	void broadcastsInTheWindowAreJoinedInOrder() {
		String[] names = {"Pikachu", "Eevee", "Vulpix", "Zubat", "Onix", "Ditto", "Abra"};
		for (int i = 0; i < names.length; i++) {
			queue.add(BroadcastType.STARTED, pokemon(names[i]), 100, i * 100L);
		}
		assertEquals(List.of("The hunt for Pikachu, Eevee, Vulpix, Zubat, Onix, Ditto, Abra has begun!"),
				due(WINDOW));
		assertTrue(queue.isEmpty());
	}

	@Test
	void nothingBeforeTheWindowEnds() {
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 1_000);
		assertEquals(List.of(), due(1_000 + WINDOW - 1));
		assertEquals(List.of("The hunt for Pikachu has begun!"), due(1_000 + WINDOW));
	}

	@Test
	void aNewWindowOpensAfterAFlush() {
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		assertEquals(1, due(WINDOW).size());
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 100, 6_000);
		assertEquals(List.of(), due(6_000 + WINDOW - 1));
		assertEquals(List.of("The hunt for Eevee has begun!"), due(6_000 + WINDOW));
		assertEquals(List.of(), due(100_000));
	}

	@Test
	void templateWithPriceSendsEveryHunt() {
		templates.put(BroadcastType.STARTED, "{pokemon} for {price}");
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 250, 0);
		queue.add(BroadcastType.STARTED, pokemon("Vulpix"), 100, 0);
		assertEquals(List.of("Pikachu for 100", "Eevee for 250", "Vulpix for 100"), due(WINDOW));
	}

	@Test
	void templateWithSpeciesIdOrFormSendsEveryHunt() {
		templates.put(BroadcastType.STARTED, "{pokemon} ({species_id}, {form})");
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 100, 0);
		assertEquals(List.of("Pikachu (pikachu, normal)", "Eevee (eevee, normal)"), due(WINDOW));
	}

	@Test
	void templateWithoutPokemonIsSentOnce() {
		templates.put(BroadcastType.STARTED, "New hunts have begun!");
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 200, 0);
		assertEquals(List.of("New hunts have begun!"), due(WINDOW));
	}

	@Test
	void endedComesBeforeBegunAndTypesAreSeparate() {
		queue.add(BroadcastType.ENDED, pokemon("Zubat"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		queue.add(BroadcastType.ENDED, pokemon("Onix"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 100, 0);
		assertEquals(List.of("The hunt for Zubat, Onix has ended!", "The hunt for Pikachu, Eevee has begun!"),
				due(WINDOW));
	}

	@Test
	void typesHaveTheirOwnWindow() {
		queue.add(BroadcastType.ENDED, pokemon("Zubat"), 100, 0);
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 3_000);
		assertEquals(List.of("The hunt for Zubat has ended!"), due(WINDOW));
		assertEquals(List.of("The hunt for Pikachu has begun!"), due(3_000 + WINDOW));
	}

	@Test
	void zeroWindowSendsAtTheNextCheck() {
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 500);
		queue.add(BroadcastType.STARTED, pokemon("Eevee"), 100, 500);
		assertEquals(List.of("The hunt for Pikachu, Eevee has begun!"), queue.due(500, 0, templates::get));
	}

	// A /hunt reload during the window changes the text; the queued hunts are sent with the new one.
	@Test
	void templateIsReadWhenSent() {
		queue.add(BroadcastType.STARTED, pokemon("Pikachu"), 100, 0);
		templates.put(BroadcastType.STARTED, "Lov na {pokemon} začal!");
		assertEquals(List.of("Lov na Pikachu začal!"), due(WINDOW));
	}
}
