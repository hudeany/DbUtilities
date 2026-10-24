package de.soderer.utilities.db;

import static de.soderer.utilities.db.FakeJdbc.error;
import static de.soderer.utilities.db.FakeJdbc.rows;
import static de.soderer.utilities.db.FakeJdbc.update;
import static de.soderer.utilities.db.FakeJdbc.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.FakeJdbc.FakeDatabase;
import de.soderer.utilities.db.FakeJdbc.Result;
import de.soderer.utilities.db.data.DbColumnType;

/**
 * Tests of the vendor specific data operations of {@link DbUtilities} (table creation, duplicate handling, inserts
 * and updates) against the scriptable {@link FakeJdbc} database.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesVendorDataOperationsTest {
	private static final Result BASE_COLUMNS = rows(new String[] { "id", "name" });

	/**
	 * Runs code, which may print stack traces of expected errors, without polluting the test output.
	 */
	private interface QuietCode {
		void run() throws Exception;
	}

	private static void runQuietly(final QuietCode code) throws Exception {
		final PrintStream originalErr = System.err;
		try (PrintStream nullStream = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
			System.setErr(nullStream);
			code.run();
		} finally {
			System.setErr(originalErr);
		}
	}

	/**
	 * Column types of table "tab" in the vendor specific format: "id" is auto increment (where supported) and not
	 * nullable, "name" is a nullable string.
	 */
	private static FakeDatabase withColumnTypes(final FakeDatabase database, final String productName, final boolean withAutoIncrement) {
		switch (productName) {
			case "Apache Derby":
				return database.on("FROM sys.systables, sys.syscolumns", rows(new String[] { "columnname", "columndatatype", "autoincrementvalue" },
						new Object[] { "ID", "INTEGER NOT NULL", withAutoIncrement ? "1" : null }, new Object[] { "NAME", "VARCHAR(30)", null }));
			case "PostgreSQL":
				return database.on("FROM information_schema.columns", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "column_default" },
						new Object[] { "id", "integer", null, Integer.valueOf(32), Integer.valueOf(0), "NO", withAutoIncrement ? "nextval('s')" : null }, new Object[] { "name", "character varying", Long.valueOf(30), null, null, "YES", null }));
			case "Firebird":
				return database.on("FROM rdb\\$fields", rows(new String[] { "rdb$field_name", "rdb$field_type", "rdb$field_sub_type", "rdb$field_length", "rdb$field_scale", "rdb$null_flag" },
						new Object[] { "ID", Integer.valueOf(8), Integer.valueOf(0), Long.valueOf(4), Integer.valueOf(0), Integer.valueOf(1) }, new Object[] { "NAME", Integer.valueOf(37), Integer.valueOf(0), Long.valueOf(30), Integer.valueOf(0), null }));
			case "Microsoft SQL Server":
				return database.on("COLUMNPROPERTY", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "is_identity" },
						new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", Integer.valueOf(withAutoIncrement ? 1 : 0) }, new Object[] { "name", "varchar", Long.valueOf(30), null, null, "YES", Integer.valueOf(0) }));
			case "Cassandra":
				return database.on("kind = 'partition_key'", rows(new String[] { "keyspace_name", "column_name" }, new Object[] { "ks", "id" }))
						.on("system_schema.columns", rows(new String[] { "keyspace_name", "column_name", "kind", "type" }, new Object[] { "ks", "id", "partition_key", "int" }, new Object[] { "ks", "name", "regular", "text" }));
			case "HSQL Database Engine":
				return database.on("system_columns", rows(new String[] { "column_name", "type_name", "column_size", "decimal_digits", "is_nullable", "is_autoincrement" },
						new Object[] { "ID", "INTEGER", Long.valueOf(32), Integer.valueOf(0), "NO", withAutoIncrement ? "YES" : "NO" }, new Object[] { "NAME", "VARCHAR", Long.valueOf(30), Integer.valueOf(0), "YES", "NO" }));
			case "SQLite":
				return database.on("PRAGMA table_info", rows(new String[] { "cid", "name", "type", "notnull", "dflt_value", "pk" },
						new Object[] { Integer.valueOf(0), "id", "INTEGER", Integer.valueOf(1), null, Integer.valueOf(1) }, new Object[] { Integer.valueOf(1), "name", "VARCHAR(30)", Integer.valueOf(0), null, Integer.valueOf(0) }));
			case "Oracle":
				return database.on("FROM user_tab_columns", rows(new String[] { "column_name", "data_type", "data_length", "data_precision", "data_scale", "nullable" },
						new Object[] { "ID", "NUMBER", Integer.valueOf(22), Integer.valueOf(10), Integer.valueOf(0), "N" }, new Object[] { "NAME", "VARCHAR2", Integer.valueOf(30), null, null, "Y" }));
			default:
				return database.on("FROM information_schema.columns", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "extra" },
						new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", withAutoIncrement ? "auto_increment" : "" }, new Object[] { "name", "varchar", Long.valueOf(30), null, null, "YES", "" }));
		}
	}

	@Test
	public void testCreateTable() throws Exception {
		final Map<String, DbColumnType> columns = new LinkedHashMap<>();
		columns.put("id", new DbColumnType("INTEGER", 0, 10, 0, false, false, null));
		columns.put("order", new DbColumnType("VARCHAR", 0, 0, 0, true, false, null));
		columns.put("text", new DbColumnType("VARCHAR", 50, 0, 0, true, false, null));
		columns.put("other", null);

		final FakeDatabase mysql = new FakeDatabase("MySQL");
		DbUtilities.createTable(mysql.connection(), "tab", columns, List.of("id"));
		assertEquals("CREATE TABLE tab (id INT, `order` VARCHAR(1), `text` VARCHAR(50), other VARCHAR(1), PRIMARY KEY (id))", mysql.getExecutedSql().get(0));
		// Current behavior: an already escaped key column passes the key check, but is escaped a second time
		DbUtilities.createTable(mysql.connection(), "tab2", columns, List.of("`id`"));
		assertTrue(mysql.getExecutedSql().get(1).endsWith("PRIMARY KEY (```id```))"), mysql.getExecutedSql().get(1));

		final FakeDatabase derby = new FakeDatabase("Apache Derby");
		DbUtilities.createTable(derby.connection(), "tab", columns, null);
		assertEquals(1, derby.getCommitCount());

		final FakeDatabase cassandra = new FakeDatabase("Cassandra");
		DbUtilities.createTable(cassandra.connection(), "tab", columns, List.of("id"));
		assertTrue(cassandra.getExecutedSql().get(0).contains("text VARCHAR, other VARCHAR(1)"), cassandra.getExecutedSql().get(0));

		assertTrue(assertThrows(Exception.class, () -> DbUtilities.createTable(mysql.connection(), "tab", columns, List.of("missing"))).getMessage().contains("Keycolumn 'missing'"));
	}

	@Test
	public void testDuplicateDetectionAndMarking() throws Exception {
		final FakeDatabase postgres = new FakeDatabase("PostgreSQL")
				.on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\) FROM tab GROUP BY", value(Integer.valueOf(3)), rows(new String[] { "x" }), error("broken"))
				.on("SELECT COUNT\\(\\*\\) FROM tab a WHERE EXISTS", value(Integer.valueOf(2)), error("broken"))
				.on("DELETE FROM other WHERE", update(4), error("broken"))
				.on("UPDATE broken SET", error("broken"));
		assertEquals(3, DbUtilities.detectDuplicates(postgres.connection(), "tab", List.of("id", "LOWER(name)")));
		assertTrue(postgres.executed("GROUP BY id, LOWER\\(name\\) HAVING COUNT\\(\\*\\) > 1"), postgres.getExecutedSql().toString());
		assertEquals(0, DbUtilities.detectDuplicates(postgres.connection(), "tab", List.of("id")));
		assertThrows(Exception.class, () -> DbUtilities.detectDuplicates(postgres.connection(), "tab", List.of("id")));

		assertEquals(2, DbUtilities.detectDuplicatesCrossTables(postgres.connection(), "tab", "other", List.of("id")));
		assertTrue(postgres.executed("WHERE a.id = b.id\\)"));
		assertThrows(Exception.class, () -> DbUtilities.detectDuplicatesCrossTables(postgres.connection(), "tab", "other", List.of("id")));

		assertEquals(4, DbUtilities.dropDuplicatesCrossTable(postgres.connection(), "tab", "other", List.of("id", "user")));
		assertTrue(postgres.executed("DELETE FROM other WHERE id, \"user\" IN \\(SELECT id, \"user\" FROM tab\\)"));
		assertThrows(Exception.class, () -> DbUtilities.dropDuplicatesCrossTable(postgres.connection(), "tab", "other", List.of("id")));
		assertEquals(0, DbUtilities.dropDuplicatesCrossTable(postgres.connection(), "tab", "other", List.of()));
		assertEquals(1, postgres.getRollbackCount());

		DbUtilities.markDuplicates(postgres.connection(), "tab", List.of("id", "LOWER(name)"), "item_idx", "dupl_idx");
		final String markStatement = postgres.findExecuted("UPDATE tab SET dupl_idx");
		assertTrue(markStatement.contains("SELECT id AS col0, LOWER(name) AS col1, MIN(item_idx) AS item_idx FROM tab GROUP BY id, LOWER(name)"), markStatement);
		assertTrue(markStatement.contains("WHERE subselect.col0 = tab.id AND subselect.col1 = LOWER(tab.name))"), markStatement);
		assertThrows(Exception.class, () -> DbUtilities.markDuplicates(postgres.connection(), "broken", List.of("id"), "a", "b"));
		assertThrows(Exception.class, () -> DbUtilities.markDuplicates(postgres.connection(), "tab", List.of(), "a", "b"));
	}

	@Test
	public void testDropDuplicatesForAllVendors() throws Exception {
		final String[] productNames = { "MySQL", "MariaDB", "Oracle", "SQLite", "HSQL Database Engine", "Apache Derby", "PostgreSQL", "Firebird", "Microsoft SQL Server", "Cassandra" };
		for (final String productName : productNames) {
			for (final boolean withAutoIncrement : new boolean[] { true, false }) {
				final FakeDatabase database = new FakeDatabase(productName)
						.on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS, BASE_COLUMNS, rows(new String[] { "id", "name", "drop_idx", "drop_dpl" }))
						.on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\)", value(Integer.valueOf(1)))
						.on("DELETE FROM tab WHERE drop_idx != drop_dpl", update(5))
						.on("ALTER TABLE tab DROP", update(0))
						.on("CREATE INDEX tmp\\d+_idx ON tab \\(name\\)", error("index exists"));
				withColumnTypes(database, productName, withAutoIncrement);
				final int[] deleted = new int[1];
				runQuietly(() -> deleted[0] = DbUtilities.dropDuplicates(database.connection(), "tab", List.of("name")));
				assertEquals(5, deleted[0], productName);
				assertTrue(database.executed("ALTER TABLE tab ADD drop_idx"), productName);
				assertTrue(database.executed("ALTER TABLE tab ADD drop_dpl"), productName);
				assertTrue(database.executed("UPDATE tab SET drop_dpl"), productName);
				if (!"SQLite".equals(productName)) {
					assertTrue(database.executed("ALTER TABLE tab DROP (COLUMN )?drop_idx"), productName);
				}
			}
		}

		final FakeDatabase noDuplicates = new FakeDatabase("MySQL").on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\)", value(Integer.valueOf(0)));
		assertEquals(0, DbUtilities.dropDuplicates(noDuplicates.connection(), "tab", List.of("name")));
		assertEquals(0, DbUtilities.joinDuplicates(noDuplicates.connection(), "tab", List.of("name"), true));

		final FakeDatabase failing = new FakeDatabase("MySQL")
				.on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS)
				.on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\)", value(Integer.valueOf(1)))
				.on("DELETE FROM tab", error("cannot delete"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.dropDuplicates(failing.connection(), "tab", List.of("name"))).getMessage().startsWith("Cannot dropDuplicates"));

		final FakeDatabase unsupported = new FakeDatabase("MySQL").withoutMetaData();
		assertThrows(Exception.class, () -> DbUtilities.addIndexedIntegerColumn(unsupported.connection(), "tab", "x"));
	}

	@Test
	public void testAddIndexedIntegerColumnNames() throws Exception {
		final FakeDatabase database = new FakeDatabase("MySQL").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "idx", "idx_1" }));
		assertEquals("idx_2", DbUtilities.addIndexedIntegerColumn(database.connection(), "tab", "idx"));
		assertTrue(database.executed("ALTER TABLE tab ADD idx_2 BIGINT"));
		assertTrue(database.executed("CREATE INDEX tmp\\d+_idx ON tab \\(idx_2\\)"));

		final FakeDatabase cassandra = new FakeDatabase("Cassandra").on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS);
		assertEquals("idx", DbUtilities.addIndexedIntegerColumn(cassandra.connection(), "tab", "idx"));
		assertFalse(cassandra.executed("CREATE INDEX"));

		final FakeDatabase full = new FakeDatabase("MySQL").on("SELECT \\* FROM tab WHERE 1 = 0",
				rows(new String[] { "x", "x_1", "x_2", "x_3", "x_4", "x_5", "x_6", "x_7", "x_8", "x_9", "x_10" }));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.addIndexedIntegerColumn(full.connection(), "tab", "x")).getMessage().startsWith("Cannot create columnBaseName"));

		final FakeDatabase failingIndex = new FakeDatabase("MySQL").on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS).on("CREATE INDEX", error("cannot create index"));
		runQuietly(() -> assertEquals("idx", DbUtilities.addIndexedIntegerColumn(failingIndex.connection(), "tab", "idx")));
	}

	@Test
	public void testJoinDuplicates() throws Exception {
		for (final String productName : new String[] { "MySQL", "PostgreSQL", "HSQL Database Engine", "Firebird", "Cassandra" }) {
			for (final boolean updateWithNullValues : new boolean[] { true, false }) {
				final FakeDatabase database = new FakeDatabase(productName)
						.on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS, BASE_COLUMNS, rows(new String[] { "id", "name", "join_idx", "join_dpl", "drop_idx", "drop_dpl", "updatedindex" }))
						.on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\)", value(Integer.valueOf(1)))
						.on("SELECT COUNT\\(\\*\\) FROM tab WHERE updatedindex\\w* = 1", value(Integer.valueOf(1)))
						.on("DELETE FROM tab WHERE drop_idx\\w* != drop_dpl\\w*", update(2));
				withColumnTypes(database, productName, false);
				final int[] joined = new int[1];
				runQuietly(() -> joined[0] = DbUtilities.joinDuplicates(database.connection(), "tab", List.of("name"), updateWithNullValues));
				assertEquals(2, joined[0], productName);
				assertTrue(database.executed("tmp_join_\\d+"), productName);
				assertTrue(database.executed("DROP TABLE tmp_join_\\d+"), productName);
			}
		}

		final FakeDatabase failing = new FakeDatabase("MySQL")
				.on("SELECT \\* FROM tab WHERE 1 = 0", BASE_COLUMNS)
				.on("SELECT COUNT\\(\\*\\) FROM \\(SELECT COUNT\\(\\*\\)", value(Integer.valueOf(1)))
				.on("CREATE TABLE tmp_join", error("cannot create"));
		runQuietly(() -> assertTrue(assertThrows(Exception.class, () -> DbUtilities.joinDuplicates(failing.connection(), "tab", List.of("name"), true)).getMessage().startsWith("Cannot joinDuplicates")));
	}

	@Test
	public void testDropColumnAndTable() throws Exception {
		final FakeDatabase mysql = new FakeDatabase("MySQL")
				.on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "x" }))
				.on("SELECT \\* FROM missing WHERE 1 = 0", error("no table"))
				.on("ALTER TABLE tab DROP COLUMN broken", error("cannot drop"));
		assertTrue(DbUtilities.dropColumnIfExists(mysql.connection(), "tab", "x"));
		assertFalse(DbUtilities.dropColumnIfExists(mysql.connection(), "tab", "other"));
		assertFalse(DbUtilities.dropColumnIfExists(mysql.connection(), "tab", null));
		assertFalse(DbUtilities.dropColumnIfExists(null, "tab", "x"));
		assertTrue(DbUtilities.dropTableIfExists(mysql.connection(), "tab"));
		assertFalse(DbUtilities.dropTableIfExists(mysql.connection(), "missing"));
		assertFalse(DbUtilities.dropTableIfExists(mysql.connection(), null));
		final FakeDatabase failingMysql = new FakeDatabase("MySQL").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "broken" })).on("ALTER TABLE tab DROP COLUMN broken", error("cannot drop"));
		assertThrows(Exception.class, () -> DbUtilities.dropColumnIfExists(failingMysql.connection(), "tab", "broken"));
		assertTrue(failingMysql.getRollbackCount() > 0);

		final FakeDatabase cassandra = new FakeDatabase("Cassandra").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "x" }));
		assertTrue(DbUtilities.dropColumnIfExists(cassandra.connection(), "tab", "x"));
		assertTrue(cassandra.executed("ALTER TABLE tab DROP x"));

		// SQLite: direct drop
		final FakeDatabase sqlite = new FakeDatabase("SQLite").on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "x" }));
		assertTrue(DbUtilities.dropColumnIfExists(sqlite.connection(), "tab", "x"));

		// SQLite: fallback for old versions without "DROP COLUMN" by copying the table
		final FakeDatabase oldSqlite = new FakeDatabase("SQLite")
				.on("SELECT \\* FROM tab WHERE 1 = 0", rows(new String[] { "id", "x", "name" }))
				.on("ALTER TABLE tab DROP COLUMN", error("syntax error"))
				.on("SELECT sql FROM sqlite_master", rows(new String[] { "sql" }, new Object[] { "CREATE TABLE tab (id INTEGER, x VARCHAR(10), name VARCHAR(20))" }), rows(new String[] { "sql" }, new Object[] { "CREATE TABLE tab (id INTEGER, name VARCHAR(20), x INTEGER)" }),
								rows(new String[] { "sql" }, new Object[] { "CREATE TABLE tab (id INTEGER, x TEXT NOT NULL)" }), rows(new String[] { "sql" }));
		assertTrue(DbUtilities.dropColumnIfExists(oldSqlite.connection(), "tab", "x"));
		assertTrue(oldSqlite.executed("^CREATE TABLE tab \\(id INTEGER, name VARCHAR\\(20\\)\\)$"));
		assertTrue(oldSqlite.executed("INSERT INTO tab \\(.*\\) SELECT .* FROM tmp\\d+_old"));
		assertTrue(DbUtilities.dropColumnIfExists(oldSqlite.connection(), "tab", "x"));
		assertTrue(oldSqlite.executed("^CREATE TABLE tab \\(id INTEGER, name VARCHAR\\(20\\)\\)$"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.dropColumnIfExists(oldSqlite.connection(), "tab", "x")).getMessage().contains("Unsupported column definition"));
		assertEquals("Cannot find create table sql", assertThrows(Exception.class, () -> DbUtilities.dropColumnIfExists(oldSqlite.connection(), "tab", "x")).getMessage());
	}

	@Test
	public void testCopyTableStructure() throws Exception {
		final String[] productNames = { "MySQL", "MariaDB", "Oracle", "PostgreSQL", "Microsoft SQL Server", "HSQL Database Engine", "Apache Derby", "Firebird", "Cassandra" };
		for (final String productName : productNames) {
			final FakeDatabase database = new FakeDatabase(productName);
			withColumnTypes(database, productName, false);
			DbUtilities.copyTableStructure(database.connection(), "tab", List.of("id", "name"), List.of("name"), "dest");
			assertTrue(database.executed("dest"), productName);
			if (!"Cassandra".equals(productName)) {
				// The not nullable non key column "id" is made nullable
				assertTrue(database.executed("ALTER TABLE dest (MODIFY|ALTER COLUMN) id"), productName + ": " + database.getExecutedSql());
			}
		}
		final FakeDatabase sqlite = new FakeDatabase("SQLite");
		withColumnTypes(sqlite, "SQLite", false);
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.copyTableStructure(sqlite.connection(), "tab", List.of("id", "name"), null, "dest")).getMessage().contains("SQLite does not support"));
		final FakeDatabase sqliteWithKey = new FakeDatabase("SQLite");
		withColumnTypes(sqliteWithKey, "SQLite", false);
		DbUtilities.copyTableStructure(sqliteWithKey.connection(), "tab", List.of("id", "name"), List.of("ID"), "dest");
		assertTrue(sqliteWithKey.executed("CREATE TABLE dest AS SELECT id, name FROM tab WHERE 1 = 0"));
	}

	@Test
	public void testIndexAndClearTable() throws Exception {
		final FakeDatabase database = new FakeDatabase("MySQL").on("DELETE FROM tab", update(7)).on("DELETE FROM broken", error("broken")).on("CREATE INDEX .* ON broken", error("broken"));
		final String indexName = DbUtilities.createIndex(database.connection(), "a_very_long_table_name_for_index", List.of("a", "b"));
		assertTrue(indexName.length() <= 30, indexName);
		assertTrue(indexName.endsWith("_ix"));
		assertTrue(database.executed("CREATE INDEX " + indexName + " ON a_very_long_table_name_for_index \\(a, b\\)"));
		assertThrows(Exception.class, () -> DbUtilities.createIndex(database.connection(), "broken", List.of("a")));
		assertEquals(7, DbUtilities.clearTable(database.connection(), "tab"));
		assertThrows(Exception.class, () -> DbUtilities.clearTable(database.connection(), "broken"));
	}

	@Test
	public void testInsertItems() throws Exception {
		final FakeDatabase database = new FakeDatabase("MySQL").on("INSERT INTO dest", update(3), update(2), error("broken"));
		assertEquals(3, DbUtilities.insertNotExistingItems(database.connection(), "src", "dest", List.of("id", "name"), List.of("id"), "created = NOW()\nsource = 'import'"));
		assertEquals("INSERT INTO dest (created, source, id, `name`) SELECT NOW(), 'import', id, `name` FROM src a WHERE NOT EXISTS (SELECT 1 FROM dest b WHERE a.id = b.id)", database.getExecutedSql().get(0));
		assertEquals(2, DbUtilities.insertAllItems(database.connection(), "src", "dest", List.of("id"), null));
		assertEquals("INSERT INTO dest (id) SELECT id FROM src a", database.getExecutedSql().get(1));
		assertThrows(Exception.class, () -> DbUtilities.insertAllItems(database.connection(), "src", "dest", List.of("id"), null));
		assertEquals(1, database.getRollbackCount());
	}

	@Test
	public void testRemoveNewEntriesWithInvalidNullValues() throws Exception {
		final FakeDatabase database = new FakeDatabase("MySQL")
				.on("FROM information_schema.columns WHERE LOWER", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "extra" },
						new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", "" },
						new Object[] { "name", "varchar", Long.valueOf(30), null, null, "NO", "" },
						new Object[] { "status", "varchar", Long.valueOf(30), null, null, "NO", "" },
						new Object[] { "optional", "varchar", Long.valueOf(30), null, null, "YES", "" }))
				.on("column_default FROM information_schema.columns", value(null), value(null), value("'new'"))
				.on("DELETE FROM tmp", update(4));
		assertEquals(4, DbUtilities.removeNewEntriesWithInvalidNullValues(database.connection(), "tmp", "dest", List.of("ID", "Name", "status"), "dupl"));
		final String deleteStatement = database.findExecuted("DELETE FROM tmp");
		assertTrue(deleteStatement.startsWith("DELETE FROM tmp WHERE (dupl = 0 OR dupl IS NULL) AND ("), deleteStatement);
		assertTrue(deleteStatement.contains("id IS NULL"), deleteStatement);
		assertTrue(deleteStatement.contains("(name IS NULL OR name = '')"), deleteStatement);
		assertFalse(deleteStatement.contains("status"), deleteStatement);
		assertFalse(deleteStatement.contains("optional"), deleteStatement);

		final FakeDatabase withoutDuplicateColumn = new FakeDatabase("MySQL")
				.on("FROM information_schema.columns WHERE LOWER", rows(new String[] { "column_name", "data_type", "character_maximum_length", "numeric_precision", "numeric_scale", "is_nullable", "extra" },
						new Object[] { "id", "int", null, Integer.valueOf(10), Integer.valueOf(0), "NO", "" }))
				.on("DELETE FROM tmp", update(1));
		assertEquals(1, DbUtilities.removeNewEntriesWithInvalidNullValues(withoutDuplicateColumn.connection(), "tmp", "dest", null, null));
		assertEquals("DELETE FROM tmp WHERE id IS NULL", withoutDuplicateColumn.findExecuted("DELETE FROM tmp"));

		assertEquals(0, DbUtilities.removeNewEntriesWithInvalidNullValues(new FakeDatabase("MySQL").connection(), "tmp", "dest", null, null));
	}

	@Test
	public void testUpdateAllExistingItems() throws Exception {
		final FakeDatabase withNulls = new FakeDatabase("MySQL").on("UPDATE dest SET", update(6));
		assertEquals(6, DbUtilities.updateAllExistingItems(withNulls.connection(), "src", "dest", List.of("id", "name", "order"), List.of("id"), "idx", true, "changed = NOW()"));
		final String updateStatement = withNulls.findExecuted("UPDATE dest SET");
		assertTrue(updateStatement.startsWith("UPDATE dest SET changed = NOW(), `name` = (SELECT `name` FROM src WHERE idx = (SELECT MAX(idx) FROM src c WHERE dest.id = c.id)), `order` = "), updateStatement);
		assertTrue(updateStatement.endsWith(" WHERE EXISTS (SELECT 1 FROM src b WHERE dest.id = b.id)"), updateStatement);

		final FakeDatabase withoutNulls = new FakeDatabase("MySQL")
				.on("SELECT \\* FROM dest WHERE 1 = 0", BASE_COLUMNS, rows(new String[] { "id", "name", "updatedindex" }))
				.on("SELECT COUNT\\(\\*\\) FROM dest WHERE updatedindex = 1", value(Integer.valueOf(4)));
		assertEquals(4, DbUtilities.updateAllExistingItems(withoutNulls.connection(), "src", "dest", List.of("id", "name"), List.of("id"), "idx", false, null));
		assertTrue(withoutNulls.executed("SET updatedindex = 1, `name` = \\(SELECT `name` FROM src WHERE idx = \\(SELECT MAX\\(idx\\) FROM src c WHERE `name` IS NOT NULL AND dest.id = c.id\\)\\)"), withoutNulls.getExecutedSql().toString());
		assertTrue(withoutNulls.executed("ALTER TABLE dest DROP COLUMN updatedindex"));

		assertEquals(0, DbUtilities.updateAllExistingItems(withNulls.connection(), "src", "dest", List.of("id"), List.of("id"), "idx", true, null));
		assertEquals("Missing keycolumns", assertThrows(Exception.class, () -> DbUtilities.updateAllExistingItems(withNulls.connection(), "src", "dest", List.of("a"), List.of(), "idx", true, null)).getMessage());
		assertThrows(Exception.class, () -> DbUtilities.updateAllExistingItems(withNulls.connection(), "src", "dest", List.of("a"), null, "idx", true, null));

		final FakeDatabase failing = new FakeDatabase("MySQL").on("UPDATE dest SET", error("broken"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.updateAllExistingItems(failing.connection(), "src", "dest", List.of("id", "name"), List.of("id"), "idx", true, null)).getMessage().startsWith("Cannot update"));
		assertEquals(1, failing.getRollbackCount());
	}

	@Test
	public void testUpdateFirstExistingItems() throws Exception {
		for (final boolean updateWithNullValues : new boolean[] { true, false }) {
			final FakeDatabase database = new FakeDatabase("Oracle")
					.on("SELECT \\* FROM dest WHERE 1 = 0", BASE_COLUMNS, rows(new String[] { "id", "name", "itemindex", "updatedindex" }))
					.on("UPDATE dest SET .*WHERE EXISTS", update(3))
					.on("SELECT COUNT\\(\\*\\) FROM dest WHERE updatedindex\\w* = 1", value(Integer.valueOf(3)));
			assertEquals(3, DbUtilities.updateFirstExistingItems(database.connection(), "src", "dest", List.of("id", "name"), List.of("id"), "srcidx", updateWithNullValues, "changed = SYSDATE"));
			assertTrue(database.executed("UPDATE dest SET itemindex = ROWNUM"));
			assertTrue(database.executed("UPDATE src SET srcidx = COALESCE\\(\\(SELECT MIN\\(src.itemindex\\) FROM dest src WHERE src.id = src.id AND src.id IS NOT NULL\\), 0\\)"), database.getExecutedSql().toString());
			assertTrue(database.executed("ALTER TABLE dest DROP COLUMN itemindex"));
		}
		final FakeDatabase database = new FakeDatabase("Oracle");
		assertEquals(0, DbUtilities.updateFirstExistingItems(database.connection(), "src", "dest", List.of("id"), List.of("id"), "srcidx", true, null));
		assertThrows(Exception.class, () -> DbUtilities.updateFirstExistingItems(database.connection(), "src", "dest", List.of("id"), List.of(), "srcidx", true, null));
		assertThrows(Exception.class, () -> DbUtilities.updateFirstExistingItems(database.connection(), "src", "dest", List.of("id"), null, "srcidx", true, null));

		final FakeDatabase failing = new FakeDatabase("Oracle").on("SELECT \\* FROM dest WHERE 1 = 0", BASE_COLUMNS).on("UPDATE src SET", error("broken"));
		assertTrue(assertThrows(Exception.class, () -> DbUtilities.updateFirstExistingItems(failing.connection(), "src", "dest", List.of("id", "name"), List.of("id"), "srcidx", true, null)).getMessage().startsWith("Cannot update first entries"));
	}
}
