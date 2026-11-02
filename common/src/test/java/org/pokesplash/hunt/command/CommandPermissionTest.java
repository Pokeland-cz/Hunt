package org.pokesplash.hunt.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.Permission;
import com.cobblemon.mod.common.api.permission.PermissionValidator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.command.basecommand.HuntCommand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUNT-009 and HUNT-017: who can use which /hunt subcommand. The command tree is registered into its own
 * dispatcher and only parsed, never run. The fake validator works like fabric-permissions-api without
 * LuckPerms data for the source: a granted node passes, otherwise the source's OP level decides.
 * Nodes are the literal LuckPerms sees (hunt.reload, no "cobblemon." prefix).
 */
class CommandPermissionTest {

	private static final String ADMIN_SAVE = "hunt admin save";
	private static final String ADMIN_RESET = "hunt admin stats reset Steve";
	private static final String ADMIN_RESET_CONFIRM = "hunt admin stats reset Steve confirm";
	private static final String ADMIN_RESET_ALL = "hunt admin stats resetall";
	private static final String ADMIN_RESET_ALL_CONFIRM = "hunt admin stats resetall confirm";

	private final Set<String> granted = new HashSet<>();
	private final List<String> sourceChecks = new ArrayList<>();
	private CommandDispatcher<CommandSourceStack> dispatcher;

	@BeforeEach
	void setUp() {
		Cobblemon.INSTANCE.setPermissionValidator(new PermissionValidator() {
			@Override
			public void initialize() {
			}

			@Override
			public boolean hasPermission(ServerPlayer player, Permission permission) {
				throw new AssertionError("Permissions must be checked on the command source, not the player");
			}

			@Override
			public boolean hasPermission(CommandSourceStack source, Permission permission) {
				sourceChecks.add(permission.getLiteral());
				return granted.contains(permission.getLiteral())
						|| source.hasPermission(permission.getLevel().getNumericalValue());
			}
		});
		dispatcher = new CommandDispatcher<>();
		new HuntCommand().register(dispatcher);
	}

