package org.pokesplash.hunt.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUNT-013: a reset only happens when the same admin confirms the same action in time.
 */
class PendingConfirmationsTest {
	private long now = 1_000_000;
	private final PendingConfirmations confirmations = new PendingConfirmations(() -> now, 30_000);

	@Test
	void confirmWithoutRequestFails() {
		assertFalse(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void confirmInTimeWorksOnce() {
		confirmations.request("admin", "reset:a");
		now += 30_000;
		assertTrue(confirmations.confirm("admin", "reset:a"));
		assertFalse(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void confirmAfterTimeoutFails() {
		confirmations.request("admin", "reset:a");
		now += 30_001;
		assertFalse(confirmations.confirm("admin", "reset:a"));
		now -= 30_001;
		assertFalse(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void otherSourceCannotConfirm() {
		confirmations.request("admin", "reset:a");
		assertFalse(confirmations.confirm("console", "reset:a"));
		assertTrue(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void otherActionIsNotConfirmed() {
		confirmations.request("admin", "reset:a");
		assertFalse(confirmations.confirm("admin", "reset:b"));
		assertFalse(confirmations.confirm("admin", "resetall"));
		// A wrong confirm doesn't use up the pending one.
		assertTrue(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void newRequestReplacesOld() {
		confirmations.request("admin", "reset:a");
		confirmations.request("admin", "resetall");
		assertFalse(confirmations.confirm("admin", "reset:a"));
		assertTrue(confirmations.confirm("admin", "resetall"));
	}

	@Test
	void newRequestRestartsTheTimer() {
		confirmations.request("admin", "reset:a");
		now += 20_000;
		confirmations.request("admin", "reset:a");
		now += 20_000;
		assertTrue(confirmations.confirm("admin", "reset:a"));
	}

	@Test
	void defaultTimeoutIsThirtySeconds() {
		assertEquals(30, new PendingConfirmations().timeoutSeconds());
	}
}
