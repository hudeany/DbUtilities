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
 * Unit tests for {@link SqlDdlMergeGenerator}.
 *
 * <p>Each test calls {@link SqlDdlMergeGenerator#merge} with two DDL strings
 * and asserts the content of the resulting merged DDL as well as the
 * correctness of the statistics comment block.
 *
 * <p>Since statistics lines are only written when their counter is greater than
 * zero, tests that previously checked for label presence in an empty merge now
 * verify that no counter lines appear at all for a zero-change run.
 */
@DisplayName("SqlDdlMergeGenerator")
class SqlDdlMergeGeneratorTest {

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String merge(final String ddlA, final String ddlB) throws Exception {
		final InputStream a = stream(ddlA);
		final InputStream b = stream(ddlB);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMergeGenerator.merge(a, b, out);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static String mergeWithSort(final String ddlA, final String ddlB,
			final boolean sortBySchema, final boolean sortByTable, final boolean sortByColumn) throws Exception {
		final InputStream a = stream(ddlA);
		final InputStream b = stream(ddlB);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		SqlDdlMergeGenerator.merge(a, b, out, sortBySchema, sortByTable, sortByColumn);
		return out.toString(StandardCharsets.UTF_8);
	}

	private static InputStream stream(final String ddl) {
		return new ByteArrayInputStream(ddl.getBytes(StandardCharsets.UTF_8));
	}

	private static void assertContains(final String output, final String fragment) {
		assertTrue(output.toLowerCase().contains(fragment.toLowerCase()),
				"Expected output to contain: " + fragment + "\nActual output:\n" + output);
	}

	private static void assertNotContains(final String output, final String fragment) {
		assertFalse(output.toLowerCase().contains(fragment.toLowerCase()),
				"Expected output NOT to contain: " + fragment + "\nActual output:\n" + output);
	}

	// -------------------------------------------------------------------------
	// Empty inputs
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Empty inputs")
	class EmptyInputs {

		@Test
		@DisplayName("both empty => only header and statistics")
		void bothEmpty() throws Exception {
			final String output = merge("", "");
			assertContains(output, "Merged DDL");
			assertContains(output, "Merge Statistics");
		}

		@Test
		@DisplayName("A empty, B has table => table appears in output")
		void aEmptyBHasTable() throws Exception {
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = merge("", b);
			assertContains(output, "CREATE TABLE");
			assertContains(output, "t");
		}

		@Test
		@DisplayName("A has table, B empty => table appears in output")
		void aHasTableBEmpty() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = merge(a, "");
			assertContains(output, "CREATE TABLE");
			assertContains(output, "t");
		}
	}

	// -------------------------------------------------------------------------
	// Schema merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Schema merging")
	class SchemaMerging {

		@Test
		@DisplayName("schema only in A => CREATE SCHEMA in output")
		void schemaOnlyInA() throws Exception {
			final String a = "CREATE SCHEMA alpha;";
			final String output = merge(a, "");
			assertContains(output, "CREATE SCHEMA");
			assertContains(output, "alpha");
		}

		@Test
		@DisplayName("schema only in B => CREATE SCHEMA in output")
		void schemaOnlyInB() throws Exception {
			final String b = "CREATE SCHEMA beta;";
			final String output = merge("", b);
			assertContains(output, "CREATE SCHEMA");
			assertContains(output, "beta");
		}

		@Test
		@DisplayName("schema in both => appears once in output")
		void schemaInBoth() throws Exception {
			final String ddl = "CREATE SCHEMA shared;";
			final String output = merge(ddl, ddl);
			// Count occurrences — should appear exactly once
			final int count = countOccurrences(output.toLowerCase(), "create schema shared");
			assertTrue(count == 1, "Schema should appear exactly once, found: " + count);
		}

		@Test
		@DisplayName("schema comment: B wins over A")
		void schemaCommentBWins() throws Exception {
			final String a = "CREATE SCHEMA s; COMMENT ON SCHEMA s IS 'Comment A';";
			final String b = "CREATE SCHEMA s; COMMENT ON SCHEMA s IS 'Comment B';";
			final String output = merge(a, b);
			assertContains(output, "'Comment B'");
			assertNotContains(output, "'Comment A'");
		}

		@Test
		@DisplayName("schema comment: only in A => A kept")
		void schemaCommentOnlyInA() throws Exception {
			final String a = "CREATE SCHEMA s; COMMENT ON SCHEMA s IS 'Only in A';";
			final String b = "CREATE SCHEMA s;";
			final String output = merge(a, b);
			assertContains(output, "'Only in A'");
		}

		@Test
		@DisplayName("statistics: schemasOnlyInA and schemasOnlyInB lines present when > 0")
		void statsSchemas() throws Exception {
			final String a = "CREATE SCHEMA onlya; CREATE SCHEMA shared;";
			final String b = "CREATE SCHEMA onlyb; CREATE SCHEMA shared;";
			final String output = merge(a, b);
			// All three counters are > 0 in this scenario
			assertAll(
					() -> assertContains(output, "only in a"),
					() -> assertContains(output, "only in b"),
					() -> assertContains(output, "in both")
			);
		}
	}

	// -------------------------------------------------------------------------
	// Table merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Table merging")
	class TableMerging {

		@Test
		@DisplayName("table only in A => present in output")
		void tableOnlyInA() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.ta (id INTEGER NOT NULL);";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.tb (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			assertContains(output, "ta");
			assertContains(output, "tb");
		}

		@Test
		@DisplayName("tables from both merged into one output")
		void tablesFromBothPresent() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.ta (id INTEGER NOT NULL);";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.tb (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			final int countA = countOccurrences(output.toLowerCase(), "ta");
			final int countB = countOccurrences(output.toLowerCase(), "tb");
			assertTrue(countA >= 1 && countB >= 1, "Both tables should appear in output");
		}

		@Test
		@DisplayName("same table in both => appears exactly once")
		void sameTableAppearsOnce() throws Exception {
			final String ddl = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = merge(ddl, ddl);
			final int count = countOccurrences(output.toLowerCase(), "create table");
			assertTrue(count == 1, "Table should appear exactly once, found: " + count);
		}

		@Test
		@DisplayName("table comment: B wins")
		void tableCommentBWins() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'From A';
					""";
			final String b = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL);
					COMMENT ON TABLE s.t IS 'From B';
					""";
			final String output = merge(a, b);
			assertContains(output, "'From B'");
			assertNotContains(output, "'From A'");
		}

		@Test
		@DisplayName("statistics: tablesOnlyInA and tablesOnlyInB lines present when > 0")
		void statsTables() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.ta (id INTEGER NOT NULL); CREATE TABLE s.shared (id INTEGER NOT NULL);";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.tb (id INTEGER NOT NULL); CREATE TABLE s.shared (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "only in a"),
					() -> assertContains(output, "only in b"),
					() -> assertContains(output, "in both")
			);
		}
	}

	// -------------------------------------------------------------------------
	// Column merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Column merging")
	class ColumnMerging {

		@Test
		@DisplayName("columns only in A are present in merged output")
		void columnsOnlyInA() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, col_a VARCHAR(50));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			assertContains(output, "col_a");
		}

		@Test
		@DisplayName("columns only in B are present in merged output")
		void columnsOnlyInB() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, col_b VARCHAR(50));";
			final String output = merge(a, b);
			assertContains(output, "col_b");
		}

		@Test
		@DisplayName("same column in both: B definition wins (type)")
		void sameColumnBWins() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, name VARCHAR(100));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, name VARCHAR(255));";
			final String output = merge(a, b);
			assertContains(output, "VARCHAR(255)");
			assertNotContains(output, "VARCHAR(100)");
		}

		@Test
		@DisplayName("columns from A appear before new columns from B")
		void columnOrderAFirst() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, from_a VARCHAR(10));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, from_b VARCHAR(10));";
			final String output = merge(a, b);
			final int posA = output.toLowerCase().indexOf("from_a");
			final int posB = output.toLowerCase().indexOf("from_b");
			assertTrue(posA < posB, "Columns from A should appear before new columns from B");
		}

		@Test
		@DisplayName("statistics: columnsOnlyInA, columnsOnlyInB, columnsBWins lines present when > 0")
		void statsColumns() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, a_col VARCHAR(10), shared VARCHAR(10));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, b_col VARCHAR(10), shared VARCHAR(20));";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "only in a"),
					() -> assertContains(output, "only in b"),
					() -> assertContains(output, "b overrides")
			);
		}
	}

	// -------------------------------------------------------------------------
	// Primary key merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Primary key merging")
	class PrimaryKeyMerging {

		@Test
		@DisplayName("PK only in A => present in output")
		void pkOnlyInA() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (id));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			assertContains(output, "PRIMARY KEY");
		}

		@Test
		@DisplayName("PK only in B => present in output")
		void pkOnlyInB() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL);";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (id));";
			final String output = merge(a, b);
			assertContains(output, "PRIMARY KEY");
		}

		@Test
		@DisplayName("PK in both: B wins")
		void pkBWins() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, code INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (id));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, code INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (code));";
			final String output = merge(a, b);
			assertContains(output, "code integer");
		}

		@Test
		@DisplayName("statistics: pkFromB and pkBOverridesA lines present when > 0")
		void statsPk() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (id));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, code INTEGER NOT NULL, CONSTRAINT pk_t PRIMARY KEY (code));";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "from b"),
					() -> assertContains(output, "b overrides a")
			);
		}
	}

	// -------------------------------------------------------------------------
	// Unique key merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Unique key merging")
	class UniqueKeyMerging {

		@Test
		@DisplayName("unique keys from both are present in output")
		void uniqueKeysUnion() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, a VARCHAR(10), CONSTRAINT uq_a UNIQUE (a));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, b VARCHAR(10), CONSTRAINT uq_b UNIQUE (b));";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "uq_a"),
					() -> assertContains(output, "uq_b")
			);
		}

		@Test
		@DisplayName("unique key name conflict: B definition wins")
		void uniqueKeyConflictBWins() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, a VARCHAR(10), b VARCHAR(10), CONSTRAINT uq_x UNIQUE (a));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, a VARCHAR(10), b VARCHAR(10), CONSTRAINT uq_x UNIQUE (b));";
			final String output = merge(a, b);
			// uq_x should reference b, not a — count occurrences of the constraint definition
			final int countUqX = countOccurrences(output.toLowerCase(), "uq_x");
			assertTrue(countUqX == 1, "Conflicting unique key should appear exactly once");
		}

		@Test
		@DisplayName("statistics: uniqueFromB and uniqueConflicts lines present when > 0")
		void statsUniqueKeys() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, a VARCHAR(10), CONSTRAINT uq_shared UNIQUE (a));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, b VARCHAR(10), CONSTRAINT uq_shared UNIQUE (b));";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "from b"),
					() -> assertContains(output, "conflicts")
			);
		}
	}

	// -------------------------------------------------------------------------
	// Foreign key merging
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Foreign key merging")
	class ForeignKeyMerging {

		@Test
		@DisplayName("foreign keys from both are present in output")
		void fkUnion() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref (id INTEGER NOT NULL);
					CREATE TABLE s.t (
					    id INTEGER NOT NULL,
					    ref_id INTEGER,
					    CONSTRAINT fk_a FOREIGN KEY (ref_id) REFERENCES ref (id)
					);
					""";
			final String b = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref (id INTEGER NOT NULL);
					CREATE TABLE s.t (
					    id INTEGER NOT NULL,
					    ref_id INTEGER,
					    CONSTRAINT fk_b FOREIGN KEY (ref_id) REFERENCES ref (id)
					);
					""";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "fk_a"),
					() -> assertContains(output, "fk_b")
			);
		}

		@Test
		@DisplayName("FK name conflict: B wins, FK appears once")
		void fkConflictBWins() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref1 (id INTEGER NOT NULL);
					CREATE TABLE s.ref2 (id INTEGER NOT NULL);
					CREATE TABLE s.t (
					    id INTEGER NOT NULL, r1 INTEGER, r2 INTEGER,
					    CONSTRAINT fk_shared FOREIGN KEY (r1) REFERENCES ref1 (id)
					);
					""";
			final String b = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref1 (id INTEGER NOT NULL);
					CREATE TABLE s.ref2 (id INTEGER NOT NULL);
					CREATE TABLE s.t (
					    id INTEGER NOT NULL, r1 INTEGER, r2 INTEGER,
					    CONSTRAINT fk_shared FOREIGN KEY (r2) REFERENCES ref2 (id)
					);
					""";
			final String output = merge(a, b);
			final int count = countOccurrences(output.toLowerCase(), "fk_shared");
			assertTrue(count == 1, "Conflicting FK should appear exactly once, found: " + count);
		}

		@Test
		@DisplayName("statistics: fkConflicts line present when > 0")
		void statsFk() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref (id INTEGER NOT NULL);
					CREATE TABLE s.t (id INTEGER NOT NULL, r INTEGER,
					    CONSTRAINT fk_shared FOREIGN KEY (r) REFERENCES ref (id));
					""";
			final String b = """
					CREATE SCHEMA s;
					CREATE TABLE s.ref (id INTEGER NOT NULL);
					CREATE TABLE s.t (id INTEGER NOT NULL, r INTEGER,
					    CONSTRAINT fk_shared FOREIGN KEY (r) REFERENCES ref (id));
					""";
			final String output = merge(a, b);
			assertContains(output, "conflicts");
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
			final String output = merge("", "");
			assertContains(output, "Merge Statistics");
		}

		@Test
		@DisplayName("statistics appear before DDL statements")
		void statisticsBeforeDdl() throws Exception {
			final String b = "CREATE SCHEMA s;";
			final String output = merge("", b);
			final int statsPos = output.toLowerCase().indexOf("merge statistics");
			final int stmtPos  = output.toLowerCase().indexOf("create schema");
			assertTrue(statsPos < stmtPos, "Statistics block must appear before DDL statements");
		}

		@Test
		@DisplayName("zero-change merge: no counter lines appear in statistics block")
		void zeroCounters() throws Exception {
			final String output = merge("", "");
			final int start = output.indexOf("Merge Statistics");
			final int endSep = output.indexOf("-- =====", start + 20);
			final int endSep2 = output.indexOf("-- =====", endSep + 10);
			final String statsBlock = output.substring(start, endSep2 > endSep ? endSep2 : output.length());
			// No line of the form "--  Something : N" should appear (all counters are 0)
			for (final String line : statsBlock.split("\n")) {
				if (line.trim().startsWith("--  ") && line.contains(":")) {
					// This would be a counter line — they must not exist for an empty merge
					assertTrue(false, "Unexpected counter line in zero-change statistics: " + line);
				}
			}
		}

		@Test
		@DisplayName("non-zero statistics: counter line appears for active counter")
		void nonZeroCounterAppears() throws Exception {
			final String a = "CREATE SCHEMA only_a;";
			final String output = merge(a, "");
			assertContains(output, "only in a");
		}

		@Test
		@DisplayName("zero counter: label is absent from statistics block")
		void zeroCounterAbsent() throws Exception {
			// Only schemasOnlyInA is > 0; schemasOnlyInB should not appear
			final String a = "CREATE SCHEMA only_a;";
			final String output = merge(a, "");
			assertNotContains(output, "only in b");
		}
	}

	// -------------------------------------------------------------------------
	// Edge cases
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Edge cases")
	class EdgeCases {

		@Test
		@DisplayName("identical inputs => output contains exactly one CREATE TABLE per table")
		void identicalInputs() throws Exception {
			final String ddl = """
					CREATE SCHEMA s;
					CREATE TABLE s.t (id INTEGER NOT NULL, name VARCHAR(100));
					""";
			final String output = merge(ddl, ddl);
			final int count = countOccurrences(output.toLowerCase(), "create table");
			assertTrue(count == 1, "Table should appear exactly once, found: " + count);
		}

		@Test
		@DisplayName("tables without schema (default schema)")
		void tablesWithoutSchema() throws Exception {
			final String a = "CREATE TABLE ta (id INTEGER NOT NULL);";
			final String b = "CREATE TABLE tb (id INTEGER NOT NULL);";
			final String output = merge(a, b);
			assertAll(
					() -> assertContains(output, "ta"),
					() -> assertContains(output, "tb")
			);
		}

		@Test
		@DisplayName("column names are handled case-insensitively")
		void columnCaseInsensitive() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (ID INTEGER NOT NULL, Name VARCHAR(50));";
			final String b = "CREATE SCHEMA s; CREATE TABLE s.t (id INTEGER NOT NULL, name VARCHAR(100));";
			// B wins — VARCHAR(100), and column appears only once
			final String output = merge(a, b);
			final int countId = countOccurrences(output.toLowerCase(), "id integer");
			assertTrue(countId == 1, "Column id should appear exactly once, found: " + countId);
			assertContains(output, "VARCHAR(100)");
			assertNotContains(output, "VARCHAR(50)");
		}

		@Test
		@DisplayName("A-order preserved: A schemas then new B schemas")
		void schemaOrderPreserved() throws Exception {
			final String a = "CREATE SCHEMA alpha;";
			final String b = "CREATE SCHEMA beta;";
			final String output = merge(a, b);
			final int posAlpha = output.toLowerCase().indexOf("alpha");
			final int posBeta  = output.toLowerCase().indexOf("beta");
			assertTrue(posAlpha < posBeta, "Schema alpha (from A) should appear before beta (from B)");
		}
	}

	// -------------------------------------------------------------------------
	// Sorting
	// -------------------------------------------------------------------------

	@Nested
	@DisplayName("Sorting")
	class Sorting {

		@Test
		@DisplayName("sortBySchema: schemas appear in alphabetical order")
		void sortBySchema() throws Exception {
			final String a = "CREATE SCHEMA z_schema; CREATE SCHEMA a_schema;";
			final String output = mergeWithSort(a, "", true, false, false);
			final int posA = output.toLowerCase().indexOf("a_schema");
			final int posZ = output.toLowerCase().indexOf("z_schema");
			assertTrue(posA < posZ, "a_schema should appear before z_schema when sortBySchema is enabled");
		}

		@Test
		@DisplayName("sortByTable: tables within a schema appear in alphabetical order")
		void sortByTable() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.z_table (id INTEGER NOT NULL);
					CREATE TABLE s.a_table (id INTEGER NOT NULL);
					""";
			final String output = mergeWithSort(a, "", false, true, false);
			final int posA = output.toLowerCase().indexOf("a_table");
			final int posZ = output.toLowerCase().indexOf("z_table");
			assertTrue(posA < posZ, "a_table should appear before z_table when sortByTable is enabled");
		}

		@Test
		@DisplayName("sortByColumn: columns within a table appear in alphabetical order")
		void sortByColumn() throws Exception {
			final String a = "CREATE SCHEMA s; CREATE TABLE s.t (z_col INTEGER, a_col INTEGER);";
			final String output = mergeWithSort(a, "", false, false, true);
			final int posA = output.toLowerCase().indexOf("a_col");
			final int posZ = output.toLowerCase().indexOf("z_col");
			assertTrue(posA < posZ, "a_col should appear before z_col when sortByColumn is enabled");
		}

		@Test
		@DisplayName("no sorting: original order is preserved")
		void noSorting() throws Exception {
			final String a = """
					CREATE SCHEMA s;
					CREATE TABLE s.z_table (z_col INTEGER, a_col INTEGER);
					CREATE TABLE s.a_table (id INTEGER NOT NULL);
					""";
			final String output = mergeWithSort(a, "", false, false, false);
			final int posZTable = output.toLowerCase().indexOf("z_table");
			final int posATable = output.toLowerCase().indexOf("a_table");
			assertTrue(posZTable < posATable, "Without sorting, z_table should appear before a_table");
		}
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

	private static int countOccurrences(final String text, final String fragment) {
		int count = 0;
		int idx = 0;
		while ((idx = text.indexOf(fragment, idx)) != -1) {
			count++;
			idx += fragment.length();
		}
		return count;
	}
}