package org.pokesplash.hunt.economy;

import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.api.economy.HuntEconomy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Blanket Economy bridge. Blanket is an optional dependency, so it's accessed reflectively and
 * isn't needed to compile Hunt. Equivalent to:
 * {@code BlanketEconomy.INSTANCE.getAPI().addBalance(player, amount, currency)}
 */
public class BlanketService implements HuntEconomy {

    private final Object service;
    private final Method addBalance;

    public BlanketService() throws ReflectiveOperationException {
        Class<?> blanket = Class.forName("org.blanketeconomy.api.BlanketEconomy");
        Class<?> api = Class.forName("org.blanketeconomy.api.EconomyAPI");

        Object instance = blanket.getField("INSTANCE").get(null);
        service = blanket.getMethod("getAPI").invoke(instance);
        // Looked up on the public API type, so it works whatever the implementation class is.
        addBalance = api.getMethod("addBalance", UUID.class, BigDecimal.class, String.class);
    }

    @Override
    public boolean add(UUID player, double amount) {
        try {
            addBalance.invoke(service, player, new BigDecimal(amount), Hunt.config.getCurrencyName());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Blanket Economy addBalance failed", e);
        }
        return true;
    }
}
