package de.soderer.utilities.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.soderer.utilities.db.data.DbColumn;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbForeignKey;
import de.soderer.utilities.db.data.DbSchema;
import de.soderer.utilities.db.data.DbSimpleDataType;
import de.soderer.utilities.db.data.DbStructure;
import de.soderer.utilities.db.data.DbTable;
import de.soderer.utilities.db.exception.DbStructureException;

/**
 * Generates a SQL migration script that transforms a <em>source</em> database
 * structure into a <em>destination</em> database structure.
 *
 * <p>The following DDL changes are detected and emitted:
 * <ul>
 *   <li>New schemas => {@code CREATE SCHEMA}</li>
 *   <li>Dropped schemas => {@code DROP SCHEMA} (with all contained tables)</li>
 *   <li>New tables => {@code CREATE TABLE} (columns, PK, FKs, unique keys)</li>
 *   <li>Dropped tables => {@code DROP TABLE}</li>
 *   <li>New columns => {@code ALTER TABLE … ADD COLUMN}</li>
 *   <li>Dropped columns => {@code ALTER TABLE … DROP COLUMN}</li>
 *   <li>Changed column types => {@code ALTER TABLE … ALTER COLUMN … TYPE}</li>
 *   <li>Changed nullability => {@code ALTER TABLE … ALTER COLUMN … SET/DROP NOT NULL}</li>
 *   <li>Changed default values => {@code ALTER TABLE … ALTER COLUMN … SET/DROP DEFAULT}</li>
 *   <li>Changed primary keys => {@code DROP CONSTRAINT} + {@code ADD PRIMARY KEY}</li>
 *   <li>New unique keys => {@code ALTER TABLE … ADD CONSTRAINT … UNIQUE}</li>
 *   <li>Dropped unique keys => {@code ALTER TABLE … DROP CONSTRAINT}</li>
 *   <li>New foreign keys => {@code ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY}</li>
 *   <li>Dropped foreign keys => {@code ALTER TABLE … DROP CONSTRAINT}</li>
 *   <li>Changed comments => {@code COMMENT ON …}</li>
 * </ul>
 */
public class SqlDdlMigrationGenerator {
	private static class MigrationStatistics {
		int schemasCreated          = 0;
		int schemasDropped          = 0;
		int tablesCreated           = 0;
		int tablesDropped           = 0;
		int columnsAdded            = 0;
		int columnsDropped          = 0;
		int columnsTypeChanged      = 0;
		int columnsNullChanged      = 0;
		int columnsDefaultChanged   = 0;
		int columnsCommentChanged   = 0;
		int primaryKeysChanged      = 0;
		int uniqueKeysAdded         = 0;
		int uniqueKeysDropped       = 0;
		int foreignKeysAdded        = 0;
		int foreignKeysDropped      = 0;
		int commentsChanged         = 0; // table / schema comments

		private static void writeLine(final PrintWriter writer, final String label, final int value) {
			if (value > 0) {
				writer.println("--  " + label + value);
			}
		}

		void writeTo(final PrintWriter writer) {
			writer.println("-- ============================================================");
			writer.println("-- Migration Statistics");
			writer.println("-- ============================================================");
			writer.println("--");
			writeLine(writer, "Schemas  created  : ", schemasCreated);
			writeLine(writer, "         dropped  : ", schemasDropped);
			writer.println("--");
			writeLine(writer, "Tables   created  : ", tablesCreated);
			writeLine(writer, "         dropped  : ", tablesDropped);
			writer.println("--");
			writeLine(writer, "Columns  added    : ", columnsAdded);
			writeLine(writer, "         dropped  : ", columnsDropped);
			writeLine(writer, "         type     : ", columnsTypeChanged);
			writeLine(writer, "         nullable : ", columnsNullChanged);
			writeLine(writer, "         default  : ", columnsDefaultChanged);
			writeLine(writer, "         comment  : ", columnsCommentChanged);
			writer.println("--");
			writeLine(writer, "PK       changed  : ", primaryKeysChanged);
			writer.println("--");
			writeLine(writer, "Unique   added    : ", uniqueKeysAdded);
			writeLine(writer, "         dropped  : ", uniqueKeysDropped);
			writer.println("--");
			writeLine(writer, "FK       added    : ", foreignKeysAdded);
			writeLine(writer, "         dropped  : ", foreignKeysDropped);
			writer.println("--");
			writeLine(writer, "Comments changed  : ", commentsChanged);
			writer.println("-- ============================================================");
			writer.println();
		}
	}

