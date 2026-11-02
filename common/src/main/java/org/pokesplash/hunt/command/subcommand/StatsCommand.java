package org.pokesplash.hunt.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.hunts.Bucket;
import org.pokesplash.hunt.stats.PlayerStats;
import org.pokesplash.hunt.stats.StatsLookup;
import org.pokesplash.hunt.util.Subcommand;
import org.pokesplash.hunt.util.Utils;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /hunt stats [player|uuid] - shows how many hunts a player completed and how much they earned.
 */
public class StatsCommand extends Subcommand {

	public StatsCommand() {
		super("§9Usage:\\n§3- hunt stats [player|uuid]");
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("stats")
				.requires(Hunt.permissions.requires("HuntStats"))
				.executes(this::run)
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (String player : ctx.getSource().getServer().getPlayerNames()) {
								builder.suggest(player);
							}
							return builder.buildFuture();
						})
						.executes(this::runForPlayer))
				.build();
	}

	/**
	 * Shows the sender's own stats.
	 */
	@Override
	public int run(CommandContext<CommandSourceStack> context) {
		if (!context.getSource().isPlayer()) {
			return showUsage(context);
		}
		ServerPlayer player = context.getSource().getPlayer();
		show(context, player.getUUID(), player.getName().getString());
		return 1;
	}

	private int runForPlayer(CommandContext<CommandSourceStack> context) {
		String query = StringArgumentType.getString(context, "player");

		switch (Hunt.stats.lookup(query, online(context.getSource()))) {
			case StatsLookup.Found found -> show(context, found.uuid(),
					found.name() != null ? found.name() : Hunt.stats.displayName(found.uuid(), null));
			case StatsLookup.Ambiguous ambiguous -> send(context, Hunt.language.getStatsAmbiguous()
					.replace("{player}", query)
					.replace("{matches}", describe(ambiguous)));
			case StatsLookup.NotFound notFound ->
					send(context, Hunt.language.getStatsNotFound().replace("{player}", query));
		}
		return 1;
	}

	/**
	 * @return the online players by name.
	 */
	static Map<String, UUID> online(CommandSourceStack source) {
		Map<String, UUID> players = new LinkedHashMap<>();
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			players.put(player.getName().getString(), player.getUUID());
		}
		return players;
	}

	/**
	 * @return "Steve (uuid), steve (uuid)", for a name that matches several players.
	 */
	static String describe(StatsLookup.Ambiguous ambiguous) {
		return ambiguous.matches().stream()
				.map(match -> (match.name() == null ? "?" : match.name()) + " (" + match.uuid() + ")")
				.collect(Collectors.joining(", "));
	}

	private void show(CommandContext<CommandSourceStack> context, UUID uuid, String name) {
		PlayerStats stats = Hunt.stats.get(uuid);
		if (stats == null) {
			send(context, Hunt.language.getStatsNotFound().replace("{player}", name));
			return;
		}

		send(context, format(Hunt.language.getStatsHeader(), name, stats));
		send(context, format(Hunt.language.getStatsCompletions(), name, stats));
		send(context, format(Hunt.language.getStatsEarned(), name, stats));
		send(context, format(Hunt.language.getStatsRarities(), name, stats));
	}

	/**
	 * Fills in the stats placeholders: {player}, {completions}, {earned} and the per-rarity
	 * {common}, {uncommon}, {rare}, {ultra_rare}.
	 */
	static String format(String message, String player, PlayerStats stats) {
		return message
				.replace("{player}", player)
				.replace("{completions}", String.valueOf(stats.getCompletions()))
				.replace("{earned}", new DecimalFormat("#,##0.##").format(stats.getEarned()))
				.replace("{common}", String.valueOf(stats.getCompletions(Bucket.COMMON)))
				.replace("{uncommon}", String.valueOf(stats.getCompletions(Bucket.UNCOMMON)))
				.replace("{rare}", String.valueOf(stats.getCompletions(Bucket.RARE)))
				.replace("{ultra_rare}", String.valueOf(stats.getCompletions(Bucket.ULTRA_RARE)));
	}

	static void send(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendSystemMessage(Component.literal(
				Utils.formatMessage(message, context.getSource().isPlayer())));
	}
}
