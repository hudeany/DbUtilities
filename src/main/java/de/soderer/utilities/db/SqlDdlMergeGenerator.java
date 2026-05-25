package de.soderer.utilities.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import de.soderer.utilities.db.data.DbColumn;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbForeignKey;
import de.soderer.utilities.db.data.DbSchema;
import de.soderer.utilities.db.data.DbSimpleDataType;
import de.soderer.utilities.db.data.DbStructure;
import de.soderer.utilities.db.data.DbTable;
import de.soderer.utilities.db.exception.DbStructureException;

/**
 * Merges two SQL DDL structures into a single, unified DDL structure.
 *
 * <p>The merger performs a structural union of both {@link DbStructure}s:
 * <ul>
 *   <li>Schemas present in only one structure are taken over as-is.</li>
 *   <li>Schemas present in both structures are merged table by table.</li>
 *   <li>Tables present in only one structure are taken over as-is.</li>
 *   <li>Tables present in both structures are merged column by column:
 *     <ul>
 *       <li>Columns from structure A that are missing in structure B are added.</li>
 *       <li>If the same column exists in both structures, the definition from
 *           {@code structureB} (the "dominant" structure) wins.</li>
 *       <li>Primary keys, unique keys, foreign keys, and comments from
 *           {@code structureB} win when both structures define them; otherwise the
 *           non-null value is used.</li>
 *     </ul>
 *   </li>
 * </ul>
 * }</pre>
 */
public class SqlDdlMergeGenerator {
	private static class MergeStatistics {
		// Schemas
		int schemasOnlyInA  = 0;
		int schemasOnlyInB  = 0;
		int schemasMerged   = 0;

		// Tables
		int tablesOnlyInA   = 0;
		int tablesOnlyInB   = 0;
		int tablesMerged    = 0;

		// Columns
		int columnsOnlyInA      = 0;
		int columnsOnlyInB      = 0;
		int columnsAWins        = 0; // same name in both, but B had no definition → A kept
		int columnsBWins        = 0; // same name in both, B overrides A

		// Primary keys
		int pkFromA             = 0;
		int pkFromB             = 0;
		int pkBOverridesA       = 0; // both defined, B wins

		// Unique keys
		int uniqueFromA         = 0;
		int uniqueFromB         = 0;
		int uniqueConflicts     = 0; // same name in both, B wins

		// Foreign keys
		int fkFromA             = 0;
		int fkFromB             = 0;
		int fkConflicts         = 0; // same name in both, B wins

		// Comments (table / schema)
		int commentsFromA       = 0;
		int commentsFromB       = 0;
		int commentsBOverrideA  = 0; // both defined, B wins

		private static void writeLine(final PrintWriter writer, final String label, final int value) {
			if (value > 0) {
				writer.println("--  " + label + value);
			}
		}

		void writeTo(final PrintWriter writer) {
			writer.println("-- ============================================================");
			writer.println("-- Merge Statistics");
			writer.println("-- ============================================================");
			writer.println("--");
			writeLine(writer, "Schemas  only in A (taken over)  : ", schemasOnlyInA);
			writeLine(writer, "         only in B (taken over)  : ", schemasOnlyInB);
			writeLine(writer, "         in both  (merged)       : ", schemasMerged);
			writer.println("--");
			writeLine(writer, "Tables   only in A (taken over)  : ", tablesOnlyInA);
			writeLine(writer, "         only in B (taken over)  : ", tablesOnlyInB);
			writeLine(writer, "         in both  (merged)       : ", tablesMerged);
			writer.println("--");
			writeLine(writer, "Columns  only in A (taken over)  : ", columnsOnlyInA);
			writeLine(writer, "         only in B (taken over)  : ", columnsOnlyInB);
			writeLine(writer, "         in both  - A kept       : ", columnsAWins);
			writeLine(writer, "         in both  - B overrides  : ", columnsBWins);
			writer.println("--");
			writeLine(writer, "PK       from A                  : ", pkFromA);
			writeLine(writer, "         from B                  : ", pkFromB);
			writeLine(writer, "         B overrides A           : ", pkBOverridesA);
			writer.println("--");
			writeLine(writer, "Unique   from A                  : ", uniqueFromA);
			writeLine(writer, "         from B                  : ", uniqueFromB);
			writeLine(writer, "         conflicts (B wins)      : ", uniqueConflicts);
			writer.println("--");
			writeLine(writer, "FK       from A                  : ", fkFromA);
			writeLine(writer, "         from B                  : ", fkFromB);
			writeLine(writer, "         conflicts (B wins)      : ", fkConflicts);
			writer.println("--");
			writeLine(writer, "Comments from A                  : ", commentsFromA);
			writeLine(writer, "         from B                  : ", commentsFromB);
			writeLine(writer, "         B overrides A           : ", commentsBOverrideA);
			writer.println("-- ============================================================");
			writer.println();
		}
	}

