package de.soderer.utilities.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.data.DbSchema;
import de.soderer.utilities.db.data.DbStructure;
import de.soderer.utilities.db.data.DbTable;

/**
 * Tests for owners, primary key names and the pg_dump specific syntax in {@link SqlDdlParser},
 * {@link SqlDdlMigrationGenerator} and {@link SqlDdlMergeGenerator}.
 */
@DisplayName("Owners, primary key names and pg_dump syntax")
class SqlDdlOwnerAndPgDumpTest {

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static InputStream stream(final String ddl) {
		return new ByteArrayInputStream(ddl.getBytes(StandardCharsets.UTF_8));
	}

	private static DbStructure parse(final String ddl) throws Exception {
		return SqlDdlParser.parse(stream(ddl));
	}

	private static DbSchema schema(final DbStructure structure, final String schemaName) {
		final DbSchema schema = structure.getSchemas().get(schemaName);
		assertNotNull(schema, "Schema not found: " + schemaName);
		return schema;
	}

	private static DbTable table(final DbStructure structure, final String schemaName, final String tableName) {
		final DbTable table = schema(structure, schemaName).getTables().get(tableName);
		assertNotNull(table, "Table not found: " + schemaName + "." + tableName);
		return table;
	}

	private static String diff(final String sourceDdl, final String destinationDdl) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMigrationGenerator.diff(stream(sourceDdl), stream(destinationDdl), out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static String merge(final String ddlA, final String ddlB) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMergeGenerator.merge(stream(ddlA), stream(ddlB), out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static void assertContains(final String output, final String fragment) {
		assertTrue(output.contains(fragment), "Expected output to contain: «" + fragment + "»\nActual output:\n" + output);
	}

	private static void assertNotContains(final String output, final String fragment) {
		assertFalse(output.contains(fragment), "Expected output NOT to contain: «" + fragment + "»\nActual output:\n" + output);
	}

	// -------------------------------------------------------------------------
	// Parser
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Parser: owners")
	class ParserOwners {
		@Test
		@DisplayName("CREATE SCHEMA … AUTHORIZATION sets the schema owner")
		void createSchemaAuthorization() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA s1 AUTHORIZATION app_owner;");
			assertEquals("app_owner", schema(structure, "s1").getOwner());
		}

		@Test
		@DisplayName("CREATE SCHEMA AUTHORIZATION without name creates a schema named like the owner")
		void createSchemaAuthorizationOnly() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA AUTHORIZATION app_owner;");
			assertEquals("app_owner", schema(structure, "app_owner").getOwner());
		}

		@Test
		@DisplayName("ALTER SCHEMA … OWNER TO sets the schema owner, quoted owners keep their case")
		void alterSchemaOwner() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA s1; ALTER SCHEMA s1 OWNER TO \"Other_Role\";");
			assertEquals("Other_Role", schema(structure, "s1").getOwner());
		}

		@Test
		@DisplayName("ALTER TABLE [ONLY] … OWNER TO sets the table owner")
		void alterTableOwner() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA s1;"
					+ " CREATE TABLE s1.t1 (id integer);"
					+ " CREATE TABLE s1.t2 (id integer);"
					+ " ALTER TABLE s1.t1 OWNER TO owner1;"
					+ " ALTER TABLE ONLY s1.t2 OWNER TO owner2;");
			assertEquals("owner1", table(structure, "s1", "t1").getOwner());
			assertEquals("owner2", table(structure, "s1", "t2").getOwner());
		}

		@Test
		@DisplayName("Without OWNER TO the owner is null")
		void noOwner() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA s1; CREATE TABLE s1.t1 (id integer);");
			assertNull(schema(structure, "s1").getOwner());
			assertNull(table(structure, "s1", "t1").getOwner());
		}
	}

	@Nested
	@DisplayName("Parser: pg_dump syntax")
	class ParserPgDump {
		@Test
		@DisplayName("ALTER TABLE ONLY … ADD CONSTRAINT … PRIMARY KEY is read with its name")
		void alterTableOnlyPrimaryKey() throws Exception {
			final DbStructure structure = parse("CREATE TABLE public.t1 (id integer NOT NULL);"
					+ " ALTER TABLE ONLY public.t1\n    ADD CONSTRAINT t1_pkey PRIMARY KEY (id);");
			final DbTable table = table(structure, "public", "t1");
			assertEquals(Arrays.asList("id"), table.getPrimaryKey());
			assertEquals("t1_pkey", table.getPrimaryKeyName());
		}

		@Test
		@DisplayName("Primary key name of a CREATE TABLE constraint is read")
		void createTablePrimaryKeyName() throws Exception {
			final DbStructure structure = parse("CREATE TABLE public.t1 (id integer, CONSTRAINT my_pk PRIMARY KEY (id));");
			assertEquals("my_pk", table(structure, "public", "t1").getPrimaryKeyName());
		}

		@Test
		@DisplayName("Inline PRIMARY KEY has no name")
		void inlinePrimaryKeyWithoutName() throws Exception {
			final DbStructure structure = parse("CREATE TABLE public.t1 (id integer PRIMARY KEY);");
			assertEquals(Arrays.asList("id"), table(structure, "public", "t1").getPrimaryKey());
			assertNull(table(structure, "public", "t1").getPrimaryKeyName());
		}

		@Test
		@DisplayName("DROP CONSTRAINT with the primary key name removes the primary key")
		void dropPrimaryKeyConstraint() throws Exception {
			final DbStructure structure = parse("CREATE TABLE public.t1 (id integer, CONSTRAINT t1_pkey PRIMARY KEY (id));"
					+ " ALTER TABLE public.t1 DROP CONSTRAINT IF EXISTS t1_pkey;");
			assertNull(table(structure, "public", "t1").getPrimaryKey());
			assertNull(table(structure, "public", "t1").getPrimaryKeyName());
		}

		@Test
		@DisplayName("ALTER COLUMN … ADD GENERATED … AS IDENTITY keeps the column type and sets auto increment")
		void identityColumn() throws Exception {
			final DbStructure structure = parse("CREATE TABLE public.t1 (id bigint NOT NULL);\n"
					+ "ALTER TABLE public.t1 ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (\n"
					+ "    SEQUENCE NAME public.t1_id_seq\n    START WITH 1\n    INCREMENT BY 1\n    NO MINVALUE\n    NO MAXVALUE\n    CACHE 1\n);");
			final DbTable table = table(structure, "public", "t1");
			assertEquals("bigint", table.getColumns().get("id").getColumnType().getTypeName().toLowerCase());
			assertTrue(table.getColumns().get("id").getColumnType().isAutoIncrement());
			assertFalse(table.getColumns().get("id").getColumnType().isNullable());
		}

		@Test
		@DisplayName("Semicolons and statements within $$ function bodies are no statements")
		void dollarQuotedFunctionBody() throws Exception {
			final DbStructure structure = parse("CREATE SCHEMA s1;\n"
					+ "CREATE FUNCTION s1.f() RETURNS void LANGUAGE plpgsql AS $$ begin\n"
					+ "  -- it's a comment with apostrophe\n"
					+ "  execute 'x'; CREATE TABLE evil (a int); end; $$;\n"
					+ "CREATE FUNCTION s1.g(a integer) RETURNS integer LANGUAGE sql AS $_$ select $1 + 1; $$ $_$;\n"
					+ "CREATE TABLE s1.real (id integer, CONSTRAINT real_pkey PRIMARY KEY (id));");
			assertNull(structure.getSchemas().get(""), "No table may be read from a function body");
			assertEquals(1, schema(structure, "s1").getTables().size());
			assertEquals("real_pkey", table(structure, "s1", "real").getPrimaryKeyName());
		}

		@Test
		@DisplayName("psql meta commands like \\restrict are ignored")
		void psqlMetaCommands() throws Exception {
			final DbStructure structure = parse("\\restrict abc123\n\nCREATE SCHEMA s1 AUTHORIZATION app_owner;\n"
					+ "CREATE TABLE s1.t1 (id integer);\n\\unrestrict abc123\n");
			assertEquals("app_owner", schema(structure, "s1").getOwner());
			assertNotNull(table(structure, "s1", "t1"));
		}
	}

	// -------------------------------------------------------------------------
	// Migration
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Migration")
	class Migration {
		private static final String SOURCE = "CREATE SCHEMA s1; ALTER SCHEMA s1 OWNER TO owner_a;"
				+ " CREATE TABLE s1.t1 (id integer NOT NULL); ALTER TABLE ONLY s1.t1 OWNER TO owner_a;"
				+ " ALTER TABLE ONLY s1.t1 ADD CONSTRAINT t1_pkey PRIMARY KEY (id);";

		@Test
		@DisplayName("Different owners in both files => OWNER TO with the destination owner")
		void ownerChanged() throws Exception {
			final String output = diff(SOURCE, SOURCE.replace("owner_a", "owner_b"));
			assertContains(output, "ALTER SCHEMA s1 OWNER TO owner_b;");
			assertContains(output, "ALTER TABLE s1.t1 OWNER TO owner_b;");
		}

		@Test
		@DisplayName("Equal owners => no OWNER TO")
		void ownerUnchanged() throws Exception {
			final String output = diff(SOURCE, SOURCE);
			assertNotContains(output, "OWNER TO");
			assertContains(output, "No structural differences detected.");
		}

		@Test
		@DisplayName("Owner missing in one file (e.g. pg_dump --no-owner) => no OWNER TO")
		void ownerMissing() throws Exception {
			final String withoutOwners = SOURCE.replace(" ALTER SCHEMA s1 OWNER TO owner_a;", "").replace(" ALTER TABLE ONLY s1.t1 OWNER TO owner_a;", "");
			assertNotContains(diff(SOURCE, withoutOwners), "OWNER TO");
			assertNotContains(diff(withoutOwners, SOURCE), "OWNER TO");
		}

		@Test
		@DisplayName("New schema and table get their owner")
		void newObjectsWithOwner() throws Exception {
			final String destination = SOURCE + " CREATE SCHEMA s2 AUTHORIZATION owner_c;"
					+ " CREATE TABLE s2.t2 (id integer); ALTER TABLE ONLY s2.t2 OWNER TO owner_d;";
			final String output = diff(SOURCE, destination);
			assertContains(output, "CREATE SCHEMA s2;");
			assertContains(output, "ALTER SCHEMA s2 OWNER TO owner_c;");
			assertContains(output, "ALTER TABLE s2.t2 OWNER TO owner_d;");
		}

		@Test
		@DisplayName("Changed primary key is dropped and created with its real name")
		void primaryKeyWithRealName() throws Exception {
			final String destination = SOURCE.replace("PRIMARY KEY (id)", "PRIMARY KEY (id, name)").replace("(id integer NOT NULL)", "(id integer NOT NULL, name varchar(10) NOT NULL)");
			final String output = diff(SOURCE, destination);
			assertContains(output, "DROP CONSTRAINT IF EXISTS t1_pkey;");
			assertContains(output, "ADD CONSTRAINT t1_pkey PRIMARY KEY (id, name);");
			assertNotContains(output, "pk_t1");
		}

		@Test
		@DisplayName("Primary key without name uses \"pk_<table>\" as before")
		void primaryKeyWithoutName() throws Exception {
			final String output = diff("CREATE TABLE public.t1 (id integer PRIMARY KEY, x integer);", "CREATE TABLE public.t1 (id integer, x integer, PRIMARY KEY (id, x));");
			assertContains(output, "DROP CONSTRAINT IF EXISTS pk_t1;");
			assertContains(output, "ADD CONSTRAINT pk_t1 PRIMARY KEY (id, x);");
		}

		@Test
		@DisplayName("Owner changes are counted in the statistics")
		void ownerStatistics() throws Exception {
			final String output = diff(SOURCE, SOURCE.replace("owner_a", "owner_b"));
			assertContains(output, "-- Owners");
			assertTrue(output.matches("(?s).*--\\s+changed\\s+:\\s+2\\b.*"), output);
		}
	}

	// -------------------------------------------------------------------------
	// Merge
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Merge")
	class Merge {
		@Test
		@DisplayName("Owner of B wins over A")
		void ownerBWins() throws Exception {
			final String a = "CREATE SCHEMA s1 AUTHORIZATION owner_a; CREATE TABLE s1.t1 (id integer); ALTER TABLE s1.t1 OWNER TO owner_a;";
			final String b = "CREATE SCHEMA s1 AUTHORIZATION owner_b; CREATE TABLE s1.t1 (id integer); ALTER TABLE s1.t1 OWNER TO owner_b;";
			final String output = merge(a, b);
			assertContains(output, "ALTER SCHEMA s1 OWNER TO owner_b;");
			assertContains(output, "ALTER TABLE s1.t1 OWNER TO owner_b;");
			assertNotContains(output, "owner_a;");
		}

		@Test
		@DisplayName("Owner of A is kept, if B defines none")
		void ownerFromA() throws Exception {
			final String a = "CREATE SCHEMA s1 AUTHORIZATION owner_a; CREATE TABLE s1.t1 (id integer); ALTER TABLE s1.t1 OWNER TO owner_a;";
			final String b = "CREATE SCHEMA s1; CREATE TABLE s1.t1 (id integer);";
			final String output = merge(a, b);
			assertContains(output, "ALTER SCHEMA s1 OWNER TO owner_a;");
			assertContains(output, "ALTER TABLE s1.t1 OWNER TO owner_a;");
		}

		@Test
		@DisplayName("Schemas are created with IF NOT EXISTS")
		void createSchemaIfNotExists() throws Exception {
			assertContains(merge("CREATE TABLE public.t1 (id integer);", ""), "CREATE SCHEMA IF NOT EXISTS public;");
		}

		@Test
		@DisplayName("Primary key keeps its name")
		void primaryKeyName() throws Exception {
			final String output = merge("CREATE TABLE public.t1 (id integer, CONSTRAINT t1_pkey PRIMARY KEY (id));", "CREATE TABLE public.t2 (id integer PRIMARY KEY);");
			assertContains(output, "CONSTRAINT t1_pkey PRIMARY KEY (id)");
			assertContains(output, "CONSTRAINT pk_t2 PRIMARY KEY (id)");
		}
	}
}
