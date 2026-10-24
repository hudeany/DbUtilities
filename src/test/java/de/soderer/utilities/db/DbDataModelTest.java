package de.soderer.utilities.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.DatabaseConstraint.ConstraintType;
import de.soderer.utilities.db.data.DbColumn;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbConnectionDefinition;
import de.soderer.utilities.db.data.DbForeignKey;
import de.soderer.utilities.db.data.DbSchema;
import de.soderer.utilities.db.data.DbSimpleDataType;
import de.soderer.utilities.db.data.DbStructure;
import de.soderer.utilities.db.data.DbTable;
import de.soderer.utilities.db.data.DbVendor;
import de.soderer.utilities.db.exception.DbDefinitionException;
import de.soderer.utilities.db.exception.DbNotExistsException;
import de.soderer.utilities.db.exception.DbStructureException;

/**
 * Tests of the data classes: connection definition, structure model, column types, vendors, constraints, indices
 * and exceptions.
 */
@SuppressWarnings("static-method")
public class DbDataModelTest {
	@Test
	public void testDbVendor() throws Exception {
		assertEquals(DbVendor.MySQL, DbVendor.getDbVendorByName("mysql"));
		assertEquals(DbVendor.PostgreSQL, DbVendor.getDbVendorByName("Postgres"));
		assertEquals(DbVendor.HSQL, DbVendor.getDbVendorByName("HyperSQL"));
		assertThrows(Exception.class, () -> DbVendor.getDbVendorByName("unknown"));
		assertEquals("org.sqlite.JDBC", DbVendor.SQLite.getDriverClassName());
		assertEquals(5432, DbVendor.PostgreSQL.getDefaultPort());
		assertEquals(0, DbVendor.Derby.getDefaultPort());
		assertEquals("SELECT 1 FROM DUAL", DbVendor.Oracle.getTestStatement());
		assertEquals("", DbVendor.Cassandra.getTestStatement());
	}

	@Test
	public void testSimpleDataTypes() {
		assertEquals(DbSimpleDataType.Blob, DbSimpleDataType.getSimpleDataTypeByName("BLOB"));
		assertThrows(RuntimeException.class, () -> DbSimpleDataType.getSimpleDataTypeByName("unknown"));

		final String[][] expectedTypes = {
				{ "TIMESTAMP", "DateTime" }, { "datetime", "DateTime" }, { "DATE", "Date" },
				{ "CLOB", "Clob" }, { "longtext", "Clob" }, { "VARCHAR2", "String" }, { "char", "String" },
				{ "character varying", "String" }, { "NVARCHAR", "String" }, { "nchar", "String" }, { "national char", "String" },
				{ "BLOB", "Blob" }, { "bytea", "Blob" }, { "varbinary", "Blob" }, { "raw", "Blob" }, { "long raw", "Blob" }, { "image", "Blob" },
				{ "BIGINT", "BigInteger" }, { "INTEGER", "Integer" }, { "smallint", "Integer" },
				{ "interval", "Float" }, { "point", "Float" }, { "boolean", "Boolean" }, { "REAL", "Float" }, { "NUMBER", "Float" } };
		for (final String[] expectedType : expectedTypes) {
			assertEquals(DbSimpleDataType.valueOf(expectedType[1]), new DbColumnType(expectedType[0], 0, 0, 0, true, false, null).getSimpleDataType(), expectedType[0]);
		}
		assertEquals(DbSimpleDataType.Float, new DbColumnType(null, 0, 0, 0, true, false, null).getSimpleDataType());
	}

	@Test
	public void testColumnType() {
		final DbColumnType stringType = new DbColumnType("VARCHAR", 100, 0, 0, false, false, "'x'");
		assertEquals("VARCHAR", stringType.getTypeName());
		assertEquals(100, stringType.getCharacterByteSize());
		assertFalse(stringType.isNullable());
		assertFalse(stringType.isAutoIncrement());
		assertEquals("'x'", stringType.getDefaultValue());
		assertEquals("VARCHAR(100) not nullable default('x')", stringType.toString());

		final DbColumnType numberType = new DbColumnType("NUMBER", 0, 10, 2, true, false, null);
		assertEquals(10, numberType.getNumericPrecision());
		assertEquals(2, numberType.getNumericScale());
		assertEquals("NUMBER(10, 2) nullable", numberType.toString());

		assertEquals("INTEGER not nullable autoIncrement", new DbColumnType("INTEGER", 0, 0, 0, false, true, null).toString());
	}