	/**
	 * Convenience overload without sorting.
	 */
	public static void merge(final InputStream structureSqlDataA, final InputStream structureSqlDataB,
			final OutputStream mergeSqlData) throws IOException, DbStructureException {
		merge(structureSqlDataA, structureSqlDataB, mergeSqlData, false, false, false);
	}

	/**
	 * Parses {@code structureSqlDataA} and {@code structureSqlDataB}, merges their
	 * structures, and writes the resulting unified DDL to {@code mergeSqlData}.
	 *
	 * @param structureSqlDataA first SQL DDL input stream
	 * @param structureSqlDataB second SQL DDL input stream (wins on conflicts)
	 * @param mergeSqlData      output stream for the merged DDL
	 * @param sortBySchema      if {@code true}, schemas are emitted in alphabetical order
	 * @param sortByTable       if {@code true}, tables within each schema are emitted in alphabetical order
	 * @param sortByColumn      if {@code true}, columns within each table are emitted in alphabetical order
	 * @throws IOException          on read / write errors
	 * @throws DbStructureException if either file contains structural errors
	 */
	public static void merge(final InputStream structureSqlDataA, final InputStream structureSqlDataB,
			final OutputStream mergeSqlData,
			final boolean sortBySchema, final boolean sortByTable, final boolean sortByColumn)
			throws IOException, DbStructureException {

		final DbStructure structureA = SqlDdlParser.parse(structureSqlDataA);
		final DbStructure structureB = SqlDdlParser.parse(structureSqlDataB);

		final MergeStatistics stats = new MergeStatistics();
		final DbStructure merged = mergeStructures(structureA, structureB, stats);

		try (final PrintWriter writer = new PrintWriter(new OutputStreamWriter(mergeSqlData, StandardCharsets.UTF_8))) {
			writer.println("-- Merged DDL");
			writer.println("-- Generated: " + java.time.LocalDateTime.now());
			writer.println();
			stats.writeTo(writer);

			final List<String> schemaNames = new ArrayList<>(merged.getSchemas().keySet());
			if (sortBySchema) {
				schemaNames.sort(Comparator.naturalOrder());
			}

			for (final String schemaName : schemaNames) {
				final DbSchema schema = merged.getSchemas().get(schemaName);

				if (!schemaName.isEmpty()) {
					writer.println("CREATE SCHEMA " + quote(schemaName) + ";");
					if (schema.getSchemaComment() != null) {
						writer.println("COMMENT ON SCHEMA " + quote(schemaName) + " IS " + sqlString(schema.getSchemaComment()) + ";");
					}
					writer.println();
				}

				final List<DbTable> tables = new ArrayList<>(schema.getTables().values());
				if (sortByTable) {
					tables.sort(Comparator.comparing(DbTable::getTableName));
				}

				for (final DbTable table : tables) {
					writeCreateTable(writer, schemaName, table, sortByColumn);
				}
			}
		}
	}

