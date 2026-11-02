package org.pokesplash.hunt.broadcast;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.util.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Class used to handle the broadcasts to the server.
 */
public class Broadcaster {
    // Collected "begun" and "ended" broadcasts. Kept on reload, so nothing queued is lost.
    private final BroadcastQueue queue = new BroadcastQueue();
    private HashMap<UUID, PlayerBroadcast> playerBuffers; // The buffers for individual hunts.
    private HashMap<BroadcastType, String> broadcastMessages; // The messages for the broadcast type.


    /**
     * Method used to initialise the broadcaster.
     */
    public void init() {
        playerBuffers = new HashMap<>();
        broadcastMessages = new HashMap<>();

        broadcastMessages.put(BroadcastType.STARTED, Hunt.language.getNewHuntMessage());
        broadcastMessages.put(BroadcastType.ENDED, Hunt.language.getEndedHuntMessage());
        broadcastMessages.put(BroadcastType.CAPTURED, Hunt.language.getCaptureHuntBroadcast());
    }

    /**
     * Private message used to send the broadcast.
     * @param type The type of broadcast.
     * @param player The player relevant.
     * @param pokemon The pokemon the broadcast is about.
     * @param price The price of the broadcast.
     */
    private void broadcastMessage(BroadcastType type, ServerPlayer player, Pokemon pokemon, double price) {

        String message = Utils.formatPlaceholders(
                broadcastMessages.get(type),
                player,
                pokemon,
                price
        );

        MinecraftServer server = Hunt.server;
        ArrayList<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());

        for (ServerPlayer pl : players) {
            pl.sendSystemMessage(Component.literal(message));
        }
    }

    /**
     * Internal method used to send a message to a player.
     * @param owner The player to send the message to.
     * @param type The type of message to send.
     * @param player The player relevant to the message.
     * @param pokemon The Pokemon involved.
     * @param price The price of the hunt.
     */
    private void sendMessage(UUID owner, BroadcastType type, ServerPlayer player, Pokemon pokemon, double price) {
        String message = Utils.formatPlaceholders(
                broadcastMessages.get(type),
                player,
                pokemon,
                price
        );

        Hunt.server.getPlayerList().getPlayer(owner).sendSystemMessage(
                Component.literal(message)
        );
    }

    /**
     * Method called to send a new message. Creates a timer.
     * @param type The broadcast type
     * @param player The player relevant
     * @param pokemon The pokemon relevant
     * @param price The price of the hunt
     */
    public void sendPlayerMessage(UUID owner, BroadcastType type, ServerPlayer player, Pokemon pokemon, double price) {

        // If the buffer config option is false, just send the message.
        if (!Hunt.config.isTimerCooldowns()) {
            sendMessage(owner, type, player, pokemon, price);
            return;
        }

        PlayerBroadcast playerBroadcast = playerBuffers.get(owner);


        if (playerBroadcast == null) {
            playerBroadcast = new PlayerBroadcast();
        }

        // If there hasn't been enough time between broadcasts, return.
        if (    playerBroadcast.buffers.get(type) == null ||
                new Date().getTime() - playerBroadcast.buffers.get(type) > (1000L * Hunt.config.getBufferDuration())) {
            sendMessage(owner, type, player, pokemon, price);
            playerBroadcast.buffers.put(type, new Date().getTime());

            // Add the timer to the hashmap.
            playerBuffers.put(owner, playerBroadcast);
        }
    }

    /**
     * Sends a broadcast to all players. "Begun" and "ended" broadcasts are collected for bufferDuration
     * seconds (if timerCooldowns is on) and sent together by {@link #flushDue()}, none are dropped.
     * Captured broadcasts are sent right away: they name the player, so they can't be joined.
     * @param type The broadcast type
     * @param player The player relevant
     * @param pokemon The pokemon relevant
     * @param price The price of the hunt
     */
    public void sendBroadcast(BroadcastType type, ServerPlayer player, Pokemon pokemon, double price) {
        if (!Hunt.config.isTimerCooldowns() || type == BroadcastType.CAPTURED) {
            broadcastMessage(type, player, pokemon, price);
            return;
        }
        queue.add(type, Utils.pokemonText(pokemon), price, System.currentTimeMillis());
    }

    /**
     * Sends the collected broadcasts whose buffer window is over. Called every second from Hunt.check().
     */
    public void flushDue() {
        if (queue.isEmpty() || Hunt.server == null) {
            return;
        }
        List<String> lines = queue.due(System.currentTimeMillis(), 1000L * Hunt.config.getBufferDuration(),
                broadcastMessages::get);
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            for (ServerPlayer pl : new ArrayList<>(Hunt.server.getPlayerList().getPlayers())) {
                pl.sendSystemMessage(Component.literal(line));
            }
        }
    }
}
