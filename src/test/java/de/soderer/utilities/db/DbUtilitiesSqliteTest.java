package de.soderer.utilities.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbConnectionDefinition;
import de.soderer.utilities.db.data.DbVendor;
import de.soderer.utilities.db.exception.DbNotExistsException;
import de.soderer.utilities.db.utilities.Utilities;

/**
 * Integration tests of {@link DbUtilities} with a real SQLite file database. The tests are skipped, if the SQLite
 * JDBC driver (org.xerial:sqlite-jdbc) is not on the test classpath.
 */
public class DbUtilitiesSqliteTest {
	private File directory;
	private File databaseFile;

	@BeforeEach
	public void setUp() throws Exception {
		boolean driverAvailable;
		try {
			Class.forName(DbVendor.SQLite.getDriverClassName());
			driverAvailable = true;
		} catch (@SuppressWarnings("unused") final ClassNotFoundException e) {
			driverAvailable = false;
		}
		Assumptions.assumeTrue(driverAvailable, "SQLite JDBC driver is not available");

		directory = Files.createTempDirectory("DbUtilitiesSqliteTest").toFile();
		databaseFile = new File(directory, "test.sqlite");
	}

	@AfterEach
	public void tearDown() {
		if (directory != null) {
			Utilities.delete(directory);
		}
	}

