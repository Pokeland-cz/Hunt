package org.pokesplash.hunt.economy;

import org.pokesplash.hunt.api.economy.HuntEconomy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Pebbles Economy bridge. Pebbles is an optional dependency, so it's accessed reflectively and
 * isn't needed to compile Hunt. Equivalent to:
 * {@code PebblesEconomyInitializer.INSTANCE.getEconomy().deposit(player, amount)}
 */
public class PebblesService implements HuntEconomy {

    private final Object service;
    private final MethodHandle deposit;

    public PebblesService() throws ReflectiveOperationException {
        Class<?> initializer = Class.forName("tech.sethi.pebbleseconomy.PebblesEconomyInitializer");
        Class<?> economy = Class.forName("tech.sethi.pebbleseconomy.Economy");

        Object instance = initializer.getField("INSTANCE").get(null);
        service = initializer.getMethod("getEconomy").invoke(instance);
        deposit = MethodHandles.publicLookup().findVirtual(economy, "deposit",
                MethodType.methodType(void.class, UUID.class, double.class));
    }

    @Override
    public boolean add(UUID player, double amount) {
        try {
            deposit.invoke(service, player, amount);
        } catch (Throwable e) {
            throw new RuntimeException("Pebbles Economy deposit failed", e);
        }
        return true;
    }
}
