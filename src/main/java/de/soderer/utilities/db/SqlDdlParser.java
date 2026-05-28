package de.soderer.utilities.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.soderer.utilities.db.data.DbColumn;
import de.soderer.utilities.db.data.DbColumnType;
import de.soderer.utilities.db.data.DbForeignKey;
import de.soderer.utilities.db.data.DbSchema;
import de.soderer.utilities.db.data.DbStructure;
import de.soderer.utilities.db.data.DbTable;
import de.soderer.utilities.db.exception.DbStructureException;

/**
 * Parses a stream of SQL DDL statements (CREATE SCHEMA / CREATE TABLE) and
 * populates a {@link DbStructure}.
 *
 * <p>
 * Supported statements:
 * <ul>
 * <li>{@code CREATE SCHEMA <name>}</li>
 * <li>{@code CREATE TABLE [<schema>.]
 * <table>
 *  ( <column_defs>, [constraints] )}</li>
 * <li>{@code CREATE TABLE … AS SELECT …} — detected and skipped (no column
 * block)</li>
 * <li>{@code ALTER TABLE [<schema>.]
 * <table>
 *  <clauses>} — see below</li>
 * <li>{@code COMMENT ON TABLE  [<schema>.]
 * <table>
 *   IS '<text>'} (PostgreSQL / Oracle)</li>
 * <li>{@code COMMENT ON COLUMN [<schema>.]
 * <table>
 * .<col> IS '<text>'} (PostgreSQL / Oracle)</li>
 * <li>{@code COMMENT ON SCHEMA <schema> IS '<text>'} (PostgreSQL)</li>
 * </ul>
 *
 * <p>
 * Supported {@code ALTER TABLE} clauses (multiple comma-separated clauses per
 * statement allowed):
 * <ul>
 * <li>{@code ADD [COLUMN] <col_def>}</li>
 * <li>{@code DROP [COLUMN] <col>}</li>
 * <li>{@code MODIFY [COLUMN] <col_def>} (MySQL / Oracle)</li>
 * <li>{@code ALTER [COLUMN] <col> TYPE <type> [USING …]} (PostgreSQL)</li>
 * <li>{@code ALTER [COLUMN] <col> SET NOT NULL / DROP NOT NULL}
 * (PostgreSQL)</li>
 * <li>{@code ALTER [COLUMN] <col> SET DEFAULT <expr> / DROP DEFAULT}
 * (PostgreSQL)</li>
 * <li>{@code CHANGE [COLUMN] <old> <new_col_def>} (MySQL rename + retype)</li>
 * <li>{@code RENAME COLUMN <old> TO <new>}</li>
 * <li>{@code ADD [CONSTRAINT <name>] PRIMARY KEY (<cols>)}</li>
 * <li>{@code ADD [CONSTRAINT <name>] UNIQUE (<cols>)}</li>
 * <li>{@code ADD [CONSTRAINT <name>] FOREIGN KEY (<cols>) REFERENCES …}</li>
 * <li>{@code DROP CONSTRAINT <name>}</li>
 * <li>{@code DROP PRIMARY KEY}</li>
 * <li>{@code DROP FOREIGN KEY <name>} (MySQL)</li>
 * <li>{@code DROP INDEX <name>} (MySQL unique index)</li>
 * </ul>
 *
 * <p>
 * Supported column-level options:
 * <ul>
 * <li>Type with optional length / precision+scale: {@code VARCHAR(255)},
 * {@code NUMERIC(10,2)}</li>
 * <li>{@code NOT NULL} / {@code NULL}</li>
 * <li>{@code DEFAULT <value>} — stored in
 * {@link DbColumnType#getDefaultValue()}; supports string literals, bare
 * keywords/numbers, and parenthesised expressions</li>
 * <li>{@code AUTO_INCREMENT} / {@code AUTOINCREMENT} /
 * {@code GENERATED ALWAYS AS IDENTITY}</li>
 * <li>{@code PRIMARY KEY} (inline or table-level
 * {@code CONSTRAINT … PRIMARY KEY (…)})</li>
 * <li>{@code UNIQUE} (inline or table-level; stored in
 * {@link DbTable#getUniqueKeys()})</li>
 * <li>{@code CONSTRAINT … FOREIGN KEY (…) REFERENCES
 * <table>
 *  (…)}</li>
 * <li>MySQL inline column comment: {@code ... COMMENT 'text'}</li>
 * </ul>
 *
 * <p>
 * MySQL table-level comment suffix {@code ... COMMENT [=] 'text'} on CREATE
 * TABLE is also supported.
 *
 * <p>
 * SQL syntax comments ({@code -- …} and {@code /* … *}{@code /}) are stripped
 * before parsing.
 */
public class SqlDdlParser {

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Parse all DDL statements from {@code inputStream} and return a fully
	 * populated {@link DbStructure}.
	 *
	 * <p>
	 * Recognised statement types: {@code CREATE SCHEMA}, {@code CREATE TABLE},
	 * {@code ALTER TABLE}, {@code COMMENT ON TABLE}, {@code COMMENT ON COLUMN},
	 * {@code COMMENT ON SCHEMA}. All other statement types are silently ignored.
	 *
	 * @param inputStream SQL text encoded in UTF-8
	 * @return populated structure (never {@code null})
	 * @throws IOException          on read errors
	 * @throws DbStructureException on duplicate / missing schema, table, or column
	 *                              names
	 */
	public static DbStructure parse(final InputStream inputStream) throws IOException, DbStructureException {

		final String sql = readAll(inputStream);
		final String stripped = stripComments(sql);
		final List<String> statements = splitStatements(stripped);

		final DbStructure structure = new DbStructure();

		for (final String stmt : statements) {
			final String trimmed = stmt.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			final String upper = trimmed.toUpperCase();
			if (upper.startsWith("CREATE SCHEMA")) {
				parseCreateSchema(trimmed, structure);
			} else if (upper.startsWith("CREATE TABLE")) {
				parseCreateTable(trimmed, structure);
			} else if (upper.startsWith("ALTER TABLE")) {
				parseAlterTable(trimmed, structure);
			} else if (upper.startsWith("COMMENT ON")) {
				parseCommentOn(trimmed, structure);
			}
			// All other statement types (CREATE INDEX, DROP TABLE, …) are ignored.
		}
		return structure;
	}

	// -------------------------------------------------------------------------
	// Schema parsing
	// -------------------------------------------------------------------------

