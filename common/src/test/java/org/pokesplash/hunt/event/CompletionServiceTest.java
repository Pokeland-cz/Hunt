package org.pokesplash.hunt.event;

import org.junit.jupiter.api.Test;
import org.pokesplash.hunt.api.economy.HuntEconomy;
import org.pokesplash.hunt.event.CompletionService.Completion;
import org.pokesplash.hunt.event.CompletionService.Outcome;
import org.pokesplash.hunt.event.CompletionService.PayResult;
import org.pokesplash.hunt.event.CompletionService.Result;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.util.CommandResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payout flow with fake dependencies. Every fake records what was called, in order, in {@link #calls}.
 */
class CompletionServiceTest {

	private final List<String> calls = new ArrayList<>();
	private final Set<UUID> board = new HashSet<>();

	// What the fakes do; tests change these.
	private HuntEconomy economy = (player, amount) -> {
		calls.add("pay " + amount);
		return true;
	};
	private boolean economyMissing = false;
	private boolean pendingResult = true;
	private boolean pendingThrows = false;
	private boolean notifierThrows = false;
	private boolean statsThrow = false;
	private boolean commandsThrow = false;

	// What the fakes received.
	private PayResult donePay;
	private Double donePaid;
	private List<CommandResult> doneCommands;
	private Double statsPaid;
	private List<String> commandsRun;

	private CompletionService service() {
		return new CompletionService(
				huntId -> {
					calls.add("claim");
					return board.remove(huntId);
				},
				() -> {
					if (economyMissing) {
						throw new Exception("No economy could be found.");
					}
					return economy;
				},
				commands -> {
					calls.add("commands");
					if (commandsThrow) {
						throw new IllegalStateException("dispatcher broken");
					}
					commandsRun = commands;
					return commands.stream()
							.filter(c -> c != null)
							.map(c -> new CommandResult(c, c, CommandResult.Status.RAN, 1))
							.toList();
				},
				new CompletionService.Notifier() {
					@Override
					public void captured() {
						calls.add("captured");
						if (notifierThrows) {
							throw new IllegalStateException("broadcast broken");
						}
					}

					@Override
					public void completedEvent() {
						calls.add("event");
						if (notifierThrows) {
							throw new IllegalStateException("event broken");
						}
					}

					@Override
					public void paid() {
						calls.add("paidMessage");
					}

					@Override
					public void paymentFailed() {
						calls.add("failedMessage");
					}
				},
				(bucket, paid) -> {
					calls.add("stats");
					statsPaid = paid;
					if (statsThrow) {
						throw new IllegalStateException("stats broken");
					}
				},
				new CompletionService.Journal() {
					@Override
					public boolean pending(Completion completion) {
						calls.add("pending");
						if (pendingThrows) {
							throw new IllegalStateException("disk full");
						}
						return pendingResult;
					}

					@Override
					public void done(Completion completion, PayResult pay, double paid, List<CommandResult> commands) {
						calls.add("done");
						donePay = pay;
						donePaid = paid;
						doneCommands = commands;
					}
				});
	}

	private Completion completion(double price, String... commands) {
		UUID huntId = UUID.randomUUID();
		board.add(huntId);
		return new Completion(UUID.randomUUID(), UUID.randomUUID(), "Steve", huntId, "pikachu/normal",
				Bucket.RARE, price, Arrays.asList(commands), UUID.randomUUID().toString());
	}

	@Test
	void paidFlowRunsEveryStepInOrder() {
		Result result = service().complete(completion(500, "give {uuid} minecraft:diamond 1"));

		assertTrue(result.claimed());
		assertEquals(Outcome.PAID, result.outcome());
		assertEquals(500, result.paid());
		assertTrue(result.journalOk());
		assertEquals(List.of("claim", "captured", "event", "pending", "pay 500.0", "paidMessage", "commands",
				"stats", "done"), calls);
		assertEquals(Outcome.PAID, donePay.outcome());
		assertNull(donePay.error());
		assertEquals(500, donePaid);
		assertEquals(500, statsPaid);
		assertEquals(1, doneCommands.size());
		assertEquals(CommandResult.Status.RAN, doneCommands.get(0).status());
	}

	// HUNT-007: the same hunt can only be completed once.
	@Test
	void secondCompletionOfTheSameHuntDoesNothing() {
		Completion completion = completion(500, "give {uuid} minecraft:diamond 1");
		CompletionService service = service();
		assertTrue(service.complete(completion).claimed());
		calls.clear();
		donePay = null;

		Result second = service.complete(completion);

		assertFalse(second.claimed());
		assertEquals(List.of("claim"), calls);
		assertNull(donePay);
	}

	// HUNT-007: a failed payment must not run the reward commands.
	@Test
	void declinedPaymentSkipsCommands() {
		economy = (player, amount) -> {
			calls.add("pay " + amount);
			return false;
		};
		Result result = service().complete(completion(500, "give {uuid} minecraft:diamond 1", null, "say hi"));

		assertEquals(Outcome.DECLINED, result.outcome());
		assertEquals(0, result.paid());
		assertFalse(calls.contains("commands"));
		assertTrue(calls.contains("failedMessage"));
		assertFalse(calls.contains("paidMessage"));
		assertEquals(Outcome.DECLINED, donePay.outcome());
		assertNotNull(donePay.economy());
		assertEquals(0, donePaid);
		assertEquals(0, statsPaid);
		assertEquals(2, doneCommands.size());
		doneCommands.forEach(c -> assertEquals(CommandResult.Status.SKIPPED_PAYMENT_FAILED, c.status()));
		assertEquals("say hi", doneCommands.get(1).template());
	}

	@Test
	void economyErrorSkipsCommands() {
		economy = (player, amount) -> {
			throw new IllegalStateException("database down");
		};
		Result result = service().complete(completion(500, "give {uuid} minecraft:diamond 1"));

		assertEquals(Outcome.ERROR, result.outcome());
		assertFalse(calls.contains("commands"));
		assertTrue(calls.contains("failedMessage"));
		assertTrue(donePay.error().contains("database down"));
		assertEquals(CommandResult.Status.SKIPPED_PAYMENT_FAILED, doneCommands.get(0).status());
		assertEquals(0, statsPaid);
	}

	@Test
	void missingEconomyIsAnError() {
		economyMissing = true;
		Result result = service().complete(completion(500, "say hi"));

		assertEquals(Outcome.ERROR, result.outcome());
		assertNull(donePay.economy());
		assertTrue(donePay.error().contains("No economy"));
		assertFalse(calls.contains("commands"));
	}

	@Test
	void noPriceRunsCommandsWithoutPaying() {
		Result result = service().complete(completion(0, "give {uuid} minecraft:diamond 1"));

		assertEquals(Outcome.NO_PRICE, result.outcome());
		assertTrue(calls.stream().noneMatch(c -> c.startsWith("pay")));
		assertFalse(calls.contains("paidMessage"));
		assertFalse(calls.contains("failedMessage"));
		assertTrue(calls.contains("commands"));
		assertEquals(CommandResult.Status.RAN, doneCommands.get(0).status());
	}

	@Test
	void noCommandsDoesNotCallTheRunner() {
		Result result = service().complete(completion(100));

		assertEquals(Outcome.PAID, result.outcome());
		assertFalse(calls.contains("commands"));
		assertTrue(doneCommands.isEmpty());
	}

	@Test
	void nullCommandListIsEmpty() {
		UUID huntId = UUID.randomUUID();
		board.add(huntId);
		Completion completion = new Completion(UUID.randomUUID(), UUID.randomUUID(), "Steve", huntId, null,
				null, 100, null, null);

		Result result = service().complete(completion);

		assertEquals(Outcome.PAID, result.outcome());
		assertTrue(doneCommands.isEmpty());
	}

	// The player must not lose the reward because the log couldn't be written.
	@Test
	void failedPendingWriteStillPays() {
		pendingResult = false;
		Result result = service().complete(completion(500, "say hi"));

		assertFalse(result.journalOk());
		assertEquals(Outcome.PAID, result.outcome());
		assertTrue(calls.contains("commands"));
		assertTrue(calls.contains("done"));
	}

	@Test
	void throwingPendingWriteStillPays() {
		pendingThrows = true;
		Result result = service().complete(completion(500, "say hi"));

		assertFalse(result.journalOk());
		assertEquals(Outcome.PAID, result.outcome());
		assertTrue(calls.contains("commands"));
		assertTrue(calls.contains("done"));
	}

	@Test
	void failingSideStepsDoNotStopThePayout() {
		notifierThrows = true;
		statsThrow = true;
		Result result = service().complete(completion(500, "say hi"));

		assertEquals(Outcome.PAID, result.outcome());
		assertEquals(List.of("claim", "captured", "event", "pending", "pay 500.0", "paidMessage", "commands",
				"stats", "done"), calls);
		assertEquals(Outcome.PAID, donePay.outcome());
	}

	@Test
	void failingCommandRunnerStillWritesDone() {
		commandsThrow = true;
		Result result = service().complete(completion(500, "say hi"));

		assertEquals(Outcome.PAID, result.outcome());
		assertTrue(result.commands().isEmpty());
		assertTrue(calls.contains("stats"));
		assertTrue(calls.contains("done"));
	}

	@Test
	void throwingClaimPaysNothing() {
		CompletionService service = new CompletionService(
				huntId -> {
					throw new IllegalStateException("broken board");
				},
				() -> economy, commands -> List.of(), null, null, null);

		Result result = service.complete(completion(500, "say hi"));

		assertFalse(result.claimed());
		assertTrue(calls.isEmpty());
	}

	@Test
	void commandsGetTheTemplatesInOrder() {
		service().complete(completion(0, "a", "b", "c"));
		assertEquals(List.of("a", "b", "c"), commandsRun);
	}
}
