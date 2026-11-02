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
import org.pokesplash.hunt.event.CompletionService.Completion;
import org.pokesplash.hunt.hunts.CurrentHunts;
import org.pokesplash.hunt.hunts.HuntTarget;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.util.Utils;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Listens for captures and hands matching ones to {@link CompletionService}, which claims the hunt and pays.
 */
public abstract class CaptureEvent {
	public static void registerEvents() {
		// Capture event checks the current hunts.
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> {
			// While Hunt is paused (config.json couldn't be loaded) nothing can be completed or paid.
			if (!Hunt.isActive()) {
				return Unit.INSTANCE;
			}
			ServerPlayer player = e.getPlayer();
			Pokemon pokemon = e.getPokemon();
			UUID huntId = null;

			// This runs inside the Poke Ball entity tick, so an exception here would crash the server.
			try {
				CurrentHunts currentHunts = Hunt.getHuntsFor(player.getUUID());
				huntId = currentHunts == null ? null : currentHunts.matches(pokemon);

				// If the pokemon doesn't match a hunt pokemon, huntId is null.
				if (huntId != null) {
					SingleHunt hunt = currentHunts.getHunt(huntId);
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

	private static void complete(CurrentHunts currentHunts, SingleHunt hunt, ServerPlayer player, Pokemon pokemon) {
		double price = hunt.getPrice();
		Completion completion = new Completion(
				UUID.randomUUID(),
				player.getUUID(),
				player.getName().getString(),
				hunt.getId(),
				safe(() -> HuntTarget.key(hunt.getPokemon().getSpecies(), hunt.getPokemon().getForm())),
				hunt.getBucket(),
				price,
				hunt.getCommands(),
				safe(() -> pokemon.getUuid().toString()));

		CompletionService.Notifier notifier = new CompletionService.Notifier() {
			@Override
			public void captured() {
				if (!Hunt.config.isIndividualHunts()) {
					Hunt.broadcaster.sendBroadcast(BroadcastType.CAPTURED, player, pokemon, price);
				}
			}

			@Override
			public void completedEvent() {
				HuntEvents.COMPLETED.trigger(new CompletedEvent(hunt, player.getUUID()));
			}

			@Override
			public void paid() {
				player.sendSystemMessage(Component.literal(Utils.formatPlaceholders(
						Hunt.language.getPayMessage(), player, pokemon, price)));
			}

			@Override
			public void paymentFailed() {
				player.sendSystemMessage(Component.literal(Utils.formatPlaceholders(
						Hunt.language.getPayFailedMessage(), player, pokemon, price)));
			}
		};

		new CompletionService(
				id -> claim(currentHunts, id, completion),
				HuntEconomyProvider::getHighestEconomy,
				commands -> Utils.runCommands(commands, player, pokemon, price),
				notifier,
				(bucket, paid) -> Hunt.stats.recordCompletion(player, bucket, paid),
				PayoutJournal.DEFAULT
		).complete(completion);
	}

	/**
	 * Removes the hunt, adds a replacement (before the captured broadcast, same message order as before)
	 * and queues hunts.json, so it's on disk before the payout starts.
	 * @return false if the hunt was already gone.
	 */
	private static boolean claim(CurrentHunts currentHunts, UUID huntId, Completion completion) {
		SingleHunt removed = currentHunts.removeHunt(huntId, false);
		if (removed == null) {
			return false;
		}
		try {
			// The completed species isn't hunted again right away (HUNT-026).
			currentHunts.addHunt(CurrentHunts.keyOf(removed));
		} catch (Exception ex) {
			Hunt.LOGGER.error("Could not create a replacement hunt. Fix the config, then run /hunt reload "
					+ "to top up the hunts. " + completion.describe(), ex);
		}
		try {
			Hunt.storage.saveSoon();
		} catch (Exception ex) {
			Hunt.LOGGER.error("Could not queue saving the hunts. " + completion.describe(), ex);
		}
		return true;
	}

	private static String safe(Supplier<String> value) {
		try {
			return value.get();
		} catch (Exception ignored) {
			return null;
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
