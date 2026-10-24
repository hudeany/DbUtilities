package de.soderer.utilities.db;

import static de.soderer.utilities.db.FakeJdbc.error;
import static de.soderer.utilities.db.FakeJdbc.rows;
import static de.soderer.utilities.db.FakeJdbc.update;
import static de.soderer.utilities.db.FakeJdbc.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.DatabaseConstraint.ConstraintType;
import de.soderer.utilities.db.FakeJdbc.FakeDatabase;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbVendor;

/**
 * Tests of the vendor specific metadata queries of {@link DbUtilities} against the scriptable {@link FakeJdbc}
 * database, so that the SQL of all vendors is covered without real database servers.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesVendorMetadataTest {
	private static final String[] PRODUCT_NAMES = { "Oracle", "MySQL", "MariaDB", "PostgreSQL", "SQLite", "Apache Derby", "HSQL Database Engine", "Firebird", "Cassandra", "Microsoft SQL Server" };

	private static Connection connection(final FakeDatabase database) {
		return database.connection();
	}

	@Test
	public void testGetDbVendor() throws Exception {
		for (final String productName : PRODUCT_NAMES) {
			final DbVendor dbVendor = DbUtilities.getDbVendor(new FakeDatabase(productName).connection());
			assertTrue(productName.toLowerCase().contains(dbVendor.name().toLowerCase().replace("mssql", "microsoft")), productName + " -> " + dbVendor);
		}
		assertEquals(DbVendor.HSQL, DbUtilities.getDbVendor(new FakeDatabase("HSQL Database Engine").dataSource()));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.getDbVendor(new FakeDatabase("Unknown DB").connection())).getMessage().startsWith("Unknown database vendor"));
		assertThrows(Exception.class, () -> DbUtilities.getDbVendor(new FakeDatabase(null).connection()));
		assertEquals("Undetectable database vendor", assertThrows(Exception.class, () -> DbUtilities.getDbVendor(new FakeDatabase("MySQL").withoutMetaData().connection())).getMessage());

		assertEquals("jdbc:fake:MySQL", DbUtilities.getDbUrl(new FakeDatabase("MySQL").connection()));
		assertEquals("jdbc:fake:MySQL", DbUtilities.getDbUrl(new FakeDatabase("MySQL").dataSource()));
		assertNull(DbUtilities.getDbUrl(new FakeDatabase("MySQL").withoutMetaData().connection()));
	}

	@Test
	public void testColumnNamesAndTableChecks() throws Exception {
		final FakeDatabase database = new FakeDatabase("MySQL")
				.on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "Name" }))
				.on("SELECT \\* FROM missing WHERE 1 = 0", error("Table does not exist"))
				.on("SELECT COUNT\\(\\*\\) FROM tab", value(Integer.valueOf(42)))
				.on("SELECT COUNT\\(\\*\\) FROM empty", rows(new String[] { "count" }))
				.on("SELECT COUNT\\(\\*\\) FROM missing", error("Table does not exist"));
		final Connection connection = connection(database);

		assertEquals(2, DbUtilities.getColumnNames(connection, "tab").size());
		assertTrue(DbUtilities.getColumnNames(database.dataSource(), "tab").contains("name"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnNames((Connection) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnNames(connection, " "));
		assertThrows(Exception.class, () -> DbUtilities.getColumnNames((javax.sql.DataSource) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnNames(database.dataSource(), "missing"));

		assertTrue(DbUtilities.checkTableExist(connection, "tab"));
		assertFalse(DbUtilities.checkTableExist(connection, "missing"));
		assertEquals("Table 'missing' does not exist", assertThrows(Exception.class, () -> DbUtilities.checkTableExist(connection, "missing", true)).getMessage());

		assertTrue(DbUtilities.checkTableAndColumnsExist(connection, "tab", "ID", "`name`", null));
		assertFalse(DbUtilities.checkTableAndColumnsExist(connection, "tab", "other"));
		assertTrue(DbUtilities.checkTableAndColumnsExist(connection, "tab", (String[]) null));
		assertTrue(DbUtilities.checkTableAndColumnsExist(database.dataSource(), "tab", true, "id"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.checkTableAndColumnsExist(connection, "tab", true, "other")).getMessage().contains("does not exist"));

		assertTrue(DbUtilities.containsColumnName(database.dataSource(), "tab", " NAME "));
		assertFalse(DbUtilities.containsColumnName(database.dataSource(), "tab", "other"));
		assertThrows(Exception.class, () -> DbUtilities.containsColumnName(null, "tab", "id"));
		assertThrows(Exception.class, () -> DbUtilities.containsColumnName(database.dataSource(), " ", "id"));
		assertThrows(Exception.class, () -> DbUtilities.containsColumnName(database.dataSource(), "tab", " "));

		assertEquals(42, DbUtilities.getTableEntriesCount(connection, "tab"));
		assertEquals(0, DbUtilities.getTableEntriesCount(connection, "empty"));
		assertThrows(Exception.class, () -> DbUtilities.getTableEntriesCount(connection, " "));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.getTableEntriesCount(connection, "missing")).getMessage().startsWith("Cannot count lines of table 'missing'"));
	}

	@Test
	public void testColumnDataTypesOracleMysqlMariaDb() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle").on("FROM user_tab_columns", rows(new String[] { "column_name", "data_type", "data_length", "data_precision", "data_scale", "nullable" },
				new Object[] { "NAME", "VARCHAR2", Integer.valueOf(100), null, null, "Y" },
				new Object[] { "ID", "NUMBER", Integer.valueOf(22), Integer.valueOf(10), Integer.valueOf(0), "N" }));
		final Map<String, DbColumnType> oracleTypes = DbUtilities.getColumnDataTypes(oracle.connection(), "tab");
		assertEquals(100, oracleTypes.get("name").getCharacterByteSize());
		assertEquals(-1, oracleTypes.get("name").getNumericPrecision());
		assertTrue(oracleTypes.get("name").isNullable());
		assertEquals(10, oracleTypes.get("id").getNumericPrecision());
		assertFalse(oracleTypes.get("id").isNullable());
		assertEquals("tab", oracle.getExecutedParameters().get(0).get(0));

		for (final String productName : new String[] { "MySQL", "MariaDB" }) {
			final FakeDatabase database = new FakeDatabase(productName).on("FROM information_schema.columns", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "extra" },
					new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", "auto_increment" },
					new Object[] { "name", "varchar", Long.valueOf(50), null, null, "YES", "" }));
			final Map<String, DbColumnType> types = DbUtilities.getColumnDataTypes(database.connection(), "tab");
			assertTrue(types.get("id").isAutoIncrement());
			assertEquals(-1, types.get("id").getCharacterByteSize());
			assertEquals(50, types.get("name").getCharacterByteSize());
			assertEquals(-1, types.get("name").getNumericScale());
			assertTrue(types.get("name").isNullable());
		}
		assertThrows(Exception.class, () -> DbUtilities.getColumnDataTypes((Connection) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDataTypes(oracle.connection(), " "));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDataTypes((javax.sql.DataSource) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDataTypes(oracle.dataSource(), " "));
		assertEquals(2, DbUtilities.getColumnDataTypes(oracle.dataSource(), "tab").size());
	}

	@Test
	public void testColumnDataTypesHsqlDerbyFirebird() throws Exception {
		final FakeDatabase hsql = new FakeDatabase("HSQL Database Engine").on("system_columns", rows(new String[] { "column_name", "type_name", "column_size", "decimal_digits", "is_nullable", "is_autoincrement" },
				new Object[] { "ID", "INTEGER", Long.valueOf(32), Integer.valueOf(0), "NO", "YES" },
				new Object[] { "TXT", "VARCHAR", null, null, "YES", "NO" }));
		final Map<String, DbColumnType> hsqlTypes = DbUtilities.getColumnDataTypes(hsql.connection(), "tab");
		assertTrue(hsqlTypes.get("id").isAutoIncrement());
		assertEquals(-1, hsqlTypes.get("txt").getCharacterByteSize());

		final FakeDatabase derby = new FakeDatabase("Apache Derby").on("FROM sys.systables, sys.syscolumns", rows(new String[] { "columnname", "columndatatype", "autoincrementvalue" },
				new Object[] { "ID", "INTEGER NOT NULL", "1" },
				new Object[] { "NAME", "VARCHAR(30)", null },
				new Object[] { "AMOUNT", "DECIMAL(10,2)", null },
				new Object[] { "BROKEN", "VARCHAR(x)", null }));
		final Map<String, DbColumnType> derbyTypes = DbUtilities.getColumnDataTypes(derby.connection(), "tab");
		assertEquals("INTEGER", derbyTypes.get("id").getTypeName());
		assertFalse(derbyTypes.get("id").isNullable());
		assertTrue(derbyTypes.get("id").isAutoIncrement());
		assertEquals(30, derbyTypes.get("name").getCharacterByteSize());
		assertEquals(10, derbyTypes.get("amount").getNumericPrecision());
		assertEquals(2, derbyTypes.get("amount").getNumericScale());
		assertEquals(-1, derbyTypes.get("broken").getCharacterByteSize());

		final String[] firebirdColumns = { "rdb$field_name", "rdb$field_type", "rdb$field_sub_type", "rdb$field_length", "rdb$field_scale", "rdb$null_flag" };
		final Object[][] firebirdRows = new Object[][] {
				{ "C7 ", Integer.valueOf(7), Integer.valueOf(0), Long.valueOf(2), Integer.valueOf(0), Integer.valueOf(1) },
				{ "C8", Integer.valueOf(8), Integer.valueOf(0), Long.valueOf(4), Integer.valueOf(0), null },
				{ "C10", Integer.valueOf(10), Integer.valueOf(0), null, null, null },
				{ "C12", Integer.valueOf(12), Integer.valueOf(0), Long.valueOf(4), Integer.valueOf(0), null },
				{ "C13", Integer.valueOf(13), Integer.valueOf(0), Long.valueOf(4), Integer.valueOf(0), null },
				{ "C14", Integer.valueOf(14), Integer.valueOf(0), Long.valueOf(4), Integer.valueOf(0), null },
				{ "C16", Integer.valueOf(16), Integer.valueOf(0), Long.valueOf(8), Integer.valueOf(0), null },
				{ "C27", Integer.valueOf(27), Integer.valueOf(0), Long.valueOf(8), Integer.valueOf(0), null },
				{ "C35", Integer.valueOf(35), Integer.valueOf(0), Long.valueOf(8), Integer.valueOf(0), null },
				{ "C37", Integer.valueOf(37), Integer.valueOf(0), Long.valueOf(40), Integer.valueOf(0), null },
				{ "C261A", Integer.valueOf(261), Integer.valueOf(1), Long.valueOf(8), Integer.valueOf(0), null },
				{ "C261B", Integer.valueOf(261), Integer.valueOf(0), Long.valueOf(8), Integer.valueOf(0), null },
				{ "C1", Integer.valueOf(1), Integer.valueOf(0), Long.valueOf(8), Integer.valueOf(0), null } };
		final FakeDatabase firebird = new FakeDatabase("Firebird").on("FROM rdb\\$fields", rows(firebirdColumns, firebirdRows));
		final Map<String, DbColumnType> firebirdTypes = DbUtilities.getColumnDataTypes(firebird.connection(), "tab");
		assertEquals("SMALLINT", firebirdTypes.get("c7").getTypeName());
		assertFalse(firebirdTypes.get("c7").isNullable());
		assertEquals("INTEGER", firebirdTypes.get("c8").getTypeName());
		assertEquals("FLOAT", firebirdTypes.get("c10").getTypeName());
		assertEquals(-1, firebirdTypes.get("c10").getCharacterByteSize());
		assertEquals("DATE", firebirdTypes.get("c12").getTypeName());
		assertEquals("TIME", firebirdTypes.get("c13").getTypeName());
		assertEquals("CHAR", firebirdTypes.get("c14").getTypeName());
		assertEquals("BIGINT", firebirdTypes.get("c16").getTypeName());
		assertEquals("DOUBLE PRECISION", firebirdTypes.get("c27").getTypeName());
		assertEquals("TIMESTAMP", firebirdTypes.get("c35").getTypeName());
		assertEquals("VARCHAR", firebirdTypes.get("c37").getTypeName());
		assertEquals("CLOB", firebirdTypes.get("c261a").getTypeName());
		assertEquals("BLOB", firebirdTypes.get("c261b").getTypeName());
		assertEquals("CHAR", firebirdTypes.get("c1").getTypeName());
		assertEquals("TAB", firebird.getExecutedParameters().get(0).get(0));
	}

	@Test
	public void testColumnDataTypesSqlitePostgresCassandraMsSql() throws Exception {
		final FakeDatabase sqlite = new FakeDatabase("SQLite")
				.on("SELECT \\* FROM sqlite_sequence WHERE 1 = 0", rows(new String[] { "name", "seq" }))
				.on("FROM sqlite_sequence WHERE", value(Integer.valueOf(1)))
				.on("PRAGMA table_info\\(tab\\)", rows(new String[] { "cid", "name", "type", "notnull", "dflt_value", "pk" },
						new Object[] { Integer.valueOf(0), "id", "INTEGER", Integer.valueOf(1), null, Integer.valueOf(1) },
						new Object[] { Integer.valueOf(1), "name", "VARCHAR(20)", Integer.valueOf(0), null, Integer.valueOf(0) },
						new Object[] { Integer.valueOf(2), "untyped", null, Integer.valueOf(0), null, Integer.valueOf(0) }));
		final Map<String, DbColumnType> sqliteTypes = DbUtilities.getColumnDataTypes(sqlite.connection(), "tab");
		assertTrue(sqliteTypes.get("id").isAutoIncrement());
		assertFalse(sqliteTypes.get("name").isAutoIncrement());
		assertEquals(20, sqliteTypes.get("name").getCharacterByteSize());
		assertEquals("", sqliteTypes.get("untyped").getTypeName());

		final String[] postgresColumns = { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "column_default" };
		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("FROM information_schema.columns", rows(postgresColumns,
				new Object[] { "id", "integer", null, Integer.valueOf(32), Integer.valueOf(0), "NO", "nextval('tab_id_seq'::regclass)" },
				new Object[] { "name", "character varying", Long.valueOf(30), null, null, "YES", null }));
		final Map<String, DbColumnType> postgresTypes = DbUtilities.getColumnDataTypes(postgres.connection(), "tab");
		assertTrue(postgresTypes.get("id").isAutoIncrement());
		assertFalse(postgresTypes.get("name").isAutoIncrement());
		DbUtilities.getColumnDataTypes(postgres.connection(), "app.tab");
		assertTrue(postgres.executed("LOWER\\(table_schema\\) = LOWER\\(\\?\\) AND LOWER\\(table_name\\)"));
		assertEquals(List.of("app", "tab"), postgres.getExecutedParameters().get(postgres.getExecutedParameters().size() - 1));

		final String[] cassandraColumns = { "keyspace_name", "column_name", "kind", "type" };
		final FakeDatabase cassandra = new FakeDatabase("Cassandra")
				.on("WHERE keyspace_name = \\? AND table_name = \\?", rows(cassandraColumns, new Object[] { "ks", "id", "partition_key", "int" }))
				.on("ALLOW FILTERING", rows(cassandraColumns, new Object[] { "ks", "id", "partition_key", "int" }, new Object[] { "ks", "value", "regular", "text" }));
		final Map<String, DbColumnType> cassandraTypes = DbUtilities.getColumnDataTypes(cassandra.connection(), "ks.tab");
		assertFalse(cassandraTypes.get("id").isNullable());
		assertTrue(DbUtilities.getColumnDataTypes(cassandra.connection(), "tab").get("value").isNullable());
		final FakeDatabase ambiguousCassandra = new FakeDatabase("Cassandra")
				.on("ALLOW FILTERING", rows(cassandraColumns, new Object[] { "ks1", "id", "partition_key", "int" }, new Object[] { "ks2", "id", "partition_key", "int" }));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.getColumnDataTypes(ambiguousCassandra.connection(), "tab")).getMessage().startsWith("Multiple tables"));

		final FakeDatabase msSql = new FakeDatabase("Microsoft SQL Server").on("COLUMNPROPERTY", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "is_identity" },
				new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", Integer.valueOf(1) },
				new Object[] { "name", "varchar", Long.valueOf(10), null, null, "YES", Integer.valueOf(0) }));
		final Map<String, DbColumnType> msSqlTypes = DbUtilities.getColumnDataTypes(msSql.connection(), "tab");
		assertTrue(msSqlTypes.get("id").isAutoIncrement());
		assertFalse(msSqlTypes.get("name").isAutoIncrement());
	}

	@Test
	public void testColumnDefaultValue() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle").on("SELECT data_default FROM user_tab_cols", value("'abc'"), value("null"), value("SYSDATE"), rows(new String[] { "x" }), value(null));
		assertEquals("abc", DbUtilities.getColumnDefaultValue(oracle.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(oracle.connection(), "tab", "col"));
		assertEquals("SYSDATE", DbUtilities.getColumnDefaultValue(oracle.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(oracle.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(oracle.connection(), "tab", "col"));
		assertEquals(List.of("TAB", "COL"), oracle.getExecutedParameters().get(0));

		final FakeDatabase hsql = new FakeDatabase("HSQL Database Engine").on("column_default", value("NULL"), value("5"), rows(new String[] { "x" }));
		assertNull(DbUtilities.getColumnDefaultValue(hsql.connection(), "tab", "col"));
		assertEquals("5", DbUtilities.getColumnDefaultValue(hsql.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(hsql.connection(), "tab", "col"));

		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("column_default", value("'7'::integer"), value("12::integer"), value("NULL"), value("'x'::character varying"), rows(new String[] { "x" }));
		assertEquals("7", DbUtilities.getColumnDefaultValue(postgres.connection(), "tab", "col"));
		assertEquals("12", DbUtilities.getColumnDefaultValue(postgres.connection(), "app.tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(postgres.connection(), "tab", "col"));
		assertEquals("'x'::character varying", DbUtilities.getColumnDefaultValue(postgres.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(postgres.connection(), "tab", "col"));
		assertEquals(List.of("app", "tab", "col"), postgres.getExecutedParameters().get(1));

		final FakeDatabase mysql = new FakeDatabase("MySQL").on("column_default", value("NULL"), value("x"), rows(new String[] { "x" }));
		assertNull(DbUtilities.getColumnDefaultValue(mysql.dataSource(), "tab", "col"));
		assertEquals("x", DbUtilities.getColumnDefaultValue(mysql.connection(), "tab", "col"));
		assertNull(DbUtilities.getColumnDefaultValue(mysql.connection(), "tab", "col"));

		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue((Connection) null, "tab", "col"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue(mysql.connection(), " ", "col"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue(mysql.connection(), "tab", " "));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue((javax.sql.DataSource) null, "tab", "col"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue(mysql.dataSource(), " ", "col"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValue(mysql.dataSource(), "tab", " "));
	}

	@Test
	public void testColumnDefaultValues() throws Exception {
		final String[] columns = { "column_name", "default_value" };
		final FakeDatabase oracle = new FakeDatabase("Oracle").on("user_tab_cols", rows(columns, new Object[] { "A", "'x'" }, new Object[] { "B", null }, new Object[] { "C", "0" }));
		final Map<String, Object> oracleDefaults = DbUtilities.getColumnDefaultValues(oracle.connection(), "tab");
		assertEquals("x", oracleDefaults.get("a"));
		assertTrue(oracleDefaults.containsKey("b"));
		assertNull(oracleDefaults.get("b"));
		assertEquals("0", oracleDefaults.get("c"));

		final FakeDatabase hsql = new FakeDatabase("HSQL Database Engine").on("column_default", rows(columns, new Object[] { "A", "NULL" }, new Object[] { "B", "1" }));
		final Map<String, Object> hsqlDefaults = DbUtilities.getColumnDefaultValues(hsql.connection(), "tab");
		assertNull(hsqlDefaults.get("a"));
		assertEquals("1", hsqlDefaults.get("b"));

		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("column_default", rows(columns, new Object[] { "A", "'5'::integer" }, new Object[] { "B", "NULL" }, new Object[] { "C", "now()" }));
		final Map<String, Object> postgresDefaults = DbUtilities.getColumnDefaultValues(postgres.connection(), "app.tab");
		assertEquals(Integer.valueOf(5), postgresDefaults.get("a"));
		assertNull(postgresDefaults.get("b"));
		assertEquals("now()", postgresDefaults.get("c"));
		assertEquals(1, DbUtilities.getColumnDefaultValues(new FakeDatabase("PostgreSQL").on("column_default", rows(columns, new Object[] { "A", "1::integer" })).connection(), "tab").size());

		final FakeDatabase mysql = new FakeDatabase("MySQL").on("column_default", rows(columns, new Object[] { "A", "NULL" }, new Object[] { "B", "x" }));
		final Map<String, Object> mysqlDefaults = DbUtilities.getColumnDefaultValues(mysql.dataSource(), "tab");
		assertNull(mysqlDefaults.get("a"));
		assertEquals("x", mysqlDefaults.get("b"));

		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValues((Connection) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValues(mysql.connection(), " "));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValues((javax.sql.DataSource) null, "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getColumnDefaultValues(mysql.dataSource(), " "));
	}

	@Test
	public void testDateDefaultValueAndAlterColumns() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "existing" }));
		final FakeDatabase mysql = new FakeDatabase("MySQL").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "existing" }));
		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "existing" }));

		assertEquals("current_timestamp", DbUtilities.getDateDefaultValue(oracle.dataSource(), "SYSDATE"));
		assertEquals("current_timestamp", DbUtilities.getDateDefaultValue(mysql.dataSource(), "sysdate()"));
		assertEquals("to_date('01.02.2024', 'DD.MM.YYYY')", DbUtilities.getDateDefaultValue(oracle.dataSource(), "01.02.2024"));
		assertEquals("'2024-02-01'", DbUtilities.getDateDefaultValue(mysql.dataSource(), "2024-02-01"));
		assertThrows(Exception.class, () -> DbUtilities.getDateDefaultValue(postgres.dataSource(), "current_timestamp"));
		assertThrows(Exception.class, () -> DbUtilities.getDateDefaultValue(postgres.dataSource(), "2024-02-01"));

		assertTrue(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "Name", "varchar", 0, "it's", true));
		assertEquals("ALTER TABLE tab ADD (name VARCHAR(100) DEFAULT 'it''s' NOT NULL)", oracle.findExecuted("ALTER TABLE tab ADD \\(name"));
		assertTrue(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "created", "date", 0, "sysdate", false));
		assertTrue(oracle.executed("ADD \\(created DATE DEFAULT current_timestamp\\)"));
		assertTrue(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "amount", "number", 0, "0", false));
		assertTrue(oracle.executed("ADD \\(amount NUMBER DEFAULT 0\\)"));
		assertFalse(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "existing", "number", 0, null, false));
		assertFalse(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", " ", "number", 0, null, false));
		assertFalse(DbUtilities.addColumnToDbTable(oracle.dataSource(), "ta'b", "x", "number", 0, null, false));
		assertFalse(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "x'y", "number", 0, null, false));
		assertFalse(DbUtilities.addColumnToDbTable(oracle.dataSource(), "tab", "x", " ", 0, null, false));
		final FakeDatabase failingOracle = new FakeDatabase("Oracle").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id" })).on("ALTER TABLE", error("failed"));
		assertFalse(DbUtilities.addColumnToDbTable(failingOracle.dataSource(), "tab", "x", "varchar", 10, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(failingOracle.dataSource(), "tab", "id", "varchar", 10, null, false));

		assertTrue(DbUtilities.alterColumnTypeInDbTable(oracle.dataSource(), "tab", "existing", "varchar", 0, "a'b", true));
		assertEquals("ALTER TABLE tab MODIFY (existing VARCHAR(100) DEFAULT 'a''b' NOT NULL)", oracle.findExecuted("MODIFY \\(existing"));
		assertTrue(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", "existing", "date", 0, "2024-01-01", false));
		assertEquals("ALTER TABLE tab MODIFY existing DATE DEFAULT '2024-01-01'", mysql.findExecuted("MODIFY existing"));
		assertTrue(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", "existing", "int", 0, "1", false));
		assertThrows(Exception.class, () -> DbUtilities.alterColumnTypeInDbTable(postgres.dataSource(), "tab", "existing", "int", 0, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", "missing", "int", 0, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", " ", "int", 0, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "t'ab", "existing", "int", 0, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", "a'b", "int", 0, null, false));
		assertFalse(DbUtilities.alterColumnTypeInDbTable(mysql.dataSource(), "tab", "existing", null, 0, null, false));
	}

	@Test
	public void testTablespaceAndPrimaryKeys() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle").on("dba_tablespaces", value(Integer.valueOf(1)), value(Integer.valueOf(0)), error("no privilege"))
				.on("META getPrimaryKeys TAB", rows(new String[] { "COLUMN_NAME" }, new Object[] { "ID" }, new Object[] { "SUB_ID" }))
				.on("META getPrimaryKeys APP.TAB", error("broken metadata"));
		assertTrue(DbUtilities.checkOracleTablespaceExists(oracle.connection(), "USERS"));
		assertFalse(DbUtilities.checkOracleTablespaceExists(oracle.dataSource(), "USERS"));
		assertThrows(Exception.class, () -> DbUtilities.checkOracleTablespaceExists(oracle.connection(), "USERS"));
		assertFalse(DbUtilities.checkOracleTablespaceExists(oracle.connection(), null));
		assertFalse(DbUtilities.checkOracleTablespaceExists(new FakeDatabase("MySQL").connection(), "USERS"));

		assertEquals(2, DbUtilities.getPrimaryKeyColumns(oracle.connection(), "tab").size());
		assertTrue(DbUtilities.getPrimaryKeyColumns(oracle.dataSource(), "tab").contains("sub_id"));
		assertNull(DbUtilities.getPrimaryKeyColumns(oracle.connection(), " "));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.getPrimaryKeyColumns(oracle.connection(), "app.tab")).getMessage().startsWith("Cannot read primarykey columns"));

		final FakeDatabase cassandra = new FakeDatabase("Cassandra")
				.on("keyspace_name = \\? AND table_name = \\?", rows(new String[] { "column_name" }, new Object[] { "id" }))
				.on("ALLOW FILTERING", rows(new String[] { "keyspace_name", "column_name" }, new Object[] { "ks", "id" }), rows(new String[] { "keyspace_name", "column_name" }, new Object[] { "ks1", "id" }, new Object[] { "ks2", "id" }));
		assertTrue(DbUtilities.getPrimaryKeyColumns(cassandra.connection(), "ks.tab").contains("id"));
		assertTrue(DbUtilities.getPrimaryKeyColumns(cassandra.connection(), "tab").contains("id"));
		assertThrows(Exception.class, () -> DbUtilities.getPrimaryKeyColumns(cassandra.connection(), "tab"));
	}

	@Test
	public void testAvailableTables() throws Exception {
		final String pattern = "app*, !app_tmp?;other_%";

		final FakeDatabase oracle = new FakeDatabase("Oracle").on("FROM all_tables", rows(new String[] { "table_name" }, new Object[] { "APP1" }));
		assertEquals(List.of("APP1"), DbUtilities.getAvailableTables(oracle.connection(), pattern));
		assertTrue(oracle.executed("table_name LIKE 'APP%' ESCAPE '\\\\' AND table_name NOT LIKE 'APP\\\\_TMP_' ESCAPE '\\\\' AND table_name LIKE 'OTHER\\\\_\\\\%'"));

		for (final String productName : new String[] { "MySQL", "MariaDB" }) {
			final FakeDatabase database = new FakeDatabase(productName).on("FROM information_schema.tables", rows(new String[] { "table_schema", "table_name" }, new Object[] { "db", "app1" }, new Object[] { "", "app2" }));
			assertEquals(List.of("db.app1", "app2"), DbUtilities.getAvailableTables(database.connection(), "app*, other.x?, !app_tmp, !db.old*"));
			assertTrue(database.executed("WHERE \\(\\(table_schema = SCHEMA\\(\\) AND table_name LIKE 'app%' ESCAPE '\\\\'\\) OR \\(table_schema = 'other' AND table_name LIKE 'x_' ESCAPE '\\\\'\\)\\)"
					+ " AND NOT \\(table_schema = SCHEMA\\(\\) AND table_name LIKE 'app\\\\_tmp' ESCAPE '\\\\'\\) AND NOT \\(table_schema = 'db' AND table_name LIKE 'old%' ESCAPE '\\\\'\\)"));
			DbUtilities.getAvailableTables(database.connection(), "!tmp*");
			assertTrue(database.executed("tables WHERE NOT \\(table_schema = SCHEMA\\(\\)"));
			DbUtilities.getAvailableTables(database.connection(), "");
			assertTrue(database.executed("information_schema.tables ORDER BY"));
		}

		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("FROM information_schema.tables", rows(new String[] { "table_schema", "table_name" }, new Object[] { "public", "app1" }, new Object[] { "app", "app2" }));
		assertEquals(List.of("app1", "app.app2"), DbUtilities.getAvailableTables(postgres.connection(), "app*, s.x, !tmp*, !s.old"));
		assertTrue(postgres.executed("AND table_name NOT LIKE 'tmp%' ESCAPE '\\\\' AND NOT \\(table_schema = 's' AND table_name LIKE 'old' ESCAPE '\\\\'\\) AND \\(table_name LIKE 'app%'"));

		final FakeDatabase sqlite = new FakeDatabase("SQLite").on("FROM sqlite_master", rows(new String[] { "name" }, new Object[] { "app1" }));
		assertEquals(List.of("app1"), DbUtilities.getAvailableTables(sqlite.connection(), pattern));
		assertTrue(sqlite.executed("name NOT LIKE 'APP\\\\_TMP_'"));

		final FakeDatabase derby = new FakeDatabase("Apache Derby").on("FROM sys.systables", rows(new String[] { "tablename" }, new Object[] { "APP1" }));
		assertEquals(List.of("APP1"), DbUtilities.getAvailableTables(derby.connection(), pattern));
		assertTrue(derby.executed("tablename NOT LIKE 'APP\\\\_TMP_' \\{ESCAPE '\\\\'\\}"));

		final FakeDatabase firebird = new FakeDatabase("Firebird").on("FROM rdb\\$relations", rows(new String[] { "table_name" }, new Object[] { "APP1" }));
		assertEquals(List.of("APP1"), DbUtilities.getAvailableTables(firebird.connection(), pattern));
		assertTrue(firebird.executed("TRIM\\(rdb\\$relation_name\\) NOT LIKE"));

		final FakeDatabase hsql = new FakeDatabase("HSQL Database Engine").on("system_tables", rows(new String[] { "table_name" }, new Object[] { "APP1" }));
		assertEquals(List.of("APP1"), DbUtilities.getAvailableTables(hsql.connection(), pattern));
		assertTrue(hsql.executed("table_name NOT LIKE 'APP\\\\_TMP_'"));

		final FakeDatabase cassandra = new FakeDatabase("Cassandra").on("system_schema.tables", rows(new String[] { "keyspace_name", "table_name" },
				new Object[] { "system", "local" }, new Object[] { "ks", "app1" }, new Object[] { "ks", "app_tmp1" }, new Object[] { "ks", "other_x" }, new Object[] { "ks", "unmatched" }));
		assertEquals(List.of("app1", "other_x"), DbUtilities.getAvailableTables(cassandra.connection(), pattern));
		assertEquals(List.of("app1", "other_x"), DbUtilities.getAvailableTables(cassandra.connection(), "!app_tmp*, !unmatched"));

		final FakeDatabase msSql = new FakeDatabase("Microsoft SQL Server").on("FROM information_schema.tables", rows(new String[] { "table_name" }, new Object[] { "app1" }));
		assertEquals(List.of("app1"), DbUtilities.getAvailableTables(msSql.connection(), pattern));
		assertTrue(msSql.executed("WHERE table_name LIKE 'app%' ESCAPE '\\\\' AND table_name NOT LIKE 'app\\\\_tmp_'"));
	}

	@Test
	public void testIndexChecksAndNames() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle")
				.on("FROM user_ind_columns WHERE LOWER\\(table_name\\) = \\? AND", value(Integer.valueOf(1)), value(Integer.valueOf(1)), value(Integer.valueOf(0)))
				.on("SELECT index_name FROM user_ind_columns", rows(new String[] { "index_name" }, new Object[] { "IX_A" }, new Object[] { "IX_B" }))
				.on("SUM\\(bytes\\)", value(Long.valueOf(4096)), rows(new String[] { "x" }));
		assertTrue(DbUtilities.checkForIndex(oracle.connection(), "\"TAB\"", List.of("a")).booleanValue());
		assertFalse(DbUtilities.checkForIndex(oracle.connection(), "tab", List.of("a", "b")).booleanValue());
		assertEquals(List.of("IX_A", "IX_B"), DbUtilities.getTableIndexNames(oracle.connection(), "tab"));
		assertEquals(4096, DbUtilities.getTableStorageSize(oracle.connection(), "tab"));
		assertEquals(List.of("TAB", "TAB", "IX_A", "IX_B"), oracle.getExecutedParameters().get(oracle.getExecutedSql().size() - 1));
		assertThrows(Exception.class, () -> DbUtilities.getTableStorageSize(oracle.connection(), "tab"));

		final FakeDatabase mysql = new FakeDatabase("MySQL")
				.on("information_schema.statistics", value(Integer.valueOf(1)), rows(new String[] { "x" }))
				.on("SHOW INDEX FROM tab", rows(new String[] { "Key_name" }, new Object[] { "PRIMARY" }, new Object[] { "PRIMARY" }, new Object[] { "ix_name" }))
				.on("data_length \\+ data_free", value(Long.valueOf(16384)), rows(new String[] { "x" }));
		assertTrue(DbUtilities.checkForIndex(mysql.connection(), "db.`tab`", List.of("`a`")).booleanValue());
		assertEquals(List.of("db", "tab", "a"), mysql.getExecutedParameters().get(mysql.getExecutedParameters().size() - 1));
		assertFalse(DbUtilities.checkForIndex(mysql.connection(), "tab", List.of("a")).booleanValue());
		assertEquals(List.of("PRIMARY", "ix_name"), DbUtilities.getTableIndexNames(mysql.connection(), "TAB"));
		assertEquals(16384, DbUtilities.getTableStorageSize(mysql.connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getTableStorageSize(mysql.connection(), "tab"));

		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("FROM pg_indexes", rows(new String[] { "indexname" }, new Object[] { "TAB_PKEY" }));
		assertEquals(List.of("tab_pkey"), DbUtilities.getTableIndexNames(postgres.connection(), "tab"));
		assertEquals(List.of("tab_pkey"), DbUtilities.getTableIndexNames(postgres.connection(), "app.tab"));
		assertNull(DbUtilities.checkForIndex(postgres.connection(), "tab", List.of("a")));
		assertThrows(Exception.class, () -> DbUtilities.getTableStorageSize(postgres.connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getTableIndexNames(new FakeDatabase("SQLite").connection(), "tab"));
		assertThrows(RuntimeException.class, () -> DbUtilities.checkForIndex(new FakeDatabase("Cassandra").connection(), "tab", List.of("a")));
	}

	@Test
	public void testMysqlVariablesAndLargeQueryStatement() throws Exception {
		final FakeDatabase mysql = new FakeDatabase("MySQL")
				.on("SHOW VARIABLES like 'max_allowed_packet'", rows(new String[] { "variable_name", "value" }, new Object[] { "max_allowed_packet", "1048576" }), rows(new String[] { "variable_name", "value" }, new Object[] { "x", null }), rows(new String[] { "x" }))
				.on("SHOW VARIABLES WHERE variable_name", rows(new String[] { "variable_name", "value" }, new Object[] { "max_allowed_packet", Long.valueOf(2048) }), rows(new String[] { "variable_name", "value" }, new Object[] { "x", null }), rows(new String[] { "x" }));
		final Connection connection = mysql.connection();
		assertEquals(1048576, DbUtilities.getMysqlMaxAllowedPacketSize(connection));
		assertEquals(0, DbUtilities.getMysqlMaxAllowedPacketSize(connection));
		assertEquals(0, DbUtilities.getMysqlMaxAllowedPacketSize(connection));
		assertEquals(2048, DbUtilities.getMysqlConnectionNumericVariable(connection, "max_allowed_packet"));
		assertEquals(0, DbUtilities.getMariaDBConnectionNumericVariable(connection, "x"));
		assertEquals(0, DbUtilities.getMariaDBConnectionNumericVariable(connection, "x"));
		assertEquals(0, DbUtilities.getMysqlConnectionNumericVariable(connection, "x"));

		DbUtilities.setMaysqlDBConnectionNumericVariable(connection, "max_allowed_packet", 4096);
		DbUtilities.setMariaDBConnectionNumericVariable(connection, "max_allowed_packet", 8192);
		assertEquals(2, mysql.getExecutedSql().stream().filter(sql -> sql.equals("SET GLOBAL max_allowed_packet = ?")).count());
		assertEquals(List.of(Long.valueOf(8192)), mysql.getExecutedParameters().get(mysql.getExecutedParameters().size() - 1));

		try (Statement statement = DbUtilities.getStatementForLargeQuery(connection)) {
			statement.executeQuery("SELECT 1").close();
		}
		try (Statement statement = DbUtilities.getStatementForLargeQuery(new FakeDatabase("Oracle").connection());
				ResultSet resultSet = statement.executeQuery("SELECT 1")) {
			assertFalse(resultSet.next());
		}
	}

	@Test
	public void testOracleStatisticsAndProcedures() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle")
				.on("SELECT USER FROM DUAL", value("scott"), rows(new String[] { "x" }))
				.on("dbms_stats.gather_table_stats", update(0), error("no privileges"))
				.on("dbms_output.get_line", rows(new String[] { "maxbytes", "done", "buffer" }, new Object[] { null, Integer.valueOf(0), " line1 " }), rows(new String[] { "maxbytes", "done", "buffer" }, new Object[] { null, Integer.valueOf(1), "line2" }));
		DbUtilities.gatherTableStats(oracle.connection(), "\"tab\"");
		assertEquals(List.of("SCOTT", "TAB"), oracle.getExecutedParameters().get(oracle.getExecutedSql().size() - 1));
		assertThrows(Exception.class, () -> DbUtilities.gatherTableStats(oracle.connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.gatherTableStats(oracle.connection(), "tab"));
		DbUtilities.gatherTableStats(new FakeDatabase("MySQL").connection(), "tab");

		final Date date = new Date(0);
		assertEquals("line1line2", DbUtilities.callStoredProcedureWithDbmsOutput(oracle.connection(), "proc", "a", null, date));
		assertTrue(oracle.executed("\\{call proc\\(\\?, \\?, \\?\\)\\}"));
		assertTrue(oracle.getExecutedParameters().stream().anyMatch(parameters -> parameters.size() == 3 && parameters.get(2) instanceof java.sql.Date));
		DbUtilities.callStoredProcedureWithDbmsOutput(oracle.connection(), "proc2", (Object[]) null);
		assertTrue(oracle.executed("\\{call proc2\\(\\)\\}"));
		assertTrue(oracle.executed("dbms_output.disable"));
	}

	@Test
	public void testForeignKeyAndTriggerStatus() throws Exception {
		// The failing "ALTER" statements are reported via printStackTrace(), which is suppressed here
		final java.io.PrintStream originalErr = System.err;
		try (java.io.PrintStream nullStream = new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8)) {
			System.setErr(nullStream);
			checkForeignKeyAndTriggerStatus();
		} finally {
			System.setErr(originalErr);
		}
	}

	private static void checkForeignKeyAndTriggerStatus() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle")
				.on("all_constraints.*status = 'ENABLED'", rows(new String[] { "owner", "table_name", "constraint_name" }, new Object[] { "APP", "TAB", "FK_1" }))
				.on("all_constraints.*status = 'DISABLED' AND UPPER", rows(new String[] { "owner", "table_name", "constraint_name" }, new Object[] { "APP", "TAB", "FK_1" }))
				.on("status != 'ENABLED'", rows(new String[] { "table_name", "constraint_name" }), rows(new String[] { "table_name", "constraint_name" }, new Object[] { "TAB", "FK_2" }))
				.on("ALTER TABLE \"APP\".\"TAB\" ENABLE", error("cannot enable"))
				.on("SELECT trigger_name FROM user_triggers WHERE status = 'ENABLED'", rows(new String[] { "trigger_name" }, new Object[] { "TRG_1" }))
				.on("SELECT trigger_name FROM user_triggers WHERE status = 'DISABLED'", rows(new String[] { "trigger_name" }, new Object[] { "TRG_1" }), rows(new String[] { "trigger_name" }), rows(new String[] { "trigger_name" }, new Object[] { "TRG_1" }))
				.on("ALTER TRIGGER TRG_1 ENABLE", update(0), error("cannot enable"));

		DbUtilities.setForeignKeyConstraintStatus(DbVendor.Oracle, oracle.connection(), false);
		assertTrue(oracle.executed("ALTER TABLE \"APP\".\"TAB\" DISABLE CONSTRAINT \"FK_1\""));
		DbUtilities.setForeignKeyConstraintStatus(DbVendor.Oracle, oracle.connection(), true);
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.setForeignKeyConstraintStatus(DbVendor.Oracle, oracle.connection(), true)).getMessage().contains("FK_2 (TABLE: TAB)"));

		DbUtilities.setTriggerStatus(DbVendor.Oracle, oracle.connection(), false);
		assertTrue(oracle.executed("ALTER TRIGGER TRG_1 DISABLE"));
		DbUtilities.setTriggerStatus(DbVendor.Oracle, oracle.connection(), true);
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.setTriggerStatus(DbVendor.Oracle, oracle.connection(), true)).getMessage().contains("TRG_1"));
		assertThrows(Exception.class, () -> DbUtilities.setTriggerStatus(DbVendor.MySQL, oracle.connection(), true));

		final FakeDatabase mysql = new FakeDatabase("MySQL");
		DbUtilities.setForeignKeyConstraintStatus(DbVendor.MySQL, mysql.connection(), false);
		DbUtilities.setForeignKeyConstraintStatus(DbVendor.MariaDB, mysql.connection(), true);
		assertEquals(List.of("SET FOREIGN_KEY_CHECKS = 0", "SET FOREIGN_KEY_CHECKS = 1"), mysql.getExecutedSql());
		assertThrows(Exception.class, () -> DbUtilities.setForeignKeyConstraintStatus(DbVendor.PostgreSQL, mysql.connection(), true));
	}

	@Test
	public void testForeignKeysConstraintsAndIndices() throws Exception {
		final FakeDatabase oracle = new FakeDatabase("Oracle")
				.on("META getImportedKeys TAB", rows(new String[] { "FK_NAME", "FKCOLUMN_NAME", "PKTABLE_NAME", "PKCOLUMN_NAME" }, new Object[] { "FK_PARENT", "PARENT_ID", "PARENT", "ID" }, new Object[] { null, "X_ID", "X", "ID" }))
				.on("META getImportedKeys APP.TAB", error("broken"))
				.on("FROM user_constraints", rows(new String[] { "constraint_name", "constraint_type", "column_name" }, new Object[] { "PK_TAB", "P", "ID" }, new Object[] { "FK_TAB", "R", "PARENT_ID" }))
				.on("FROM all_ind_columns", rows(new String[] { "index_name", "column_name" }, new Object[] { "IX_B", "B" }, new Object[] { "PRIMARY", "ID" }, new Object[] { "IX_B", "C" }));
		final List<DatabaseForeignKey> foreignKeys = DbUtilities.getForeignKeys(oracle.connection(), "tab");
		assertEquals(2, foreignKeys.size());
		assertEquals("fk_parent: tab(parent_id) references parent(id)", foreignKeys.get(0).toString());
		assertNull(foreignKeys.get(1).getForeignKeyName());
		assertNull(DbUtilities.getForeignKeys(oracle.connection(), " "));
		assertThrows(Exception.class, () -> DbUtilities.getForeignKeys(oracle.connection(), "app.tab"));

		final List<DatabaseConstraint> oracleConstraints = DbUtilities.getConstraints(oracle.connection(), "tab");
		assertEquals(ConstraintType.PrimaryKey, oracleConstraints.get(0).getConstraintType());
		assertEquals("parent_id", oracleConstraints.get(1).getColumnName());
		assertNull(DbUtilities.getConstraints(oracle.connection(), null));

		final List<DatabaseIndex> oracleIndices = DbUtilities.getIndices(oracle.connection(), "tab");
		assertEquals("primary", oracleIndices.get(0).getIndexName());
		assertEquals(List.of("b", "c"), oracleIndices.get(1).getIndexedColumns());
		assertNull(DbUtilities.getIndices(oracle.connection(), ""));

		for (final String productName : new String[] { "MySQL", "MariaDB" }) {
			final FakeDatabase database = new FakeDatabase(productName)
					.on("FROM information_schema.table_constraints WHERE", rows(new String[] { "constraint_name", "constraint_type" }, new Object[] { "PRIMARY", "PRIMARY KEY" }, new Object[] { "chk_value", "CHECK" }))
					.on("FROM information_schema.check_constraints", rows(new String[] { "constraint_name", "check_clause" }, new Object[] { "chk_value", "value > 0" }), error("not supported"))
					.on("SHOW index FROM", rows(new String[] { "key_name", "column_name" }, new Object[] { "PRIMARY", "id" }, new Object[] { "ix_name", "name" }));
			final List<DatabaseConstraint> constraints = DbUtilities.getConstraints(database.connection(), "tab");
			assertEquals(2, constraints.size());
			assertEquals("value > 0", constraints.get(1).getCondition());
			// Check constraints are optional
			assertNull(DbUtilities.getConstraints(database.connection(), "db.tab").get(1).getCondition());
			final List<DatabaseIndex> indices = DbUtilities.getIndices(database.connection(), "tab");
			assertEquals("primary", indices.get(0).getIndexName());
			assertEquals("ix_name", indices.get(1).getIndexName());
		}

		final FakeDatabase postgres = new FakeDatabase("PostgreSQL")
				.on("FROM information_schema.table_constraints", rows(new String[] { "constraint_name", "constraint_type" }, new Object[] { "tab_pkey", "PRIMARY KEY" }, new Object[] { "tab_fk", "FOREIGN KEY" }))
				.on("FROM pg_class", rows(new String[] { "relnamespace", "index_name", "column_name" }, new Object[] { "1", "tab_pkey", "id" }));
		assertEquals(ConstraintType.ForeignKey, DbUtilities.getConstraints(postgres.connection(), "tab").get(1).getConstraintType());
		assertEquals(2, DbUtilities.getConstraints(postgres.connection(), "app.tab").size());
		assertEquals("tab_pkey", DbUtilities.getIndices(postgres.connection(), "tab").get(0).getIndexName());
		assertEquals(1, DbUtilities.getIndices(postgres.connection(), "app.tab").size());
		assertTrue(postgres.executed("t.relnamespace = \\?::regnamespace"));

		assertNull(DbUtilities.getConstraints(new FakeDatabase("SQLite").connection(), "tab"));
		assertNull(DbUtilities.getIndices(new FakeDatabase("HSQL Database Engine").connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getConstraints(new FakeDatabase("Oracle").on("user_constraints", error("broken")).connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.getIndices(new FakeDatabase("Oracle").on("all_ind_columns", error("broken")).connection(), "tab"));
	}

	@Test
	public void testCheckSchemaExist() throws Exception {
		final FakeDatabase postgres = new FakeDatabase("PostgreSQL").on("information_schema.schemata", value(Integer.valueOf(1)), rows(new String[] { "x" }));
		assertTrue(DbUtilities.checkSchemaExist(postgres.connection(), "app"));
		assertFalse(DbUtilities.checkSchemaExist(postgres.connection(), "missing"));
		assertFalse(DbUtilities.checkSchemaExist(postgres.connection(), " "));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.checkSchemaExist(new FakeDatabase("Oracle").connection(), "app")).getMessage().contains("Unsupported db vendor"));
	}
}
