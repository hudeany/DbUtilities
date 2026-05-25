package de.soderer.utilities.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
	/**
	 * Parses {@code structureSqlDataA} and {@code structureSqlDataB}, merges their structures, and writes
	 * the resulting unified DDL to {@code mergeSqlData}.
	 *
	 * @param fileA      first SQL DDL file
	 * @param fileB      second SQL DDL file (wins on conflicts)
	 * @param outputFile path where the merged DDL will be written
	 * @throws IOException          on read / write errors
	 * @throws DbStructureException if either file contains structural errors
	 */
	public static void merge(final InputStream structureSqlDataA, final InputStream structureSqlDataB, final OutputStream mergeSqlData) throws IOException, DbStructureException {
		final DbStructure structureA = SqlDdlParser.parse(structureSqlDataA);
		final DbStructure structureB = SqlDdlParser.parse(structureSqlDataB);

		final DbStructure merged = mergeStructures(structureA, structureB);

		try (final PrintWriter writer = new PrintWriter(new OutputStreamWriter(mergeSqlData, StandardCharsets.UTF_8))) {
			writer.println("-- Merged DDL");
			writer.println("-- Generated: " + java.time.LocalDateTime.now());
			writer.println();

			for (final Map.Entry<String, DbSchema> schemaEntry : merged.getSchemas().entrySet()) {
				final String schemaName = schemaEntry.getKey();
				final DbSchema schema = schemaEntry.getValue();

				if (!schemaName.isEmpty()) {
					writer.println("CREATE SCHEMA " + quote(schemaName) + ";");
					if (schema.getSchemaComment() != null) {
						writer.println("COMMENT ON SCHEMA " + quote(schemaName) + " IS " + sqlString(schema.getSchemaComment()) + ";");
					}
					writer.println();
				}

				for (final DbTable table : schema.getTables().values()) {
					writeCreateTable(writer, schemaName, table);
				}
			}
		}
	}

	/**
	 * Returns a new {@link DbStructure} that is the union of {@code a} and
	 * {@code b}. When the same schema / table / column appears in both,
	 * {@code b} is authoritative.
	 */
	private static DbStructure mergeStructures(final DbStructure a, final DbStructure b) throws DbStructureException {
		final DbStructure result = new DbStructure();

		// Collect all schema names (order: A first, then new ones from B)
		final List<String> schemaNames = new ArrayList<>(a.getSchemas().keySet());
		for (final String name : b.getSchemas().keySet()) {
			if (!schemaNames.contains(name)) {
				schemaNames.add(name);
			}
		}

		for (final String schemaName : schemaNames) {
			final DbSchema schemaA = a.getSchemas().get(schemaName);
			final DbSchema schemaB = b.getSchemas().get(schemaName);

			final DbSchema mergedSchema = mergeSchemas(schemaA, schemaB);
			result.createSchema(schemaName, mergedSchema);
		}

		return result;
	}

	private static DbSchema mergeSchemas(final DbSchema schemaA, final DbSchema schemaB) throws DbStructureException {
		// One of the two may be null when the schema exists only in one file
		final DbSchema base = schemaA != null ? schemaA : schemaB;
		final DbSchema other = schemaA != null ? schemaB : null;

		final DbSchema result = new DbSchema();
		result.setSchemaName(base.getSchemaName());

		// Comment: B wins
		final String comment = (other != null && other.getSchemaComment() != null)
				? other.getSchemaComment()
				: base.getSchemaComment();
		if (comment != null) {
			result.setSchemaComment(comment);
		}

		if (other == null) {
			// Only one side has this schema — copy all tables verbatim
			for (final Map.Entry<String, DbTable> e : base.getTables().entrySet()) {
				result.createTable(e.getKey(), e.getValue());
			}
			return result;
		}

		// Both sides have this schema — merge tables
		final List<String> tableNames = new ArrayList<>(base.getTables().keySet());
		for (final String name : other.getTables().keySet()) {
			if (!tableNames.contains(name)) {
				tableNames.add(name);
			}
		}

		for (final String tableName : tableNames) {
			final DbTable tableA = base.getTables().get(tableName);
			final DbTable tableB = other.getTables().get(tableName);
			final DbTable mergedTable = mergeTables(tableA, tableB);
			result.createTable(tableName, mergedTable);
		}

		return result;
	}

	private static DbTable mergeTables(final DbTable tableA, final DbTable tableB) throws DbStructureException {
		// One may be null when the table exists only in one file
		final DbTable base = tableA != null ? tableA : tableB;
		final DbTable other = tableA != null ? tableB : null;

		final DbTable result = new DbTable();
		result.setTableName(base.getTableName());

		if (other == null) {
			// Table only in one file — copy verbatim
			for (final Map.Entry<String, DbColumn> e : base.getColumns().entrySet()) {
				result.createColumn(e.getKey(), e.getValue());
			}
			result.setPrimaryKey(base.getPrimaryKey());
			if (base.getForeignKeys() != null) {
				for (final DbForeignKey fk : base.getForeignKeys()) {
					result.addForeignKey(fk);
				}
			}
			if (base.getUniqueKeys() != null) {
				for (final Map.Entry<String, List<String>> uq : base.getUniqueKeys().entrySet()) {
					result.addUniqueKey(uq.getKey(), uq.getValue());
				}
			}
			result.setTableComment(base.getTableComment());
			return result;
		}

		// ---- Columns: A first, then new ones from B; B wins on conflict ----
		final List<String> colNames = new ArrayList<>(base.getColumns().keySet());
		for (final String name : other.getColumns().keySet()) {
			if (!colNames.contains(name)) {
				colNames.add(name);
			}
		}
		for (final String colName : colNames) {
			final DbColumn colA = base.getColumns().get(colName);
			final DbColumn colB = other.getColumns().get(colName);
			// B wins; fall back to A when B doesn't have this column
			result.createColumn(colName, colB != null ? colB : colA);
		}

		// ---- Primary key: B wins ----
		final List<String> pk = (other.getPrimaryKey() != null && !other.getPrimaryKey().isEmpty())
				? other.getPrimaryKey()
				: base.getPrimaryKey();
		result.setPrimaryKey(pk);

		// ---- Unique keys: union; B wins on name conflict ----
		if (base.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : base.getUniqueKeys().entrySet()) {
				if (!other.getUniqueKeys().containsKey(uq.getKey())) {
					result.addUniqueKey(uq.getKey(), uq.getValue());
				}
			}
		}
		if (other.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : other.getUniqueKeys().entrySet()) {
				result.addUniqueKey(uq.getKey(), uq.getValue());
			}
		}

		// ---- Foreign keys: union by name; B wins on name conflict ----
		final List<DbForeignKey> mergedFks = new ArrayList<>();
		if (base.getForeignKeys() != null) {
			for (final DbForeignKey fk : base.getForeignKeys()) {
				final boolean overriddenByB = other.getForeignKeys() != null
						&& other.getForeignKeys().stream()
								.anyMatch(bFk -> fk.getForeignKeyName() != null
										&& fk.getForeignKeyName().equals(bFk.getForeignKeyName()));
				if (!overriddenByB) {
					mergedFks.add(fk);
				}
			}
		}
		if (other.getForeignKeys() != null) {
			mergedFks.addAll(other.getForeignKeys());
		}
		for (final DbForeignKey fk : mergedFks) {
			result.addForeignKey(fk);
		}

		// ---- Comment: B wins ----
		final String tableComment = other.getTableComment() != null
				? other.getTableComment()
				: base.getTableComment();
		if (tableComment != null) {
			result.setTableComment(tableComment);
		}

		return result;
	}

	// -------------------------------------------------------------------------
	// DDL output
	// -------------------------------------------------------------------------

	private static void writeCreateTable(final PrintWriter writer, final String schemaName, final DbTable table) {
		final String qualifiedTable = qualifiedName(schemaName, table.getTableName());

		writer.println("CREATE TABLE " + qualifiedTable + " (");

		final List<String> entries = new ArrayList<>();

		for (final DbColumn col : table.getColumns().values()) {
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

		for (final DbColumn col : table.getColumns().values()) {
			if (col.getColumnComment() != null) {
				writer.println("COMMENT ON COLUMN " + qualifiedTable + "." + quote(col.getColumnName())
						+ " IS " + sqlString(col.getColumnComment()) + ";");
			}
		}

		writer.println();
	}

	// -------------------------------------------------------------------------
	// SQL snippet helpers (same conventions as SqlDdlMigrationGenerator)
	// -------------------------------------------------------------------------

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