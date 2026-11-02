package org.pokesplash.hunt.util;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.pokesplash.hunt.Hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Abstract class that contains some utility methods.
 */
public abstract class Utils {
	/**
	 * Method to create a new gson builder.
	 * @return Gson instance.
	 */
	public static Gson newGson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

	/**
	 * Formats a message by removing minecraft formatting codes if sending to console.
	 * @param message The message to format.
	 * @param isPlayer If the sender is a player or console.
	 * @return String that is the formatted message.
	 */
	public static String formatMessage(String message, Boolean isPlayer) {
		if (isPlayer) {
			return message.trim();
		} else {
			return message.replaceAll("§[0-9a-fk-or]", "").trim();
		}
	}

	public static String parseLongDate(long time) {
		long second = 1000;
		long minute = second * 60;
		long hour = minute * 60;
		long day = hour * 24;

		long timeLeft = Math.max(0, time);
		StringBuilder output = new StringBuilder();

		if (timeLeft >= day) {
			output.append(timeLeft / day).append("d ");
			timeLeft %= day;
		}

		if (timeLeft >= hour) {
			output.append(timeLeft / hour).append("h ");
			timeLeft %= hour;
		}

		if (timeLeft >= minute) {
			output.append(timeLeft / minute).append("m ");
			timeLeft %= minute;
		}

		// Always show seconds when nothing else is shown, so the output is never empty.
		if (timeLeft >= second || output.length() == 0) {
			output.append(timeLeft / second).append("s ");
		}

		return output.toString();
	}

	public static String capitaliseFirst(String message) {

		if (message.contains("[") || message.contains("]")) {
			return message.replaceAll("\\[|\\]", "");
		}

		if (message.contains("_")) {
			String[] messages = message.split("_");
			String output = "";
			for (String msg : messages) {
				output += capitaliseFirst(msg);
			}
			return output;
		}

		return message.substring(0, 1).toUpperCase() + message.substring(1).toLowerCase();
	}

	/**
	 * Fills in the {player}, {uuid}, {pokemon} and {price} placeholders. Values are inserted literally,
	 * so characters such as $ or \ in a name can't break the substitution.
	 */
	public static String formatPlaceholders(String message, ServerPlayer player, Pokemon pokemon, double price) {
		String name = player == null ? null : player.getName().getString();
		String uuid = player == null ? null : player.getUUID().toString();
		String pokemonName = pokemon == null ? null : pokemon.getDisplayName(true).getString();
		return Placeholders.fill(message, name, uuid, pokemonName, price);
	}

	public static ItemStack parseItemId(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putInt("Count", 1);
		return ItemStack.parse(HolderLookup.Provider.create(Stream.empty()), tag).get();
	}

	/**
	 * Runs the reward commands as the server. A command that uses {player} is skipped if the player's
	 * name isn't safe to put into a command (it could be a selector like @a); commands using {uuid} still run.
	 * @return what happened to each command, for the payout log.
	 */
	public static List<CommandResult> runCommands(List<String> commands, ServerPlayer player, Pokemon pokemon,
												  double price) {
		List<CommandResult> results = new ArrayList<>();
		CommandDispatcher<CommandSourceStack> dispatcher =
				Hunt.server.getCommands().getDispatcher();
		String name = player == null ? null : player.getName().getString();
		for (String command : commands) {
			if (command == null) {
				continue;
			}
			String formatted = command;
			try {
				if (player != null && !Placeholders.commandAllowed(command, name)) {
					Hunt.LOGGER.warn("Skipped hunt reward command \"" + command + "\" for player " + player.getUUID()
							+ " (name \"" + name + "\"): the name is not safe to use in a command. "
							+ "Use {uuid} instead of {player} where the command accepts it.");
					results.add(new CommandResult(command, command, CommandResult.Status.SKIPPED_UNSAFE_NAME, 0));
					continue;
				}
				formatted = formatPlaceholders(command, player, pokemon, price);
				int result = dispatcher.execute(formatted, Hunt.server.createCommandSourceStack());
				results.add(new CommandResult(command, formatted, CommandResult.Status.RAN, result));
			} catch (Exception ex) {
				// Log and carry on so one broken command doesn't stop the other rewards.
				Hunt.LOGGER.error("Failed to run hunt reward command \"" + command + "\" for player "
						+ (player == null ? "null" : player.getUUID()) + ": " + ex.getMessage());
				results.add(new CommandResult(command, formatted, CommandResult.Status.FAILED, 0));
			}
		}
		return results;
	}
}
