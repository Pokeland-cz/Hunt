package org.pokesplash.hunt.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.event.CompletionService.Completion;
import org.pokesplash.hunt.event.CompletionService.Outcome;
import org.pokesplash.hunt.event.CompletionService.PayResult;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.util.CommandResult;
import org.pokesplash.hunt.util.FileStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The journal works in config/hunt under the test working directory (build/test-run, emptied before each run).
 * Every test uses its own file.
 */
class PayoutJournalTest {

	private static Completion completion(String name, String... commands) {
		return new Completion(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), "pikachu/normal",
				Bucket.ULTRA_RARE, 1000, List.of(commands), UUID.randomUUID().toString());
	}

	private static List<String> lines(String file) throws IOException {
		FileStore.writeSync("flush-" + file, ""); // waits for queued appends
		return Files.readAllLines(FileStore.path(file), StandardCharsets.UTF_8);
	}

	private static void writeLines(String file, String... lines) throws IOException {
		Files.createDirectories(FileStore.path(file).getParent());
		Files.write(FileStore.path(file), List.of(lines), StandardCharsets.UTF_8);
	}

	@Test
	void pendingAndDoneShareTheCompletionId() throws IOException {
		PayoutJournal journal = new PayoutJournal("journal-pair.log");
		Completion completion = completion("Ash\"\\", "give {uuid} minecraft:diamond 1");

		assertTrue(journal.pending(completion));
		journal.done(completion, new PayResult(Outcome.PAID, "ImpactorService", null), 1000,
				List.of(new CommandResult("give {uuid} minecraft:diamond 1", "give x minecraft:diamond 1",
						CommandResult.Status.RAN, 1)));

		List<String> lines = lines("journal-pair.log");
		assertEquals(2, lines.size());
		JsonObject pending = JsonParser.parseString(lines.get(0)).getAsJsonObject();
		JsonObject done = JsonParser.parseString(lines.get(1)).getAsJsonObject();

		assertEquals("PENDING", pending.get("phase").getAsString());
		assertEquals("DONE", done.get("phase").getAsString());
		assertEquals(completion.completionId().toString(), pending.get("completionId").getAsString());
		assertEquals(completion.completionId().toString(), done.get("completionId").getAsString());
		assertEquals("Ash\"\\", pending.get("name").getAsString());
		assertEquals("ultra_rare", pending.get("bucket").getAsString());
		assertEquals("give {uuid} minecraft:diamond 1", pending.getAsJsonArray("commands").get(0).getAsString());

		// The DONE line keeps the fields of the 1.3.3 payout line.
		for (String field : List.of("time", "uuid", "name", "huntId", "target", "bucket", "price", "economy",
				"outcome", "paid", "error", "caughtPokemon", "commands")) {
			assertTrue(done.has(field), field);
		}
		assertEquals("PAID", done.get("outcome").getAsString());
		assertEquals(1000, done.get("paid").getAsDouble());
		assertTrue(done.get("error").isJsonNull());
		assertEquals("RAN", done.getAsJsonArray("commands").get(0).getAsJsonObject().get("status").getAsString());
	}

	@Test
	void scanFindsPendingWithoutDone() throws IOException {
		PayoutJournal journal = new PayoutJournal("journal-incomplete.log");
		Completion finished = completion("Steve");
		Completion crashed = completion("Alex");

		journal.pending(finished);
		journal.done(finished, new PayResult(Outcome.PAID, "Economy", null), 1000, List.of());
		journal.pending(crashed);
		lines("journal-incomplete.log");

		PayoutJournal.Scan scan = journal.scan();
		assertEquals(1, scan.incomplete().size());
		assertTrue(scan.incomplete().containsKey(crashed.completionId().toString()));
		assertTrue(scan.incomplete().get(crashed.completionId().toString()).contains("\"Alex\""));
		assertTrue(scan.claimedHuntIds().contains(finished.huntId()));
		assertTrue(scan.claimedHuntIds().contains(crashed.huntId()));
		assertEquals(0, scan.unreadable());
	}

	@Test
	void failedPaymentWithDoneIsNotIncomplete() throws IOException {
		PayoutJournal journal = new PayoutJournal("journal-declined.log");
		Completion completion = completion("Steve", "say hi");
		journal.pending(completion);
		journal.done(completion, new PayResult(Outcome.DECLINED, "Economy", null), 0,
				List.of(new CommandResult("say hi", "say hi", CommandResult.Status.SKIPPED_PAYMENT_FAILED, 0)));
		lines("journal-declined.log");

		assertTrue(journal.scan().incomplete().isEmpty());
	}

	@Test
	void legacyLinesOnlyMarkTheHuntCompleted() throws IOException {
		UUID huntId = UUID.randomUUID();
		writeLines("journal-legacy.log",
				"{\"time\":\"2026-11-02T10:00:00Z\",\"uuid\":\"" + UUID.randomUUID() + "\",\"name\":\"Steve\","
						+ "\"huntId\":\"" + huntId + "\",\"outcome\":\"PAID\",\"commands\":[]}");

		PayoutJournal.Scan scan = new PayoutJournal("journal-legacy.log").scan();
		assertTrue(scan.incomplete().isEmpty());
		assertEquals(java.util.Set.of(huntId), scan.claimedHuntIds());
	}

	@Test
	void brokenLinesAreSkipped() throws IOException {
		String completionId = UUID.randomUUID().toString();
		writeLines("journal-broken.log",
				"{\"phase\":\"PENDING\",\"completionId\":\"" + completionId + "\",\"huntId\":\"not-a-uuid\"}",
				"{broken",
				"",
				"[1,2]",
				"{\"phase\":\"PENDING\",\"huntId\":\"" + UUID.randomUUID() + "\"}");

		PayoutJournal.Scan scan = new PayoutJournal("journal-broken.log").scan();
		assertEquals(2, scan.unreadable());
		assertEquals(1, scan.incomplete().size());
		assertTrue(scan.incomplete().containsKey(completionId));
		assertEquals(1, scan.claimedHuntIds().size());
	}

	@Test
	void invalidUtf8DoesNotHideOtherLines() throws IOException {
		String completionId = UUID.randomUUID().toString();
		Files.createDirectories(FileStore.path("x").getParent());
		byte[] bad = "{\"name\":\"\u0000\"}\n".getBytes(StandardCharsets.UTF_8);
		bad[9] = (byte) 0xE9;
		byte[] good = ("{\"phase\":\"PENDING\",\"completionId\":\"" + completionId + "\"}\n")
				.getBytes(StandardCharsets.UTF_8);
		byte[] all = new byte[bad.length + good.length];
		System.arraycopy(bad, 0, all, 0, bad.length);
		System.arraycopy(good, 0, all, bad.length, good.length);
		Files.write(FileStore.path("journal-latin1.log"), all);

		assertTrue(new PayoutJournal("journal-latin1.log").scan().incomplete().containsKey(completionId));
	}

	@Test
	void missingFileIsEmpty() throws IOException {
		PayoutJournal.Scan scan = new PayoutJournal("journal-missing.log").scan();
		assertTrue(scan.incomplete().isEmpty());
		assertTrue(scan.claimedHuntIds().isEmpty());
		assertEquals(0, scan.unreadable());
	}

	// The pending line must be on disk before the payout, together with everything queued before it.
	@Test
	void appendSyncWaitsForQueuedAppends() throws IOException {
		for (int i = 0; i < 20; i++) {
			FileStore.appendAsync("journal-order.log", "line " + i);
		}
		assertTrue(FileStore.appendSync("journal-order.log", "last"));

		List<String> lines = Files.readAllLines(FileStore.path("journal-order.log"), StandardCharsets.UTF_8);
		assertEquals(21, lines.size());
		assertEquals("line 0", lines.get(0));
		assertEquals("last", lines.get(20));
		assertFalse(Files.exists(FileStore.path("journal-order.log.bak.1")));
	}

	@Test
	void appendSyncReportsFailure() throws IOException {
		Files.createDirectories(FileStore.path("journal-dir.log"));
		assertFalse(FileStore.appendSync("journal-dir.log", "x"));
	}
}
