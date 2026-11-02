package org.pokesplash.hunt.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Fills in message and command placeholders. Has no Minecraft dependencies, so it can be tested without a server.
 */
public final class Placeholders {
	public static final String PLAYER = "{player}";
	public static final String UUID = "{uuid}";
	public static final String POKEMON = "{pokemon}";
	public static final String PRICE = "{price}";

	private Placeholders() {
	}

	/**
	 * Fills in the {player}, {uuid}, {pokemon} and {price} placeholders. Values are inserted literally,
	 * so characters such as $ or \ in a name can't break the substitution.
	 * @param template the message or command.
	 * @param playerName the player's name, or null to leave {player} and {uuid} as they are.
	 * @param uuid the player's UUID, used together with the name.
	 * @param pokemonName the Pokemon's display name, or null to leave {pokemon} as it is.
	 * @param price the price, or -1 to leave {price} as it is.
	 * @return the filled in text, or an empty string if the template is null.
	 */
	public static String fill(String template, String playerName, String uuid, String pokemonName, double price) {
		if (template == null) {
			return "";
		}
		String result = template;

		if (playerName != null) {
			result = result.replace(PLAYER, playerName);
			result = result.replace(UUID, String.valueOf(uuid));
		}

		if (pokemonName != null) {
			result = result.replace(POKEMON, pokemonName);
		}

		if (price != -1) {
			result = result.replace(PRICE, formatPrice(price));
		}

		return result;
	}

	/**
	 * Formats a price with at most two decimals and always a dot as the decimal separator, whatever the
	 * server's locale, so reward commands such as "eco give {uuid} {price}" can read it (HUNT-030).
	 */
	public static String formatPrice(double price) {
		// DecimalFormat isn't thread-safe, so a new one is made each time.
		return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(price);
	}

	/**
	 * Checks whether a reward command can run for a player. A command that uses {player} only runs if the
	 * name is safe to put into a command (it could be a selector like @a); other commands always run.
	 * @param template the command as written in the config.
	 * @param playerName the player's name.
	 */
	public static boolean commandAllowed(String template, String playerName) {
		return !template.contains(PLAYER) || PlayerNames.isCommandSafe(playerName);
	}
}