	/**
	 * A source without an entity, like the console (4), a command block (2) or "execute as" a mob run by a
	 * player without OP (0).
	 */
	private static CommandSourceStack source(int level) {
		return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "test",
				Component.literal("test"), null, null);
	}

	private boolean canUse(String command, int level) {
		ParseResults<CommandSourceStack> result = dispatcher.parse(command, source(level));
		return !result.getReader().canRead() && result.getExceptions().isEmpty();
	}

	// "execute as @e[type=cow] run hunt refresh" from a player without OP.
	@ParameterizedTest
	@ValueSource(strings = {"hunt refresh", "hunt reload", "hunts refresh", "hunts reload", "hunt refresh Steve"})
	void adminCommandsNeedOpWithoutAPlayer(String command) {
		assertFalse(canUse(command, 0));
		assertFalse(canUse(command, 1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"hunt refresh", "hunt reload", "hunts refresh", "hunts reload", "hunt refresh Steve"})
	void consoleAndCommandBlocksCanRunAdminCommands(String command) {
		assertTrue(canUse(command, 2));
		assertTrue(canUse(command, 4));
	}

	@ParameterizedTest
	@ValueSource(strings = {ADMIN_SAVE, ADMIN_RESET, ADMIN_RESET_CONFIRM, ADMIN_RESET_ALL, ADMIN_RESET_ALL_CONFIRM,
			"hunt admin", "hunt admin stats", "hunt admin stats reset", "hunts admin save"})
	void adminDataCommandsNeedOp(String command) {
		assertFalse(canUse(command, 0));
		assertFalse(canUse(command, 1));
		assertTrue(canUse(command, 2));
		assertTrue(canUse(command, 4));
	}

	@Test
	void adminSaveNodeAllowsOnlySave() {
		granted.add("hunt.admin.save");
		assertTrue(canUse(ADMIN_SAVE, 0));
		assertTrue(canUse("hunt admin", 0));
		assertFalse(canUse("hunt admin stats", 0));
		assertFalse(canUse(ADMIN_RESET_CONFIRM, 0));
		assertFalse(canUse(ADMIN_RESET_ALL_CONFIRM, 0));
	}

	@Test
	void adminResetNodeDoesNotAllowResetAll() {
		granted.add("hunt.admin.reset");
		assertTrue(canUse(ADMIN_RESET, 0));
		assertTrue(canUse(ADMIN_RESET_CONFIRM, 0));
		assertFalse(canUse(ADMIN_RESET_ALL_CONFIRM, 0));
		assertFalse(canUse(ADMIN_SAVE, 0));
	}

	@Test
	void adminResetAllNodeDoesNotAllowReset() {
		granted.add("hunt.admin.resetall");
		assertTrue(canUse(ADMIN_RESET_ALL, 0));
		assertTrue(canUse(ADMIN_RESET_ALL_CONFIRM, 0));
		assertFalse(canUse(ADMIN_RESET_CONFIRM, 0));
		assertFalse(canUse(ADMIN_SAVE, 0));
	}

	// HUNT-028: a UUID (with dashes) is accepted as the player argument.
	@ParameterizedTest
	@ValueSource(strings = {"hunt stats 069a79f4-44e9-4726-a5be-fca90e38aaf5",
			"hunt admin stats reset 069a79f4-44e9-4726-a5be-fca90e38aaf5 confirm"})
	void uuidIsAPlayerArgument(String command) {
		assertTrue(canUse(command, 4));
	}

	@ParameterizedTest
	@ValueSource(strings = {"hunt", "hunts", "hunt stats", "hunts stats", "hunt stats Steve", "hunt top",
			"hunts top earned"})
	void playerCommandsNeedNoOp(String command) {
		assertTrue(canUse(command, 0));
	}

	@Test
	void grantedNodeAllowsWithoutOp() {
		granted.add("hunt.refresh");
		assertTrue(canUse("hunt refresh", 0));
		assertTrue(canUse("hunts refresh", 0));
		assertFalse(canUse("hunt reload", 0));
	}

	// The alias used to hide everything behind hunt.base; now /hunt and /hunts behave the same.
	@Test
	void aliasDoesNotCheckHuntBase() {
		canUse("hunts stats", 0);
		assertFalse(sourceChecks.contains("hunt.base"));
		assertTrue(sourceChecks.contains("hunt.stats"));
	}

	@Test
	void eachSubcommandChecksItsOwnNode() {
		canUse("hunt reload", 0);
		canUse("hunt top", 0);
		assertTrue(sourceChecks.contains("hunt.reload"));
		assertTrue(sourceChecks.contains("hunt.top"));
	}

	@Test
	void unknownPermissionFailsAtRegistration() {
		assertThrows(IllegalArgumentException.class, () -> Hunt.permissions.requires("HuntTypo"));
	}

	// HUNT-033: the nodes LuckPerms sees are used as written. Cobblemon's CobblemonPermission would add "cobblemon.".
	@Test
	void permissionNodesHaveNoPrefix() {
		assertEquals("hunt.base", Hunt.permissions.getPermission("HuntBase").getLiteral());
		assertEquals("hunt.notify", Hunt.permissions.getPermission("HuntNotify").getLiteral());
		assertEquals("hunt.stats", Hunt.permissions.getPermission("HuntStats").getLiteral());
		assertEquals("hunt.top", Hunt.permissions.getPermission("HuntTop").getLiteral());
		assertEquals("hunt.reload", Hunt.permissions.getPermission("HuntReload").getLiteral());
		assertEquals("hunt.refresh", Hunt.permissions.getPermission("HuntRefresh").getLiteral());
		assertEquals("hunt.admin.save", Hunt.permissions.getPermission("HuntAdminSave").getLiteral());
		assertEquals("hunt.admin.reset", Hunt.permissions.getPermission("HuntAdminReset").getLiteral());
		assertEquals("hunt.admin.resetall", Hunt.permissions.getPermission("HuntAdminResetAll").getLiteral());
	}

	@Test
	void defaultLevels() {
		assertEquals(0, Hunt.permissions.getPermission("HuntBase").getLevel().getNumericalValue());
		assertEquals(0, Hunt.permissions.getPermission("HuntStats").getLevel().getNumericalValue());
		assertEquals(0, Hunt.permissions.getPermission("HuntTop").getLevel().getNumericalValue());
		assertEquals(2, Hunt.permissions.getPermission("HuntReload").getLevel().getNumericalValue());
		assertEquals(2, Hunt.permissions.getPermission("HuntRefresh").getLevel().getNumericalValue());
		assertEquals(2, Hunt.permissions.getPermission("HuntAdminSave").getLevel().getNumericalValue());
		assertEquals(2, Hunt.permissions.getPermission("HuntAdminReset").getLevel().getNumericalValue());
		assertEquals(2, Hunt.permissions.getPermission("HuntAdminResetAll").getLevel().getNumericalValue());
	}
}
