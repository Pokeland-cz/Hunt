package org.pokesplash.hunt.event;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.hunts.HuntTarget;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.util.CommandResult;
import org.pokesplash.hunt.util.FileStore;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Records every hunt completion: one JSON line in config/hunt/payouts.log and one readable line in the
 * server log. Currency can be moved between players, so this is the way to trace a payout afterwards.
 */
public final class PayoutLog {
	public static final String FILENAME = "payouts.log";

	// Nulls are kept so every line has the same fields; HTML escaping is off so commands stay readable.
	private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	/**
	 * What happened to the money part of a reward.
	 */
	public enum Outcome {
		/** The hunt has no price, so nothing was paid. */
		NO_PRICE,
		/** The economy paid the price. */
		PAID,
		/** The economy refused the payment (returned false). */
		DECLINED,
		/** The economy threw an error. */
		ERROR
	}

	private PayoutLog() {
	}

	/**
	 * Records a completed hunt. Never throws: a field that can't be read is logged as null.
	 * @param economy the economy class used, or null if none was found.
	 * @param error the economy error message, or null.
	 */
	public static void record(ServerPlayer player, SingleHunt hunt, Pokemon caught, double price, String economy,
							  Outcome outcome, double paid, String error, List<CommandResult> commands) {
		String uuid = safe(() -> player.getUUID().toString());
		String name = safe(() -> player.getName().getString());
		String huntId = safe(() -> hunt.getId().toString());
		String target = safe(() -> HuntTarget.key(hunt.getPokemon().getSpecies(), hunt.getPokemon().getForm()));
		String bucket = safe(() -> hunt.getBucket().name().toLowerCase());

		JsonObject json = new JsonObject();
		json.addProperty("time", Instant.now().toString());
		json.addProperty("uuid", uuid);
		json.addProperty("name", name);
		json.addProperty("huntId", huntId);
		json.addProperty("target", target);
		json.addProperty("bucket", bucket);
		json.addProperty("price", price);
		json.addProperty("economy", economy);
		json.addProperty("outcome", outcome == null ? null : outcome.name());
		json.addProperty("paid", paid);
		json.addProperty("error", error);
		json.addProperty("caughtPokemon", safe(() -> caught.getUuid().toString()));

		int ran = 0;
		int skipped = 0;
		int failed = 0;
		JsonArray commandsJson = new JsonArray();
		if (commands != null) {
			for (CommandResult result : commands) {
				JsonObject commandJson = new JsonObject();
				commandJson.addProperty("command", result.command());
				commandJson.addProperty("status", result.status().name());
				commandJson.addProperty("result", result.result());
				commandsJson.add(commandJson);
				switch (result.status()) {
					case RAN -> ran++;
					case SKIPPED_UNSAFE_NAME -> skipped++;
					case FAILED -> failed++;
				}
			}
		}
		json.add("commands", commandsJson);

		String line = GSON.toJson(json);
		FileStore.appendAsync(FILENAME, line);

		Hunt.LOGGER.info("Hunt payout: " + name + " (" + uuid + ") hunt " + huntId + " " + target
				+ " [" + bucket + "] price " + price + " -> " + outcome + (error == null ? "" : " (" + error + ")")
				+ ", commands: " + ran + " ran, " + skipped + " skipped, " + failed + " failed");
	}

	private static String safe(Supplier<String> value) {
		try {
			return value.get();
		} catch (Exception ignored) {
			return null;
		}
	}
}
