package org.pokesplash.hunt.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.command.PendingConfirmations;
import org.pokesplash.hunt.stats.HuntStats;
import org.pokesplash.hunt.stats.PlayerStats;
import org.pokesplash.hunt.stats.StatsLookup;
import org.pokesplash.hunt.util.FileStore;
import org.pokesplash.hunt.util.Subcommand;
import org.pokesplash.hunt.util.Utils;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin tools for the saved data (HUNT-013):
 * <ul>
 *     <li>/hunt admin save - writes hunts.json and stats.json now.</li>
 *     <li>/hunt admin stats reset &lt;player|uuid&gt; [confirm] - deletes one player's stats.</li>
 *     <li>/hunt admin stats resetall [confirm] - deletes everyone's stats, after saving a copy.</li>
 * </ul>
 * Resets only happen when run again with "confirm" within 30 seconds, by the same admin.
 */
public class AdminCommand extends Subcommand {
	private static final String CONFIRM = "confirm";
	private static final String RESET_ALL = "resetall";
	private static final DateTimeFormatter SNAPSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final PendingConfirmations confirmations = new PendingConfirmations();

	public AdminCommand() {
		super("§9Usage:\\n§3- hunt admin save\\n§3- hunt admin stats reset <player|uuid> [confirm]"
				+ "\\n§3- hunt admin stats resetall [confirm]");
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		SuggestionProvider<CommandSourceStack> players = (ctx, builder) -> {
			Set<String> names = new LinkedHashSet<>(StatsCommand.online(ctx.getSource()).keySet());
			names.addAll(Hunt.stats.knownNames());
			return SharedSuggestionProvider.suggest(names, builder);
		};

		return Commands.literal("admin")
				.requires(Hunt.permissions.requiresAny("HuntAdminSave", "HuntAdminReset", "HuntAdminResetAll"))
				.executes(this::run)
				.then(Commands.literal("save")
						.requires(Hunt.permissions.requires("HuntAdminSave"))
						.executes(this::save))
				.then(Commands.literal("stats")
						.requires(Hunt.permissions.requiresAny("HuntAdminReset", "HuntAdminResetAll"))
						.executes(this::run)
						.then(Commands.literal("reset")
								.requires(Hunt.permissions.requires("HuntAdminReset"))
								.executes(this::run)
								.then(Commands.argument("player", StringArgumentType.word())
										.suggests(players)
										.executes(ctx -> reset(ctx, false))
										.then(Commands.literal(CONFIRM).executes(ctx -> reset(ctx, true)))))
						.then(Commands.literal(RESET_ALL)
								.requires(Hunt.permissions.requires("HuntAdminResetAll"))
								.executes(ctx -> resetAll(ctx, false))
								.then(Commands.literal(CONFIRM).executes(ctx -> resetAll(ctx, true)))))
				.build();
	}

	@Override
	public int run(CommandContext<CommandSourceStack> context) {
		return showUsage(context);
	}

	private int save(CommandContext<CommandSourceStack> context) {
		boolean ok = true;
		if (Hunt.isActive()) {
			boolean hunts = Hunt.storage.saveNow();
			ok = hunts;
			send(context, hunts ? "§a[Hunt] hunts.json saved." : "§c[Hunt] hunts.json was NOT saved, see the server log.");
		} else {
			send(context, "§e[Hunt] Hunt is paused, so hunts.json was not saved (no hunts are loaded).");
		}
		boolean stats = Hunt.stats.saveNow();
		ok &= stats;
		send(context, stats ? "§a[Hunt] stats.json saved." : "§c[Hunt] stats.json was NOT saved, see the server log.");
		Hunt.LOGGER.info(context.getSource().getTextName() + " saved the Hunt data" + (ok ? "." : ", with errors."));
		return ok ? 1 : 0;
	}

