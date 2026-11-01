package org.pokesplash.hunt.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.api.economy.HuntEconomyProvider;
import org.pokesplash.hunt.api.event.HuntEvents;
import org.pokesplash.hunt.api.event.events.CompletedEvent;
import org.pokesplash.hunt.broadcast.BroadcastType;
import org.pokesplash.hunt.hunts.CurrentHunts;
import org.pokesplash.hunt.hunts.HuntTarget;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.util.Utils;

import java.util.UUID;

public abstract class CaptureEvent {
	public static void registerEvents() {
		// Capture event checks the current hunts.
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> {
			ServerPlayer player = e.getPlayer();
			Pokemon pokemon = e.getPokemon();
			UUID huntId = null;

			// This runs inside the Poke Ball entity tick, so an exception here would crash the server.
			try {
				CurrentHunts currentHunts = Hunt.getHuntsFor(player.getUUID());
				huntId = currentHunts == null ? null : currentHunts.matches(pokemon);

				// If the pokemon doesn't match a hunt pokemon, huntId is null.
				if (huntId != null) {
					// Claim the hunt first, so it can't be completed twice.
					SingleHunt hunt = currentHunts.removeHunt(huntId, false);
					if (hunt != null) {
						complete(currentHunts, hunt, player, pokemon);
					}
				}
			} catch (VirtualMachineError err) {
				throw err;
			} catch (Throwable t) {
				Hunt.LOGGER.error("Hunt capture handling failed. " + describe(player, huntId, pokemon)
						+ " The player may need to be compensated manually.", t);
			}
			return Unit.INSTANCE;
		});
	}

	/**
	 * Completes a hunt that was already removed from the current hunts. Each step is guarded on its own,
	 * so a failing step doesn't cancel the others (in particular not the payout).
	 */
	private static void complete(CurrentHunts currentHunts, SingleHunt hunt, ServerPlayer player, Pokemon pokemon) {
		double price = hunt.getPrice();

		// Replacement hunt, before the captured broadcast (same message order as before).
		try {
			currentHunts.addHunt();
		} catch (Exception ex) {
			Hunt.LOGGER.error("Could not create a replacement hunt. Fix the config, then run /hunt reload "
					+ "to top up the hunts. " + describe(player, hunt.getId(), pokemon), ex);
		}

		if (!Hunt.config.isIndividualHunts()) {
			try {
				Hunt.broadcaster.sendBroadcast(BroadcastType.CAPTURED, player, pokemon, price);
			} catch (Exception ex) {
				Hunt.LOGGER.error("Could not send the hunt captured broadcast. "
						+ describe(player, hunt.getId(), pokemon), ex);
			}
		}

		// Subscribers are guarded in Event.trigger.
		HuntEvents.COMPLETED.trigger(new CompletedEvent(hunt, player.getUUID()));

		double paid = 0;

		// Checks there's a price.
		if (price > 0) {
			try {
				// Performs the transaction.
				boolean success = HuntEconomyProvider.getHighestEconomy().add(player.getUUID(), price);

				if (success) {
					paid = price;

					player.sendSystemMessage(Component.literal(Utils.formatPlaceholders(
							Hunt.language.getPayMessage(), player, pokemon, price
					)));
				}
			} catch (Exception | LinkageError ex) {
				Hunt.LOGGER.error("Could not pay " + price + " for a hunt. "
						+ describe(player, hunt.getId(), pokemon), ex);
			}
		}

		try {
			Hunt.stats.recordCompletion(player, hunt.getBucket(), paid);
		} catch (Exception ex) {
			Hunt.LOGGER.error("Could not record the hunt completion in the stats. "
					+ describe(player, hunt.getId(), pokemon), ex);
		}

		// Runs commands (each command is guarded in runCommands).
		if (hunt.getCommands() != null) {
			Utils.runCommands(hunt.getCommands(), player, pokemon, price);
		}
	}

	/**
	 * Describes a capture for the log, without throwing, so an admin can compensate the player.
	 */
	private static String describe(ServerPlayer player, UUID huntId, Pokemon pokemon) {
		String uuid = "?";
		String name = "?";
		String species = "?";
		try {
			uuid = player.getUUID().toString();
			name = player.getName().getString();
		} catch (Exception ignored) {
		}
		try {
			species = HuntTarget.key(pokemon.getSpecies(), pokemon.getForm());
		} catch (Exception ignored) {
		}
		return "Player " + uuid + " (" + name + "), hunt " + huntId + ", species " + species + ".";
	}
}