	private static void parseCreateSchema(final String stmt, final DbStructure structure) throws DbStructureException {

		// CREATE SCHEMA [IF NOT EXISTS] <name>
		final Pattern p = Pattern.compile("(?i)CREATE\\s+SCHEMA\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w\"`.]+)");
		final Matcher m = p.matcher(stmt);
		if (!m.find()) {
			return;
		}
		final String schemaName = unquote(m.group(1));
		final DbSchema schema = new DbSchema().setSchemaName(schemaName);
		structure.createSchema(schemaName, schema);
	}

	// -------------------------------------------------------------------------
	// Table parsing
	// -------------------------------------------------------------------------

	private static void parseCreateTable(final String stmt, final DbStructure structure) throws DbStructureException {

		// Guard: CREATE TABLE … AS SELECT … has no column-definition block — skip it.
		if (Pattern.compile("(?i)\\bAS\\s+SELECT\\b").matcher(stmt).find() && !stmt.contains("(")) {
			return;
		}

		// CREATE [OR REPLACE] TABLE [IF NOT EXISTS] [schema.]table ( … ) [COMMENT [=]
		// 'text']
		final Pattern tableHeader = Pattern
				.compile("(?i)CREATE\\s+(?:OR\\s+REPLACE\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
						+ "([\\w\"`.]+(?:\\.[\\w\"`.]+)?)\\s*\\((.+)\\)"
						+ "(?:[^']*COMMENT\\s*=?\\s*'((?:[^']|'')*)')?\\s*;?$", Pattern.DOTALL);

		final Matcher m = tableHeader.matcher(stmt);
		if (!m.find()) {
			return;
		}

		final String fullTableName = m.group(1);
		final String columnBlock = m.group(2);
		final String mysqlTableComment = m.group(3) != null ? unescapeSqlString(m.group(3)) : null;

		// Split schema.table
		final String schemaName;
		final String tableName;
		final int dot = fullTableName.lastIndexOf('.');
		if (dot >= 0) {
			schemaName = unquote(fullTableName.substring(0, dot));
			tableName = unquote(fullTableName.substring(dot + 1));
		} else {
			schemaName = null;
			tableName = unquote(fullTableName);
		}

		// Build table
		final DbTable table = new DbTable().setTableName(tableName);
		final LinkedHashMap<String, DbColumn> columns = new LinkedHashMap<>();
		final List<String> pkCols = new ArrayList<>();
		final LinkedHashMap<String, List<String>> uniqueKeys = new LinkedHashMap<>();
		final List<DbForeignKey> fkList = new ArrayList<>();

		for (final String entry : splitColumnEntries(columnBlock)) {
			final String e = entry.trim();
			if (e.isEmpty()) {
				continue;
			}
			final String eUpper = e.toUpperCase();

			if (eUpper.startsWith("PRIMARY KEY")) {
				// Table-level: PRIMARY KEY (col1, col2, …)
				pkCols.addAll(extractColumnList(e));

			} else if (eUpper.startsWith("UNIQUE")) {
				// Table-level: UNIQUE (col1, …)
				parseUniqueConstraint(e, tableName, null, uniqueKeys);

			} else if (eUpper.startsWith("CONSTRAINT")) {
				// CONSTRAINT name PRIMARY KEY / UNIQUE / FOREIGN KEY
				if (eUpper.contains("PRIMARY KEY")) {
					pkCols.addAll(extractColumnList(e));
				} else if (eUpper.contains("UNIQUE")) {
					// Extract the constraint name to pass it explicitly
					final Pattern cnPat = Pattern.compile("(?i)CONSTRAINT\\s+([\\w\"`.]+)");
					final Matcher cnm = cnPat.matcher(e);
					final String explicitName = cnm.find() ? unquote(cnm.group(1)) : null;
					parseUniqueConstraint(e, tableName, explicitName, uniqueKeys);
				} else {
					final DbForeignKey fk = parseForeignKey(e);
					if (fk != null) {
						fkList.add(fk);
					}
				}

			} else if (eUpper.startsWith("FOREIGN KEY")) {
				final DbForeignKey fk = parseForeignKey(e);
				if (fk != null) {
					fkList.add(fk);
				}

			} else if (eUpper.startsWith("INDEX") || eUpper.startsWith("KEY") || eUpper.startsWith("CHECK")) {
				// Ignored

			} else {
				// Column definition — also captures inline UNIQUE / PRIMARY KEY
				final DbColumn col = parseColumnDefinition(e, tableName, pkCols, uniqueKeys);
				if (col != null) {
					columns.put(col.getColumnName(), col);
				}
			}
		}

		// Wire up table internals
		for (final Entry<String, DbColumn> columnEntry : columns.entrySet()) {
			table.createColumn(columnEntry.getKey(), columnEntry.getValue());
		}
		table.setPrimaryKey(pkCols.isEmpty() ? null : pkCols);
		for (final Entry<String, List<String>> uq : uniqueKeys.entrySet()) {
			table.addUniqueKey(uq.getKey(), uq.getValue());
		}
		for (final DbForeignKey foreignKey : fkList) {
			table.addForeignKey(foreignKey);
		}
		if (mysqlTableComment != null) {
			table.setTableComment(mysqlTableComment);
		}

		// Ensure schema exists; create a default one if no schema was declared
		final String resolvedSchema = schemaName != null ? schemaName : "";
		ensureSchema(resolvedSchema, structure);

		final DbSchema schema = structure.getSchemas().get(resolvedSchema);
		schema.createTable(tableName, table);
	}

	// -------------------------------------------------------------------------
	// ALTER TABLE parsing
	// -------------------------------------------------------------------------

