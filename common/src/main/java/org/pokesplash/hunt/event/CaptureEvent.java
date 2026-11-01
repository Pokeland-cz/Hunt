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
import org.pokesplash.hunt.hunts.ReplacedHunt;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.util.Utils;

import java.util.UUID;

public abstract class CaptureEvent {
	public static void registerEvents() {
		// Capture event checks the current hunts.
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> {

			ServerPlayer player = e.getPlayer();
			Pokemon pokemon = e.getPokemon();

			CurrentHunts currentHunts = Hunt.getHuntsFor(player.getUUID());
			UUID matchedUUID = currentHunts == null ? null : currentHunts.matches(pokemon);

			// If the pokemon doesn't match a hunt pokemon, matchedUUID will be null, otherwise it'll be a UUID.
			if (matchedUUID != null) {
				SingleHunt hunt = currentHunts.getHunt(matchedUUID);

				double price = hunt.getPrice();

				ReplacedHunt replacedHunt = currentHunts.replaceHunt(matchedUUID, false);

				if (!Hunt.config.isIndividualHunts()) {
					Hunt.broadcaster.sendBroadcast(BroadcastType.CAPTURED, player, pokemon, price);
				}

				if (replacedHunt != null) {
					HuntEvents.COMPLETED.trigger(new CompletedEvent(replacedHunt.getOldHunt(), player.getUUID()));
				}

				double paid = 0;

				// Checks there's a price.
				if (price > 0) {
					try {
						// Performs the transaction.
						boolean success = HuntEconomyProvider.getHighestEconomy().add(player.getUUID(), price);

						// If the transaction was successful, replace the caught pokemon in hunt and send some messages.
						if (success) {
							paid = price;

							player.sendSystemMessage(Component.literal(Utils.formatPlaceholders(
									Hunt.language.getPayMessage(), player, pokemon, price
							)));
						}
					} catch (Exception | LinkageError ex) {
						// If any errors occur, send log to console.
						Hunt.LOGGER.error("Could not process hunt " + matchedUUID + " for " + player.getName().getString());
						// Just in case playerlist is empty for some random reason.
						ex.printStackTrace();
					}
				}

				Hunt.stats.recordCompletion(player, hunt.getBucket(), paid);

				// Runs commands
				if (hunt.getCommands() != null) {
					Utils.runCommands(hunt.getCommands(), player, pokemon, price);
				}

			}
			return Unit.INSTANCE;
		});
	}
}