	private static DbStructure mergeStructures(final DbStructure a, final DbStructure b, final MergeStatistics stats) throws DbStructureException {
		final DbStructure result = new DbStructure();

		final List<String> schemaNames = new ArrayList<>(a.getSchemas().keySet());
		for (final String name : b.getSchemas().keySet()) {
			if (!schemaNames.contains(name)) {
				schemaNames.add(name);
			}
		}

		for (final String schemaName : schemaNames) {
			final DbSchema schemaA = a.getSchemas().get(schemaName);
			final DbSchema schemaB = b.getSchemas().get(schemaName);

			if (schemaA != null && schemaB == null) {
				stats.schemasOnlyInA++;
			} else if (schemaA == null && schemaB != null) {
				stats.schemasOnlyInB++;
			} else {
				stats.schemasMerged++;
			}

			final DbSchema mergedSchema = mergeSchemas(schemaA, schemaB, stats);
			result.createSchema(schemaName, mergedSchema);
		}

		return result;
	}

	private static DbSchema mergeSchemas(final DbSchema schemaA, final DbSchema schemaB, final MergeStatistics stats) throws DbStructureException {
		final DbSchema base = schemaA != null ? schemaA : schemaB;
		final DbSchema other = schemaA != null ? schemaB : null;

		final DbSchema result = new DbSchema();
		result.setSchemaName(base.getSchemaName());

		if (other != null && other.getSchemaComment() != null) {
			result.setSchemaComment(other.getSchemaComment());
			if (base.getSchemaComment() != null) {
				stats.commentsBOverrideA++;
			} else {
				stats.commentsFromB++;
			}
		} else if (base.getSchemaComment() != null) {
			result.setSchemaComment(base.getSchemaComment());
			stats.commentsFromA++;
		}

		if (other == null) {
			for (final Map.Entry<String, DbTable> e : base.getTables().entrySet()) {
				result.createTable(e.getKey(), e.getValue());
			}
			return result;
		}

		final List<String> tableNames = new ArrayList<>(base.getTables().keySet());
		for (final String name : other.getTables().keySet()) {
			if (!tableNames.contains(name)) {
				tableNames.add(name);
			}
		}

		for (final String tableName : tableNames) {
			final DbTable tableA = base.getTables().get(tableName);
			final DbTable tableB = other.getTables().get(tableName);

			if (tableA != null && tableB == null) {
				stats.tablesOnlyInA++;
			} else if (tableA == null && tableB != null) {
				stats.tablesOnlyInB++;
			} else {
				stats.tablesMerged++;
			}

			final DbTable mergedTable = mergeTables(tableA, tableB, stats);
			result.createTable(tableName, mergedTable);
		}

		return result;
	}