	/**
	 * Handles the most common structural {@code ALTER TABLE} forms:
	 *
	 * <pre>
	 * -- Column changes
	 * ALTER TABLE [schema.]t ADD [COLUMN] col_def
	 * ALTER TABLE [schema.]t DROP [COLUMN] col
	 * ALTER TABLE [schema.]t ALTER  COLUMN col …   (PostgreSQL)
	 * ALTER TABLE [schema.]t MODIFY [COLUMN] col_def  (MySQL / Oracle)
	 * ALTER TABLE [schema.]t CHANGE [COLUMN] old new col_def  (MySQL rename+retype)
	 * ALTER TABLE [schema.]t RENAME COLUMN old TO new
	 *
	 * -- Constraint changes
	 * ALTER TABLE [schema.]t ADD [CONSTRAINT name] PRIMARY KEY (cols)
	 * ALTER TABLE [schema.]t ADD [CONSTRAINT name] UNIQUE (cols)
	 * ALTER TABLE [schema.]t ADD [CONSTRAINT name] FOREIGN KEY (cols) REFERENCES …
	 * ALTER TABLE [schema.]t DROP CONSTRAINT name
	 * ALTER TABLE [schema.]t DROP PRIMARY KEY
	 * ALTER TABLE [schema.]t DROP FOREIGN KEY name   (MySQL)
	 * ALTER TABLE [schema.]t DROP INDEX name         (MySQL unique index)
	 * </pre>
	 *
	 * Multiple comma-separated clauses in one statement are supported:
	 * {@code ALTER TABLE t ADD col1 INT, ADD col2 VARCHAR(50)}. Unknown /
	 * unsupported clauses are silently ignored.
	 */
	private static void parseAlterTable(final String stmt, final DbStructure structure) throws DbStructureException {

		// ALTER TABLE [schema.]table <clauses>
		final Pattern headerPat = Pattern.compile("(?i)^ALTER\\s+TABLE\\s+([\\w\"`.]+(?:\\.[\\w\"`.]+)?)\\s+(.+)$",
				Pattern.DOTALL);
		final Matcher hm = headerPat.matcher(stmt.trim());
		if (!hm.find()) {
			return;
		}

		final String fullTableName = hm.group(1);
		final String clausesRaw = hm.group(2);

		// Resolve schema + table
		final String[] parts = splitObjectName(fullTableName);
		final String schemaName = parts[0];
		final String tableName = parts[1];

		final DbSchema schema = structure.getSchemas().get(schemaName);
		if (schema == null) {
			return; // table's schema not in this DDL stream — ignore
		}
		final DbTable table = schema.getTables().get(tableName);
		if (table == null) {
			return; // table not in this DDL stream — ignore
		}

		// Split into individual clauses on top-level commas
		for (final String clause : splitColumnEntries(clausesRaw)) {
			applyAlterClause(clause.trim(), tableName, table);
		}
	}

	/**
	 * Applies a single ALTER TABLE clause to {@code table}.
	 */
	private static void applyAlterClause(final String clause, final String tableName, final DbTable table)
			throws DbStructureException {

		final String upper = clause.toUpperCase();

		// ---- ADD ----
		if (upper.startsWith("ADD")) {
			final String body = clause.substring(3).trim(); // strip "ADD"
			final String bodyUpper = body.toUpperCase();

			// Strip optional leading COLUMN keyword for column definitions
			final String colBody = bodyUpper.startsWith("COLUMN") ? body.substring(6).trim() : body;
			final String colBodyUpper = colBody.toUpperCase();

			if (colBodyUpper.startsWith("PRIMARY KEY") || colBodyUpper.startsWith("CONSTRAINT")) {
				// ADD [CONSTRAINT name] PRIMARY KEY (cols)
				if (colBodyUpper.contains("PRIMARY KEY")) {
					final List<String> pkCols = extractColumnList(colBody);
					if (!pkCols.isEmpty()) {
						table.setPrimaryKey(pkCols);
					}
					return;
				}
				// ADD [CONSTRAINT name] UNIQUE (cols)
				if (colBodyUpper.contains("UNIQUE")) {
					final LinkedHashMap<String, List<String>> uqMap = new LinkedHashMap<>();
					final Pattern cnPat = Pattern.compile("(?i)CONSTRAINT\\s+([\\w\"`.]+)");
					final Matcher cnm = cnPat.matcher(colBody);
					final String explicitName = cnm.find() ? unquote(cnm.group(1)) : null;
					parseUniqueConstraint(colBody, tableName, explicitName, uqMap);
					for (final Entry<String, List<String>> uq : uqMap.entrySet()) {
						table.addUniqueKey(uq.getKey(), uq.getValue());
					}
					return;
				}
				// ADD [CONSTRAINT name] FOREIGN KEY (cols) REFERENCES …
				if (colBodyUpper.contains("FOREIGN KEY")) {
					final DbForeignKey fk = parseForeignKey(colBody);
					if (fk != null) {
						table.addForeignKey(fk);
					}
					return;
				}
			}

			// ADD FOREIGN KEY (cols) REFERENCES … (without CONSTRAINT prefix)
			if (colBodyUpper.startsWith("FOREIGN KEY")) {
				final DbForeignKey fk = parseForeignKey(colBody);
				if (fk != null) {
					table.addForeignKey(fk);
				}
				return;
			}

			// ADD UNIQUE (cols) / ADD UNIQUE INDEX name (cols)
			if (colBodyUpper.startsWith("UNIQUE")) {
				final LinkedHashMap<String, List<String>> uqMap = new LinkedHashMap<>();
				parseUniqueConstraint(colBody, tableName, null, uqMap);
				for (final Entry<String, List<String>> uq : uqMap.entrySet()) {
					table.addUniqueKey(uq.getKey(), uq.getValue());
				}
				return;
			}

			// ADD col_def (regular column)
			final List<String> dummyPk = new ArrayList<>();
			final LinkedHashMap<String, List<String>> dummyUq = new LinkedHashMap<>();
			final DbColumn col = parseColumnDefinition(colBody, tableName, dummyPk, dummyUq);
			if (col != null) {
				table.createColumn(col.getColumnName(), col);
				if (!dummyPk.isEmpty()) {
					table.setPrimaryKey(dummyPk);
				}
				for (final Entry<String, List<String>> uq : dummyUq.entrySet()) {
					table.addUniqueKey(uq.getKey(), uq.getValue());
				}
			}
			return;
		}

		// ---- DROP ----
		if (upper.startsWith("DROP")) {
			final String body = clause.substring(4).trim();
			final String bodyUpper = body.toUpperCase();

			if (bodyUpper.startsWith("COLUMN")) {
				final String colName = unquote(body.substring(6).trim().split("\\s+")[0]);
				table.dropColumn(colName);

			} else if (bodyUpper.startsWith("PRIMARY KEY")) {
				table.setPrimaryKey(null);

			} else if (bodyUpper.startsWith("CONSTRAINT")) {
				// DROP CONSTRAINT name — could be PK, UK, or FK
				final String name = unquote(body.substring(10).trim().split("\\s+")[0]);
				// Try unique key first
				if (table.getUniqueKeys().containsKey(name)) {
					table.getUniqueKeys().remove(name);
					return;
				}
				// Try foreign key
				if (table.getForeignKeys() != null) {
					table.getForeignKeys().removeIf(fk -> name.equals(fk.getForeignKeyName()));
				}

			} else if (bodyUpper.startsWith("FOREIGN KEY")) {
				// MySQL: DROP FOREIGN KEY name
				final String name = unquote(body.substring(11).trim().split("\\s+")[0]);
				if (table.getForeignKeys() != null) {
					table.getForeignKeys().removeIf(fk -> name.equals(fk.getForeignKeyName()));
				}

			} else if (bodyUpper.startsWith("INDEX") || bodyUpper.startsWith("KEY")) {
				// MySQL: DROP INDEX name (unique index = unique constraint)
				final int skip = bodyUpper.startsWith("INDEX") ? 5 : 3;
				final String name = unquote(body.substring(skip).trim().split("\\s+")[0]);
				table.getUniqueKeys().remove(name);
			}
			// DROP CHECK, DROP DEFAULT, … silently ignored
			return;
		}

		// ---- MODIFY COLUMN (MySQL / Oracle) ----
		if (upper.startsWith("MODIFY")) {
			final String body = clause.substring(6).trim();
			final String colBody = body.toUpperCase().startsWith("COLUMN") ? body.substring(6).trim() : body;
			applyModifyColumn(colBody, tableName, table);
			return;
		}

		// ---- ALTER COLUMN (PostgreSQL / SQL Server) ----
		if (upper.startsWith("ALTER")) {
			final String body = clause.substring(5).trim();
			final String colBody = body.toUpperCase().startsWith("COLUMN") ? body.substring(6).trim() : body;
			applyModifyColumn(colBody, tableName, table);
			return;
		}

		// ---- CHANGE [COLUMN] old new col_def (MySQL rename + retype) ----
		if (upper.startsWith("CHANGE")) {
			final String body = clause.substring(6).trim();
			final String colBody = body.toUpperCase().startsWith("COLUMN") ? body.substring(6).trim() : body;
			// First token = old name, rest = new column definition (new name + type +
			// options)
			final int sp = colBody.indexOf(' ');
			if (sp < 0) {
				return;
			}
			final String oldName = unquote(colBody.substring(0, sp).trim());
			final String newDef = colBody.substring(sp).trim();
			final List<String> dummyPk = new ArrayList<>();
			final LinkedHashMap<String, List<String>> dummyUq = new LinkedHashMap<>();
			final DbColumn newCol = parseColumnDefinition(newDef, tableName, dummyPk, dummyUq);
			if (newCol != null) {
				// Replace: drop old, add new (preserving insertion order is not guaranteed
				// here)
				if (table.getColumns().containsKey(oldName)) {
					table.dropColumn(oldName);
				}
				table.createColumn(newCol.getColumnName(), newCol);
				if (!dummyPk.isEmpty()) {
					table.setPrimaryKey(dummyPk);
				}
				for (final Entry<String, List<String>> uq : dummyUq.entrySet()) {
					table.addUniqueKey(uq.getKey(), uq.getValue());
				}
			}
			return;
		}

		// ---- RENAME COLUMN old TO new ----
		if (upper.startsWith("RENAME")) {
			final Pattern renamePat = Pattern.compile("(?i)RENAME\\s+COLUMN\\s+([\\w\"`.]+)\\s+TO\\s+([\\w\"`.]+)");
			final Matcher rm = renamePat.matcher(clause);
			if (rm.find()) {
				final String oldName = unquote(rm.group(1));
				final String newName = unquote(rm.group(2));
				final DbColumn col = table.getColumns().get(oldName);
				if (col != null) {
					table.dropColumn(oldName);
					col.setColumnName(newName);
					table.createColumn(newName, col);
				}
			}
		}
		// All other clauses (RENAME TABLE, ENGINE=, CHARSET=, …) silently ignored
	}

