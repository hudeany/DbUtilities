package de.soderer.utilities.db;

import static de.soderer.utilities.db.FakeJdbc.error;
import static de.soderer.utilities.db.FakeJdbc.rows;
import static de.soderer.utilities.db.FakeJdbc.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.FakeJdbc.FakeDatabase;
import de.soderer.utilities.db.data.DbConnectionDefinition;
import de.soderer.utilities.db.data.DbVendor;

/**
 * Tests of the check for NOT NULL columns without value and of the help texts for connection errors.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesInsertCheckTest {
	private static final String[] POSTGRESQL_COLUMN_TYPE_COLUMNS = { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "column_default" };
	private static final String[] METADATA_COLUMNS = { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "COLUMN_DEF", "IS_AUTOINCREMENT", "IS_GENERATEDCOLUMN" };

	/**
	 * PostgreSQL table "customer" with: id (serial), code and name (NOT NULL without default), created (NOT NULL with
	 * default), note (nullable), flag (NOT NULL without default) and calc (generated column).
	 */
	private static FakeDatabase createPostgreSqlCustomerDatabase(final Object triggerCount) {
		return createPostgreSqlCustomerDatabase(triggerCount,
				new Object[] { null, "public", "customer", "id", "nextval('customer_id_seq'::regclass)", "YES", "NO" },
				new Object[] { null, "public", "customer", "code", null, "NO", "NO" },
				new Object[] { null, "public", "customer", "name", null, "NO", "NO" },
				new Object[] { null, "public", "customer", "created", "now()", "NO", "NO" },
				new Object[] { null, "public", "customer", "note", null, "NO", "NO" },
				new Object[] { null, "public", "customer", "flag", null, "NO", "NO" },
				new Object[] { null, "public", "customer", "calc", null, "NO", "YES" });
	}

	private static FakeDatabase createPostgreSqlCustomerDatabase(final Object triggerCount, final Object[]... metadataRows) {
		final FakeDatabase database = new FakeDatabase("PostgreSQL")
				.on("FROM information_schema.columns", rows(POSTGRESQL_COLUMN_TYPE_COLUMNS,
						new Object[] { "id", "integer", null, 32, 0, "NO", "nextval('customer_id_seq'::regclass)" },
						new Object[] { "code", "character varying", 10, null, null, "NO", null },
						new Object[] { "name", "character varying", 100, null, null, "NO", null },
						new Object[] { "created", "timestamp without time zone", null, null, null, "NO", "now()" },
						new Object[] { "note", "text", null, null, null, "YES", null },
						new Object[] { "flag", "integer", null, 32, 0, "NO", null },
						new Object[] { "calc", "integer", null, 32, 0, "NO", null }))
				.on("META getColumns", rows(METADATA_COLUMNS, metadataRows));
		if (triggerCount instanceof String) {
			database.on("FROM pg_trigger", error((String) triggerCount));
		} else {
			database.on("FROM pg_trigger", value(triggerCount));
		}
		return database;
	}

	@Test
	public void testMissingNotNullColumns() throws Exception {
		final FakeDatabase database = createPostgreSqlCustomerDatabase(Integer.valueOf(0));

		assertEquals(Arrays.asList("flag", "name"), DbUtilities.getMissingNotNullColumnsForInsert(database.connection(), "customer", Arrays.asList("code"), null, false));

		// Values given by additional insert values (also quoted names and several separators)
		assertEquals(Arrays.asList("name"), DbUtilities.getMissingNotNullColumnsForInsert(database.connection(), "customer", Arrays.asList("code"), "flag = 1", false));
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(database.connection(), "customer", Arrays.asList("\"code\"", "NAME"), "flag = 1;note='x'", false));
	}

	@Test
	public void testMissingNotNullColumnsNotReliable() throws Exception {
		// Active insert trigger may fill the columns
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(createPostgreSqlCustomerDatabase(Integer.valueOf(1)).connection(), "customer", Arrays.asList("code"), null, false));

		// Triggers are deactivated for the import, so they do not fill the columns
		assertEquals(Arrays.asList("flag", "name"), DbUtilities.getMissingNotNullColumnsForInsert(createPostgreSqlCustomerDatabase(Integer.valueOf(1)).connection(), "customer", Arrays.asList("code"), null, true));

		// Triggers cannot be read (e.g. missing privileges)
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(createPostgreSqlCustomerDatabase("permission denied").connection(), "customer", Arrays.asList("code"), null, false));

		// Same table name in several schemas
		final FakeDatabase ambiguousDatabase = createPostgreSqlCustomerDatabase(Integer.valueOf(0),
				new Object[] { null, "public", "customer", "name", null, "NO", "NO" },
				new Object[] { null, "archive", "customer", "name", "'x'", "NO", "NO" });
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(ambiguousDatabase.connection(), "customer", Arrays.asList("code"), null, false));

		// Table not found in the metadata
		final FakeDatabase noMetadataDatabase = createPostgreSqlCustomerDatabase(Integer.valueOf(0), new Object[0][]);
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(noMetadataDatabase.connection(), "customer", Arrays.asList("code"), null, false));

		// Cassandra has no NOT NULL constraints
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(new FakeDatabase("Cassandra").connection(), "customer", Arrays.asList("code"), null, false));
	}

	@Test
	public void testMissingNotNullColumnsMySqlStrictMode() throws Exception {
		final String[] mysqlColumns = { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "extra" };
		final Object[][] columnRows = {
				new Object[] { "id", "int", null, 10, 0, "NO", "auto_increment" },
				new Object[] { "name", "varchar", 100, null, null, "NO", "" } };
		final Object[][] metadataRows = {
				new Object[] { "shop", null, "customer", "id", null, "YES", "NO" },
				new Object[] { "shop", null, "customer", "name", null, "NO", "NO" } };

		final FakeDatabase strictDatabase = new FakeDatabase("MySQL")
				.on("@@SESSION.sql_mode", value("ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_DATE"))
				.on("FROM information_schema.triggers", value(Integer.valueOf(0)))
				.on("FROM information_schema.columns", rows(mysqlColumns, columnRows))
				.on("META getColumns customer", rows(METADATA_COLUMNS, metadataRows));
		assertEquals(Arrays.asList("name"), DbUtilities.getMissingNotNullColumnsForInsert(strictDatabase.connection(), "customer", Arrays.asList("other"), null, false));

		// Without strict mode MySQL fills missing values with implicit defaults
		final FakeDatabase nonStrictDatabase = new FakeDatabase("MySQL")
				.on("@@SESSION.sql_mode", value("NO_ENGINE_SUBSTITUTION"))
				.on("FROM information_schema.triggers", value(Integer.valueOf(0)))
				.on("FROM information_schema.columns", rows(mysqlColumns, columnRows))
				.on("META getColumns customer", rows(METADATA_COLUMNS, metadataRows));
		assertEquals(Collections.emptyList(), DbUtilities.getMissingNotNullColumnsForInsert(nonStrictDatabase.connection(), "customer", Arrays.asList("other"), null, false));
	}

	@Test
	public void testHasInsertTriggers() throws Exception {
		assertEquals(Boolean.TRUE, DbUtilities.hasInsertTriggers(new FakeDatabase("SQLite").on("FROM sqlite_master WHERE type = 'trigger'", value(Integer.valueOf(2))).connection(), "customer"));
		assertEquals(Boolean.FALSE, DbUtilities.hasInsertTriggers(new FakeDatabase("Oracle").on("FROM all_triggers", value(Integer.valueOf(0))).connection(), "customer"));
		assertNull(DbUtilities.hasInsertTriggers(new FakeDatabase("Oracle").on("FROM all_triggers", error("ORA-00942: table or view does not exist")).connection(), "customer"));

		final FakeDatabase database = new FakeDatabase("PostgreSQL").on("FROM pg_trigger", value(Integer.valueOf(0)));
		assertEquals(Boolean.FALSE, DbUtilities.hasInsertTriggers(database.connection(), "sales.customer"));
		assertEquals(Arrays.asList("customer", "sales"), database.getExecutedParameters().get(0));
	}

	@Test
	public void testConnectionErrorHelpTexts() {
		assertEquals("", DbUtilities.getConnectionErrorHelpText(DbVendor.PostgreSQL, new Exception("Something else"), null));

		final String authenticationHelp = DbUtilities.getConnectionErrorHelpText(DbVendor.PostgreSQL, new Exception("wrapper", new SQLException("FATAL: password authentication failed for user \"test\"", "28P01")), null);
		assertTrue(authenticationHelp.startsWith("\nHint: The login was rejected"), authenticationHelp);

		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.MySQL, new SQLException("Access denied for user 'x'@'host'", "28000", 1045), null).contains("login was rejected"));
		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.MySQL, new SQLException("Unknown database 'shop'", "42000", 1049), null).contains("database is unknown"));
		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.Oracle, new SQLException("ORA-12514, TNS:listener does not currently know of service", null, 12514), null).contains("\"/servicename\""));
		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.MySQL, new SQLException("Public Key Retrieval is not allowed", "08001"), null).contains("-secure"));
		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.Oracle, new Exception("IO Error: PKIX path building failed"), null).contains("createtruststore"));
		assertTrue(DbUtilities.getConnectionErrorHelpText(DbVendor.MariaDB, new ClassNotFoundException("org.mariadb.jdbc.Driver"), null).contains(DbUtilities.DOWNLOAD_LOCATION_MARIADB));

		final String hostHelp = DbUtilities.getConnectionErrorHelpText(DbVendor.PostgreSQL, new UnknownHostException("dbserver"), "Cannot resolve hostname 'dbserver'");
		assertTrue(hostHelp.contains("hostname cannot be resolved"), hostHelp);
		final String serverHelp = DbUtilities.getConnectionErrorHelpText(DbVendor.PostgreSQL, new Exception("Connection refused"), "Cannot connect to host 'dbserver' on port 5433: ConnectException: Connection refused");
		assertTrue(serverHelp.contains("cannot be reached") && serverHelp.contains("5432"), serverHelp);

		// The same cause in several exceptions of the chain is only reported once
		final SQLException loginException = new SQLException("Login failed for user 'sa'", "S0001", 18456);
		final String singleHint = DbUtilities.getConnectionErrorHelpText(DbVendor.MsSQL, new Exception("Login failed for user 'sa'", loginException), null);
		assertEquals(singleHint.indexOf("Hint:"), singleHint.lastIndexOf("Hint:"), singleHint);
		assertFalse(singleHint.isEmpty());
	}

	@Test
	public void testMissingDriverHelpText() {
		// The Firebird JDBC driver is not part of the test classpath
		try {
			DbUtilities.createConnection(new DbConnectionDefinition(DbVendor.Firebird, "host", "db", "u", "p".toCharArray()), false);
			throw new AssertionError("Exception expected");
		} catch (final Exception e) {
			assertTrue(e.getMessage().startsWith("Cannot create database connection, caused by unknown DriverClassName"), e.getMessage());
			assertTrue(e.getMessage().contains("\nHint: The JDBC driver of Firebird is not available") && e.getMessage().contains(DbUtilities.DOWNLOAD_LOCATION_FIREBIRD), e.getMessage());
		}
	}

	@Test
	public void testUnknownColumnsAreNotReported() throws Exception {
		// Column "flag" is missing in the metadata, so its default value is unknown
		final FakeDatabase database = createPostgreSqlCustomerDatabase(Integer.valueOf(0),
				new Object[] { null, "public", "customer", "name", null, "NO", "NO" });
		final List<String> missingColumns = DbUtilities.getMissingNotNullColumnsForInsert(database.connection(), "customer", Arrays.asList("code"), null, false);
		assertEquals(Arrays.asList("name"), missingColumns);
	}
}
