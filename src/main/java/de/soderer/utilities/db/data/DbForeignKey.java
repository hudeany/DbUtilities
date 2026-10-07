package de.soderer.utilities.db.data;

import java.util.List;

/**
 * Foreign key of a database table within a {@link DbStructure}.
 */
public class DbForeignKey {
	/**
	 * Creates a new foreign key without values.
	 */
	public DbForeignKey() {
		// Values are set by the setters
	}

	private String foreignKeyName;
	private List<String> columnNames;
	private String referencedTableName;
	private List<String> referencedColumnNames;

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
	public DbForeignKey withForeignKeyName(final String newForeignKeyName) {
		setForeignKeyName(newForeignKeyName);
		return this;
	}

	/**
	 * Returns the column names.
	 *
	 * @return the column names
	 */
	public List<String> getColumnNames() {
		return columnNames;
	}

	/**
	 * Sets the column names.
	 *
	 * @param columnNames the column names
	 */
	public void setColumnNames(final List<String> columnNames) {
		this.columnNames = columnNames;
	}

	/**
	 * Sets the column names and returns this instance for method chaining.
	 *
	 * @param newColumnNames the column names
	 * @return this instance
	 */
	public DbForeignKey withColumnNames(final List<String> newColumnNames) {
		setColumnNames(newColumnNames);
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
	public DbForeignKey withReferencedTableName(final String newReferencedTableName) {
		setReferencedTableName(newReferencedTableName);
		return this;
	}

	/**
	 * Returns the referenced column names.
	 *
	 * @return the referenced column names
	 */
	public List<String> getReferencedColumnNames() {
		return referencedColumnNames;
	}

	/**
	 * Sets the referenced column names.
	 *
	 * @param referencedColumnNames the referenced column names
	 */
	public void setReferencedColumnNames(final List<String> referencedColumnNames) {
		this.referencedColumnNames = referencedColumnNames;
	}

	/**
	 * Sets the referenced column names and returns this instance for method chaining.
	 *
	 * @param newReferencedColumnNames the referenced column names
	 * @return this instance
	 */
	public DbForeignKey withReferencedColumnNames(final List<String> newReferencedColumnNames) {
		setReferencedColumnNames(newReferencedColumnNames);
		return this;
	}
}