	/**
	 * Shared logic for {@code MODIFY COLUMN} and {@code ALTER COLUMN}: re-parses
	 * the column definition and replaces the existing {@link DbColumnType}. For
	 * PostgreSQL's {@code ALTER COLUMN col SET NOT NULL / DROP NOT NULL /
	 * SET DEFAULT … / DROP DEFAULT} sub-forms, only the affected attribute is
	 * changed.
	 */
	private static void applyModifyColumn(final String colBody, final String tableName, final DbTable table) {
		// PostgreSQL: ALTER COLUMN col SET NOT NULL / DROP NOT NULL
		final Pattern setNullPat = Pattern.compile("(?i)^([\\w\"`.]+)\\s+(SET\\s+NOT\\s+NULL|DROP\\s+NOT\\s+NULL)$");
		final Matcher snm = setNullPat.matcher(colBody.trim());
		if (snm.find()) {
			final String colName = unquote(snm.group(1));
			final boolean notNull = snm.group(2).toUpperCase().startsWith("SET");
			replaceNullability(colName, !notNull, table);
			return;
		}

		// PostgreSQL: ALTER COLUMN col SET DEFAULT expr / DROP DEFAULT
		final Pattern setDefPat = Pattern.compile("(?i)^([\\w\"`.]+)\\s+(?:SET\\s+DEFAULT\\s+(.+)|DROP\\s+DEFAULT)$",
				Pattern.DOTALL);
		final Matcher sdm = setDefPat.matcher(colBody.trim());
		if (sdm.find()) {
			final String colName = unquote(sdm.group(1));
			final String newDefault = sdm.group(2) != null ? sdm.group(2).trim() : null;
			replaceDefault(colName, newDefault, table);
			return;
		}

		// PostgreSQL: ALTER COLUMN col TYPE new_type [USING …]
		final Pattern typePat = Pattern.compile(
				"(?i)^([\\w\"`.]+)\\s+(?:TYPE|SET\\s+DATA\\s+TYPE)\\s+(.+?)(?:\\s+USING\\s+.+)?$", Pattern.DOTALL);
		final Matcher tpm = typePat.matcher(colBody.trim());
		if (tpm.find()) {
			final String colName = unquote(tpm.group(1));
			final String typeStr = tpm.group(2).trim();
			final DbColumn col = table.getColumns().get(colName);
			if (col != null) {
				final DbColumnType old = col.getColumnType();
				col.setColumnType(parseColumnType(typeStr, old != null && old.isNullable(),
						old != null && old.isAutoIncrement(), old != null ? old.getDefaultValue() : null));
			}
			return;
		}

		// MySQL / Oracle: MODIFY col type [options] — full re-parse
		final List<String> dummyPk = new ArrayList<>();
		final LinkedHashMap<String, List<String>> dummyUq = new LinkedHashMap<>();
		final DbColumn newCol = parseColumnDefinition(colBody, tableName, dummyPk, dummyUq);
		if (newCol != null) {
			final DbColumn existing = table.getColumns().get(newCol.getColumnName());
			if (existing != null) {
				existing.setColumnType(newCol.getColumnType());
				if (newCol.getColumnComment() != null) {
					existing.setColumnComment(newCol.getColumnComment());
				}
			}
		}
	}