	/**
	 * Convenience overload without sorting.
	 */
	public static void diff(final InputStream sourceSqlData, final InputStream destinationSqlData,
			final OutputStream diffSqlData) throws IOException, DbStructureException {
		diff(sourceSqlData, destinationSqlData, diffSqlData, false, false, false);
	}

	/**
	 * Reads {@code sourceSqlData} and {@code destinationSqlData}, computes the
	 * structural diff, and writes the resulting migration script to
	 * {@code diffSqlData}.
	 *
	 * @param sourceSqlData      current database structure as SQL DDL stream
	 * @param destinationSqlData desired database structure as SQL DDL stream
	 * @param diffSqlData        output stream for the migration script
	 * @param sortBySchema       if {@code true}, schema-level statements are emitted in alphabetical order
	 * @param sortByTable        if {@code true}, table-level statements are emitted in alphabetical order per schema
	 * @param sortByColumn       if {@code true}, column definitions in CREATE TABLE are emitted in alphabetical order
	 * @throws IOException          on read / write errors
	 * @throws DbStructureException if either file contains structural errors
	 */
	public static void diff(final InputStream sourceSqlData, final InputStream destinationSqlData,
			final OutputStream diffSqlData,
			final boolean sortBySchema, final boolean sortByTable, final boolean sortByColumn)
			throws IOException, DbStructureException {

		final DbStructure source = SqlDdlParser.parse(sourceSqlData);
		final DbStructure destination = SqlDdlParser.parse(destinationSqlData);

		final MigrationStatistics stats = new MigrationStatistics();
		final List<String> statements = diffStructures(source, destination, stats, sortBySchema, sortByTable, sortByColumn);

		try (final PrintWriter writer = new PrintWriter(new OutputStreamWriter(diffSqlData, StandardCharsets.UTF_8))) {
			writer.println("-- Migration script");
			writer.println("-- Generated  : " + java.time.LocalDateTime.now());
			writer.println();
			stats.writeTo(writer);
			if (statements.isEmpty()) {
				writer.println("-- No structural differences detected.");
			} else {
				for (final String stmt : statements) {
					writer.println(stmt);
					writer.println();
				}
			}
		}
	}

