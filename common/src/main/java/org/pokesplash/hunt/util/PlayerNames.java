package org.pokesplash.hunt.util;

import java.util.regex.Pattern;

/**
 * Checks on player names. Has no Minecraft dependencies, so it can be tested without a server.
 */
public final class PlayerNames {
	// Names Mojang accounts can have. Anything else (offline mode, proxies) may contain
	// selectors, spaces or quotes that change the meaning of a command.
	private static final Pattern COMMAND_SAFE = Pattern.compile("[A-Za-z0-9_]{1,16}");

	private PlayerNames() {
	}

	/**
	 * Checks that a name can be put into a command as a literal argument.
	 * @param name the player name.
	 * @return true if the name only contains letters, digits and underscores and is 1-16 characters long.
	 */
	public static boolean isCommandSafe(String name) {
		return name != null && COMMAND_SAFE.matcher(name).matches();
	}
}