	/** Replaces only the {@code nullable} flag of an existing column's type. */
	private static void replaceNullability(final String columnName, final boolean nullable, final DbTable table) {
		final DbColumn col = table.getColumns().get(columnName);
		if (col == null || col.getColumnType() == null) {
			return;
		}
		final DbColumnType old = col.getColumnType();
		col.setColumnType(new DbColumnType(old.getTypeName(), old.getCharacterByteSize(), old.getNumericPrecision(),
				old.getNumericScale(), nullable, old.isAutoIncrement(), old.getDefaultValue()));
	}

	/** Replaces only the {@code defaultValue} of an existing column's type. */
	private static void replaceDefault(final String columnName, final String newDefault, final DbTable table) {
		final DbColumn col = table.getColumns().get(columnName);
		if (col == null || col.getColumnType() == null) {
			return;
		}
		final DbColumnType old = col.getColumnType();
		col.setColumnType(new DbColumnType(old.getTypeName(), old.getCharacterByteSize(), old.getNumericPrecision(),
				old.getNumericScale(), old.isNullable(), old.isAutoIncrement(), newDefault));
	}

	// -------------------------------------------------------------------------
	// Column definition parsing
	// -------------------------------------------------------------------------

	/**
	 * Parses a single column definition such as:
	 *
	 * <pre>
	 *   customer_id BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY
	 *   status      VARCHAR(20)     NOT NULL DEFAULT 'active' COMMENT 'order status'
	 *   price       DECIMAL(10,2)   NOT NULL DEFAULT (0)
	 *   email       VARCHAR(255)    NOT NULL UNIQUE
	 * </pre>
	 */
	private static DbColumn parseColumnDefinition(final String entry, final String tableName,
			final List<String> pkAccumulator, final LinkedHashMap<String, List<String>> uniqueKeys) {
		// Group 1: column name
		// Group 2: type name + optional (len) or (precision,scale)
		// The type parameter must be followed by whitespace, comma, or end-of-string
		// so that DEFAULT(...) is never mistaken for a type parameter.
		// Group 3: everything after the type
		final Pattern colPat = Pattern.compile(
				"(?i)^([\\w\"`.]+)\\s+([\\w]+(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?)(.*)$", Pattern.DOTALL);
		final Matcher m = colPat.matcher(entry.trim());
		if (!m.find()) {
			return null;
		}

		final String columnName = unquote(m.group(1));
		final String typeStr = m.group(2).trim();
		final String restOrig = m.group(3); // original case – needed for string values
		final String rest = restOrig.toUpperCase();

		// --- nullable ---
		final boolean nullable = !rest.contains("NOT NULL");

		// --- autoIncrement ---
		final boolean autoIncrement = rest.contains("AUTO_INCREMENT") || rest.contains("AUTOINCREMENT")
				|| rest.contains("GENERATED ALWAYS AS IDENTITY") || rest.contains("GENERATED BY DEFAULT AS IDENTITY");

		// --- inline PRIMARY KEY ---
		if (rest.contains("PRIMARY KEY")) {
			pkAccumulator.add(columnName);
		}

		// --- inline UNIQUE ---
		// Only treat as inline UNIQUE when the keyword is NOT part of "UNIQUE INDEX" or
		// preceded by PRIMARY (which some dialects allow as "PRIMARY UNIQUE").
		if (rest.contains("UNIQUE") && !rest.contains("UNIQUE INDEX")) {
			final String uqName = "uq_" + tableName + "_" + columnName;
			uniqueKeys.put(uqName, Collections.singletonList(columnName));
		}

		// --- DEFAULT value ---
		// Parsed character-by-character to correctly handle:
		//   'string literals'  (including escaped '' inside)
		//   (expressions)      with arbitrary nesting depth, e.g. (NEXTVAL('seq'))
		//   bare keywords/numbers e.g. TRUE, 42, CURRENT_TIMESTAMP
		// Stops at the first whitespace-separated keyword that signals the end of the
		// DEFAULT clause: NOT, NULL, AUTO_INCREMENT, AUTOINCREMENT, COMMENT, UNIQUE,
		// PRIMARY, CHECK, REFERENCES, ON, CONSTRAINT, GENERATED.
		String defaultValue = null;
		defaultValue = parseDefaultValue(restOrig);

		// --- MySQL inline column comment: COMMENT 'text' ---
		String columnComment = null;
		final Pattern commentPat = Pattern.compile("(?i)\\bCOMMENT\\s+'((?:[^']|'')*)'");
		final Matcher cm = commentPat.matcher(restOrig);
		if (cm.find()) {
			columnComment = unescapeSqlString(cm.group(1));
		}

		// --- assemble type ---
		final DbColumnType columnType = parseColumnType(typeStr, nullable, autoIncrement, defaultValue);

		return new DbColumn().setColumnName(columnName).setColumnType(columnType).setColumnComment(columnComment);
	}

	// -------------------------------------------------------------------------
	// Type parsing
	// -------------------------------------------------------------------------

	private static DbColumnType parseColumnType(final String typeStr, final boolean nullable,
			final boolean autoIncrement, final String defaultValue) {
		final Pattern typePat = Pattern.compile("(?i)^([\\w]+)(?:\\s*\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\s*\\))?$");
		final Matcher m = typePat.matcher(typeStr.trim());

		if (!m.find()) {
			// Fallback: treat the whole string as the type name without parameters
			return new DbColumnType(typeStr.trim(), 0, 0, 0, nullable, autoIncrement, defaultValue);
		}

		final String typeName = m.group(1);
		final long charByteSize = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
		final int numericPrecision = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
		final int numericScale = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;

		return new DbColumnType(typeName, charByteSize, numericPrecision, numericScale, nullable, autoIncrement,
				defaultValue);
	}

	// -------------------------------------------------------------------------
	// Unique constraint parsing
	// -------------------------------------------------------------------------