	/**
	 * Computes the list of SQL statements needed to migrate {@code source} into
	 * {@code destination}.
	 *
	 * @param source      current database structure
	 * @param destination desired database structure
	 * @return ordered list of SQL statements (may be empty, never {@code null})
	 */
	private static List<String> diffStructures(final DbStructure source, final DbStructure destination,
			final MigrationStatistics stats,
			final boolean sortBySchema, final boolean sortByTable, final boolean sortByColumn) {

		final List<String> statements = new ArrayList<>();

		final Map<String, DbSchema> srcSchemas = source.getSchemas();
		final Map<String, DbSchema> dstSchemas = destination.getSchemas();

		final List<String> allSchemaNames = new ArrayList<>();
		for (final String name : srcSchemas.keySet()) {
			allSchemaNames.add(name);
		}
		for (final String name : dstSchemas.keySet()) {
			if (!allSchemaNames.contains(name)) {
				allSchemaNames.add(name);
			}
		}
		if (sortBySchema) {
			allSchemaNames.sort(Comparator.naturalOrder());
		}

		for (final String schemaName : allSchemaNames) {
			final boolean inSrc = srcSchemas.containsKey(schemaName);
			final boolean inDst = dstSchemas.containsKey(schemaName);

			if (inSrc && !inDst) {
				final DbSchema srcSchema = srcSchemas.get(schemaName);
				final List<String> tableNames = new ArrayList<>(srcSchema.getTables().keySet());
				if (sortByTable) {
					tableNames.sort(Comparator.naturalOrder());
				}
				for (final String tableName : tableNames) {
					statements.add(dropTable(schemaName, tableName));
					stats.tablesDropped++;
				}
				statements.add("DROP SCHEMA " + quote(schemaName) + ";");
				stats.schemasDropped++;

			} else if (!inSrc && inDst) {
				final DbSchema dstSchema = dstSchemas.get(schemaName);
				statements.add("CREATE SCHEMA " + quote(schemaName) + ";");
				stats.schemasCreated++;
				final List<String> tableNames = new ArrayList<>(dstSchema.getTables().keySet());
				if (sortByTable) {
					tableNames.sort(Comparator.naturalOrder());
				}
				for (final String tableName : tableNames) {
					statements.addAll(createTable(schemaName, dstSchema.getTables().get(tableName), sortByColumn));
					stats.tablesCreated++;
				}

			} else {
				statements.addAll(diffSchema(schemaName, srcSchemas.get(schemaName), dstSchemas.get(schemaName),
						stats, sortByTable, sortByColumn));
			}
		}

		return statements;
	}

	private static List<String> diffSchema(final String schemaName, final DbSchema source, final DbSchema destination,
			final MigrationStatistics stats, final boolean sortByTable, final boolean sortByColumn) {

		final List<String> statements = new ArrayList<>();

		final Map<String, DbTable> srcTables = source.getTables();
		final Map<String, DbTable> dstTables = destination.getTables();

		final List<String> allTableNames = new ArrayList<>();
		for (final String name : srcTables.keySet()) {
			allTableNames.add(name);
		}
		for (final String name : dstTables.keySet()) {
			if (!allTableNames.contains(name)) {
				allTableNames.add(name);
			}
		}
		if (sortByTable) {
			allTableNames.sort(Comparator.naturalOrder());
		}

		for (final String tableName : allTableNames) {
			final boolean inSrc = srcTables.containsKey(tableName);
			final boolean inDst = dstTables.containsKey(tableName);

			if (inSrc && !inDst) {
				statements.add(dropTable(schemaName, tableName));
				stats.tablesDropped++;
			} else if (!inSrc && inDst) {
				statements.addAll(createTable(schemaName, dstTables.get(tableName), sortByColumn));
				stats.tablesCreated++;
			} else {
				statements.addAll(diffTable(schemaName, srcTables.get(tableName), dstTables.get(tableName), stats, sortByColumn));
			}
		}

		if (!Objects.equals(source.getSchemaComment(), destination.getSchemaComment())
				&& destination.getSchemaComment() != null) {
			statements.add("COMMENT ON SCHEMA " + quote(schemaName)
					+ " IS " + sqlString(destination.getSchemaComment()) + ";");
			stats.commentsChanged++;
		}

		return statements;
	}

