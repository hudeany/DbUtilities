package de.soderer.utilities.db.data;

/**
 * Column of a database table within a {@link DbStructure}, e.g. created by {@link de.soderer.utilities.db.SqlDdlParser}.
 */
public class DbColumn {
	/**
	 * Creates a new column without name and type.
	 */
	public DbColumn() {
		// Values are set by the setters
	}

	private String columnName;
	private DbColumnType columnType;
	private String columnComment;

	/**
	 * Returns the column name.
	 *
	 * @return the column name
	 */
	public String getColumnName() {
		return columnName;
	}

	/**
	 * Sets the column name. The name is stored lowercased and trimmed.
	 *
	 * @param columnName the column name
	 */
	public void setColumnName(final String columnName) {
		this.columnName = columnName.toLowerCase().trim();
	}

	/**
	 * Sets the column name and returns this instance for method chaining.
	 *
	 * @param newColumnName the column name
	 * @return this instance
	 */
	public DbColumn withColumnName(final String newColumnName) {
		setColumnName(newColumnName);
		return this;
	}

	/**
	 * Returns the column type.
	 *
	 * @return the column type
	 */
	public DbColumnType getColumnType() {
		return columnType;
	}

	/**
	 * Sets the column type.
	 *
	 * @param columnType the column type
	 */
	public void setColumnType(final DbColumnType columnType) {
		this.columnType = columnType;
	}

	/**
	 * Sets the column type and returns this instance for method chaining.
	 *
	 * @param newColumnType the column type
	 * @return this instance
	 */
	public DbColumn withColumnType(final DbColumnType newColumnType) {
		setColumnType(newColumnType);
		return this;
	}

	/**
	 * Returns the column comment.
	 *
	 * @return the column comment
	 */
	public String getColumnComment() {
		return columnComment;
	}

	/**
	 * Sets the column comment.
	 *
	 * @param columnComment the column comment
	 */
	public void setColumnComment(final String columnComment) {
		this.columnComment = columnComment;
	}

	/**
	 * Sets the column comment and returns this instance for method chaining.
	 *
	 * @param newColumnComment the column comment
	 * @return this instance
	 */
	public DbColumn withColumnComment(final String newColumnComment) {
		setColumnComment(newColumnComment);
		return this;
	}
}
