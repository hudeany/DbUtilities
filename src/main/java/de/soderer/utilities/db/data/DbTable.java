package de.soderer.utilities.db.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.soderer.utilities.db.exception.DbStructureException;
import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

public class DbTable {
	private String tableName;
	private final Map<String, DbColumn> columns = new CaseInsensitiveLinkedMap<>();
	private List<String> primaryKey;
	private List<DbForeignKey> foreignKeys;
	private final Map<String, List<String>> uniqueKeys = new LinkedHashMap<>();
	private String tableComment;

	public String getTableName() {
		return tableName;
	}

	public DbTable setTableName(final String tableName) {
		this.tableName = tableName.toLowerCase().trim();
		return this;
	}

	public Map<String, DbColumn> getColumns() {
		return columns;
	}

	public List<String> getPrimaryKey() {
		return primaryKey;
	}

	public DbTable setPrimaryKey(final List<String> primaryKey) {
		this.primaryKey = primaryKey;
		return this;
	}

	public List<DbForeignKey> getForeignKeys() {
		return foreignKeys;
	}

	public DbTable addForeignKey(final DbForeignKey foreignKey) {
		foreignKeys.add(foreignKey);
		return this;
	}

	public Map<String, List<String>> getUniqueKeys() {
		return uniqueKeys;
	}

	public DbTable addUniqueKey(final String constraintName, final List<String> columnNames) throws DbStructureException {
		if (uniqueKeys.containsKey(constraintName)) {
			throw new DbStructureException("Cannot add unique key. Unique key already exists: '" + constraintName + "'");
		} else {
			uniqueKeys.put(constraintName, columnNames);
			return this;
		}
	}

	public String getTableComment() {
		return tableComment;
	}

	public DbTable setTableComment(final String tableComment) {
		this.tableComment = tableComment;
		return this;
	}

	public DbTable createColumn(final String columnName, final DbColumn columnData) throws DbStructureException {
		if (columns.containsKey(columnName)) {
			throw new DbStructureException("Cannot create column. Column already exists: '" + columnName + "'");
		} else {
			columns.put(columnName, columnData);
			return this;
		}
	}

	public DbColumn dropColumn(final String columnName) throws DbStructureException {
		if (!columns.containsKey(columnName)) {
			throw new DbStructureException("Cannot drop column. No such column: '" + columnName + "'");
		} else {
			return columns.remove(columnName);
		}
	}
}