	/**
	 * Parses table-level unique entries and adds them to {@code uniqueKeys}:
	 *
	 * <pre>
	 *   UNIQUE (col1, col2)
	 *   UNIQUE INDEX idx_name (col1)          – MySQL
	 *   CONSTRAINT uq_name UNIQUE (col1, col2)
	 * </pre>
	 *
	 * When no explicit constraint name is present one is generated as
	 * {@code uq_<tableName>_<col1>[_col2…]}.
	 */
	private static void parseUniqueConstraint(final String entry, final String tableName, final String explicitName,
			final LinkedHashMap<String, List<String>> uniqueKeys) {
		// Try to extract an explicit constraint name first
		// e.g. CONSTRAINT uq_name UNIQUE (…)
		String constraintName = explicitName;
		if (constraintName == null) {
			final Pattern namePat = Pattern.compile("(?i)CONSTRAINT\\s+([\\w\"`.]+)\\s+UNIQUE");
			final Matcher nm = namePat.matcher(entry);
			if (nm.find()) {
				constraintName = unquote(nm.group(1));
			}
		}

		// MySQL: UNIQUE INDEX idx_name (…) or UNIQUE KEY idx_name (…)
		if (constraintName == null) {
			final Pattern idxPat = Pattern.compile("(?i)UNIQUE\\s+(?:INDEX|KEY)\\s+([\\w\"`.]+)\\s*\\(");
			final Matcher im = idxPat.matcher(entry);
			if (im.find()) {
				constraintName = unquote(im.group(1));
			}
		}

		final List<String> cols = extractColumnList(entry);
		if (cols.isEmpty()) {
			return;
		}

		if (constraintName == null) {
			// Generate a name from table + column names
			constraintName = "uq_" + tableName + "_" + String.join("_", cols);
		}

		// Silently skip duplicates (can happen when IF NOT EXISTS scripts are re-run)
		if (!uniqueKeys.containsKey(constraintName)) {
			uniqueKeys.put(constraintName, cols);
		}
	}

	// -------------------------------------------------------------------------
	// Foreign key parsing
	// -------------------------------------------------------------------------

	/**
	 * Parses table-level foreign key entries:
	 *
	 * <pre>
	 *   [CONSTRAINT fk_name] FOREIGN KEY (col1, …) REFERENCES other_table (ref1, …)
	 * </pre>
	 */
	private static DbForeignKey parseForeignKey(final String entry) {
		final Pattern fkPat = Pattern.compile("(?i)(?:CONSTRAINT\\s+([\\w\"`.]+)\\s+)?FOREIGN\\s+KEY\\s*\\(([^)]+)\\)"
				+ "\\s+REFERENCES\\s+([\\w\"`.]+(?:\\.[\\w\"`.]+)?)\\s*\\(([^)]+)\\)");
		final Matcher m = fkPat.matcher(entry);
		if (!m.find()) {
			return null;
		}

		final DbForeignKey fk = new DbForeignKey();
		fk.setForeignKeyName(m.group(1) != null ? unquote(m.group(1)) : null);
		fk.setColumnNames(parseNameList(m.group(2)));
		fk.setReferencedTableName(unquote(m.group(3)));
		fk.setReferencedColumnNames(parseNameList(m.group(4)));
		return fk;
	}

	// -------------------------------------------------------------------------
	// COMMENT ON parsing (PostgreSQL / Oracle style)
	// -------------------------------------------------------------------------

	/**
	 * Handles:
	 *
	 * <pre>
	 *   COMMENT ON SCHEMA schema              IS 'text'  (PostgreSQL)
	 *   COMMENT ON TABLE  [schema.]table      IS 'text'
	 *   COMMENT ON COLUMN [schema.]table.col  IS 'text'
	 * </pre>
	 *
	 * A {@code NULL} literal (without quotes) clears any previously set comment.
	 */
	private static void parseCommentOn(final String stmt, final DbStructure structure) {

		// COMMENT ON SCHEMA schema IS 'text'
		final Pattern schemaCommentPat = Pattern
				.compile("(?i)COMMENT\\s+ON\\s+SCHEMA\\s+([\\w\"`.]+)" + "\\s+IS\\s+(?:'((?:[^']|'')*)'|(NULL))");
		final Matcher sm = schemaCommentPat.matcher(stmt);
		if (sm.find()) {
			final String schemaName = unquote(sm.group(1));
			final String commentText = sm.group(2) != null ? unescapeSqlString(sm.group(2)) : null;
			final DbSchema schema = structure.getSchemas().get(schemaName);
			if (schema != null) {
				schema.setSchemaComment(commentText);
			}
			return;
		}

		// COMMENT ON TABLE [schema.]table IS 'text'
		final Pattern tableCommentPat = Pattern.compile("(?i)COMMENT\\s+ON\\s+TABLE\\s+([\\w\"`.]+(?:\\.[\\w\"`.]+)?)"
				+ "\\s+IS\\s+(?:'((?:[^']|'')*)'|(NULL))");
		final Matcher tm = tableCommentPat.matcher(stmt);
		if (tm.find()) {
			final String fullName = tm.group(1);
			final String commentText = tm.group(2) != null ? unescapeSqlString(tm.group(2)) : null;
			applyTableComment(fullName, commentText, structure);
			return;
		}

		// COMMENT ON COLUMN [schema.]table.column IS 'text'
		final Pattern colCommentPat = Pattern
				.compile("(?i)COMMENT\\s+ON\\s+COLUMN\\s+([\\w\"`.]+(?:\\.[\\w\"`.]+){1,2})"
						+ "\\s+IS\\s+(?:'((?:[^']|'')*)'|(NULL))");
		final Matcher cm = colCommentPat.matcher(stmt);
		if (cm.find()) {
			final String fullName = cm.group(1);
			final String commentText = cm.group(2) != null ? unescapeSqlString(cm.group(2)) : null;
			applyColumnComment(fullName, commentText, structure);
		}
	}

	/**
	 * Resolves {@code [schema.]table} and sets the table comment. Unknown schema /
	 * table names are silently ignored (the COMMENT statement may appear before or
	 * refer to objects created outside this DDL stream).
	 */
	private static void applyTableComment(final String fullName, final String comment, final DbStructure structure) {
		final String[] parts = splitObjectName(fullName);
		final String schemaName = parts[0];
		final String tableName = parts[1];

		final DbSchema schema = structure.getSchemas().get(schemaName);
		if (schema == null) {
			return;
		}
		final DbTable table = schema.getTables().get(tableName);
		if (table == null) {
			return;
		}
		table.setTableComment(comment);
	}

