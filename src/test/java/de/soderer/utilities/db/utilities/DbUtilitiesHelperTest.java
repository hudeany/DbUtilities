package de.soderer.utilities.db.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

/**
 * Tests of the static helper methods in {@link Utilities}.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesHelperTest {
	@Test
	public void testEmptyAndBlank() {
		assertNotNull(new Utilities());
		assertTrue(Utilities.isEmpty((String) null));
		assertTrue(Utilities.isEmpty(""));
		assertFalse(Utilities.isEmpty(" "));
		assertTrue(Utilities.isNotEmpty(" "));
		assertTrue(Utilities.isEmpty((List<String>) null));
		assertTrue(Utilities.isEmpty(List.of()));
		assertTrue(Utilities.isNotEmpty(List.of("a")));
		assertTrue(Utilities.isBlank((String) null));
		assertTrue(Utilities.isBlank(" \t"));
		assertTrue(Utilities.isNotBlank(" a "));

		assertTrue(Utilities.isEmpty((char[]) null));
		assertTrue(Utilities.isEmpty(new char[0]));
		assertTrue(Utilities.isNotEmpty(new char[] { ' ' }));
		assertTrue(Utilities.isBlank((char[]) null));
		assertTrue(Utilities.isBlank(new char[0]));
		assertTrue(Utilities.isBlank(new char[] { ' ', '\n' }));
		assertTrue(Utilities.isNotBlank(new char[] { ' ', 'x' }));
	}

	@Test
	public void testRepeatAndJoin() {
		assertEquals("xxx", Utilities.repeat('x', 3));
		assertEquals("abab", Utilities.repeat("ab", 2));
		assertEquals("a, a, a", Utilities.repeat("a", 3, ", "));
		assertNull(Utilities.repeat(null, 3));
		assertEquals("", Utilities.repeat("", 3));
		assertEquals("", Utilities.repeat("a", 0));

		assertEquals("a-b", Utilities.join(new char[] { 'a', 'b' }, "-"));
		assertEquals("ab", Utilities.join(new char[] { 'a', 'b' }, null));
		assertEquals("", Utilities.join(new char[0], "-"));
		assertNull(Utilities.join((char[]) null, "-"));

		assertEquals("a,,1", Utilities.join(new Object[] { "a", null, 1 }, ","));
		assertEquals("ab", Utilities.join(new Object[] { "a", "b" }, null));
		assertEquals("", Utilities.join(new Object[0], ","));
		assertNull(Utilities.join((Object[]) null, ","));

		assertEquals("a;;b", Utilities.join(Arrays.asList("a", null, "b"), ";"));
		assertEquals("ab", Utilities.join(List.of("a", "b"), null));
		assertNull(Utilities.join((Iterable<?>) null, ";"));
	}

	@Test
	public void testReplaceUsersHome() {
		final String home = System.getProperty("user.home");
		assertNull(Utilities.replaceUsersHome(null));
		assertEquals(home, Utilities.replaceUsersHome("~"));
		assertEquals(home + "/data", Utilities.replaceUsersHome("~/data"));
		assertEquals(home + File.separator + "data", Utilities.replaceUsersHome("~" + File.separator + "data"));
		assertEquals(home + "/x", Utilities.replaceUsersHome("${HOME}/x"));
		assertEquals(home + "/x", Utilities.replaceUsersHome("$HOME/x"));
		// "~" inside a path is a normal character
		assertEquals("/tmp/a~b", Utilities.replaceUsersHome("/tmp/a~b"));
	}

	@Test
	public void testTrimAndNumbers() {
		assertTrue(Utilities.isInteger("-12"));
		assertFalse(Utilities.isInteger("1.5"));
		assertFalse(Utilities.isInteger(null));
		assertNull(Utilities.trim(null));
		assertEquals("a", Utilities.trim(" a "));
		assertEquals("abc", Utilities.trim("--abc--", '-'));
		assertNull(Utilities.trim(null, '-'));
		assertEquals("", Utilities.trim("---", '-'));

		assertNull(Utilities.trimSimultaneously(null, "\""));
		assertEquals("\"a", Utilities.trimSimultaneously("\"a", null));
		assertEquals("a", Utilities.trimSimultaneously("\"a\"", "\""));
		assertEquals("\"a", Utilities.trimSimultaneously("\"a", "\""));
		assertEquals("\"", Utilities.trimSimultaneously("\"", "\""));
		assertEquals("", Utilities.trimSimultaneously("''", "'"));
	}

	@Test
	public void testSortButPutItemsFirst() {
		assertEquals(List.of("z", "x", "a", "b", "c"), Utilities.sortButPutItemsFirst(Arrays.asList("c", "x", "a", "z", "b"), "z", "x"));
		assertEquals(List.of("a", "b"), Utilities.sortButPutItemsFirst(Arrays.asList("b", "a")));
		assertEquals(List.of("b", "a", "a"), Utilities.sortButPutItemsFirst(Arrays.asList("a", "b", "a"), "b"));
	}

	@Test
	public void testSplitAndTrimListQuoted() {
		assertEquals(List.of("a", "b", "c"), Utilities.splitAndTrimListQuoted(" a, b ;c ", ',', ';'));
		// Current behavior: a separator following an empty item is kept as part of the next item
		assertEquals(List.of("a", ",b"), Utilities.splitAndTrimListQuoted("a,,b", ','));
		assertEquals(List.of("'a,b'", "\"c;d\"", "e"), Utilities.splitAndTrimListQuoted("'a,b', \"c;d\", e", ',', ';'));
		assertEquals(List.of("'it\"s'", "x"), Utilities.splitAndTrimListQuoted("'it\"s', x", ','));
		assertEquals(List.of("\"it's\"", "x"), Utilities.splitAndTrimListQuoted("\"it's\", x", ','));
		assertTrue(Utilities.splitAndTrimListQuoted("  ", ',').isEmpty());
	}

	@Test
	public void testShortenString() {
		assertEquals("abc...", Utilities.shortenStringToMaxLengthCutRight("abcdefghij", 6, "..."));
		assertEquals("abcdef", Utilities.shortenStringToMaxLengthCutRight("abcdefghij", 6, null));
		assertEquals("...", Utilities.shortenStringToMaxLengthCutRight("abcdefghij", 2, "..."));
		assertEquals("abc", Utilities.shortenStringToMaxLengthCutRight("abc", 6, "..."));
		assertNull(Utilities.shortenStringToMaxLengthCutRight(null, 6, "..."));
	}

	@Test
	public void testEndsWithIgnoreCase() {
		assertTrue(Utilities.endsWithIgnoreCase(null, null));
		assertFalse(Utilities.endsWithIgnoreCase(null, "a"));
		assertTrue(Utilities.endsWithIgnoreCase("a", null));
		assertTrue(Utilities.endsWithIgnoreCase("File.TXT", ".txt"));
		assertFalse(Utilities.endsWithIgnoreCase("File.csv", ".txt"));
	}

	@Test
	public void testParseDates() {
		assertEquals(LocalDate.of(2024, 2, 29), Utilities.parseLocalDate("dd.MM.yyyy", "29.02.2024"));
		assertEquals(LocalDateTime.of(2024, 2, 29, 13, 45, 10), Utilities.parseLocalDateTime("yyyy-MM-dd HH:mm:ss", "2024-02-29 13:45:10"));
		assertThrows(Exception.class, () -> Utilities.parseLocalDate("dd.MM.yyyy", "2024-02-29"));
	}

	@Test
	public void testFilesByPatternAndDelete() throws Exception {
		final File baseDirectory = Files.createTempDirectory("DbUtilitiesHelperTest").toFile();
		try {
			final File subDirectory = new File(baseDirectory, "sub");
			assertTrue(subDirectory.mkdir());
			Files.write(new File(baseDirectory, "a.sql").toPath(), "a".getBytes(StandardCharsets.UTF_8));
			Files.write(new File(baseDirectory, "b.txt").toPath(), "b".getBytes(StandardCharsets.UTF_8));
			Files.write(new File(subDirectory, "c.sql").toPath(), "c".getBytes(StandardCharsets.UTF_8));

			assertEquals(1, Utilities.getFilesByPattern(baseDirectory, ".*\\.sql", false).size());
			assertEquals(2, Utilities.getFilesByPattern(baseDirectory, ".*\\.sql", true).size());
			assertTrue(Utilities.getFilesByPattern(new File(baseDirectory, "a.sql"), ".*", true).isEmpty());

			assertTrue(Utilities.delete(baseDirectory));
			assertFalse(baseDirectory.exists());
		} finally {
			if (baseDirectory.exists()) {
				Utilities.delete(baseDirectory);
			}
		}
	}

	@Test
	public void testTrustAllTrustManager() throws Exception {
		final X509TrustManager trustManager = Utilities.createTrustAllTrustManager();
		assertNull(trustManager.getAcceptedIssuers());
		trustManager.checkClientTrusted(null, "RSA");
		trustManager.checkServerTrusted(null, "RSA");
	}

	@Test
	public void testCreateTrustStoreFileErrors() throws Exception {
		final File existingFile = File.createTempFile("DbUtilitiesHelperTest", ".jks");
		try {
			assertTrue(assertThrows(Exception.class, () -> Utilities.createTrustStoreFile("localhost", 443, existingFile, null, null)).getMessage().contains("already exists"));
		} finally {
			Files.deleteIfExists(existingFile.toPath());
		}
		final File newFile = new File(existingFile.getParentFile(), existingFile.getName() + ".new");
		assertEquals("Invalid port: abc", assertThrows(Exception.class, () -> Utilities.createTrustStoreFile("localhost:abc", 443, newFile, null, null)).getMessage());
		assertFalse(newFile.exists());
	}

	@Test
	public void testTestConnection() throws Exception {
		final int port;
		try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = serverSocket.getLocalPort();
			assertTrue(Utilities.testConnection(InetAddress.getLoopbackAddress().getHostAddress(), port));
		}
		// The port is closed now
		assertTrue(assertThrows(Exception.class, () -> Utilities.testConnection(InetAddress.getLoopbackAddress().getHostAddress(), port)).getMessage().startsWith("Cannot connect to host"));
	}
}