	private Connection createDatabaseWithData() throws Exception {
		final Connection connection = DbUtilities.createNewDatabase(DbVendor.SQLite, databaseFile.getAbsolutePath());
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, email VARCHAR(100) NOT NULL, name VARCHAR(50), amount DECIMAL(10,2) DEFAULT 0, created TIMESTAMP)");
			statement.execute("INSERT INTO person (email, name, amount, created) VALUES ('a@x.de', 'Anna', 1.5, '2024-01-02 10:00:00')");
			statement.execute("INSERT INTO person (email, name, amount) VALUES ('b@x.de', 'Bob', 2)");
			statement.execute("INSERT INTO person (email, name, amount) VALUES ('A@x.de', NULL, 3)");
			statement.execute("INSERT INTO person (email, name, amount) VALUES ('b@x.de', 'Bobby', NULL)");
		}
		// The data operations of DbUtilities commit or roll back themselves, which requires a connection without auto commit
		connection.setAutoCommit(false);
		return connection;
	}

	private static int count(final Connection connection, final String sql) throws Exception {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	@Test
	public void testCreateConnectAndDeleteDatabase() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			assertEquals(DbVendor.SQLite, DbUtilities.getDbVendor(connection));
			assertTrue(DbUtilities.getDbUrl(connection).startsWith("jdbc:sqlite:"));
		}
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createNewDatabase(DbVendor.SQLite, databaseFile.getAbsolutePath())).getMessage().contains("already exists"));

		final DbConnectionDefinition definition = new DbConnectionDefinition(DbVendor.SQLite, null, databaseFile.getAbsolutePath(), null, null);
		try (Connection connection = DbUtilities.createConnection(definition, false)) {
			assertEquals(4, DbUtilities.getTableEntriesCount(connection, "person"));
		}
		assertThrows(DbNotExistsException.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.SQLite, null, new File(directory, "missing.sqlite").getAbsolutePath(), null, null), false));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.SQLite, null, directory.getAbsolutePath(), null, null), false)).getMessage().contains("is not a file"));

		assertTrue(DbUtilities.deleteDatabase(DbVendor.SQLite, databaseFile.getAbsolutePath()));
		assertFalse(databaseFile.exists());
	}

	@Test
	public void testMetadata() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			final Map<String, DbColumnType> columnTypes = DbUtilities.getColumnDataTypes(connection, "person");
			assertEquals(5, columnTypes.size());
			assertTrue(columnTypes.get("id").isAutoIncrement());
			assertFalse(columnTypes.get("email").isNullable());
			assertEquals(100, columnTypes.get("email").getCharacterByteSize());
			assertEquals(10, columnTypes.get("amount").getNumericPrecision());
			assertEquals(2, columnTypes.get("amount").getNumericScale());

			assertTrue(DbUtilities.getColumnNames(connection, "person").contains("EMAIL"));
			assertTrue(DbUtilities.checkTableExist(connection, "person"));
			assertFalse(DbUtilities.checkTableExist(connection, "missing"));
			assertTrue(DbUtilities.checkTableAndColumnsExist(connection, "person", "id", "name"));
			assertEquals(List.of("person"), DbUtilities.getAvailableTables(connection, "pers*, !sqlite_*"));
			assertTrue(DbUtilities.getPrimaryKeyColumns(connection, "person").contains("id"));
			assertTrue(DbUtilities.getForeignKeys(connection, "person").isEmpty());
			assertNull(DbUtilities.getConstraints(connection, "person"));
			assertNull(DbUtilities.checkForIndex(connection, "person", List.of("id")));
		}
	}

	@Test
	public void testCreateTableAndCopy() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			final Map<String, DbColumnType> columns = new LinkedHashMap<>();
			columns.put("key", new DbColumnType("INTEGER", 0, 10, 0, false, false, null));
			columns.put("text", new DbColumnType("VARCHAR", 20, 0, 0, true, false, null));
			DbUtilities.createTable(connection, "created_table", columns, List.of("key"));
			assertTrue(DbUtilities.checkTableAndColumnsExist(connection, "created_table", "key", "text"));
			assertTrue(DbUtilities.getPrimaryKeyColumns(connection, "created_table").contains("key"));

			DbUtilities.copyTableStructure(connection, "person", List.of("id", "name"), List.of("id"), "person_copy");
			assertEquals(0, DbUtilities.getTableEntriesCount(connection, "person_copy"));
			assertEquals(2, DbUtilities.getColumnNames(connection, "person_copy").size());

			assertEquals(4, DbUtilities.insertAllItems(connection, "person", "person_copy", List.of("id", "name"), null));
			assertEquals(0, DbUtilities.insertNotExistingItems(connection, "person", "person_copy", List.of("id", "name"), List.of("id"), null));
			assertEquals(4, DbUtilities.clearTable(connection, "person_copy"));

			final String indexName = DbUtilities.createIndex(connection, "person", List.of("email"));
			assertTrue(indexName.startsWith("person_"));

			assertTrue(DbUtilities.dropTableIfExists(connection, "person_copy"));
			assertFalse(DbUtilities.dropTableIfExists(connection, "person_copy"));
		}
	}

	@Test
	public void testDuplicates() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			assertEquals(1, DbUtilities.detectDuplicates(connection, "person", List.of("email")));
			assertEquals(2, DbUtilities.detectDuplicates(connection, "person", List.of("LOWER(email)")));

			assertEquals(1, DbUtilities.dropDuplicates(connection, "person", List.of("email")));
			assertEquals(3, DbUtilities.getTableEntriesCount(connection, "person"));
			assertEquals(0, DbUtilities.detectDuplicates(connection, "person", List.of("email")));
			assertEquals(0, DbUtilities.dropDuplicates(connection, "person", List.of("email")));
			// The temporary columns were removed again
			assertEquals(5, DbUtilities.getColumnNames(connection, "person").size());
		}
	}

	@Test
	public void testJoinDuplicates() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			assertEquals(2, DbUtilities.joinDuplicates(connection, "person", List.of("LOWER(email)"), false));
			assertEquals(2, DbUtilities.getTableEntriesCount(connection, "person"));
			// The name of the duplicate was joined into the remaining entry
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM person WHERE LOWER(email) = 'a@x.de' AND name = 'Anna'"));
			assertEquals(5, DbUtilities.getColumnNames(connection, "person").size());
		}
	}

	@Test
	public void testUpdateItems() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE updates (email VARCHAR(100), name VARCHAR(50), idx INTEGER)");
				statement.execute("INSERT INTO updates VALUES ('a@x.de', 'Annabell', 1)");
				statement.execute("INSERT INTO updates VALUES ('c@x.de', 'Carl', 2)");
				statement.execute("INSERT INTO updates VALUES ('b@x.de', NULL, 3)");
			}
			assertEquals(1, DbUtilities.updateAllExistingItems(connection, "updates", "person", List.of("email", "name"), List.of("email"), "idx", false, null));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM person WHERE name = 'Annabell'"));
			// Without null values the names of "b@x.de" are kept
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM person WHERE name = 'Bob'"));

			assertEquals(3, DbUtilities.updateAllExistingItems(connection, "updates", "person", List.of("email", "name"), List.of("email"), "idx", true, null));
			assertEquals(0, count(connection, "SELECT COUNT(*) FROM person WHERE name = 'Bob'"));

			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE first_updates (email VARCHAR(100), name VARCHAR(50), srcidx INTEGER)");
				statement.execute("INSERT INTO first_updates (email, name) VALUES ('b@x.de', 'Bobbi')");
			}
			assertEquals(1, DbUtilities.updateFirstExistingItems(connection, "first_updates", "person", List.of("email", "name"), List.of("email"), "srcidx", true, null));
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM person WHERE name = 'Bobbi'"));

			// Current behavior: the column default values are read from "information_schema", which SQLite does not provide
			assertThrows(Exception.class, () -> DbUtilities.removeNewEntriesWithInvalidNullValues(connection, "updates", "person", null, null));
			assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue(connection, "person", "amount"));
		}
	}

	@Test
	public void testColumnsAndDefaults() throws Exception {
		try (Connection connection = createDatabaseWithData()) {
			final String columnName = DbUtilities.addIndexedIntegerColumn(connection, "person", "extra");
			assertEquals("extra", columnName);
			assertEquals("extra_1", DbUtilities.addIndexedIntegerColumn(connection, "person", "extra"));
			assertTrue(DbUtilities.dropColumnIfExists(connection, "person", "extra"));
			assertTrue(DbUtilities.dropColumnIfExists(connection, "person", "extra_1"));
			assertFalse(DbUtilities.dropColumnIfExists(connection, "person", "extra"));

			try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT created FROM person WHERE name = 'Anna'");
					ResultSet resultSet = preparedStatement.executeQuery()) {
				assertTrue(resultSet.next());
				assertNotNull(DbUtilities.extractSqliteLocalDateTime(resultSet.getObject(1)));
				assertNotNull(DbUtilities.extractSqliteLocalDate(resultSet.getObject(1)));
			}
		}
	}

	@Test
	public void testUpdateBlob() throws Exception {
		createDatabaseWithData().close();
		try (Connection connection = DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.SQLite, null, databaseFile.getAbsolutePath(), null, null), false);
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE files (id INTEGER, content BLOB)");
			statement.execute("INSERT INTO files (id) VALUES (1)");
		}
		final File blobFile = new File(directory, "data.bin");
		Files.write(blobFile.toPath(), "blob content".getBytes(StandardCharsets.UTF_8));
		DbUtilities.updateBlob(new DbConnectionDefinition(DbVendor.SQLite, null, databaseFile.getAbsolutePath(), null, null), "UPDATE files SET content = ? WHERE id = 1", blobFile.getAbsolutePath());
		try (Connection connection = DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.SQLite, null, databaseFile.getAbsolutePath(), null, null), false)) {
			assertEquals(1, count(connection, "SELECT COUNT(*) FROM files WHERE LENGTH(content) = 12"));
		}
	}
}
