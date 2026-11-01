package org.pokesplash.hunt.economy;

import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.api.economy.HuntEconomy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Class to interact with the Impactor API
 */
public class ImpactorService implements HuntEconomy {

	// The impactor service
	private final EconomyService service = EconomyService.instance();

	/**
	 * Gets the currency hunts pay out in: Impactor's primary currency, or the configured one.
	 * Falls back to the primary currency if the configured one doesn't exist.
	 */
	private Currency getCurrency() {
		if (Hunt.config.isUseImpactorDefaultCurrency()) {
			return service.currencies().primary();
		}

		Optional<Currency> currency;
		try {
			currency = service.currencies().currency(Key.key(Hunt.config.getCurrencyName()));
		} catch (InvalidKeyException e) {
			currency = Optional.empty();
		}

		if (currency.isEmpty()) {
			Hunt.LOGGER.error("Could not find currency: {} from Impactor. Using default currency.",
					Hunt.config.getCurrencyName());
			return service.currencies().primary();
		}
		return currency.get();
	}

	/**
	 * Method to add to the balance of an account.
	 * @param uuid The UUID of the player to pay.
	 * @param amount The amount to add.
	 * @return true if the transaction was successful.
	 */
	@Override
	public boolean add(UUID uuid, double amount) {
		// account(currency, uuid) fetches the account, or creates it if the player doesn't have one yet.
		Account account = service.account(getCurrency(), uuid).join();

		// valueOf avoids binary rounding noise, e.g. new BigDecimal(0.1) = 0.1000000000000000055...
		EconomyTransaction transaction = account.deposit(BigDecimal.valueOf(amount));

		return transaction.successful();
	}
}