	/**
	 * Resolves {@code [schema.]table.column} and sets the column comment. The
	 * rightmost token is always the column name; what precedes it is
	 * {@code [schema.]table}.
	 */
	private static void applyColumnComment(final String fullName, final String comment, final DbStructure structure) {
		// Split off the last segment as the column name
		final int lastDot = fullName.lastIndexOf('.');
		if (lastDot < 0) {
			return; // cannot determine table
		}
		final String columnName = unquote(fullName.substring(lastDot + 1));
		final String tableAndSchema = fullName.substring(0, lastDot);

		final String[] parts = splitObjectName(tableAndSchema);
		final String schemaName = parts[0];
		final String tableName = parts[1];

		final DbSchema schema = structure.getSchemas().get(schemaName);
		if (schema == null) {
			return;
		}
		final DbTable table = schema.getTables().get(tableName);
		if (table == null) {
			return;
		}
		final DbColumn column = table.getColumns().get(columnName);
		if (column == null) {
			return;
		}
		column.setColumnComment(comment);
	}

	/**
	 * Splits {@code [schema.]object} into a two-element array
	 * {@code {schemaName, objectName}}. When no schema qualifier is present the
	 * schema name defaults to {@code ""} (the same sentinel used by
	 * {@link #ensureSchema}).
	 */
	private static String[] splitObjectName(final String fullName) {
		final int dot = fullName.indexOf('.');
		if (dot >= 0) {
			return new String[] { unquote(fullName.substring(0, dot)), unquote(fullName.substring(dot + 1)) };
		}
		return new String[] { "", unquote(fullName) };
	}

	/**
	 * Replaces SQL escaped single-quotes ({@code ''}) with a plain apostrophe.
	 */
	private static String unescapeSqlString(final String s) {
		return s == null ? null : s.replace("''", "'");
	}

	// -------------------------------------------------------------------------
	// Helper: schema auto-creation
	// -------------------------------------------------------------------------

	private static void ensureSchema(final String schemaName, final DbStructure structure) throws DbStructureException {
		if (!structure.getSchemas().containsKey(schemaName)) {
			structure.createSchema(schemaName, new DbSchema().setSchemaName(schemaName));
		}
	}

	// -------------------------------------------------------------------------
	// Splitting helpers
	// -------------------------------------------------------------------------

	/**
	 * Splits a SQL text into individual statements on {@code ;}, respecting
	 * parenthesis depth (so function bodies with {@code ;} inside are not split).
	 */
	private static List<String> splitStatements(final String sql) {
		final List<String> stmts = new ArrayList<>();
		final StringBuilder buf = new StringBuilder();
		int depth = 0;
		boolean inSingleQuote = false;

		for (int i = 0; i < sql.length(); i++) {
			final char c = sql.charAt(i);

			if (c == '\'' && !inSingleQuote) {
				inSingleQuote = true;
				buf.append(c);
			} else if (c == '\'' && inSingleQuote) {
				inSingleQuote = false;
				buf.append(c);
			} else if (!inSingleQuote && c == '(') {
				depth++;
				buf.append(c);
			} else if (!inSingleQuote && c == ')') {
				depth--;
				buf.append(c);
			} else if (!inSingleQuote && c == ';' && depth == 0) {
				final String s = buf.toString().trim();
				if (!s.isEmpty()) {
					stmts.add(s);
				}
				buf.setLength(0);
			} else {
				buf.append(c);
			}
		}
		// Trailing statement without semicolon
		final String last = buf.toString().trim();
		if (!last.isEmpty()) {
			stmts.add(last);
		}
		return stmts;
	}

	/**
	 * Splits the body of a CREATE TABLE (...) block into individual entries
	 * (columns, primary-key, foreign-key), respecting nested parentheses.
	 */
	private static List<String> splitColumnEntries(final String body) {
		final List<String> entries = new ArrayList<>();
		final StringBuilder buf = new StringBuilder();
		int depth = 0;

		for (int i = 0; i < body.length(); i++) {
			final char c = body.charAt(i);
			if (c == '(') {
				depth++;
				buf.append(c);
			} else if (c == ')') {
				depth--;
				buf.append(c);
			} else if (c == ',' && depth == 0) {
				final String e = buf.toString().trim();
				if (!e.isEmpty()) {
					entries.add(e);
				}
				buf.setLength(0);
			} else {
				buf.append(c);
			}
		}
		final String last = buf.toString().trim();
		if (!last.isEmpty()) {
			entries.add(last);
		}
		return entries;
	}

	// -------------------------------------------------------------------------
	// Comment stripping
	// -------------------------------------------------------------------------

	/**
	 * Removes:
	 * <ul>
	 * <li>Single-line comments: {@code -- …}</li>
	 * <li>Block comments: {@code /* … *}{@code /}</li>
	 * </ul>
	 * String literals are left intact.
	 */
	private static String stripComments(final String sql) {
		final StringBuilder out = new StringBuilder(sql.length());
		int i = 0;
		final int len = sql.length();

		while (i < len) {
			final char c = sql.charAt(i);

			// String literal – copy verbatim
			if (c == '\'') {
				out.append(c);
				i++;
				while (i < len) {
					final char sc = sql.charAt(i);
					out.append(sc);
					i++;
					if (sc == '\'') {
						// Handle escaped quote ''
						if (i < len && sql.charAt(i) == '\'') {
							out.append('\'');
							i++;
						} else {
							break;
						}
					}
				}
				continue;
			}

			// Block comment /* … */
			if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
				i += 2;
				while (i + 1 < len) {
					if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
						i += 2;
						break;
					}
					// Preserve newlines so line numbers stay meaningful
					if (sql.charAt(i) == '\n') {
						out.append('\n');
					}
					i++;
				}
				continue;
			}

