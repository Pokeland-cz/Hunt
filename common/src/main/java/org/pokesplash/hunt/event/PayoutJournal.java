package org.pokesplash.hunt.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.event.CompletionService.Completion;
import org.pokesplash.hunt.event.CompletionService.PayResult;
import org.pokesplash.hunt.util.CommandResult;
import org.pokesplash.hunt.util.FileStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The payout journal: config/hunt/payouts.log, one JSON line per event, never overwritten.
 * Each completion writes a PENDING line (on disk before anything is paid) and a DONE line with the result,
 * both with the same completionId. A PENDING line without a DONE line means the server stopped during the
 * payout; those are reported at startup. Lines from before 1.3.4 have no phase and count as done.
 * Currency can be moved between players, so this is also the way to trace a payout afterwards.
 */
public final class PayoutJournal implements CompletionService.Journal {
	public static final String FILENAME = "payouts.log";
	public static final PayoutJournal DEFAULT = new PayoutJournal(FILENAME);

	public static final String PENDING = "PENDING";
	public static final String DONE = "DONE";

	// Nulls are kept so every line has the same fields; HTML escaping is off so commands stay readable.
	private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	private final String filename;

	/**
	 * @param filename the file in config/hunt.
	 */
	public PayoutJournal(String filename) {
		this.filename = filename;
	}

	/**
	 * What the journal says, read at startup.
	 * @param incomplete PENDING lines without a DONE line (completionId → the line), oldest first.
	 * @param claimedHuntIds every hunt that was completed, so a saved hunt that was already completed isn't restored.
	 * @param unreadable lines that couldn't be parsed and were skipped.
	 */
	public record Scan(Map<String, String> incomplete, Set<UUID> claimedHuntIds, int unreadable) {
	}

	@Override
	public boolean pending(Completion completion) {
		JsonObject json = start(PENDING, completion);
		JsonArray commands = new JsonArray();
		for (String command : completion.commands()) {
			if (command != null) {
				commands.add(command);
			}
		}
		json.add("commands", commands);
		return FileStore.appendSync(filename, GSON.toJson(json));
	}

	@Override
	public void done(Completion completion, PayResult pay, double paid, List<CommandResult> commands) {
		JsonObject json = start(DONE, completion);
		json.addProperty("economy", pay == null ? null : pay.economy());
		json.addProperty("outcome", pay == null || pay.outcome() == null ? null : pay.outcome().name());
		json.addProperty("paid", paid);
		json.addProperty("error", pay == null ? null : pay.error());
		json.addProperty("caughtPokemon", completion.caughtPokemon());

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
					case SKIPPED_UNSAFE_NAME, SKIPPED_PAYMENT_FAILED -> skipped++;
					case FAILED -> failed++;
				}
			}
		}
		json.add("commands", commandsJson);

		FileStore.appendAsync(filename, GSON.toJson(json));

		Hunt.LOGGER.info("Hunt payout: " + completion.playerName() + " (" + completion.player() + ") hunt "
				+ completion.huntId() + " " + completion.target() + " [" + bucket(completion) + "] price "
				+ completion.price() + " -> " + (pay == null ? null : pay.outcome())
				+ (pay == null || pay.error() == null ? "" : " (" + pay.error() + ")")
				+ ", commands: " + ran + " ran, " + skipped + " skipped, " + failed + " failed");
	}

	/**
	 * Reads the journal. A missing file is an empty journal.
	 * @throws IOException if the file exists but can't be read.
	 */
	public Scan scan() throws IOException {
		Map<String, String> incomplete = new LinkedHashMap<>();
		Set<UUID> claimed = new HashSet<>();
		int unreadable = 0;

		// InputStreamReader replaces invalid UTF-8 instead of failing, so one bad byte doesn't hide the rest.
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				Files.newInputStream(FileStore.path(filename)), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				JsonObject json;
				try {
					json = JsonParser.parseString(line).getAsJsonObject();
				} catch (Exception e) {
					unreadable++;
					continue;
				}

				String huntId = string(json, "huntId");
				if (huntId != null) {
					try {
						claimed.add(UUID.fromString(huntId));
					} catch (IllegalArgumentException ignored) {
					}
				}

				String phase = string(json, "phase");
				String completionId = string(json, "completionId");
				if (completionId == null) {
					continue;
				}
				if (PENDING.equals(phase)) {
					incomplete.put(completionId, line);
				} else if (DONE.equals(phase)) {
					incomplete.remove(completionId);
				}
			}
		} catch (NoSuchFileException e) {
			return new Scan(Map.of(), Set.of(), 0);
		}
		return new Scan(incomplete, claimed, unreadable);
	}

	private static JsonObject start(String phase, Completion completion) {
		JsonObject json = new JsonObject();
		json.addProperty("time", Instant.now().toString());
		json.addProperty("phase", phase);
		json.addProperty("completionId", string(completion.completionId()));
		json.addProperty("uuid", string(completion.player()));
		json.addProperty("name", completion.playerName());
		json.addProperty("huntId", string(completion.huntId()));
		json.addProperty("target", completion.target());
		json.addProperty("bucket", bucket(completion));
		json.addProperty("price", completion.price());
		return json;
	}

	private static String bucket(Completion completion) {
		return completion.bucket() == null ? null : completion.bucket().name().toLowerCase(Locale.ROOT);
	}

	private static String string(Object value) {
		return value == null ? null : value.toString();
	}

	private static String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
	}
}
