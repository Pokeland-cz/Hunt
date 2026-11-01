package org.pokesplash.hunt.util;

import org.pokesplash.hunt.Hunt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
	 * Reads a data file.
	 * @param filename the file in config/hunt.
	 * @return the contents, or null if the file doesn't exist or can't be read.
	 */
	public static String read(String filename) {
		Path path = path(filename);
		if (!Files.exists(path)) {
			return null;
		}
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not read " + path, e);
			return null;
		}
	}

	/**
	 * Queues a write on the background thread.
	 */
	public static void writeAsync(String filename, String data) {
		WRITER.execute(() -> write(filename, data));
	}

	/**
	 * Writes a file and waits for it, and for any queued writes, to finish. Used on server shutdown.
	 */
	public static void writeSync(String filename, String data) {
		try {
			WRITER.submit(() -> write(filename, data)).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			Hunt.LOGGER.error("Could not write " + path(filename), e.getCause());
		}
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

	private static void write(String filename, String data) {
		Path path = path(filename);
		Path temp = path.resolveSibling(filename + ".tmp");
		try {
			Files.createDirectories(DIRECTORY);
			Files.writeString(temp, data, StandardCharsets.UTF_8);
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Hunt.LOGGER.error("Could not write " + path, e);
		}
	}
}