	private static DbTable mergeTables(final DbTable tableA, final DbTable tableB, final MergeStatistics stats) throws DbStructureException {
		final DbTable base = tableA != null ? tableA : tableB;
		final DbTable other = tableA != null ? tableB : null;

		final DbTable result = new DbTable();
		result.setTableName(base.getTableName());

		if (other == null) {
			for (final Map.Entry<String, DbColumn> e : base.getColumns().entrySet()) {
				result.createColumn(e.getKey(), e.getValue());
			}
			result.setPrimaryKey(base.getPrimaryKey());
			if (base.getPrimaryKey() != null && !base.getPrimaryKey().isEmpty()) {
				if (tableA != null) {
					stats.pkFromA++;
				} else {
					stats.pkFromB++;
				}
			}
			if (base.getForeignKeys() != null) {
				for (final DbForeignKey fk : base.getForeignKeys()) {
					result.addForeignKey(fk);
					if (tableA != null) {
						stats.fkFromA++;
					} else {
						stats.fkFromB++;
					}
				}
			}
			if (base.getUniqueKeys() != null) {
				for (final Map.Entry<String, List<String>> uq : base.getUniqueKeys().entrySet()) {
					result.addUniqueKey(uq.getKey(), uq.getValue());
					if (tableA != null) {
						stats.uniqueFromA++;
					} else {
						stats.uniqueFromB++;
					}
				}
			}
			if (base.getTableComment() != null) {
				result.setTableComment(base.getTableComment());
				if (tableA != null) {
					stats.commentsFromA++;
				} else {
					stats.commentsFromB++;
				}
			}
			return result;
		}

		final List<String> colNames = new ArrayList<>(base.getColumns().keySet());
		for (final String name : other.getColumns().keySet()) {
			if (!colNames.contains(name)) {
				colNames.add(name);
			}
		}
		for (final String colName : colNames) {
			final DbColumn colA = base.getColumns().get(colName);
			final DbColumn colB = other.getColumns().get(colName);
			if (colA != null && colB == null) {
				result.createColumn(colName, colA);
				stats.columnsOnlyInA++;
			} else if (colA == null && colB != null) {
				result.createColumn(colName, colB);
				stats.columnsOnlyInB++;
			} else {
				// Both have it: B wins
				result.createColumn(colName, colB);
				stats.columnsBWins++;
			}
		}

		final boolean baseHasPk = base.getPrimaryKey() != null && !base.getPrimaryKey().isEmpty();
		final boolean otherHasPk = other.getPrimaryKey() != null && !other.getPrimaryKey().isEmpty();
		if (otherHasPk) {
			result.setPrimaryKey(other.getPrimaryKey());
			stats.pkFromB++;
			if (baseHasPk) {
				stats.pkBOverridesA++;
			}
		} else if (baseHasPk) {
			result.setPrimaryKey(base.getPrimaryKey());
			stats.pkFromA++;
		}

		if (base.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : base.getUniqueKeys().entrySet()) {
				if (!other.getUniqueKeys().containsKey(uq.getKey())) {
					result.addUniqueKey(uq.getKey(), uq.getValue());
					stats.uniqueFromA++;
				}
			}
		}
		if (other.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : other.getUniqueKeys().entrySet()) {
				result.addUniqueKey(uq.getKey(), uq.getValue());
				stats.uniqueFromB++;
				if (base.getUniqueKeys() != null && base.getUniqueKeys().containsKey(uq.getKey())) {
					stats.uniqueConflicts++;
				}
			}
		}

		final List<DbForeignKey> mergedFks = new ArrayList<>();
		if (base.getForeignKeys() != null) {
			for (final DbForeignKey fk : base.getForeignKeys()) {
				final boolean overriddenByB = other.getForeignKeys() != null
						&& other.getForeignKeys().stream()
								.anyMatch(bFk -> fk.getForeignKeyName() != null
										&& fk.getForeignKeyName().equals(bFk.getForeignKeyName()));
				if (!overriddenByB) {
					mergedFks.add(fk);
					stats.fkFromA++;
				} else {
					stats.fkConflicts++;
				}
			}
		}
		if (other.getForeignKeys() != null) {
			for (final DbForeignKey fk : other.getForeignKeys()) {
				mergedFks.add(fk);
				stats.fkFromB++;
			}
		}
		for (final DbForeignKey fk : mergedFks) {
			result.addForeignKey(fk);
		}

		if (other.getTableComment() != null) {
			result.setTableComment(other.getTableComment());
			stats.commentsFromB++;
			if (base.getTableComment() != null) {
				stats.commentsBOverrideA++;
			}
		} else if (base.getTableComment() != null) {
			result.setTableComment(base.getTableComment());
			stats.commentsFromA++;
		}

		return result;
	}

	private static void writeCreateTable(final PrintWriter writer, final String schemaName, final DbTable table,
			final boolean sortByColumn) {
		final String qualifiedTable = qualifiedName(schemaName, table.getTableName());

		writer.println("CREATE TABLE " + qualifiedTable + " (");

		final List<String> entries = new ArrayList<>();

		final List<DbColumn> columns = new ArrayList<>(table.getColumns().values());
		if (sortByColumn) {
			columns.sort(Comparator.comparing(DbColumn::getColumnName));
		}

		for (final DbColumn col : columns) {
			entries.add("    " + columnDefinition(col));
		}

		final List<String> pk = table.getPrimaryKey();
		if (pk != null && !pk.isEmpty()) {
			entries.add("    CONSTRAINT " + quote("pk_" + table.getTableName())
					+ " PRIMARY KEY (" + quoteList(pk) + ")");
		}

		if (table.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : table.getUniqueKeys().entrySet()) {
				entries.add("    CONSTRAINT " + quote(uq.getKey())
						+ " UNIQUE (" + quoteList(uq.getValue()) + ")");
			}
		}

		if (table.getForeignKeys() != null) {
			for (final DbForeignKey fk : table.getForeignKeys()) {
				entries.add("    " + inlineForeignKey(fk));
			}
		}

		for (int i = 0; i < entries.size(); i++) {
			writer.print(entries.get(i));
			writer.println(i < entries.size() - 1 ? "," : "");
		}

		writer.println(");");

		if (table.getTableComment() != null) {
			writer.println("COMMENT ON TABLE " + qualifiedTable
					+ " IS " + sqlString(table.getTableComment()) + ";");
		}

		for (final DbColumn col : columns) {
			if (col.getColumnComment() != null) {
				writer.println("COMMENT ON COLUMN " + qualifiedTable + "." + quote(col.getColumnName())
						+ " IS " + sqlString(col.getColumnComment()) + ";");
			}
		}

		writer.println();
	}

	private static String columnDefinition(final DbColumn col) {
		final StringBuilder sb = new StringBuilder();
		sb.append(quote(col.getColumnName())).append(' ');
		final DbColumnType t = col.getColumnType();
		if (t != null) {
			sb.append(sqlTypeName(t));
			if (t.isAutoIncrement()) {
				sb.append(" GENERATED ALWAYS AS IDENTITY");
			}
			if (!t.isNullable()) {
				sb.append(" NOT NULL");
			}
			if (t.getDefaultValue() != null) {
				sb.append(" DEFAULT ").append(t.getDefaultValue());
			}
		}
		return sb.toString();
	}

	private static String sqlTypeName(final DbColumnType t) {
		final DbSimpleDataType simple = t.getSimpleDataType();
		final String base = t.getTypeName();
		switch (simple) {
			case String:
				return base + "(" + t.getCharacterByteSize() + ")";
			case Float:
				if (t.getNumericPrecision() > 0) {
					return base + "(" + t.getNumericPrecision() + ", " + t.getNumericScale() + ")";
				}
				return base;
			case BigInteger:
			case Blob:
			case Boolean:
			case Clob:
			case Date:
			case DateTime:
			case Integer:
			default:
				return base;
		}
	}

	private static String inlineForeignKey(final DbForeignKey fk) {
		final StringBuilder sb = new StringBuilder();
		if (fk.getForeignKeyName() != null) {
			sb.append("CONSTRAINT ").append(quote(fk.getForeignKeyName())).append(' ');
		}
		sb.append("FOREIGN KEY (").append(quoteList(fk.getColumnNames())).append(')');
		sb.append(" REFERENCES ").append(quote(fk.getReferencedTableName()));
		if (fk.getReferencedColumnNames() != null && !fk.getReferencedColumnNames().isEmpty()) {
			sb.append(" (").append(quoteList(fk.getReferencedColumnNames())).append(')');
		}
		return sb.toString();
	}

	private static String quote(final String name) {
		if (name == null) {
			return "\"\"";
		}
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private static String quoteList(final List<String> names) {
		if (names == null || names.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(quote(names.get(i)));
		}
		return sb.toString();
	}

	private static String qualifiedName(final String schemaName, final String tableName) {
		if (schemaName == null || schemaName.isEmpty()) {
			return quote(tableName);
		}
		return quote(schemaName) + "." + quote(tableName);
	}

	private static String sqlString(final String value) {
		if (value == null) {
			return "NULL";
		}
		return "'" + value.replace("'", "''") + "'";
	}
}