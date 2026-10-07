package de.soderer.utilities.db.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.soderer.utilities.db.exception.DbStructureException;
import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

/**
 * Database table with its columns, primary key, unique keys and foreign keys within a {@link DbStructure}.
 */
public class DbTable {
	/**
	 * Creates a new table without name and columns.
	 */
	public DbTable() {
		// Values are set by the setters
	}

	private String tableName;
	private final Map<String, DbColumn> columns = new CaseInsensitiveLinkedMap<>();
	private List<String> primaryKey;
	private final List<DbForeignKey> foreignKeys = new ArrayList<>();
	private final Map<String, List<String>> uniqueKeys = new LinkedHashMap<>();
	private String tableComment;

	/**
	 * Returns the table name.
	 *
	 * @return the table name
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * Sets the table name. The name is stored lowercased and trimmed.
	 *
	 * @param tableName the table name
	 */
	public void setTableName(final String tableName) {
		this.tableName = tableName.toLowerCase().trim();
	}

	/**
	 * Sets the table name and returns this instance for method chaining.
	 *
	 * @param newTableName the table name
	 * @return this instance
	 */
	public DbTable withTableName(final String newTableName) {
		setTableName(newTableName);
		return this;
	}

	/**
	 * Returns the columns by their case-insensitive names in definition order.
	 *
	 * @return the columns (modifiable)
	 */
	public Map<String, DbColumn> getColumns() {
		return columns;
	}

	/**
	 * Returns the column names of the primary key.
	 *
	 * @return the primary key column names, or null if the table has no primary key
	 */
	public List<String> getPrimaryKey() {
		return primaryKey;
	}

	/**
	 * Sets the primary key.
	 *
	 * @param primaryKey the primary key
	 */
	public void setPrimaryKey(final List<String> primaryKey) {
		this.primaryKey = primaryKey;
	}

	/**
	 * Sets the primary key and returns this instance for method chaining.
	 *
	 * @param newPrimaryKey the primary key
	 * @return this instance
	 */
	public DbTable withPrimaryKey(final List<String> newPrimaryKey) {
		setPrimaryKey(newPrimaryKey);
		return this;
	}

	/**
	 * Returns the foreign keys.
	 *
	 * @return the foreign keys (modifiable)
	 */
	public List<DbForeignKey> getForeignKeys() {
		return foreignKeys;
	}

	/**
	 * Adds a foreign key.
	 *
	 * @param foreignKey foreign key definition
	 * @return this table for method chaining
	 */
	public DbTable addForeignKey(final DbForeignKey foreignKey) {
		foreignKeys.add(foreignKey);
		return this;
	}

	/**
	 * Returns the unique keys by their constraint names.
	 *
	 * @return the column names of the unique keys by constraint name (modifiable)
	 */
	public Map<String, List<String>> getUniqueKeys() {
		return uniqueKeys;
	}

	/**
	 * Adds a unique key.
	 *
	 * @param constraintName name of the unique constraint
	 * @param columnNames names of the columns of the unique key
	 * @return this table for method chaining
	 * @throws DbStructureException if a unique key with this name already exists
	 */
	public DbTable addUniqueKey(final String constraintName, final List<String> columnNames) throws DbStructureException {
		if (uniqueKeys.containsKey(constraintName)) {
			throw new DbStructureException("Cannot add unique key. Unique key already exists: '" + constraintName + "'");
		} else {
			uniqueKeys.put(constraintName, columnNames);
			return this;
		}
	}

	/**
	 * Returns the table comment.
	 *
	 * @return the table comment
	 */
	public String getTableComment() {
		return tableComment;
	}

	/**
	 * Sets the table comment.
	 *
	 * @param tableComment the table comment
	 */
	public void setTableComment(final String tableComment) {
		this.tableComment = tableComment;
	}

	/**
	 * Sets the table comment and returns this instance for method chaining.
	 *
	 * @param newTableComment the table comment
	 * @return this instance
	 */
	public DbTable withTableComment(final String newTableComment) {
		setTableComment(newTableComment);
		return this;
	}

	/**
	 * Adds a new column.
	 *
	 * @param columnName name of the column (case-insensitive)
	 * @param columnData column definition
	 * @return this table for method chaining
	 * @throws DbStructureException if a column with this name already exists
	 */
	public DbTable createColumn(final String columnName, final DbColumn columnData) throws DbStructureException {
		if (columns.containsKey(columnName)) {
			throw new DbStructureException("Cannot create column. Column already exists: '" + columnName + "'");
		} else {
			columns.put(columnName, columnData);
			return this;
		}
	}

	/**
	 * Removes a column.
	 *
	 * @param columnName name of the column (case-insensitive)
	 * @return the removed column definition
	 * @throws DbStructureException if there is no column with this name
	 */
	public DbColumn dropColumn(final String columnName) throws DbStructureException {
		if (!columns.containsKey(columnName)) {
			throw new DbStructureException("Cannot drop column. No such column: '" + columnName + "'");
		} else {
			return columns.remove(columnName);
		}
	}
}
