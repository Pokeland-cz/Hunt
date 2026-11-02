package org.pokesplash.hunt.event;

import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.api.economy.HuntEconomy;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.util.CommandResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Completes a hunt and pays the reward, in an order that survives a crash:
 * claim the hunt (and queue hunts.json without it) → journal PENDING (written to disk) → pay →
 * commands, only if the payment didn't fail → stats → journal DONE.
 * <p>
 * If the server dies between PENDING and DONE, the PENDING line stays in payouts.log and is reported
 * at the next start, and the completed hunt doesn't come back. Every step is guarded on its own, so a
 * failing step doesn't cancel the others. Has no Minecraft or Cobblemon dependencies, so it can be tested.
 */
public final class CompletionService {

	/**
	 * A hunt that a player is completing.
	 * @param completionId unique id of this completion, links the PENDING and DONE journal lines.
	 * @param target the hunt's species/form key.
	 * @param commands the reward command templates (may contain nulls, those are skipped).
	 * @param caughtPokemon the caught Pokemon's UUID, for the log.
	 */
	public record Completion(UUID completionId, UUID player, String playerName, UUID huntId, String target,
							 Bucket bucket, double price, List<String> commands, String caughtPokemon) {
		public Completion {
			commands = commands == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(commands));
		}

		/**
		 * Describes the completion for the log, so an admin can compensate the player.
		 */
		public String describe() {
			return "Player " + player + " (" + playerName + "), hunt " + huntId + ", species " + target
					+ ", completion " + completionId + ".";
		}
	}

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
		ERROR;

		/**
		 * @return true if the payment didn't fail, so the reward commands may run.
		 */
		public boolean allowsCommands() {
			return this == PAID || this == NO_PRICE;
		}
	}

	/**
	 * @param economy the economy class used, or null if none was found.
	 * @param error the economy error, or null.
	 */
	public record PayResult(Outcome outcome, String economy, String error) {
	}

	/**
	 * What a completion did, for tests.
	 * @param claimed false if the hunt was already completed (nothing else happened then).
	 * @param journalOk false if the PENDING line couldn't be written.
	 */
	public record Result(boolean claimed, Outcome outcome, double paid, List<CommandResult> commands,
						 boolean journalOk) {
		static final Result NOT_CLAIMED = new Result(false, null, 0, List.of(), false);
	}

	/** Removes the hunt from the current hunts, adds a replacement and queues hunts.json to be saved. */
	public interface HuntClaimer {
		/**
		 * @return false if the hunt is gone already (someone else completed it).
		 */
		boolean claim(UUID huntId);
	}

	/** Finds the economy to pay with. */
	public interface EconomySource {
		HuntEconomy get() throws Exception;
	}

	/** Runs the reward commands. */
	public interface RewardCommands {
		List<CommandResult> run(List<String> commands);
	}

	/** Messages and events around a completion. */
	public interface Notifier {
		/** The captured broadcast. */
		void captured();

		/** The HuntEvents.COMPLETED event for other mods. */
		void completedEvent();

		/** Tells the player they were paid. */
		void paid();

		/** Tells the player the payment failed. */
		void paymentFailed();
	}

	/** Records the completion in the stats. */
	public interface StatsRecorder {
		void record(Bucket bucket, double paid);
	}

	/** The payout journal (payouts.log). */
	public interface Journal {
		/**
		 * Writes the PENDING line and waits until it (and anything queued before it) is on disk.
		 * @return true if it was written.
		 */
		boolean pending(Completion completion);

		/** Writes the DONE line with the result. */
		void done(Completion completion, PayResult pay, double paid, List<CommandResult> commands);
	}

	private final HuntClaimer claimer;
	private final EconomySource economy;
	private final RewardCommands commands;
	private final Notifier notifier;
	private final StatsRecorder stats;
	private final Journal journal;

	public CompletionService(HuntClaimer claimer, EconomySource economy, RewardCommands commands,
							 Notifier notifier, StatsRecorder stats, Journal journal) {
		this.claimer = claimer;
		this.economy = economy;
		this.commands = commands;
		this.notifier = notifier;
		this.stats = stats;
		this.journal = journal;
	}

	public Result complete(Completion completion) {
		// 1. Claim first, so the hunt can't be completed twice.
		boolean claimed;
		try {
			claimed = claimer.claim(completion.huntId());
		} catch (Exception | LinkageError ex) {
			Hunt.LOGGER.error("Could not claim the hunt, nothing was paid. " + completion.describe(), ex);
			return Result.NOT_CLAIMED;
		}
		if (!claimed) {
			return Result.NOT_CLAIMED;
		}

		// 2. Broadcast and event (event subscribers are also guarded in Event.trigger).
		guard("send the hunt captured broadcast", completion, notifier::captured);
		guard("trigger the hunt completed event", completion, notifier::completedEvent);

		// 3. PENDING, on disk before anything is paid. If it fails the player is still paid.
		boolean journalOk = false;
		try {
			journalOk = journal.pending(completion);
		} catch (Exception | LinkageError ex) {
			Hunt.LOGGER.error("Could not write the pending payout to the payout log.", ex);
		}
		if (!journalOk) {
			Hunt.LOGGER.error("The pending payout is not in the payout log, paying anyway. " + completion.describe());
		}

		// 4. Payment.
		PayResult pay = pay(completion);
		double paid = pay.outcome() == Outcome.PAID ? completion.price() : 0;
		if (pay.outcome() == Outcome.PAID) {
			guard("send the pay message", completion, notifier::paid);
		} else if (!pay.outcome().allowsCommands()) {
			guard("send the payment failed message", completion, notifier::paymentFailed);
		}

		// 5. Commands, only if the payment didn't fail.
		List<CommandResult> commandResults = runCommands(completion, pay.outcome());

		// 6. Stats.
		guard("record the hunt completion in the stats", completion,
				() -> stats.record(completion.bucket(), paid));

		// 7. DONE.
		guard("write the payout log", completion,
				() -> journal.done(completion, pay, paid, commandResults));

		return new Result(true, pay.outcome(), paid, commandResults, journalOk);
	}

	private PayResult pay(Completion completion) {
		if (completion.price() <= 0) {
			return new PayResult(Outcome.NO_PRICE, null, null);
		}
		String economyName = null;
		try {
			HuntEconomy found = economy.get();
			economyName = found.getClass().getSimpleName();
			if (found.add(completion.player(), completion.price())) {
				return new PayResult(Outcome.PAID, economyName, null);
			}
			Hunt.LOGGER.warn("The economy " + economyName + " refused to pay " + completion.price()
					+ " for a hunt. Reward commands were skipped. " + completion.describe());
			return new PayResult(Outcome.DECLINED, economyName, null);
		} catch (Exception | LinkageError ex) {
			Hunt.LOGGER.error("Could not pay " + completion.price() + " for a hunt. Reward commands were skipped. "
					+ completion.describe(), ex);
			return new PayResult(Outcome.ERROR, economyName, ex.toString());
		}
	}

	private List<CommandResult> runCommands(Completion completion, Outcome outcome) {
		if (!outcome.allowsCommands()) {
			List<CommandResult> skipped = new ArrayList<>();
			for (String command : completion.commands()) {
				if (command != null) {
					skipped.add(new CommandResult(command, command, CommandResult.Status.SKIPPED_PAYMENT_FAILED, 0));
				}
			}
			return skipped;
		}
		if (completion.commands().isEmpty()) {
			return List.of();
		}
		try {
			List<CommandResult> results = commands.run(completion.commands());
			return results == null ? List.of() : results;
		} catch (Exception | LinkageError ex) {
			Hunt.LOGGER.error("Could not run the hunt reward commands. " + completion.describe(), ex);
			return List.of();
		}
	}

	private static void guard(String what, Completion completion, Runnable step) {
		try {
			step.run();
		} catch (Exception | LinkageError ex) {
			Hunt.LOGGER.error("Could not " + what + ". " + completion.describe(), ex);
		}
	}
}
