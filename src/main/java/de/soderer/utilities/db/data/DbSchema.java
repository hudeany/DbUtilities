package de.soderer.utilities.db.data;

import java.util.LinkedHashMap;

import de.soderer.utilities.db.exception.DbStructureException;
import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

/**
 * Database schema with its tables within a {@link DbStructure}.
 */
public class DbSchema {
	/**
	 * Creates a new schema without name and tables.
	 */
	public DbSchema() {
		// Values are set by the setters
	}

	private String schemaName;
	private final LinkedHashMap<String, DbTable> tables = new CaseInsensitiveLinkedMap<>();
	private String schemaComment;

	/**
	 * Returns the schema name.
	 *
	 * @return the schema name
	 */
	public String getSchemaName() {
		return schemaName;
	}

	/**
	 * Sets the schema name.
	 *
	 * @param schemaName the schema name
	 */
	public void setSchemaName(final String schemaName) {
		this.schemaName = schemaName.toLowerCase().trim();
	}

	/**
	 * Sets the schema name and returns this instance for method chaining.
	 *
	 * @param newSchemaName the schema name
	 * @return this instance
	 */
	public DbSchema withSchemaName(final String newSchemaName) {
		setSchemaName(newSchemaName);
		return this;
	}

	/**
	 * Returns the tables of this schema by their case-insensitive names.
	 *
	 * @return the tables (modifiable)
	 */
	public LinkedHashMap<String, DbTable> getTables() {
		return tables;
	}

	/**
	 * Returns the schema comment.
	 *
	 * @return the schema comment
	 */
	public String getSchemaComment() {
		return schemaComment;
	}

	/**
	 * Sets the schema comment.
	 *
	 * @param schemaComment the schema comment
	 */
	public void setSchemaComment(final String schemaComment) {
		this.schemaComment = schemaComment;
	}

	/**
	 * Sets the schema comment and returns this instance for method chaining.
	 *
	 * @param newSchemaComment the schema comment
	 * @return this instance
	 */
	public DbSchema withSchemaComment(final String newSchemaComment) {
		setSchemaComment(newSchemaComment);
		return this;
	}

	/**
	 * Adds a new table to this schema.
	 *
	 * @param tableName name of the table (case-insensitive)
	 * @param tableData table definition
	 * @return this schema for method chaining
	 * @throws DbStructureException if a table with this name already exists
	 */
	public DbSchema createTable(final String tableName, final DbTable tableData) throws DbStructureException {
		if (tables.containsKey(tableName)) {
			throw new DbStructureException("Cannot create table. Table already exists: '" + tableName + "'");
		} else {
			tables.put(tableName, tableData);
			return this;
		}
	}

	/**
	 * Removes a table from this schema.
	 *
	 * @param tableName name of the table (case-insensitive)
	 * @return the removed table definition
	 * @throws DbStructureException if there is no table with this name
	 */
	public DbTable dropTable(final String tableName) throws DbStructureException {
		if (!tables.containsKey(tableName)) {
			throw new DbStructureException("Cannot drop table. No such table: '" + tableName + "'");
		} else {
			return tables.remove(tableName);
		}
	}
}
