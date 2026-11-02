package org.pokesplash.hunt.util;

import com.cobblemon.mod.common.api.permission.Permission;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public abstract class BaseCommand {

	// The command name.
	private String commandString;

	// A list of aliases for the command.
	private ArrayList<String> aliases;

	// The permission for the command.
	private Permission permission;

	// A list of subcommands for the base command.
	private ArrayList<Subcommand> subcommands;

	/**
	 * Constructor for super method.
	 * @param commandString The string of the base command.
	 * @param aliases A list of the aliases that should be used for this command.
	 * @param subcommands A list of the subcommands of the base command.
	 */
	public BaseCommand(String commandString, List<String> aliases, Permission permission,
	                   List<Subcommand> subcommands) {
		this.commandString = commandString;

		this.aliases = new ArrayList<>();
		this.aliases.addAll(aliases);

		this.permission = permission;

		this.subcommands = new ArrayList<>();
		this.subcommands.addAll(subcommands);

	}

	/**
	 * Method to register and build the command.
	 */
	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		createCommand(dispatcher);
	}

	private void createCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = Commands
				.literal(commandString)
				.executes(this::run)
				.build();

		dispatcher.getRoot().addChild(root);

		// Aliases behave exactly like the base command: run() and each subcommand check their own permission.
		for (String alias : aliases) {
			dispatcher.register(Commands.literal(alias)
					.redirect(root)
					.executes(this::run));
		}

		// Adds subcommands to the base command.
		for (Subcommand subcommand : subcommands) {
			root.addChild(subcommand.build());
		}
	}

	/**
	 * @return the permission needed to run the base command itself (not its subcommands).
	 */
	public Permission getPermission() {
		return permission;
	}

	/**
	 * Override this method to run the command.
	 * @param context the context the command is run in.
	 * @return 1 is successful or -1 is failed.
	 */
	public abstract int run(CommandContext<CommandSourceStack> context);
}