	@Test
	public void testStructureModel() throws Exception {
		final DbColumn column = new DbColumn().withColumnName(" ID ").withColumnType(new DbColumnType("INTEGER", 0, 0, 0, false, true, null)).withColumnComment("Identifier");
		assertEquals("id", column.getColumnName());
		assertEquals("INTEGER", column.getColumnType().getTypeName());
		assertEquals("Identifier", column.getColumnComment());

		final DbForeignKey foreignKey = new DbForeignKey().withForeignKeyName("fk_parent").withColumnNames(List.of("parent_id")).withReferencedTableName("parent").withReferencedColumnNames(List.of("id"));
		assertEquals("fk_parent", foreignKey.getForeignKeyName());
		assertEquals(List.of("parent_id"), foreignKey.getColumnNames());
		assertEquals("parent", foreignKey.getReferencedTableName());
		assertEquals(List.of("id"), foreignKey.getReferencedColumnNames());

		final DbTable table = new DbTable().withTableName(" Child ").withPrimaryKey(List.of("id")).withTableComment("Children");
		assertEquals("child", table.getTableName());
		assertEquals(List.of("id"), table.getPrimaryKey());
		assertEquals("Children", table.getTableComment());
		assertSame(table, table.createColumn("ID", column));
		assertThrows(DbStructureException.class, () -> table.createColumn("id", column));
		assertSame(table, table.addForeignKey(foreignKey));
		assertEquals(1, table.getForeignKeys().size());
		assertSame(table, table.addUniqueKey("uk_name", List.of("name")));
		assertThrows(DbStructureException.class, () -> table.addUniqueKey("uk_name", List.of("other")));
		assertEquals(List.of("name"), table.getUniqueKeys().get("uk_name"));
		assertSame(column, table.getColumns().get("Id"));
		assertSame(column, table.dropColumn("ID"));
		assertThrows(DbStructureException.class, () -> table.dropColumn("id"));

		final DbSchema schema = new DbSchema().withSchemaName(" Public ").withSchemaComment("Main schema");
		assertEquals("public", schema.getSchemaName());
		assertEquals("Main schema", schema.getSchemaComment());
		assertSame(schema, schema.createTable("child", table));
		assertThrows(DbStructureException.class, () -> schema.createTable("CHILD", table));
		assertSame(table, schema.getTables().get("Child"));
		assertSame(table, schema.dropTable("CHILD"));
		assertThrows(DbStructureException.class, () -> schema.dropTable("child"));

		final DbStructure structure = new DbStructure();
		assertSame(structure, structure.createSchema("public", schema));
		assertThrows(DbStructureException.class, () -> structure.createSchema("PUBLIC", schema));
		assertEquals(1, structure.getSchemas().size());
		assertSame(schema, structure.dropSchema("Public"));
		assertThrows(DbStructureException.class, () -> structure.dropSchema("public"));
	}

