package de.soderer.utilities.db;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlDdlMigrationGenerator}.
 *
 * <p>Statistics assertions search for the exact label strings as written by
 * {@code MigrationStatistics.writeTo()}, e.g. {@code "Schemas  created"}.
 * Since statistics lines are only written when their counter is greater than
 * zero, tests now verify that a label is present only when a relevant change
 * was actually detected, and absent in a zero-change run.
 *
 * <p>Table-level PRIMARY KEY / UNIQUE / FOREIGN KEY constraints are always
 * expressed via separate ALTER TABLE statements to avoid a known parser
 * limitation with the DEFAULT-value regex when closing parentheses appear
 * inside the column block.
 */
@DisplayName("SqlDdlMigrationGenerator")
class SqlDdlMigrationGeneratorTest {

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String diff(final String sourceDdl, final String destinationDdl) throws Exception {
		final InputStream src = stream(sourceDdl);
		final InputStream dst = stream(destinationDdl);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMigrationGenerator.diff(src, dst, out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static String diffWithSort(final String sourceDdl, final String destinationDdl,
			final boolean sortBySchema, final boolean sortByTable, final boolean sortByColumn) throws Exception {
		final InputStream src = stream(sourceDdl);
		final InputStream dst = stream(destinationDdl);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMigrationGenerator.diff(src, dst, out, sortBySchema, sortByTable, sortByColumn);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static InputStream stream(final String ddl) {
		return new ByteArrayInputStream(ddl.getBytes(StandardCharsets.UTF_8));
	}

	private static void assertContains(final String output, final String fragment) {
		assertTrue(output.toLowerCase().contains(fragment.toLowerCase()),
				"Expected output to contain: «" + fragment + "»\nActual output:\n" + output);
	}

	private static void assertNotContains(final String output, final String fragment) {
		assertFalse(output.toLowerCase().contains(fragment.toLowerCase()),
				"Expected output NOT to contain: «" + fragment + "»\nActual output:\n" + output);
	}

	/**
	 * Extracts the numeric counter value from a statistics line that contains
	 * {@code label}. E.g. for label {@code "created"} and line
	 * {@code "--  Schemas  created  : 3"} this returns 3.
	 * Throws {@link AssertionError} if the label is not found (i.e. counter was 0
	 * and the line was omitted).
	 */
	private static int statValue(final String output, final String label) {
		for (final String line : output.split("\n")) {
			if (line.toLowerCase().contains(label.toLowerCase()) && line.contains(":")) {
				final String after = line.substring(line.lastIndexOf(':') + 1).trim();
				try {
					return Integer.parseInt(after);
				} catch (@SuppressWarnings("unused") final NumberFormatException ignored) {
					// not a numeric line
				}
			}
		}
		throw new AssertionError("Statistics label not found in output: «" + label + "»\n" + output);
	}

	/**
	 * Returns {@code true} when a statistics line for {@code label} is present
	 * in the output (meaning the counter was > 0).
	 */
	private static boolean statPresent(final String output, final String label) {
		for (final String line : output.split("\n")) {
			if (line.toLowerCase().contains(label.toLowerCase()) && line.contains(":")) {
				final String after = line.substring(line.lastIndexOf(':') + 1).trim();
				try {
					Integer.parseInt(after);
					return true;
				} catch (@SuppressWarnings("unused") final NumberFormatException ignored) {
					// not a numeric counter line
				}
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// No differences
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("No differences")
	class NoDifferences {

		@Test
		@DisplayName("identical schemas produce no structural statements")
		void identical() throws Exception {
			final String ddl = """
					CREATE SCHEMA myschema;
					CREATE TABLE myschema.users (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100) NOT NULL
					);
					ALTER TABLE myschema.users ADD CONSTRAINT pk_users PRIMARY KEY (id);
					""";
			final String output = diff(ddl, ddl);
			assertContains(output, "No structural differences detected");
		}
	}

	// -------------------------------------------------------------------------
	// Schema-level changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Schema changes")
	class SchemaChanges {

		@Test
		@DisplayName("new schema in destination => CREATE SCHEMA")
		void newSchema() throws Exception {
			final String output = diff("", "CREATE SCHEMA newschema;");
			assertContains(output, "CREATE SCHEMA");
			assertContains(output, "newschema");
		}

		@Test
		@DisplayName("schema removed in destination => DROP SCHEMA")
		void droppedSchema() throws Exception {
			final String src = """
					CREATE SCHEMA oldschema;
					CREATE TABLE oldschema.t (id INTEGER NOT NULL);
					""";
			final String output = diff(src, "");
			assertAll(
					() -> assertContains(output, "DROP TABLE"),
					() -> assertContains(output, "DROP SCHEMA"),
					() -> assertContains(output, "oldschema")
			);
		}

		@Test
		@DisplayName("statistics: Schemas dropped line present when > 0")
		void statsDroppedSchema() throws Exception {
			final String src = """
					CREATE SCHEMA s1;
					CREATE TABLE s1.t (id INTEGER NOT NULL);
					""";
			final String output = diff(src, "");
			assertTrue(statPresent(output, "dropped"), "Expected 'dropped' statistics line to be present");
			assertTrue(statValue(output, "dropped") > 0, "Expected schemas dropped > 0");
		}

		@Test
		@DisplayName("statistics: Schemas created line present when > 0")
		void statsCreatedSchema() throws Exception {
			final String output = diff("", "CREATE SCHEMA s2;");
			assertTrue(output.contains("-- Schemas"), "Expected 'Schemas' line to be present");
			assertTrue(statPresent(output, "         created"), "Expected 'created' line to be present");
			assertTrue(statValue(output, "         created") > 0, "Expected schemas created > 0");
		}

		@Test
		@DisplayName("statistics: Schemas created line absent when no schema created")
		void statsCreatedSchemaAbsent() throws Exception {
			final String ddl = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(ddl, ddl);
			assertFalse(statPresent(output, "Schemas  created"),
					"Expected 'Schemas  created' statistics line to be absent in zero-change diff");
		}
	}

	// -------------------------------------------------------------------------
	// Table-level changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Table changes")
	class TableChanges {

		@Test
		@DisplayName("new table in destination => CREATE TABLE")
		void newTable() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.existing (id INTEGER NOT NULL);";
			final String dst = src + " CREATE TABLE s.newtable (id INTEGER NOT NULL, val VARCHAR(50));";
			final String output = diff(src, dst);
			assertContains(output, "CREATE TABLE");
			assertContains(output, "newtable");
		}

		@Test
		@DisplayName("table removed in destination => DROP TABLE")
		void droppedTable() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.gone (id INTEGER NOT NULL);
					""";
			final String output = diff(src, "CREATE SCHEMA s;");
			assertContains(output, "DROP TABLE");
			assertContains(output, "gone");
		}

		@Test
		@DisplayName("statistics: Tables created line present when > 0")
		void statsTablesCreated() throws Exception {
			final String src = "CREATE SCHEMA s;";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.newt (id INTEGER NOT NULL);";
			final String output = diff(src, dst);
			assertTrue(output.contains("-- Tables"), "Expected 'Tables' line to be present");
			assertTrue(statPresent(output, "         created"), "Expected 'created' line to be present");
			assertTrue(statValue(output, "         created") > 0, "Expected tables created > 0");
		}

		@Test
		@DisplayName("statistics: Tables dropped line present when > 0")
		void statsTablesDropped() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.old (id INTEGER NOT NULL);";
			final String dst = "CREATE SCHEMA s;";
			final String output = diff(src, dst);
			assertTrue(statPresent(output, "dropped"), "Expected 'dropped' line to be present");
			assertContains(output, "DROP TABLE");
		}
	}

	// -------------------------------------------------------------------------
	// Column-level changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Column changes")
	class ColumnChanges {

		private static final String BASE = """
				CREATE SCHEMA s;
				CREATE TABLE s.t (
				    id   INTEGER NOT NULL,
				    name VARCHAR(100) NOT NULL
				);
				""";

		@Test
		@DisplayName("new column => ADD COLUMN")
		void addColumn() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id    INTEGER NOT NULL,
					    name  VARCHAR(100) NOT NULL,
					    email VARCHAR(200)
					);
					""";
			final String output = diff(BASE, dst);
			assertContains(output, "ADD COLUMN");
			assertContains(output, "email");
		}

		@Test
		@DisplayName("removed column => DROP COLUMN")
		void dropColumn() throws Exception {
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(BASE, dst);
			assertContains(output, "DROP COLUMN");
			assertContains(output, "name");
		}

		@Test
		@DisplayName("changed column type => ALTER COLUMN TYPE")
		void changeColumnType() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(255) NOT NULL
					);
					""";
			final String output = diff(BASE, dst);
			assertContains(output, "ALTER COLUMN");
			assertContains(output, "TYPE");
			assertContains(output, "VARCHAR(255)");
		}

		@Test
		@DisplayName("nullability added => DROP NOT NULL")
		void makeNullable() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100)
					);
					""";
			final String output = diff(BASE, dst);
			assertContains(output, "DROP NOT NULL");
		}

		@Test
		@DisplayName("nullability removed => SET NOT NULL")
		void makeNotNullable() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100)
					);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100) NOT NULL
					);
					""";
			final String output = diff(src, dst);
			assertContains(output, "SET NOT NULL");
		}

		@Test
		@DisplayName("default value added => SET DEFAULT")
		void addDefault() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100) NOT NULL DEFAULT 'anonymous'
					);
					""";
			final String output = diff(BASE, dst);
			assertContains(output, "SET DEFAULT");
		}

		@Test
		@DisplayName("default value removed => DROP DEFAULT")
		void dropDefault() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100) NOT NULL DEFAULT 'anonymous'
					);
					""";
			final String output = diff(src, BASE);
			assertContains(output, "DROP DEFAULT");
		}

		@Test
		@DisplayName("statistics: Columns added line present when > 0")
		void statsColumnsAdded() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id    INTEGER NOT NULL,
					    name  VARCHAR(100) NOT NULL,
					    email VARCHAR(200)
					);
					""";
			final String output = diff(BASE, dst);
			assertTrue(output.contains("-- Columns"), "Expected 'Columns' line to be present");
			assertTrue(statPresent(output, "         added"), "Expected 'added' line to be present");
			assertTrue(statValue(output, "         added") > 0, "Expected columns added > 0");
		}

		@Test
		@DisplayName("statistics: Columns dropped line present when > 0")
		void statsColumnsDropped() throws Exception {
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(BASE, dst);
			assertTrue(statPresent(output, "dropped"), "Expected 'dropped' line to be present");
			assertContains(output, "DROP COLUMN");
		}

		@Test
		@DisplayName("statistics: Columns type line present when > 0")
		void statsTypeChanged() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(255) NOT NULL
					);
					""";
			final String output = diff(BASE, dst);
			assertTrue(statPresent(output, "type     :"), "Expected 'type' line to be present");
			assertTrue(statValue(output, "type     :") > 0, "Expected columns type changed > 0");
		}

		@Test
		@DisplayName("statistics: Columns nullable line present when > 0")
		void statsNullChanged() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100)
					);
					""";
			final String output = diff(BASE, dst);
			assertTrue(statPresent(output, "nullable :"), "Expected 'nullable' line to be present");
			assertTrue(statValue(output, "nullable :") > 0, "Expected columns nullable changed > 0");
		}

		@Test
		@DisplayName("statistics: Columns default line present when > 0")
		void statsDefaultChanged() throws Exception {
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (
					    id   INTEGER NOT NULL,
					    name VARCHAR(100) NOT NULL DEFAULT 'x'
					);
					""";
			final String output = diff(BASE, dst);
			assertTrue(statPresent(output, "default  :"), "Expected 'default' line to be present");
			assertTrue(statValue(output, "default  :") > 0, "Expected columns default changed > 0");
		}
	}

	// -------------------------------------------------------------------------
	// Primary key changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Primary key changes")
	class PrimaryKeyChanges {

		@Test
		@DisplayName("PK added => ADD PRIMARY KEY")
		void addPrimaryKey() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (id);
					""";
			final String output = diff(src, dst);
			assertContains(output, "ADD CONSTRAINT");
			assertContains(output, "PRIMARY KEY");
		}

		@Test
		@DisplayName("PK removed => DROP CONSTRAINT")
		void dropPrimaryKey() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (id);
					""";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(src, dst);
			assertContains(output, "DROP CONSTRAINT");
		}

		@Test
		@DisplayName("PK column changed => DROP + ADD")
		void changePrimaryKey() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, code VARCHAR(10) NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (id);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, code VARCHAR(10) NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (code);
					""";
			final String output = diff(src, dst);
			assertAll(
					() -> assertContains(output, "DROP CONSTRAINT"),
					() -> assertContains(output, "ADD CONSTRAINT"),
					() -> assertContains(output, "PRIMARY KEY")
			);
		}

		@Test
		@DisplayName("statistics: PK changed line present when > 0")
		void statsPkChanged() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, code INTEGER NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (id);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, code INTEGER NOT NULL);
					ALTER TABLE s.t ADD CONSTRAINT pk_t PRIMARY KEY (code);
					""";
			final String output = diff(src, dst);
			assertTrue(output.contains("-- PK"), "Expected 'PK' line to be present");
			assertTrue(statPresent(output, "         changed"), "Expected 'changed' line to be present");
			assertTrue(statValue(output, "         changed") > 0, "Expected PK changed > 0");
		}
	}

	// -------------------------------------------------------------------------
	// Unique key changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Unique key changes")
	class UniqueKeyChanges {

		@Test
		@DisplayName("unique key added => ADD CONSTRAINT UNIQUE")
		void addUniqueKey() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));
					ALTER TABLE s.t ADD CONSTRAINT uq_email UNIQUE (email);
					""";
			final String output = diff(src, dst);
			assertAll(
					() -> assertContains(output, "ADD CONSTRAINT"),
					() -> assertContains(output, "UNIQUE"),
					() -> assertContains(output, "uq_email")
			);
		}

		@Test
		@DisplayName("unique key removed => DROP CONSTRAINT")
		void dropUniqueKey() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));
					ALTER TABLE s.t ADD CONSTRAINT uq_email UNIQUE (email);
					""";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));";
			final String output = diff(src, dst);
			assertContains(output, "DROP CONSTRAINT");
			assertContains(output, "uq_email");
		}

		@Test
		@DisplayName("statistics: Unique added line present when > 0")
		void statsUniqueAdded() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));
					ALTER TABLE s.t ADD CONSTRAINT uq_email UNIQUE (email);
					""";
			final String output = diff(src, dst);
			assertTrue(output.contains("-- Unique"), "Expected 'Unique' line to be present");
			assertTrue(statPresent(output, "         added"), "Expected 'added' line to be present");
			assertTrue(statValue(output, "         added") > 0, "Expected unique added > 0");
		}

		@Test
		@DisplayName("statistics: Unique dropped line present when > 0")
		void statsUniqueDropped() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));
					ALTER TABLE s.t ADD CONSTRAINT uq_email UNIQUE (email);
					""";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, email VARCHAR(100));";
			final String output = diff(src, dst);
			assertTrue(statPresent(output, "dropped"), "Expected 'dropped' line to be present");
			assertContains(output, "DROP CONSTRAINT");
		}
	}

	// -------------------------------------------------------------------------
	// Foreign key changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Foreign key changes")
	class ForeignKeyChanges {

		@Test
		@DisplayName("foreign key added => ADD FOREIGN KEY")
		void addForeignKey() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.dept ADD CONSTRAINT pk_dept PRIMARY KEY (id);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.dept ADD CONSTRAINT pk_dept PRIMARY KEY (id);
					ALTER TABLE s.emp ADD CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES dept (id);
					""";
			final String output = diff(src, dst);
			assertAll(
					() -> assertContains(output, "FOREIGN KEY"),
					() -> assertContains(output, "fk_emp_dept")
			);
		}

		@Test
		@DisplayName("foreign key removed => DROP CONSTRAINT")
		void dropForeignKey() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.dept ADD CONSTRAINT pk_dept PRIMARY KEY (id);
					ALTER TABLE s.emp ADD CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES dept (id);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.dept ADD CONSTRAINT pk_dept PRIMARY KEY (id);
					""";
			final String output = diff(src, dst);
			assertAll(
					() -> assertContains(output, "DROP CONSTRAINT"),
					() -> assertContains(output, "fk_emp_dept")
			);
		}

		@Test
		@DisplayName("statistics: FK added line present when > 0")
		void statsFkAdded() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.emp ADD CONSTRAINT fk_new FOREIGN KEY (dept_id) REFERENCES dept (id);
					""";
			final String output = diff(src, dst);
			assertTrue(output.contains("-- FK"), "Expected 'FK' line to be present");
			assertTrue(statPresent(output, "         added"), "Expected 'added' line to be present");
			assertTrue(statValue(output, "         added") > 0, "Expected FK added > 0");
		}

		@Test
		@DisplayName("statistics: FK dropped line present when > 0")
		void statsFkDropped() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					ALTER TABLE s.emp ADD CONSTRAINT fk_old FOREIGN KEY (dept_id) REFERENCES dept (id);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.dept (id INTEGER NOT NULL);
					CREATE TABLE s.emp (id INTEGER NOT NULL, dept_id INTEGER);
					""";
			final String output = diff(src, dst);
			assertTrue(statPresent(output, "dropped"), "Expected 'dropped' line to be present");
			assertContains(output, "DROP CONSTRAINT");
		}
	}

	// -------------------------------------------------------------------------
	// Comment changes
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Comment changes")
	class CommentChanges {

		@Test
		@DisplayName("table comment added => COMMENT ON TABLE")
		void tableCommentAdded() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'User table';
					""";
			final String output = diff(src, dst);
			assertContains(output, "COMMENT ON TABLE");
		}

		@Test
		@DisplayName("column comment added => COMMENT ON COLUMN")
		void columnCommentAdded() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON COLUMN s.t.id IS 'Primary key';
					""";
			final String output = diff(src, dst);
			assertContains(output, "COMMENT ON COLUMN");
		}

		@Test
		@DisplayName("table comment changed => COMMENT ON TABLE in output")
		void tableCommentChanged() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'Old comment';
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'New comment';
					""";
			final String output = diff(src, dst);
			assertContains(output, "COMMENT ON TABLE");
			assertContains(output, "New comment");
		}

		@Test
		@DisplayName("statistics: Comments changed line present when > 0")
		void statsComments() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'Old';
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'New';
					""";
			final String output = diff(src, dst);
			assertTrue(output.contains("-- Comments"), "Expected 'Comments' line to be present");
			assertTrue(statPresent(output, "         changed"), "Expected 'changed' line to be present");
			assertTrue(statValue(output, "         changed") > 0, "Expected comments changed > 0");
		}
	}

	// -------------------------------------------------------------------------
	// Statistics block
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Statistics block")
	class StatisticsBlock {

		@Test
		@DisplayName("statistics header is always present")
		void headerAlwaysPresent() throws Exception {
			final String output = diff("", "");
			assertContains(output, "Migration Statistics");
		}

		@Test
		@DisplayName("statistics appear before SQL statements")
		void statisticsBeforeStatements() throws Exception {
			final String output = diff("", "CREATE SCHEMA s;");
			final int statsPos = output.toLowerCase().indexOf("migration statistics");
			final int stmtPos  = output.toLowerCase().indexOf("create schema");
			assertTrue(statsPos < stmtPos, "Statistics block must appear before SQL statements");
		}

		@Test
		@DisplayName("zero-change run: no counter lines appear in statistics block")
		void zeroCounters() throws Exception {
			final String ddl = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(ddl, ddl);
			// Extract statistics block
			final int start   = output.indexOf("Migration Statistics");
			final int endSep  = output.indexOf("-- =====", start + 20);
			final int endSep2 = output.indexOf("-- =====", endSep + 10);
			final String statsBlock = output.substring(start, endSep2 > endSep ? endSep2 : output.length());
			for (final String line : statsBlock.split("\n")) {
				if (line.trim().startsWith("--  ") && line.contains(":")) {
					assertTrue(false, "Unexpected counter line in zero-change statistics: " + line);
				}
			}
		}

		@Test
		@DisplayName("zero-change run: no structural differences message is present")
		void zeroChangesMessage() throws Exception {
			final String ddl = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(ddl, ddl);
			assertContains(output, "No structural differences detected");
		}

		@Test
		@DisplayName("active counter line present; inactive counter line absent")
		void activeVsInactiveCounter() throws Exception {
			// Only schemas created → schemas dropped line must not appear
			final String output = diff("", "CREATE SCHEMA s2;");
			assertTrue(output.contains("-- Schemas"), "Expected 'Schemas' to be present");
			assertTrue(statPresent(output, "         created"), "Expected 'created' to be present");
			assertFalse(statPresent(output, "dropped"), "Expected 'dropped' to be absent when nothing was dropped");
		}
	}

	// -------------------------------------------------------------------------
	// Edge cases
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("both inputs empty => no differences")
		void bothEmpty() throws Exception {
			final String output = diff("", "");
			assertContains(output, "No structural differences detected");
		}

		@Test
		@DisplayName("table without schema (default schema)")
		void tableWithoutSchema() throws Exception {
			final String src = "CREATE TABLE t (id INTEGER NOT NULL);";
			final String dst = "CREATE TABLE t (id INTEGER NOT NULL, name VARCHAR(50));";
			final String output = diff(src, dst);
			assertContains(output, "ADD COLUMN");
			assertContains(output, "name");
		}

		@Test
		@DisplayName("multiple tables changed independently")
		void multipleTablesChanged() throws Exception {
			final String src = """
					CREATE SCHEMA s;
					CREATE TABLE s.a (id INTEGER NOT NULL, x VARCHAR(10));
					CREATE TABLE s.b (id INTEGER NOT NULL, y INTEGER);
					""";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.a (id INTEGER NOT NULL, x VARCHAR(20));
					CREATE TABLE s.b (id INTEGER NOT NULL, y INTEGER, z BOOLEAN);
					""";
			final String output = diff(src, dst);
			assertAll(
					() -> assertContains(output, "ALTER COLUMN"),
					() -> assertContains(output, "ADD COLUMN"),
					() -> assertContains(output, "z")
			);
		}

		@Test
		@DisplayName("column name casing is handled case-insensitively")
		void columnCaseInsensitive() throws Exception {
			final String src = "CREATE SCHEMA s; CREATE TABLE s.t (ID INTEGER NOT NULL);";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = diff(src, dst);
			assertNotContains(output, "ADD COLUMN");
			assertNotContains(output, "DROP COLUMN");
		}

		@Test
		@DisplayName("schema comment changed => COMMENT ON SCHEMA in output")
		void schemaCommentChanged() throws Exception {
			final String src = "CREATE SCHEMA s; COMMENT ON SCHEMA s IS 'Old';";
			final String dst = "CREATE SCHEMA s; COMMENT ON SCHEMA s IS 'New';";
			final String output = diff(src, dst);
			assertContains(output, "COMMENT ON SCHEMA");
			assertContains(output, "New");
		}
	}

	// -------------------------------------------------------------------------
	// Sorting
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Sorting")
	class Sorting {

		@Test
		@DisplayName("sortBySchema: statements appear in alphabetical schema order")
		void sortBySchema() throws Exception {
			// Both schemas are new (only in dst) so CREATE SCHEMA statements are emitted.
			// Without sorting the generator would emit z_schema first (dst declaration order).
			final String src = "";
			final String dst = "CREATE SCHEMA z_schema; CREATE TABLE z_schema.t (id INTEGER NOT NULL); CREATE SCHEMA a_schema; CREATE TABLE a_schema.t (id INTEGER NOT NULL);";
			final String output = diffWithSort(src, dst, true, false, false);
			// Find the position of the first DDL statement section (after the statistics block)
			final int statsEnd = output.indexOf("-- ====", output.indexOf("Migration Statistics") + 1);
			final String ddlSection = output.substring(statsEnd);
			final int posA = ddlSection.toLowerCase().indexOf("a_schema");
			final int posZ = ddlSection.toLowerCase().indexOf("z_schema");
			assertTrue(posA >= 0 && posZ >= 0, "Both schemas must appear in the DDL section");
			assertTrue(posA < posZ, "a_schema statements should appear before z_schema when sortBySchema is enabled");
		}

		@Test
		@DisplayName("sortByTable: statements appear in alphabetical table order within schema")
		void sortByTable() throws Exception {
			final String src = "CREATE SCHEMA s;";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.z_table (id INTEGER NOT NULL);
					CREATE TABLE s.a_table (id INTEGER NOT NULL);
					""";
			final String output = diffWithSort(src, dst, false, true, false);
			final int posA = output.toLowerCase().indexOf("a_table");
			final int posZ = output.toLowerCase().indexOf("z_table");
			assertTrue(posA < posZ, "a_table should appear before z_table when sortByTable is enabled");
		}

		@Test
		@DisplayName("sortByColumn: column definitions appear in alphabetical order in CREATE TABLE")
		void sortByColumn() throws Exception {
			final String src = "CREATE SCHEMA s;";
			final String dst = "CREATE SCHEMA s; CREATE TABLE s.t (z_col INTEGER, a_col INTEGER);";
			final String output = diffWithSort(src, dst, false, false, true);
			final int posA = output.toLowerCase().indexOf("a_col");
			final int posZ = output.toLowerCase().indexOf("z_col");
			assertTrue(posA < posZ, "a_col should appear before z_col when sortByColumn is enabled");
		}

		@Test
		@DisplayName("no sorting: original destination order is preserved")
		void noSorting() throws Exception {
			final String src = "CREATE SCHEMA s;";
			final String dst = """
					CREATE SCHEMA s;
					CREATE TABLE s.z_table (id INTEGER NOT NULL);
					CREATE TABLE s.a_table (id INTEGER NOT NULL);
					""";
			final String output = diffWithSort(src, dst, false, false, false);
			final int posZ = output.toLowerCase().indexOf("z_table");
			final int posA = output.toLowerCase().indexOf("a_table");
			assertTrue(posZ < posA, "Without sorting, z_table should appear before a_table");
		}
	}
}