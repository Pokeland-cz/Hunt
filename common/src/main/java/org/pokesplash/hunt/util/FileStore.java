package org.pokesplash.hunt.util;

import org.pokesplash.hunt.Hunt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads and writes Hunt's data files in config/hunt. Writes go through a single background thread,
 * so they never block the server and never overlap, and each write replaces the file atomically,
 * so a crash mid-write can't leave a half-written file behind.
 */
public final class FileStore {
	private static final Path DIRECTORY = Paths.get("config", "hunt");
	// How many rotating backups (file.bak.1 = newest) are kept of each file before it is overwritten.
	private static final int BACKUPS = 3;
	// Files already backed up in this run. Only touched on the writer thread.
	private static final Set<String> ROTATED = new HashSet<>();

	/**
	 * The result of reading a file: it doesn't exist, it was read, or it exists but couldn't be read.
	 * A file that couldn't be read must never be overwritten, or its contents would be lost.
	 */
	public sealed interface ReadResult permits Missing, Ok, Failed {
	}

	public record Missing() implements ReadResult {
	}

	public record Ok(String content) implements ReadResult {
	}

	public record Failed(IOException error) implements ReadResult {
	}

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Hunt-Save");
		thread.setDaemon(true);
		return thread;
	});

	private FileStore() {
	}

	public static Path path(String filename) {
		return DIRECTORY.resolve(filename);
	}

	/**
	 * Reads a file. "Doesn't exist" and "couldn't be read" (permissions, I/O error, invalid UTF-8)
	 * are reported separately, so a file that couldn't be read is never mistaken for a missing one.
	 * @param filename the file in config/hunt.
	 */
	public static ReadResult read(String filename) {
		Path path = path(filename);
		try {
			return new Ok(Files.readString(path, StandardCharsets.UTF_8));
		} catch (NoSuchFileException e) {
			return new Missing();
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not read " + path + ": " + e, e);
			return new Failed(e);
		}
	}

	/**
	 * Queues a write on the background thread.
	 */
	public static void writeAsync(String filename, String data) {
		WRITER.execute(() -> write(filename, data));
	}

	/**
	 * Queues a line to be appended to a file on the background thread. Lines keep their order, and
	 * {@link #writeSync} on server shutdown waits for them to be written.
	 */
	public static void appendAsync(String filename, String line) {
		WRITER.execute(() -> append(filename, line));
	}

	/**
	 * Writes a file and waits for it, and for any queued writes, to finish.
	 * Used on server shutdown and for config files.
	 * @return true if the file was written.
	 */
	public static boolean writeSync(String filename, String data) {
		try {
			return WRITER.submit(() -> write(filename, data)).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			Hunt.LOGGER.error("Could not write " + path(filename), e.getCause());
		}
		return false;
	}

	/**
	 * Keeps a copy of a file that couldn't be parsed, so it isn't lost when it's next saved.
	 */
	public static void backupBroken(String filename) {
		Path path = path(filename);
		Path backup = path.resolveSibling(filename + ".broken");
		try {
			Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
			Hunt.LOGGER.error("A copy of the unreadable file was saved to " + backup);
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not back up " + path, e);
		}
	}

	private static void append(String filename, String line) {
		Path path = path(filename);
		try {
			Files.createDirectories(DIRECTORY);
			Files.writeString(path, line + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not append to " + path + ": " + line, e);
		}
	}

	private static boolean write(String filename, String data) {
		Path path = path(filename);
		Path temp = path.resolveSibling(filename + ".tmp");
		try {
			Files.createDirectories(DIRECTORY);
			Files.writeString(temp, data, StandardCharsets.UTF_8);
			rotateBackups(filename);
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not write " + path, e);
			return false;
		}
	}

	/**
	 * Before a file is overwritten for the first time in this run, keeps a copy of it as file.bak.1
	 * and shifts older copies up to file.bak.3. So file.bak.1 is the file as it was at the last start.
	 * A failed backup is logged, but doesn't stop the write.
	 */
	private static void rotateBackups(String filename) {
		Path path = path(filename);
		if (ROTATED.contains(filename) || !Files.exists(path)) {
			return;
		}
		try {
			Files.deleteIfExists(backup(filename, BACKUPS));
			for (int i = BACKUPS - 1; i >= 1; i--) {
				Path from = backup(filename, i);
				if (Files.exists(from)) {
					Files.move(from, backup(filename, i + 1), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			Files.copy(path, backup(filename, 1), StandardCopyOption.REPLACE_EXISTING);
			ROTATED.add(filename);
		} catch (IOException e) {
			Hunt.LOGGER.warn("Could not back up " + path + " before overwriting it: " + e);
		}
	}

	private static Path backup(String filename, int index) {
		return path(filename).resolveSibling(filename + ".bak." + index);
	}
}