	private int reset(CommandContext<CommandSourceStack> context, boolean confirm) {
		if (!checkWritable(context)) {
			return 0;
		}
		String query = StringArgumentType.getString(context, "player");

		UUID uuid;
		switch (Hunt.stats.lookup(query, StatsCommand.online(context.getSource()))) {
			case StatsLookup.Found found -> uuid = found.uuid();
			case StatsLookup.Ambiguous ambiguous -> {
				send(context, "§c[Hunt] " + query + " matches several players: " + StatsCommand.describe(ambiguous)
						+ ". Use the UUID.");
				return 0;
			}
			case StatsLookup.NotFound notFound -> {
				send(context, "§c[Hunt] No player " + query + " found.");
				return 0;
			}
		}

		PlayerStats stats = Hunt.stats.get(uuid);
		String name = Hunt.stats.displayName(uuid, stats);
		if (stats == null) {
			send(context, "§c[Hunt] " + name + " (" + uuid + ") has no hunt stats.");
			return 0;
		}

		String source = sourceKey(context.getSource());
		String action = "reset:" + uuid;
		if (!confirm) {
			confirmations.request(source, action);
			send(context, "§e[Hunt] This deletes the hunt stats of §6" + name + " §e(" + uuid + "): "
					+ stats.getCompletions() + " hunts, " + formatMoney(stats.getEarned()) + " earned.");
			send(context, "§e[Hunt] To do it, run §6/hunt admin stats reset " + uuid + " confirm §ewithin "
					+ confirmations.timeoutSeconds() + " seconds.");
			return 1;
		}
		if (!confirmations.confirm(source, action)) {
			send(context, "§c[Hunt] Nothing to confirm: run /hunt admin stats reset " + query
					+ " without 'confirm' first (it expires after " + confirmations.timeoutSeconds() + " seconds).");
			return 0;
		}

		PlayerStats removed = Hunt.stats.remove(uuid);
		if (removed == null) {
			send(context, "§c[Hunt] " + name + " has no hunt stats any more.");
			return 0;
		}
		Hunt.LOGGER.warn(context.getSource().getTextName() + " reset the hunt stats of " + name + " (" + uuid
				+ "). Deleted stats: " + HuntStats.toJson(removed));
		boolean saved = Hunt.stats.saveNow();
		send(context, "§a[Hunt] Hunt stats of " + name + " (" + uuid + ") were reset.");
		if (!saved) {
			send(context, "§c[Hunt] stats.json could not be saved, see the server log. Run /hunt admin save once that is fixed.");
		}
		return 1;
	}

	private int resetAll(CommandContext<CommandSourceStack> context, boolean confirm) {
		if (!checkWritable(context)) {
			return 0;
		}
		if (Hunt.stats.size() == 0) {
			send(context, "§e[Hunt] There are no hunt stats to reset.");
			return 0;
		}

		String source = sourceKey(context.getSource());
		if (!confirm) {
			confirmations.request(source, RESET_ALL);
			send(context, "§e[Hunt] This deletes the hunt stats of ALL " + Hunt.stats.size() + " players ("
					+ Hunt.stats.totalCompletions() + " hunts). A copy is saved to config/hunt first.");
			send(context, "§e[Hunt] To do it, run §6/hunt admin stats resetall confirm §ewithin "
					+ confirmations.timeoutSeconds() + " seconds.");
			return 1;
		}
		if (!confirmations.confirm(source, RESET_ALL)) {
			send(context, "§c[Hunt] Nothing to confirm: run /hunt admin stats resetall without 'confirm' first "
					+ "(it expires after " + confirmations.timeoutSeconds() + " seconds).");
			return 0;
		}

		String snapshot = "stats-reset-" + LocalDateTime.now().format(SNAPSHOT_TIME) + ".json";
		if (!FileStore.writeSync(snapshot, Hunt.stats.snapshot())) {
			send(context, "§c[Hunt] The copy " + snapshot + " could not be written, so nothing was reset. See the server log.");
			return 0;
		}
		int count = Hunt.stats.clear();
		Hunt.LOGGER.warn(context.getSource().getTextName() + " reset the hunt stats of all " + count
				+ " players. The previous stats were saved to " + FileStore.path(snapshot) + ".");
		boolean saved = Hunt.stats.saveNow();
		send(context, "§a[Hunt] Hunt stats of " + count + " players were reset. Copy: config/hunt/" + snapshot);
		if (!saved) {
			send(context, "§c[Hunt] stats.json could not be saved, see the server log. Run /hunt admin save once that is fixed.");
		}
		return 1;
	}

	/**
	 * A reset that can't be saved would come back after a restart, so it isn't done at all.
	 */
	private static boolean checkWritable(CommandContext<CommandSourceStack> context) {
		if (Hunt.stats.isWritable()) {
			return true;
		}
		send(context, "§c[Hunt] stats.json could not be read at startup, so stats can't be saved this run. "
				+ "Fix the file and restart the server.");
		return false;
	}

	/**
	 * Who must confirm: the same player, or the same non-player source (console, RCON).
	 */
	private static String sourceKey(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null ? "player:" + player.getUUID() : "source:" + source.getTextName();
	}

	private static String formatMoney(double amount) {
		return new DecimalFormat("#,##0.##").format(amount);
	}

	private static void send(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendSystemMessage(Component.literal(
				Utils.formatMessage(message, context.getSource().isPlayer())));
	}
}
