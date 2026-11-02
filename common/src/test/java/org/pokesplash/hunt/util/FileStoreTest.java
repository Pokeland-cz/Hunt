package org.pokesplash.hunt.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileStore works in config/hunt under the test working directory (build/test-run, emptied before each run).
 * Backups rotate once per file per run, so every test uses its own file name.
 */
class FileStoreTest {

	private static Path backup(String filename, int index) {
		return FileStore.path(filename).resolveSibling(filename + ".bak." + index);
	}

	@Test
	void missingFile() {
		assertInstanceOf(FileStore.Missing.class, FileStore.read("missing.json"));
	}

	@Test
	void writtenFileReadsBack() {
		String content = "{\"text\": \"§5[Hunt] Pokémon\"}";
		assertTrue(FileStore.writeSync("roundtrip.json", content));
		FileStore.Ok ok = assertInstanceOf(FileStore.Ok.class, FileStore.read("roundtrip.json"));
		assertEquals(content, ok.content());
	}

	// HUNT-012: a file that exists but can't be read must not look like a missing one.
	@Test
	void unreadableFileIsFailedNotMissing() throws IOException {
		Files.createDirectories(FileStore.path("directory.json"));
		assertInstanceOf(FileStore.Failed.class, FileStore.read("directory.json"));
	}

	@Test
	void invalidUtf8IsFailed() throws IOException {
		Files.createDirectories(FileStore.path("x").getParent());
		Files.write(FileStore.path("latin1.json"), new byte[]{'{', (byte) 0xE9, '}'});
		assertInstanceOf(FileStore.Failed.class, FileStore.read("latin1.json"));
	}

	@Test
	void writeLeavesNoTempFile() {
		assertTrue(FileStore.writeSync("temp.json", "{}"));
		assertFalse(Files.exists(FileStore.path("temp.json.tmp")));
	}

	@Test
	void newFileGetsNoBackup() {
		assertTrue(FileStore.writeSync("fresh.json", "{}"));
		assertFalse(Files.exists(backup("fresh.json", 1)));
	}

	@Test
	void firstOverwriteKeepsBackupOfOriginal() throws IOException {
		Files.createDirectories(FileStore.path("x").getParent());
		Files.writeString(FileStore.path("rotate.json"), "original", StandardCharsets.UTF_8);

		assertTrue(FileStore.writeSync("rotate.json", "second"));
		assertEquals("original", Files.readString(backup("rotate.json", 1)));

		// Later writes in the same run keep .bak.1 as the file was at startup.
		assertTrue(FileStore.writeSync("rotate.json", "third"));
		assertEquals("original", Files.readString(backup("rotate.json", 1)));
		assertFalse(Files.exists(backup("rotate.json", 2)));
		assertEquals("third", Files.readString(FileStore.path("rotate.json")));
	}

	@Test
	void olderBackupsShiftUp() throws IOException {
		Files.createDirectories(FileStore.path("x").getParent());
		Files.writeString(FileStore.path("shift.json"), "current");
		Files.writeString(backup("shift.json", 1), "one");
		Files.writeString(backup("shift.json", 2), "two");
		Files.writeString(backup("shift.json", 3), "three");

		assertTrue(FileStore.writeSync("shift.json", "new"));
		assertEquals("current", Files.readString(backup("shift.json", 1)));
		assertEquals("one", Files.readString(backup("shift.json", 2)));
		assertEquals("two", Files.readString(backup("shift.json", 3)));
		assertFalse(Files.exists(backup("shift.json", 4)));
	}

	@Test
	void appendedLinesKeepOrderAndWriteSyncWaitsForThem() throws IOException {
		for (int i = 0; i < 50; i++) {
			FileStore.appendAsync("append.log", "line " + i);
		}
		assertTrue(FileStore.writeSync("append-barrier.json", "{}"));
		List<String> lines = Files.readAllLines(FileStore.path("append.log"));
		assertEquals(50, lines.size());
		for (int i = 0; i < 50; i++) {
			assertEquals("line " + i, lines.get(i));
		}
		assertFalse(Files.exists(backup("append.log", 1)));
	}
}
