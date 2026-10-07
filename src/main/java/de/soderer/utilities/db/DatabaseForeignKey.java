package de.soderer.utilities.db;

/**
 * Single column reference of a foreign key as read by {@link DbUtilities#getForeignKeys(java.sql.Connection, String)}.
 * Foreign keys with several columns are represented by one instance per column.
 */
public class DatabaseForeignKey {
	private String foreignKeyName;
	private String tableName;
	private String columnName;
	private String referencedTableName;
	private String referencedColumnName;

	/**
	 * Creates a new foreign key column reference.
	 *
	 * @param foreignKeyName name of the foreign key
	 * @param tableName name of the referencing table
	 * @param columnName name of the referencing column
	 * @param referencedTableName name of the referenced table
	 * @param referencedColumnName name of the referenced column
	 */
	public DatabaseForeignKey(final String foreignKeyName, final String tableName, final String columnName, final String referencedTableName, final String referencedColumnName) {
		this.foreignKeyName = foreignKeyName;
		this.tableName = tableName;
		this.columnName = columnName;
		this.referencedTableName = referencedTableName;
		this.referencedColumnName = referencedColumnName;
	}

	/**
	 * Returns the foreign key name.
	 *
	 * @return the foreign key name
	 */
	public String getForeignKeyName() {
		return foreignKeyName;
	}

	/**
	 * Sets the foreign key name.
	 *
	 * @param foreignKeyName the foreign key name
	 */
	public void setForeignKeyName(final String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
	}

	/**
	 * Sets the foreign key name and returns this instance for method chaining.
	 *
	 * @param newForeignKeyName the foreign key name
	 * @return this instance
	 */
	public DatabaseForeignKey withForeignKeyName(final String newForeignKeyName) {
		setForeignKeyName(newForeignKeyName);
		return this;
	}

	/**
	 * Returns the table name.
	 *
	 * @return the table name
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * Sets the table name.
	 *
	 * @param tableName the table name
	 */
	public void setTableName(final String tableName) {
		this.tableName = tableName;
	}

	/**
	 * Sets the table name and returns this instance for method chaining.
	 *
	 * @param newTableName the table name
	 * @return this instance
	 */
	public DatabaseForeignKey withTableName(final String newTableName) {
		setTableName(newTableName);
		return this;
	}

	/**
	 * Returns the column name.
	 *
	 * @return the column name
	 */
	public String getColumnName() {
		return columnName;
	}

	/**
	 * Sets the column name.
	 *
	 * @param columnName the column name
	 */
	public void setColumnName(final String columnName) {
		this.columnName = columnName;
	}

	/**
	 * Sets the column name and returns this instance for method chaining.
	 *
	 * @param newColumnName the column name
	 * @return this instance
	 */
	public DatabaseForeignKey withColumnName(final String newColumnName) {
		setColumnName(newColumnName);
		return this;
	}

	/**
	 * Returns the referenced table name.
	 *
	 * @return the referenced table name
	 */
	public String getReferencedTableName() {
		return referencedTableName;
	}

	/**
	 * Sets the referenced table name.
	 *
	 * @param referencedTableName the referenced table name
	 */
	public void setReferencedTableName(final String referencedTableName) {
		this.referencedTableName = referencedTableName;
	}

	/**
	 * Sets the referenced table name and returns this instance for method chaining.
	 *
	 * @param newReferencedTableName the referenced table name
	 * @return this instance
	 */
	public DatabaseForeignKey withReferencedTableName(final String newReferencedTableName) {
		setReferencedTableName(newReferencedTableName);
		return this;
	}

	/**
	 * Returns the referenced column name.
	 *
	 * @return the referenced column name
	 */
	public String getReferencedColumnName() {
		return referencedColumnName;
	}

	/**
	 * Sets the referenced column name.
	 *
	 * @param referencedColumnName the referenced column name
	 */
	public void setReferencedColumnName(final String referencedColumnName) {
		this.referencedColumnName = referencedColumnName;
	}

	/**
	 * Sets the referenced column name and returns this instance for method chaining.
	 *
	 * @param newReferencedColumnName the referenced column name
	 * @return this instance
	 */
	public DatabaseForeignKey withReferencedColumnName(final String newReferencedColumnName) {
		setReferencedColumnName(newReferencedColumnName);
		return this;
	}
	
	@Override
	public String toString() {
		return foreignKeyName + ": " + tableName + "(" + columnName + ") references " + referencedTableName + "(" + referencedColumnName + ")";
	}
}