	@Test
	public void testConnectionDefinitionProperties() {
		final File trustStoreFile = new File("truststore.jks");
		final DbConnectionDefinition definition = new DbConnectionDefinition()
				.withDbVendor(DbVendor.PostgreSQL)
				.withHostnameAndPort("localhost:5432")
				.withDbName("db")
				.withUsername("user")
				.withPassword("secret".toCharArray())
				.withSecureConnection(true)
				.withTrustStoreFile(trustStoreFile)
				.withTrustStorePassword("tspw".toCharArray());
		assertEquals(DbVendor.PostgreSQL, definition.getDbVendor());
		assertEquals("localhost:5432", definition.getHostnameAndPort());
		assertEquals("db", definition.getDbName());
		assertEquals("user", definition.getUsername());
		assertArrayEquals("secret".toCharArray(), definition.getPassword());
		assertTrue(definition.isSecureConnection());
		assertEquals(trustStoreFile, definition.getTrustStoreFile());
		assertArrayEquals("tspw".toCharArray(), definition.getTrustStorePassword());

		final DbConnectionDefinition copy = new DbConnectionDefinition();
		copy.importParameters(definition);
		assertEquals(DbVendor.PostgreSQL, copy.getDbVendor());
		assertEquals("localhost:5432", copy.getHostnameAndPort());
		assertEquals("db", copy.getDbName());
		assertEquals("user", copy.getUsername());
		assertTrue(copy.isSecureConnection());
		assertEquals(trustStoreFile, copy.getTrustStoreFile());
		assertArrayEquals("tspw".toCharArray(), copy.getTrustStorePassword());

		copy.importParameters(null);
		assertNull(copy.getDbVendor());
		assertNull(copy.getHostnameAndPort());
		assertNull(copy.getDbName());
		assertNull(copy.getUsername());
		assertNull(copy.getPassword());
		assertFalse(copy.isSecureConnection());
		assertNull(copy.getTrustStoreFile());
		assertNull(copy.getTrustStorePassword());

		final DbConnectionDefinition secureDefinition = new DbConnectionDefinition(DbVendor.MySQL, "host", "db", "user", "pw".toCharArray(), true, trustStoreFile, null);
		assertTrue(secureDefinition.isSecureConnection());
		assertEquals(DbVendor.MySQL, new DbConnectionDefinition(DbVendor.MySQL, "host", "db", "user", "pw".toCharArray()).getDbVendor());
	}

	private static void assertDefinitionError(final String expectedMessage, final DbVendor dbVendor, final String hostnameAndPort, final String dbName, final String username, final String password) {
		final DbConnectionDefinition definition = new DbConnectionDefinition(dbVendor, hostnameAndPort, dbName, username, password == null ? null : password.toCharArray());
		assertEquals(expectedMessage, assertThrows(DbDefinitionException.class, definition::checkParameters).getMessage());
	}

	@Test
	public void testConnectionDefinitionCheckParameters() throws Exception {
		new DbConnectionDefinition(DbVendor.SQLite, null, "/tmp/test.sqlite", null, null).checkParameters();
		assertDefinitionError("SQLite database connections do not support the hostname parameter", DbVendor.SQLite, "host", "db", null, null);
		assertDefinitionError("SQLite database connections do not support the username parameter", DbVendor.SQLite, null, "db", "user", null);
		assertDefinitionError("SQLite database connections do not support the password parameter", DbVendor.SQLite, null, "db", null, "pw");

		new DbConnectionDefinition(DbVendor.Derby, "", "/tmp/derby", "", null).checkParameters();
		assertDefinitionError("Derby database connections do not support the hostname parameter", DbVendor.Derby, "host", "db", null, null);
		assertDefinitionError("Derby database connections do not support the username parameter", DbVendor.Derby, null, "db", "user", null);
		assertDefinitionError("Derby database connections do not support the password parameter", DbVendor.Derby, null, "db", null, "pw");

		final DbConnectionDefinition hsqlDefinition = new DbConnectionDefinition(DbVendor.HSQL, null, "~/hsql/db", null, null);
		hsqlDefinition.checkParameters();
		assertEquals(System.getProperty("user.home") + "/hsql/db", hsqlDefinition.getDbName());
		new DbConnectionDefinition(DbVendor.HSQL, "host:9001", "serverdb", "user", "pw".toCharArray()).checkParameters();
		assertDefinitionError("Missing or invalid dbName", DbVendor.HSQL, null, " ", null, null);
		assertDefinitionError("HSQL file database connections do not support the hostname parameter", DbVendor.HSQL, "host", "/tmp/db", null, null);
		assertDefinitionError("HSQL file database connections do not support the username parameter", DbVendor.HSQL, null, "/tmp/db", "user", null);
		assertDefinitionError("HSQL file database connections do not support the password parameter", DbVendor.HSQL, null, "/tmp/db", null, "pw");

		new DbConnectionDefinition(DbVendor.Cassandra, "host", "keyspace", null, null).checkParameters();
		assertDefinitionError("Missing or invalid hostname", DbVendor.Cassandra, " ", "keyspace", null, null);

		new DbConnectionDefinition(DbVendor.PostgreSQL, "host:5432", "db", "user", "pw".toCharArray()).checkParameters();
		new DbConnectionDefinition(DbVendor.Oracle, "host", "db", "user", "pw".toCharArray()).checkParameters();
		assertDefinitionError("Missing or invalid hostname", DbVendor.MySQL, null, "db", "user", "pw");
		assertDefinitionError("Invalid port in hostname: host:abc", DbVendor.MySQL, "host:abc", "db", "user", "pw");
		assertDefinitionError("Invalid hostname: a:1:2", DbVendor.MySQL, "a:1:2", "db", "user", "pw");
		assertDefinitionError("Missing or invalid username", DbVendor.MySQL, "host", "db", " ", "pw");
		assertDefinitionError("Missing or invalid empty password", DbVendor.MySQL, "host", "db", "user", " ");
	}