	private static List<String> diffTable(final String schemaName, final DbTable source, final DbTable destination,
			final MigrationStatistics stats, final boolean sortByColumn) {

		final List<String> statements = new ArrayList<>();
		final String qualifiedTable = qualifiedName(schemaName, destination.getTableName());

		final Map<String, DbColumn> srcCols = source.getColumns();
		final Map<String, DbColumn> dstCols = destination.getColumns();

		final List<String> allColNames = new ArrayList<>();
		for (final String name : srcCols.keySet()) {
			allColNames.add(name);
		}
		for (final String name : dstCols.keySet()) {
			if (!allColNames.contains(name)) {
				allColNames.add(name);
			}
		}
		if (sortByColumn) {
			allColNames.sort(Comparator.naturalOrder());
		}

		for (final String colName : allColNames) {
			final boolean inSrc = srcCols.containsKey(colName);
			final boolean inDst = dstCols.containsKey(colName);

			if (inSrc && !inDst) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " DROP COLUMN " + quote(colName) + ";");
				stats.columnsDropped++;
			} else if (!inSrc && inDst) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ADD COLUMN " + columnDefinition(dstCols.get(colName)) + ";");
				stats.columnsAdded++;
			} else {
				statements.addAll(diffColumn(qualifiedTable, srcCols.get(colName), dstCols.get(colName), stats));
			}
		}

		final int pksBefore = statements.size();
		statements.addAll(diffPrimaryKey(qualifiedTable, source, destination));
		if (statements.size() > pksBefore) {
			stats.primaryKeysChanged++;
		}

		statements.addAll(diffUniqueKeys(qualifiedTable, source.getUniqueKeys(), destination.getUniqueKeys(), stats));

		statements.addAll(diffForeignKeys(qualifiedTable, source.getForeignKeys(), destination.getForeignKeys(), stats));

		if (!Objects.equals(source.getTableComment(), destination.getTableComment())
				&& destination.getTableComment() != null) {
			statements.add("COMMENT ON TABLE " + qualifiedTable
					+ " IS " + sqlString(destination.getTableComment()) + ";");
			stats.commentsChanged++;
		}

		return statements;
	}

	private static List<String> diffColumn(final String qualifiedTable, final DbColumn source,
			final DbColumn destination, final MigrationStatistics stats) {
		final List<String> statements = new ArrayList<>();
		final String colName = quote(destination.getColumnName());
		final DbColumnType srcType = source.getColumnType();
		final DbColumnType dstType = destination.getColumnType();

		if (srcType == null || dstType == null) {
			return statements;
		}

		if (!typeSignatureEquals(srcType, dstType)) {
			statements.add("ALTER TABLE " + qualifiedTable
					+ " ALTER COLUMN " + colName
					+ " TYPE " + sqlTypeName(dstType) + ";");
			stats.columnsTypeChanged++;
		}

		if (srcType.isNullable() != dstType.isNullable()) {
			if (dstType.isNullable()) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ALTER COLUMN " + colName + " DROP NOT NULL;");
			} else {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ALTER COLUMN " + colName + " SET NOT NULL;");
			}
			stats.columnsNullChanged++;
		}

		if (!Objects.equals(srcType.getDefaultValue(), dstType.getDefaultValue())) {
			if (dstType.getDefaultValue() == null) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ALTER COLUMN " + colName + " DROP DEFAULT;");
			} else {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ALTER COLUMN " + colName + " SET DEFAULT " + dstType.getDefaultValue() + ";");
			}
			stats.columnsDefaultChanged++;
		}

		if (!Objects.equals(source.getColumnComment(), destination.getColumnComment())
				&& destination.getColumnComment() != null) {
			statements.add("COMMENT ON COLUMN " + qualifiedTable + "." + colName
					+ " IS " + sqlString(destination.getColumnComment()) + ";");
			stats.columnsCommentChanged++;
		}

		return statements;
	}

	private static List<String> diffPrimaryKey(final String qualifiedTable, final DbTable source,
			final DbTable destination) {
		final List<String> statements = new ArrayList<>();

		final List<String> srcPk = source.getPrimaryKey();
		final List<String> dstPk = destination.getPrimaryKey();

		if (Objects.equals(normalizeList(srcPk), normalizeList(dstPk))) {
			return statements;
		}

		if (srcPk != null && !srcPk.isEmpty()) {
			final String pkConstraintName = "pk_" + unqualifiedName(qualifiedTable);
			statements.add("ALTER TABLE " + qualifiedTable
					+ " DROP CONSTRAINT IF EXISTS " + quote(pkConstraintName) + ";");
		}

		if (dstPk != null && !dstPk.isEmpty()) {
			final String pkConstraintName = "pk_" + unqualifiedName(qualifiedTable);
			statements.add("ALTER TABLE " + qualifiedTable
					+ " ADD CONSTRAINT " + quote(pkConstraintName)
					+ " PRIMARY KEY (" + quoteList(dstPk) + ");");
		}

		return statements;
	}

	private static List<String> diffUniqueKeys(final String qualifiedTable,
			final Map<String, List<String>> srcUniqueKeys,
			final Map<String, List<String>> dstUniqueKeys,
			final MigrationStatistics stats) {

		final List<String> statements = new ArrayList<>();
		final Map<String, List<String>> src = srcUniqueKeys != null ? srcUniqueKeys : new LinkedHashMap<>();
		final Map<String, List<String>> dst = dstUniqueKeys != null ? dstUniqueKeys : new LinkedHashMap<>();

		for (final Map.Entry<String, List<String>> srcEntry : src.entrySet()) {
			final String name = srcEntry.getKey();
			if (!dst.containsKey(name) || !normalizeList(srcEntry.getValue()).equals(normalizeList(dst.get(name)))) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " DROP CONSTRAINT IF EXISTS " + quote(name) + ";");
				stats.uniqueKeysDropped++;
			}
		}

		for (final Map.Entry<String, List<String>> dstEntry : dst.entrySet()) {
			final String name = dstEntry.getKey();
			final boolean existsUnchanged = src.containsKey(name)
					&& normalizeList(src.get(name)).equals(normalizeList(dstEntry.getValue()));
			if (!existsUnchanged) {
				statements.add("ALTER TABLE " + qualifiedTable
						+ " ADD CONSTRAINT " + quote(name)
						+ " UNIQUE (" + quoteList(dstEntry.getValue()) + ");");
				stats.uniqueKeysAdded++;
			}
		}

		return statements;
	}

	private static List<String> diffForeignKeys(final String qualifiedTable,
			final List<DbForeignKey> srcForeignKeys, final List<DbForeignKey> dstForeignKeys,
			final MigrationStatistics stats) {

		final List<String> statements = new ArrayList<>();
		final List<DbForeignKey> src = srcForeignKeys != null ? srcForeignKeys : new ArrayList<>();
		final List<DbForeignKey> dst = dstForeignKeys != null ? dstForeignKeys : new ArrayList<>();

		for (final DbForeignKey srcFk : src) {
			final boolean stillPresent = dst.stream()
					.anyMatch(dstFk -> foreignKeysEqual(srcFk, dstFk));
			if (!stillPresent) {
				final String fkName = srcFk.getForeignKeyName();
				if (fkName != null) {
					statements.add("ALTER TABLE " + qualifiedTable
							+ " DROP CONSTRAINT IF EXISTS " + quote(fkName) + ";");
					stats.foreignKeysDropped++;
				}
			}
		}

		for (final DbForeignKey dstFk : dst) {
			final boolean alreadyExists = src.stream()
					.anyMatch(srcFk -> foreignKeysEqual(srcFk, dstFk));
			if (!alreadyExists) {
				statements.add(addForeignKeyStatement(qualifiedTable, dstFk));
				stats.foreignKeysAdded++;
			}
		}

		return statements;
	}

	private static List<String> createTable(final String schemaName, final DbTable table,
			final boolean sortByColumn) {
		final List<String> statements = new ArrayList<>();
		final String qualifiedTable = qualifiedName(schemaName, table.getTableName());
		final StringBuilder sb = new StringBuilder();

		sb.append("CREATE TABLE ").append(qualifiedTable).append(" (");

		final List<String> entries = new ArrayList<>();

		final List<DbColumn> columns = new ArrayList<>(table.getColumns().values());
		if (sortByColumn) {
			columns.sort(Comparator.comparing(DbColumn::getColumnName));
		}
		for (final DbColumn col : columns) {
			entries.add("\n    " + columnDefinition(col));
		}

		final List<String> pk = table.getPrimaryKey();
		if (pk != null && !pk.isEmpty()) {
			final String pkConstraintName = "pk_" + table.getTableName();
			entries.add("\n    CONSTRAINT " + quote(pkConstraintName)
					+ " PRIMARY KEY (" + quoteList(pk) + ")");
		}

		if (table.getUniqueKeys() != null) {
			for (final Map.Entry<String, List<String>> uq : table.getUniqueKeys().entrySet()) {
				entries.add("\n    CONSTRAINT " + quote(uq.getKey())
						+ " UNIQUE (" + quoteList(uq.getValue()) + ")");
			}
		}

		if (table.getForeignKeys() != null) {
			for (final DbForeignKey fk : table.getForeignKeys()) {
				entries.add("\n    " + inlineForeignKey(fk));
			}
		}

		sb.append(String.join(",", entries));
		sb.append("\n);");

		statements.add(sb.toString());

		if (table.getTableComment() != null) {
			statements.add("COMMENT ON TABLE " + qualifiedTable
					+ " IS " + sqlString(table.getTableComment()) + ";");
		}

		for (final DbColumn col : columns) {
			if (col.getColumnComment() != null) {
				statements.add("COMMENT ON COLUMN " + qualifiedTable + "." + quote(col.getColumnName())
						+ " IS " + sqlString(col.getColumnComment()) + ";");
			}
		}

		return statements;
	}

	private static String dropTable(final String schemaName, final String tableName) {
		return "DROP TABLE IF EXISTS " + qualifiedName(schemaName, tableName) + ";";
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

	private static String addForeignKeyStatement(final String qualifiedTable, final DbForeignKey fk) {
		return "ALTER TABLE " + qualifiedTable + " ADD " + inlineForeignKey(fk) + ";";
	}

	private static boolean typeSignatureEquals(final DbColumnType a, final DbColumnType b) {
		if (!a.getTypeName().equalsIgnoreCase(b.getTypeName())) {
			return false;
		}
		final DbSimpleDataType simple = a.getSimpleDataType();
		if (simple == DbSimpleDataType.String && a.getCharacterByteSize() != b.getCharacterByteSize()) {
			return false;
		}
		if (simple == DbSimpleDataType.Float
				&& (a.getNumericPrecision() != b.getNumericPrecision()
						|| a.getNumericScale() != b.getNumericScale())) {
			return false;
		}
		return true;
	}

	private static boolean foreignKeysEqual(final DbForeignKey a, final DbForeignKey b) {
		return Objects.equals(a.getForeignKeyName(), b.getForeignKeyName())
				&& Objects.equals(normalizeList(a.getColumnNames()), normalizeList(b.getColumnNames()))
				&& Objects.equals(a.getReferencedTableName(), b.getReferencedTableName())
				&& Objects.equals(normalizeList(a.getReferencedColumnNames()),
						normalizeList(b.getReferencedColumnNames()));
	}

	private static String quote(final String name) {
		final boolean needsQuoteForSyntax = !DbUtilities.SAFE_IDENTIFIER.matcher(name).matches();

		if (name == null) {
			return "\"\"";
		} else if (needsQuoteForSyntax) {
			return "\"" + name.replace("\"", "\"\"") + "\"";
		} else {
			return name;
		}
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

	private static String unqualifiedName(final String qualifiedTable) {
		final int dot = qualifiedTable.lastIndexOf('.');
		final String raw = dot >= 0 ? qualifiedTable.substring(dot + 1) : qualifiedTable;
		return raw.startsWith("\"") && raw.endsWith("\"")
				? raw.substring(1, raw.length() - 1)
				: raw;
	}

	private static String sqlString(final String value) {
		if (value == null) {
			return "NULL";
		}
		return "'" + value.replace("'", "''") + "'";
	}

	private static List<String> normalizeList(final List<String> list) {
		if (list == null) {
			return new ArrayList<>();
		}
		final List<String> normalized = new ArrayList<>(list.size());
		for (final String s : list) {
			normalized.add(s == null ? null : s.toLowerCase().trim());
		}
		return normalized;
	}
}