			// Single-line comment -- …
			if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
				while (i < len && sql.charAt(i) != '\n') {
					i++;
				}
				continue;
			}

			out.append(c);
			i++;
		}
		return out.toString();
	}

	// -------------------------------------------------------------------------
	// Small utilities
	// -------------------------------------------------------------------------

	/** Strips surrounding backticks, double-quotes, or square brackets. */
	private static String unquote(final String s) {
		if (s == null || s.length() < 2) {
			return s;
		}
		final char first = s.charAt(0);
		final char last = s.charAt(s.length() - 1);
		if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	/** Extracts column names from a parenthesised list: {@code (col1, col2)} */
	private static List<String> extractColumnList(final String text) {
		final Pattern p = Pattern.compile("\\(([^)]+)\\)");
		final Matcher m = p.matcher(text);
		if (m.find()) {
			return parseNameList(m.group(1));
		}
		return new ArrayList<>();
	}

	private static List<String> parseNameList(final String csv) {
		final List<String> names = new ArrayList<>();
		for (final String part : csv.split(",")) {
			final String name = unquote(part.trim());
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		return names;
	}

	/**
	 * Extracts the DEFAULT value from a column-definition tail string (everything
	 * after the type token) without using a regular expression, so that closing
	 * parentheses belonging to later tokens (e.g. {@code PRIMARY KEY (id)},
	 * {@code VARCHAR(n)} of the next column) never cause a
	 * {@link java.util.regex.PatternSyntaxException} or a wrong match.
	 *
	 * <p>Parsing rules:
	 * <ol>
	 *   <li>Scan forward until the keyword {@code DEFAULT} is found (word-boundary,
	 *       case-insensitive, outside string literals).</li>
	 *   <li>Skip whitespace after {@code DEFAULT}.</li>
	 *   <li>Collect the value token:
	 *     <ul>
	 *       <li>If the next character is {@code '}: read the full SQL string literal,
	 *           honouring {@code ''} as an escaped quote.</li>
	 *       <li>If the next character is {@code (}: read until the matching {@code )},
	 *           counting nesting depth and honouring string literals inside.</li>
	 *       <li>Otherwise: read non-whitespace, non-comma characters until a
	 *           stop-keyword ({@code NOT}, {@code NULL}, {@code AUTO_INCREMENT},
	 *           {@code AUTOINCREMENT}, {@code COMMENT}, {@code UNIQUE},
	 *           {@code PRIMARY}, {@code CHECK}, {@code REFERENCES}, {@code ON},
	 *           {@code CONSTRAINT}, {@code GENERATED}) or end-of-input is
	 *           reached.</li>
	 *     </ul>
	 *   </li>
	 *   <li>Strip surrounding quotes / parentheses from the collected value and
	 *       unescape {@code ''} inside string literals.</li>
	 * </ol>
	 *
	 * @param rest the part of the column definition that follows the type token
	 *             (original casing, may be {@code null})
	 * @return the plain DEFAULT value, or {@code null} if none was found
	 */
	private static String parseDefaultValue(final String rest) {
		if (rest == null || rest.isEmpty()) {
			return null;
		}

		final int len = rest.length();
		int i = 0;

		// ── 1. Find the DEFAULT keyword ──────────────────────────────────────────
		while (i < len) {
			// Skip string literals so we don't find "DEFAULT" inside a quoted value
			if (rest.charAt(i) == '\'') {
				i++;
				while (i < len) {
					if (rest.charAt(i) == '\'') {
						i++;
						if (i < len && rest.charAt(i) == '\'') {
							i++; // escaped ''
						} else {
							break;
						}
					} else {
						i++;
					}
				}
				continue;
			}

			// Check for word boundary before "DEFAULT"
			if (i + 7 <= len && rest.regionMatches(true, i, "DEFAULT", 0, 7)) {
				final boolean wordBefore = i == 0 || !Character.isLetterOrDigit(rest.charAt(i - 1));
				final boolean wordAfter  = i + 7 >= len || !Character.isLetterOrDigit(rest.charAt(i + 7));
				if (wordBefore && wordAfter) {
					i += 7; // skip "DEFAULT"
					break;
				}
			}
			i++;
		}

		if (i >= len) {
			return null; // no DEFAULT found
		}

		// ── 2. Skip whitespace after DEFAULT ─────────────────────────────────────
		while (i < len && Character.isWhitespace(rest.charAt(i))) {
			i++;
		}
		if (i >= len) {
			return null;
		}

		// ── 3. Collect the value token ────────────────────────────────────────────
		final char first = rest.charAt(i);
		final StringBuilder value = new StringBuilder();

		if (first == '\'') {
			// ── 3a. SQL string literal ────────────────────────────────────────────
			value.append(first);
			i++;
			while (i < len) {
				final char c = rest.charAt(i);
				value.append(c);
				i++;
				if (c == '\'') {
					if (i < len && rest.charAt(i) == '\'') {
						value.append('\'');
						i++; // escaped ''
					} else {
						break; // end of literal
					}
				}
			}
			// Strip surrounding quotes and unescape ''
			final String raw = value.toString();
			return unescapeSqlString(raw.substring(1, raw.length() - 1));

		} else if (first == '(') {
			// ── 3b. Parenthesised expression – count depth ────────────────────────
			int depth = 0;
			while (i < len) {
				final char c = rest.charAt(i);
				if (c == '\'') {
					// string literal inside expression
					value.append(c);
					i++;
					while (i < len) {
						final char sc = rest.charAt(i);
						value.append(sc);
						i++;
						if (sc == '\'') {
							if (i < len && rest.charAt(i) == '\'') {
								value.append('\'');
								i++;
							} else {
								break;
							}
						}
					}
					continue;
				}
				if (c == '(') {
					depth++;
				} else if (c == ')') {
					depth--;
					if (depth == 0) {
						value.append(c);
						i++;
						break;
					}
				}
				value.append(c);
				i++;
			}
			// Strip surrounding parentheses
			final String raw = value.toString();
			if (raw.startsWith("(") && raw.endsWith(")")) {
				return raw.substring(1, raw.length() - 1).trim();
			}
			return raw.trim();

		} else {
			// ── 3c. Bare keyword / number ─────────────────────────────────────────
			// Stop-keywords that signal the end of the DEFAULT clause
			final String[] stopKeywords = {
					"NOT", "NULL", "AUTO_INCREMENT", "AUTOINCREMENT",
					"COMMENT", "UNIQUE", "PRIMARY", "CHECK",
					"REFERENCES", "ON", "CONSTRAINT", "GENERATED"
			};

			while (i < len) {
				final char c = rest.charAt(i);
				if (Character.isWhitespace(c) || c == ',') {
					// Check whether what follows is a stop-keyword
					int j = i;
					while (j < len && Character.isWhitespace(rest.charAt(j))) {
						j++;
					}
					// Extract the next word
					int k = j;
					while (k < len && (Character.isLetterOrDigit(rest.charAt(k)) || rest.charAt(k) == '_')) {
						k++;
					}
					final String nextWord = rest.substring(j, k).toUpperCase();
					boolean isStop = false;
					for (final String kw : stopKeywords) {
						if (kw.equals(nextWord)) {
							isStop = true;
							break;
						}
					}
					if (isStop || c == ',') {
						break;
					}
					// Not a stop-keyword → include the whitespace and continue
					value.append(c);
				} else {
					value.append(c);
				}
				i++;
			}
			return value.toString().trim();
		}
	}

	private static String readAll(final InputStream in) throws IOException {
		final StringBuilder sb = new StringBuilder();
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}
}