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
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.data.DbConnectionDefinition;
import de.soderer.utilities.db.data.DbSimpleDataType;
import de.soderer.utilities.db.data.DbVendor;
import de.soderer.utilities.db.exception.DbNotExistsException;
import de.soderer.utilities.db.utilities.Utilities;

/**
 * Tests of the {@link DbUtilities} methods that need no database connection: connection URLs, data types, name
 * escaping, SQLite date parsing and the parameter checks of the connection and database file methods.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesStaticTest {
	private static final String MYSQL_PARAMETERS = "?useEncoding=true&useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull";

	private static boolean isTnsConfigured() {
		return Utilities.isNotBlank(System.getenv("TNS_ADMIN")) || Utilities.isNotBlank(System.getenv("ORACLE_HOME"));
	}

	@Test
	public void testOracleConnectionStrings() throws Exception {
		if (!isTnsConfigured()) {
			assertEquals("jdbc:oracle:thin:@host:1521:sid", DbUtilities.generateUrlConnectionString(DbVendor.Oracle, "host", 0, "sid", false, null, null, null));
			assertEquals("jdbc:oracle:thin:@host:1522/service", DbUtilities.generateUrlConnectionString(DbVendor.Oracle, "host", 1522, "/service", false, null, null, null));
			assertEquals("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=host)(PORT=2484))(CONNECT_DATA=(SERVICE_NAME=svc))(SECURITY=(ssl_server_cert_dn=\"CN=db\")))",
					DbUtilities.generateUrlConnectionString(DbVendor.Oracle, "host", 2484, "svc", true, null, null, "CN=db"));
			assertEquals("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=host)(PORT=2484))(CONNECT_DATA=(SERVICE_NAME=svc)))",
					DbUtilities.generateUrlConnectionString(DbVendor.Oracle, "host", 2484, "svc", true, null, null, " "));
		}
		assertEquals("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=h)))", DbUtilities.generateUrlConnectionString(DbVendor.Oracle, "ignored", 0, " (DESCRIPTION=(ADDRESS=(HOST=h))) ", false, null, null, null));
	}

	@Test
	public void testMysqlAndMariaDbConnectionStrings() throws Exception {
		final File trustStoreFile = new File("trust.jks");
		for (final DbVendor dbVendor : new DbVendor[] { DbVendor.MySQL, DbVendor.MariaDB }) {
			final String prefix = "jdbc:" + dbVendor.name().toLowerCase() + "://";
			assertEquals(prefix + "host:3306/db" + MYSQL_PARAMETERS, DbUtilities.generateUrlConnectionString(dbVendor, "host", 0, "db", false, null, null, null));
			assertEquals(prefix + "host:3307/db" + MYSQL_PARAMETERS + "&useSSL=true&trustServerCertificate=true", DbUtilities.generateUrlConnectionString(dbVendor, "host", 3307, "db", true, null, null, null));
			assertEquals(prefix + "host:3306/db" + MYSQL_PARAMETERS + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath(), DbUtilities.generateUrlConnectionString(dbVendor, "host", 0, "db", true, trustStoreFile, null, null));
			assertEquals(prefix + "host:3306/db" + MYSQL_PARAMETERS + "&useSSL=true&trustStore=" + trustStoreFile.getAbsolutePath() + "&trustStorePassword=pw",
					DbUtilities.generateUrlConnectionString(dbVendor, "host", 0, "db", true, trustStoreFile, "pw".toCharArray(), null));
		}
	}

	@Test
	public void testOtherConnectionStrings() throws Exception {
		final String home = System.getProperty("user.home");
		assertEquals("jdbc:postgresql://host:5432/db", DbUtilities.generateUrlConnectionString(DbVendor.PostgreSQL, "host", -1, "db", false, null, null, null));
		assertEquals("jdbc:postgresql://host:6543/db", DbUtilities.generateUrlConnectionString(DbVendor.PostgreSQL, "host", 6543, "db", false, null, null, null));
		assertEquals("jdbc:sqlite:" + home + "/test.sqlite", DbUtilities.generateUrlConnectionString(DbVendor.SQLite, null, 0, "~/test.sqlite", false, null, null, null));
		assertEquals("jdbc:derby:/tmp/derby", DbUtilities.generateUrlConnectionString(DbVendor.Derby, null, 0, "/tmp/derby", false, null, null, null));
		assertEquals("jdbc:firebirdsql:host/3050:/data/db.fdb", DbUtilities.generateUrlConnectionString(DbVendor.Firebird, "host", 0, "/data/db.fdb", false, null, null, null));
		assertEquals("jdbc:cassandra://host:9042/ks?primarydc=DC1&backupdc=DC2&consistency=QUORUM", DbUtilities.generateUrlConnectionString(DbVendor.Cassandra, "host", 0, "ks", false, null, null, null));
		assertEquals("jdbc:sqlserver://host:1433;database=db", DbUtilities.generateUrlConnectionString(DbVendor.MsSQL, "host", 0, "db", false, null, null, null));
		assertEquals("jdbc:sqlserver://host:1444;database=db;encrypt=true;trustServerCertificate=true", DbUtilities.generateUrlConnectionString(DbVendor.MsSQL, "host", 1444, "db", true, null, null, null));

		// HSQL file, Windows file, server and memory databases
		assertEquals("jdbc:hsqldb:file:/tmp/hsql/db;shutdown=true", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, null, 0, "/tmp/hsql/db", false, null, null, null));
		assertEquals("jdbc:hsqldb:file:C:\\data\\db;shutdown=true", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, null, 0, "C:\\data\\db", false, null, null, null));
		assertEquals("jdbc:hsqldb:http://host:9001/db", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, "host", 9001, "db", false, null, null, null));
		assertEquals("jdbc:hsqldb:http://host/db", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, "host", 80, "db", false, null, null, null));
		assertEquals("jdbc:hsqldb:https://host/db", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, "https://host", 443, "db", false, null, null, null));
		assertEquals("jdbc:hsqldb:https://host:8443/db", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, "https://host", 8443, "db", false, null, null, null));
		assertEquals("jdbc:hsqldb:mem:db", DbUtilities.generateUrlConnectionString(DbVendor.HSQL, " ", 0, "db", false, null, null, null));

		assertTrue(assertThrows(Exception.class, () -> DbUtilities.generateUrlConnectionString(DbVendor.PostgreSQL, "host", 0, "db", true, null, null, null)).getMessage().startsWith("Secure connection is only supported"));
		assertEquals("Unknown database vendor", assertThrows(Exception.class, () -> DbUtilities.generateUrlConnectionString(null, "host", 0, "db", false, null, null, null)).getMessage());
	}

	@Test
	public void testDataTypes() throws Exception {
		final String[][] expectedTypes = {
				// Vendor, Blob, Clob, DateTime, Date, Integer, BigInteger, Float, String, Boolean
				{ "Oracle", "BLOB", "CLOB", "TIMESTAMP", "DATE", "NUMBER", "NUMBER", "NUMBER", "VARCHAR2", "BOOLEAN" },
				{ "MySQL", "LONGBLOB", "LONGTEXT", "TIMESTAMP NULL", "DATE", "INT", "BIGINT", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "MariaDB", "LONGBLOB", "LONGTEXT", "TIMESTAMP NULL", "DATE", "INT", "BIGINT", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "HSQL", "BLOB", "CLOB", "TIMESTAMP", "DATE", "INTEGER", "BIGINT", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "PostgreSQL", "BYTEA", "TEXT", "TIMESTAMP", "DATE", "INTEGER", "BIGINT", "REAL", "VARCHAR", "BOOLEAN" },
				{ "SQLite", "BLOB", "CLOB", "TIMESTAMP", "DATE", "INTEGER", "INTEGER", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "Derby", "BLOB", "CLOB", "TIMESTAMP", "DATE", "INTEGER", "BIGINT", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "Firebird", "BLOB SUB_TYPE BINARY", "BLOB SUB_TYPE TEXT", "TIMESTAMP", "DATE", "INTEGER", "BIGINT", "DOUBLE PRECISION", "VARCHAR", "BOOLEAN" },
				{ "Cassandra", "BLOB", "TEXT", "TIMESTAMP", "DATE", "INT", "BIGINT", "DOUBLE", "VARCHAR", "BOOLEAN" },
				{ "MsSQL", "VARBINARY(MAX)", "VARCHAR(MAX)", "DATETIME", "DATE", "INT", "BIGINT", "REAL", "VARCHAR", "BIT" } };
		final DbSimpleDataType[] simpleDataTypes = { DbSimpleDataType.Blob, DbSimpleDataType.Clob, DbSimpleDataType.DateTime, DbSimpleDataType.Date, DbSimpleDataType.Integer,
				DbSimpleDataType.BigInteger, DbSimpleDataType.Float, DbSimpleDataType.String, DbSimpleDataType.Boolean };
		for (final String[] expectedVendorTypes : expectedTypes) {
			final DbVendor dbVendor = DbVendor.valueOf(expectedVendorTypes[0]);
			for (int i = 0; i < simpleDataTypes.length; i++) {
				assertEquals(expectedVendorTypes[i + 1], DbUtilities.getDataType(dbVendor, simpleDataTypes[i]), dbVendor + "/" + simpleDataTypes[i]);
			}
		}
		assertThrows(Exception.class, () -> DbUtilities.getDataType(null, DbSimpleDataType.String));
	}

	@Test
	public void testTypeNamesAndDownloadUrls() throws Exception {
		final int[] typeIds = { Types.BIT, Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL,
				Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.DATE, Types.TIME, Types.TIMESTAMP, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.NULL,
				Types.OTHER, Types.JAVA_OBJECT, Types.DISTINCT, Types.STRUCT, Types.ARRAY, Types.BLOB, Types.CLOB, Types.REF, Types.DATALINK, Types.BOOLEAN, Types.ROWID,
				Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.NCLOB, Types.SQLXML, Types.REF_CURSOR, Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE };
		final String[] typeNames = { "BIT", "TINYINT", "SMALLINT", "INTEGER", "BIGINT", "FLOAT", "REAL", "DOUBLE", "NUMERIC", "DECIMAL",
				"CHAR", "VARCHAR", "LONGVARCHAR", "DATE", "TIME", "TIMESTAMP", "BINARY", "VARBINARY", "LONGVARBINARY", "NULL",
				"OTHER", "JAVA_OBJECT", "DISTINCT", "STRUCT", "ARRAY", "BLOB", "CLOB", "REF", "DATALINK", "BOOLEAN", "ROWID",
				"NCHAR", "NVARCHAR", "LONGNVARCHAR", "NCLOB", "SQLXML", "REF_CURSOR", "TIME_WITH_TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" };
		for (int i = 0; i < typeIds.length; i++) {
			assertEquals(typeNames[i], DbUtilities.getTypeNameById(typeIds[i]));
		}
		assertThrows(Exception.class, () -> DbUtilities.getTypeNameById(12345));

		assertEquals(DbUtilities.DOWNLOAD_LOCATION_MYSQL, DbUtilities.getDownloadUrl(DbVendor.MySQL));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_MARIADB, DbUtilities.getDownloadUrl(DbVendor.MariaDB));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_ORACLE, DbUtilities.getDownloadUrl(DbVendor.Oracle));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_POSTGRESQL, DbUtilities.getDownloadUrl(DbVendor.PostgreSQL));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_FIREBIRD, DbUtilities.getDownloadUrl(DbVendor.Firebird));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_DERBY, DbUtilities.getDownloadUrl(DbVendor.Derby));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_SQLITE, DbUtilities.getDownloadUrl(DbVendor.SQLite));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_HSQL, DbUtilities.getDownloadUrl(DbVendor.HSQL));
		assertEquals(DbUtilities.DOWNLOAD_LOCATION_MSSQL, DbUtilities.getDownloadUrl(DbVendor.MsSQL));
		assertThrows(Exception.class, () -> DbUtilities.getDownloadUrl(DbVendor.Cassandra));
	}

	@Test
	public void testEscapeVendorReservedNames() {
		assertEquals("`select`", DbUtilities.escapeVendorReservedNames(DbVendor.MySQL, "select"));
		assertEquals("`a b`", DbUtilities.escapeVendorReservedNames(DbVendor.MariaDB, "a b"));
		assertEquals("`a``b`", DbUtilities.escapeVendorReservedNames(DbVendor.MySQL, "a`b"));
		assertEquals("plain", DbUtilities.escapeVendorReservedNames(DbVendor.MySQL, "plain"));
		assertEquals("\"user\"", DbUtilities.escapeVendorReservedNames(DbVendor.PostgreSQL, "user"));
		assertEquals("\"a\"\"b\"", DbUtilities.escapeVendorReservedNames(DbVendor.PostgreSQL, "a\"b"));
		assertEquals("plain", DbUtilities.escapeVendorReservedNames(DbVendor.PostgreSQL, "plain"));
		assertEquals("\"ACCESS\"", DbUtilities.escapeVendorReservedNames(DbVendor.Oracle, "access"));
		assertEquals("plain", DbUtilities.escapeVendorReservedNames(DbVendor.Oracle, "plain"));
		assertEquals("\"add\"", DbUtilities.escapeVendorReservedNames(DbVendor.Derby, "add"));
		assertEquals("plain", DbUtilities.escapeVendorReservedNames(DbVendor.Derby, "plain"));
		assertEquals("\"1abc\"", DbUtilities.escapeVendorReservedNames(DbVendor.SQLite, "1abc"));
		assertEquals("select", DbUtilities.escapeVendorReservedNames(DbVendor.SQLite, "select"));
		assertEquals(" ", DbUtilities.escapeVendorReservedNames(DbVendor.MySQL, " "));
		assertNull(DbUtilities.escapeVendorReservedNames(DbVendor.MySQL, null));

		assertEquals("select", DbUtilities.unescapeVendorReservedNames(DbVendor.MySQL, "`select`"));
		assertEquals("select", DbUtilities.unescapeVendorReservedNames(DbVendor.MariaDB, "`select`"));
		assertEquals("USER", DbUtilities.unescapeVendorReservedNames(DbVendor.Oracle, "\"USER\""));
		assertEquals("add", DbUtilities.unescapeVendorReservedNames(DbVendor.Derby, "\"add\""));
		assertEquals("\"x\"", DbUtilities.unescapeVendorReservedNames(DbVendor.PostgreSQL, "\"x\""));

		assertEquals("a, `order`, LOWER(b)", DbUtilities.joinColumnVendorEscaped(DbVendor.MySQL, Arrays.asList("a", "order", " LOWER(b) ")));
	}

	@Test
	public void testKeyColumnEquationList() {
		assertEquals("a.id = b.id AND LOWER(a.mail) = LOWER(b.mail)", DbUtilities.getKeyColumnEquationList(DbVendor.MySQL, List.of("id", "LOWER(mail)"), "a", "b"));
		assertEquals("id = id AND `key` = `key`", DbUtilities.getKeyColumnEquationList(DbVendor.MySQL, List.of(" id ", "key"), null, " "));
		assertEquals("UPPER(name) = UPPER(name)", DbUtilities.getKeyColumnEquationList(DbVendor.PostgreSQL, List.of("UPPER(name)"), "", null));
	}

	@Test
	public void testIdentifiers() {
		assertTrue(DbUtilities.isValidIdentifier("table_1"));
		assertFalse(DbUtilities.isValidIdentifier("a-b"));
		assertFalse(DbUtilities.isValidIdentifier("a234567890123456789012345678901"));
		assertFalse(DbUtilities.isValidIdentifier(null));
		assertTrue(DbUtilities.isValidAlias("t1"));
		assertFalse(DbUtilities.isValidAlias("t 1"));
		assertFalse(DbUtilities.isValidAlias(null));
		assertTrue(DbUtilities.SAFE_IDENTIFIER.matcher("_abc1").matches());
		assertTrue(DbUtilities.SQL_OPERATORS.contains("<>"));
		assertTrue(DbUtilities.RESERVED_WORDS_POSTGRESQL.contains("SELECT"));
	}

	@Test
	public void testDevNull() throws Exception {
		DbUtilities.DEV_NULL.write(1);
		DbUtilities.DEV_NULL.write(new byte[] { 1, 2, 3 });
		DbUtilities.DEV_NULL.write(new byte[] { 1, 2, 3 }, 1, 1);
		DbUtilities.DEV_NULL.flush();
		assertEquals(-101, DbUtilities.ORACLE_TIMESTAMPTZ_TYPECODE);
	}

	@Test
	public void testExtractSqliteLocalDate() throws Exception {
		assertNull(DbUtilities.extractSqliteLocalDate(null));
		final long epochMillis = 1670968004453L;
		assertEquals(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate(), DbUtilities.extractSqliteLocalDate(Long.valueOf(epochMillis)));
		final LocalDate expected = LocalDate.of(2022, 12, 13);
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13T22:46:44.463491400+01:00[Europe/Berlin]"));
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13T22:46:44+01:00"));
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13T22:46:44.4605153"));
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13T22:46:44"));
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13 21:46:44"));
		assertEquals(expected, DbUtilities.extractSqliteLocalDate("2022-12-13"));
		assertThrows(Exception.class, () -> DbUtilities.extractSqliteLocalDate("13.12.2022"));
		assertThrows(Exception.class, () -> DbUtilities.extractSqliteLocalDate(Integer.valueOf(1)));
	}

	@Test
	public void testExtractSqliteLocalDateTime() throws Exception {
		assertNull(DbUtilities.extractSqliteLocalDateTime(null));
		final long epochMillis = 1670968004453L;
		assertEquals(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(), DbUtilities.extractSqliteLocalDateTime(Long.valueOf(epochMillis)));
		assertEquals(LocalDateTime.of(2022, 12, 13, 22, 46, 44, 463491400), DbUtilities.extractSqliteLocalDateTime("2022-12-13T22:46:44.463491400+01:00[Europe/Berlin]"));
		assertEquals(LocalDateTime.of(2022, 12, 13, 22, 46, 44, 460000000), DbUtilities.extractSqliteLocalDateTime("2022-12-13T22:46:44.46+01:00"));
		assertEquals(LocalDateTime.of(2022, 12, 13, 22, 46, 44, 460515000), DbUtilities.extractSqliteLocalDateTime("2022-12-13T22:46:44.460515"));
		assertEquals(LocalDateTime.of(2022, 12, 13, 21, 46, 44), DbUtilities.extractSqliteLocalDateTime("2022-12-13 21:46:44"));
		assertEquals(LocalDateTime.of(2022, 12, 13, 0, 0, 0), DbUtilities.extractSqliteLocalDateTime("2022-12-13"));
		assertThrows(Exception.class, () -> DbUtilities.extractSqliteLocalDateTime("13.12.2022 10:00"));
		assertThrows(Exception.class, () -> DbUtilities.extractSqliteLocalDateTime("13.12.2022"));
		assertThrows(Exception.class, () -> DbUtilities.extractSqliteLocalDateTime(Double.valueOf(1)));
	}

	@Test
	public void testCreateConnectionParameterChecks() throws Exception {
		assertEquals("Unknown database vendor", assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(), false)).getMessage());
		assertEquals("Cannot create database connection: Missing hostname",
				assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.MySQL, "", "db", "u", "p".toCharArray()), false)).getMessage());
		assertEquals("Cannot create database connection: Missing dbName",
				assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.SQLite, null, "", null, null), false)).getMessage());
		final File missingTrustStore = new File(System.getProperty("java.io.tmpdir"), "missing-" + System.nanoTime() + ".jks");
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.MySQL, "host", "db", "u", "p".toCharArray(), true, missingTrustStore, null), false))
				.getMessage().contains("does not exist"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.PostgreSQL, "host", "db", "u", "p".toCharArray(), true, null, null), false))
				.getMessage().startsWith("Secure connection is only supported"));
		// The Firebird JDBC driver is not part of the test classpath
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.Firebird, "host", "db", "u", "p".toCharArray()), false))
				.getMessage().startsWith("Cannot create database connection, caused by unknown DriverClassName"));
	}

	@Test
	public void testCreateAndDeleteDatabaseParameterChecks() throws Exception {
		assertEquals("Unknown database vendor", assertThrows(Exception.class, () -> DbUtilities.createNewDatabase(null, "x")).getMessage());
		assertEquals("Unknown database vendor", assertThrows(Exception.class, () -> DbUtilities.deleteDatabase(null, "x")).getMessage());
		assertThrows(Exception.class, () -> DbUtilities.deleteDatabase(DbVendor.MySQL, "x"));
		// Driver class missing or vendor not supported for creation
		assertThrows(Exception.class, () -> DbUtilities.createNewDatabase(DbVendor.Cassandra, "x"));
	}

	@Test
	public void testDeleteDatabaseFiles() throws Exception {
		final File directory = Files.createTempDirectory("DbUtilitiesStaticTest").toFile();
		try {
			// SQLite: a single file
			final File sqliteFile = new File(directory, "test.sqlite");
			Files.write(sqliteFile.toPath(), "x".getBytes(StandardCharsets.UTF_8));
			assertTrue(DbUtilities.deleteDatabase(DbVendor.SQLite, sqliteFile.getAbsolutePath()));
			assertFalse(sqliteFile.exists());
			assertFalse(DbUtilities.deleteDatabase(DbVendor.SQLite, sqliteFile.getAbsolutePath()));

			// HSQL: all files with the database name as base name
			final File hsqlScript = new File(directory, "hsqldb.script");
			final File hsqlProperties = new File(directory, "hsqldb.properties");
			final File otherFile = new File(directory, "other.script");
			Files.write(hsqlScript.toPath(), "x".getBytes(StandardCharsets.UTF_8));
			Files.write(hsqlProperties.toPath(), "x".getBytes(StandardCharsets.UTF_8));
			Files.write(otherFile.toPath(), "x".getBytes(StandardCharsets.UTF_8));
			assertTrue(DbUtilities.deleteDatabase(DbVendor.HSQL, new File(directory, "hsqldb").getAbsolutePath()));
			assertFalse(hsqlScript.exists());
			assertFalse(hsqlProperties.exists());
			assertTrue(otherFile.exists());
			assertFalse(DbUtilities.deleteDatabase(DbVendor.HSQL, "memoryDb"));
			assertThrows(Exception.class, () -> DbUtilities.deleteDatabase(DbVendor.HSQL, new File(directory, "missing/hsqldb").getAbsolutePath()));

			// Derby: a directory
			final File derbyDirectory = new File(directory, "derby");
			assertTrue(derbyDirectory.mkdir());
			Files.write(new File(derbyDirectory, "service.properties").toPath(), "x".getBytes(StandardCharsets.UTF_8));
			assertTrue(DbUtilities.deleteDatabase(DbVendor.Derby, derbyDirectory.getAbsolutePath()));
			assertFalse(derbyDirectory.exists());
			assertFalse(DbUtilities.deleteDatabase(DbVendor.Derby, derbyDirectory.getAbsolutePath()));

			// Derby shutdown checks
			assertThrows(DbNotExistsException.class, () -> DbUtilities.shutDownDerbyDb(derbyDirectory.getAbsolutePath()));
			assertTrue(assertThrows(Exception.class, () -> DbUtilities.shutDownDerbyDb(otherFile.getAbsolutePath())).getMessage().contains("is not a directory"));
		} finally {
			Utilities.delete(directory);
		}
		assertNotNull(new DbUtilitiesStaticTest());
	}
}