	@Test
	public void testDatabaseForeignKey() {
		final DatabaseForeignKey foreignKey = new DatabaseForeignKey("fk", "child", "parent_id", "parent", "id");
		assertEquals("fk: child(parent_id) references parent(id)", foreignKey.toString());
		foreignKey.withForeignKeyName("fk2").withTableName("c").withColumnName("p").withReferencedTableName("par").withReferencedColumnName("pid");
		assertEquals("fk2", foreignKey.getForeignKeyName());
		assertEquals("c", foreignKey.getTableName());
		assertEquals("p", foreignKey.getColumnName());
		assertEquals("par", foreignKey.getReferencedTableName());
		assertEquals("pid", foreignKey.getReferencedColumnName());
		assertEquals("fk2: c(p) references par(pid)", foreignKey.toString());
	}

	@Test
	public void testDatabaseConstraint() throws Exception {
		assertEquals(ConstraintType.PrimaryKey, ConstraintType.fromName("primary_key"));
		assertEquals(ConstraintType.ForeignKey, ConstraintType.fromName("Foreign Key"));
		assertEquals(ConstraintType.Check, ConstraintType.fromName("check"));
		assertEquals(ConstraintType.PrimaryKey, ConstraintType.fromName("p"));
		assertEquals(ConstraintType.Unique, ConstraintType.fromName("U"));
		assertEquals(ConstraintType.ForeignKey, ConstraintType.fromName("f"));
		assertEquals(ConstraintType.Check, ConstraintType.fromName("C"));
		assertEquals(ConstraintType.Relation, ConstraintType.fromName("R"));
		assertThrows(Exception.class, () -> ConstraintType.fromName("x"));
		assertThrows(Exception.class, () -> ConstraintType.fromName("unknown"));

		final DatabaseConstraint constraint = new DatabaseConstraint("tab", "ck_value", ConstraintType.Check, null, "value > 0");
		assertEquals("tab ck_value Check value > 0", constraint.toString());
		constraint.withTableName("t").withConstraintName("pk").withConstraintType(ConstraintType.PrimaryKey).withColumnName("id").withCondition(null);
		assertEquals("t", constraint.getTableName());
		assertEquals("pk", constraint.getConstraintName());
		assertEquals(ConstraintType.PrimaryKey, constraint.getConstraintType());
		assertEquals("id", constraint.getColumnName());
		assertNull(constraint.getCondition());
		assertEquals("t pk PrimaryKey id", constraint.toString());
	}

	@Test
	public void testDatabaseIndex() {
		final DatabaseIndex index = new DatabaseIndex("tab", "ix_tab", List.of("a", "b"));
		assertEquals("tab ix_tab (a, b)", index.toString());
		index.withTableName("t").withIndexName("ix").withIndexedColumns(List.of("c"));
		assertEquals("t", index.getTableName());
		assertEquals("ix", index.getIndexName());
		assertEquals(List.of("c"), index.getIndexedColumns());
		assertEquals("t ix (c)", index.toString());
	}

	@Test
	public void testExceptions() {
		final Exception cause = new Exception("cause");
		assertEquals("message", new DbDefinitionException("message").getMessage());
		assertSame(cause, new DbDefinitionException("message", cause).getCause());
		assertEquals("structure", new DbStructureException("structure").getMessage());

		assertNull(new DbNotExistsException().getMessage());
		assertEquals("missing", new DbNotExistsException("missing").getMessage());
		assertSame(cause, new DbNotExistsException("missing", cause).getCause());
		assertSame(cause, new DbNotExistsException(cause).getCause());
		final DbNotExistsException noStackTraceException = new DbNotExistsException("missing", cause, false, false);
		assertEquals(0, noStackTraceException.getStackTrace().length);
	}
}
