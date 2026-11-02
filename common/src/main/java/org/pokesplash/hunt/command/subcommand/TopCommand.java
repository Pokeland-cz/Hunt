package org.pokesplash.hunt.command.subcommand;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.stats.HuntStats;
import org.pokesplash.hunt.stats.PlayerStats;
import org.pokesplash.hunt.util.Subcommand;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /hunt top [completions|earned] - shows the top 10 hunters.
 */
public class TopCommand extends Subcommand {
	private static final int ENTRIES = 10;

	public TopCommand() {
		super("§9Usage:\\n§3- hunt top [completions|earned]");
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("top")
				.requires(Hunt.permissions.requires("HuntTop"))
				.executes(this::run)
				.then(Commands.literal("completions").executes(ctx -> show(ctx, HuntStats.Sort.COMPLETIONS)))
				.then(Commands.literal("earned").executes(ctx -> show(ctx, HuntStats.Sort.EARNED)))
				.build();
	}

	@Override
	public int run(CommandContext<CommandSourceStack> context) {
		return show(context, HuntStats.Sort.COMPLETIONS);
	}

	private int show(CommandContext<CommandSourceStack> context, HuntStats.Sort sort) {
		List<Map.Entry<UUID, PlayerStats>> top = Hunt.stats.top(sort, ENTRIES);
		if (top.isEmpty()) {
			StatsCommand.send(context, Hunt.language.getTopEmpty());
			return 1;
		}

		StatsCommand.send(context, sort == HuntStats.Sort.EARNED
				? Hunt.language.getTopHeaderEarned()
				: Hunt.language.getTopHeaderCompletions());

		int rank = 1;
		for (Map.Entry<UUID, PlayerStats> entry : top) {
			String name = Hunt.stats.displayName(entry.getKey(), entry.getValue());
			StatsCommand.send(context, StatsCommand.format(Hunt.language.getTopEntry(), name, entry.getValue())
					.replace("{rank}", String.valueOf(rank++)));
		}
		return 1;
	}
}
