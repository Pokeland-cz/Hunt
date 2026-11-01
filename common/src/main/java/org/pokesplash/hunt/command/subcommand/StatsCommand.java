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
import org.pokesplash.hunt.util.Subcommand;
import org.pokesplash.hunt.util.Utils;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /hunt stats [player] - shows how many hunts a player completed and how much they earned.
 */
public class StatsCommand extends Subcommand {

	public StatsCommand() {
		super("§9Usage:\\n§3- hunt stats [player]");
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("stats")
				.requires(ctx -> !ctx.isPlayer() ||
						Hunt.permissions.hasPermission(ctx.getPlayer(), Hunt.permissions.getPermission("HuntStats")))
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
		String name = StringArgumentType.getString(context, "player");

		ServerPlayer online = Hunt.server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			show(context, online.getUUID(), online.getName().getString());
			return 1;
		}

		Optional<Map.Entry<UUID, PlayerStats>> found = Hunt.stats.findByName(name);
		if (found.isPresent()) {
			show(context, found.get().getKey(), found.get().getValue().getName());
		} else {
			send(context, Hunt.language.getStatsNotFound().replace("{player}", name));
		}
		return 1;
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
