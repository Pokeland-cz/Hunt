package org.pokesplash.hunt.command.subcommand;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.util.Subcommand;
import org.pokesplash.hunt.util.Utils;


public class ReloadCommand extends Subcommand {

	public ReloadCommand() {
		super("§9Usage:\\n§3- hunt reload");
	}

	/**
	 * Method used to add to the base command for this subcommand.
	 * @return source to complete the command.
	 */
	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("reload")
				.requires(Hunt.permissions.requires("HuntReload"))
				.executes(this::run)
				.build();
	}

	/**
	 * Method to perform the logic when the command is executed.
	 * @param context the source of the command.
	 * @return integer to complete command.
	 */
	@Override
	public int run(CommandContext<CommandSourceStack> context) {

		// Keeps the current hunts; they're trimmed or topped up if the hunt amount changed.
		// Files that can't be loaded are left untouched and the previous version stays in use.
		Hunt.LoadReport report = Hunt.reload();

		if (report.errors().isEmpty()) {
			send(context, Hunt.language.getReloadMessage());
		} else {
			for (String error : report.errors()) {
				send(context, "§c[Hunt] " + error);
			}
		}
		if (report.started()) {
			send(context, "§a[Hunt] Config loaded, Hunt started.");
		} else if (!Hunt.isActive()) {
			send(context, "§c[Hunt] Hunt is still paused: fix config.json, then run /hunt reload again.");
		}

		return report.errors().isEmpty() ? 1 : 0;
	}

	private static void send(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendSystemMessage(Component.literal(Utils.formatMessage(
				message, context.getSource().isPlayer())));
	}
}
