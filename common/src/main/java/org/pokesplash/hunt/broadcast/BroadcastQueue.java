package org.pokesplash.hunt.broadcast;

import org.pokesplash.hunt.util.Placeholders;
import org.pokesplash.hunt.util.Placeholders.PokemonText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects broadcasts during the buffer window and turns them into as few chat lines as possible, without
 * losing any (HUNT-018). Global hunts all expire at once, so without this only one of the seven
 * "ended" and "begun" messages was sent. Has no Minecraft dependencies, so it can be tested.
 */
public final class BroadcastQueue {
	/** Sent in this order, so "ended" comes before "begun" when hunts expire. */
	private static final List<BroadcastType> ORDER = List.of(BroadcastType.ENDED, BroadcastType.STARTED,
			BroadcastType.CAPTURED);
	/** Placeholders that differ per hunt or player besides {pokemon}; a template with them isn't joined. */
	private static final List<String> NOT_JOINABLE = List.of(Placeholders.PRICE, Placeholders.SPECIES_ID,
			Placeholders.FORM, Placeholders.PLAYER, Placeholders.UUID);

	/**
	 * A hunt a broadcast is about.
	 */
	public record Item(PokemonText pokemon, double price) {
	}

	private final Map<BroadcastType, List<Item>> pending = new EnumMap<>(BroadcastType.class);
	private final Map<BroadcastType, Long> opened = new EnumMap<>(BroadcastType.class);

	/**
	 * Queues a broadcast. The first one of a type opens its window.
	 * @param now the current time in ms.
	 */
	public void add(BroadcastType type, PokemonText pokemon, double price, long now) {
		pending.computeIfAbsent(type, t -> {
			opened.put(t, now);
			return new ArrayList<>();
		}).add(new Item(pokemon, price));
	}

	/**
	 * Takes the broadcasts whose window is over.
	 * @param now the current time in ms.
	 * @param windowMs how long a window collects broadcasts.
	 * @param templates the current message of each type.
	 * @return the chat lines to send, in order. The returned broadcasts are removed from the queue.
	 */
	public List<String> due(long now, long windowMs, Function<BroadcastType, String> templates) {
		List<String> lines = new ArrayList<>();
		for (BroadcastType type : ORDER) {
			List<Item> items = pending.get(type);
			if (items == null || now - opened.get(type) < windowMs) {
				continue;
			}
			pending.remove(type);
			opened.remove(type);
			lines.addAll(render(templates.apply(type), items));
		}
		return lines;
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	/**
	 * Turns the queued broadcasts of one type into chat lines: one line if they'd all read the same, one line
	 * with the names joined if only {pokemon} differs, otherwise one line per hunt.
	 */
	static List<String> render(String template, List<Item> items) {
		Set<String> filled = new LinkedHashSet<>();
		for (Item item : items) {
			filled.add(Placeholders.fill(template, null, null, item.pokemon(), item.price()));
		}
		if (filled.size() <= 1 || !joinable(template)) {
			return new ArrayList<>(filled.size() == 1 ? filled : fillEach(template, items));
		}
		String names = items.stream()
				.map(item -> item.pokemon() == null || item.pokemon().name() == null ? "?" : item.pokemon().name())
				.collect(Collectors.joining(", "));
		return List.of(Placeholders.fill(template, null, null, new PokemonText(names, null, null), -1));
	}

	private static List<String> fillEach(String template, List<Item> items) {
		List<String> lines = new ArrayList<>();
		for (Item item : items) {
			lines.add(Placeholders.fill(template, null, null, item.pokemon(), item.price()));
		}
		return lines;
	}

	private static boolean joinable(String template) {
		if (template == null || !template.contains(Placeholders.POKEMON)) {
			return false;
		}
		for (String placeholder : NOT_JOINABLE) {
			if (template.contains(placeholder)) {
				return false;
			}
		}
		return true;
	}
}
