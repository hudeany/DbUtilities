package de.soderer.utilities.db;

/**
 * Constraint of a database table as read by {@link DbUtilities#getConstraints(java.sql.Connection, String)}.
 */
public class DatabaseConstraint {
	/**
	 * Type of a database constraint.
	 */
	public enum ConstraintType {
		/**
		 * Primary key constraint (Oracle type "P")
		 */
		PrimaryKey,
		/**
		 * Unique constraint (Oracle type "U")
		 */
		Unique,
		/**
		 * Foreign key constraint
		 */
		ForeignKey,
		/**
		 * Check constraint (Oracle type "C", also used for NOT NULL constraints)
		 */
		Check,
		/**
		 * Referential constraint (Oracle type "R" for foreign keys)
		 */
		Relation;

		/**
		 * Returns the constraint type by its name or its single letter abbreviation (case-insensitive).
		 * Blanks, underscores and hyphens are ignored, so "PRIMARY KEY" and "primary_key" are both accepted.
		 *
		 * @param constraintTypeName name of the constraint type, e.g. "FOREIGN KEY" or "P"
		 * @return the constraint type
		 * @throws Exception if there is no matching constraint type
		 */
		public static ConstraintType fromName(String constraintTypeName) throws Exception {
			for (ConstraintType constraintType : ConstraintType.values()) {
				if (constraintType.name().replace("_", "").replace(" ", "").replace("-", "").equalsIgnoreCase(constraintTypeName.replace("_", "").replace(" ", "").replace("-", ""))) {
					return constraintType;
				} else if (constraintTypeName.length() == 1 && constraintType.name().replace("_", "").replace(" ", "").replace("-", "").charAt(0) == constraintTypeName.toUpperCase().charAt(0)) {
					return constraintType;
				}
			}
			throw new Exception("Unknown contraint type: '" + constraintTypeName + "'");
		}
	}

	private String tableName;
	private String constraintName;
	private ConstraintType constraintType;
	private String columnName;
	private String condition;

	/**
	 * Creates a new constraint.
	 *
	 * @param tableName name of the table
	 * @param constraintName name of the constraint
	 * @param constraintType type of the constraint
	 * @param columnName name of the constrained column, or null if not available
	 * @param condition condition of a check constraint, or null
	 */
	public DatabaseConstraint(final String tableName, final String constraintName, final ConstraintType constraintType, String columnName, String condition) {
		this.tableName = tableName;
		this.constraintName = constraintName;
		this.constraintType = constraintType;
		this.columnName = columnName;
		this.condition = condition;
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
	public DatabaseConstraint withTableName(final String newTableName) {
		setTableName(newTableName);
		return this;
	}

	/**
	 * Returns the constraint name.
	 *
	 * @return the constraint name
	 */
	public String getConstraintName() {
		return constraintName;
	}

	/**
	 * Sets the constraint name.
	 *
	 * @param constraintName the constraint name
	 */
	public void setConstraintName(final String constraintName) {
		this.constraintName = constraintName;
	}

	/**
	 * Sets the constraint name and returns this instance for method chaining.
	 *
	 * @param newConstraintName the constraint name
	 * @return this instance
	 */
	public DatabaseConstraint withConstraintName(final String newConstraintName) {
		setConstraintName(newConstraintName);
		return this;
	}

	/**
	 * Returns the constraint type.
	 *
	 * @return the constraint type
	 */
	public ConstraintType getConstraintType() {
		return constraintType;
	}

	/**
	 * Sets the constraint type.
	 *
	 * @param constraintType the constraint type
	 */
	public void setConstraintType(final ConstraintType constraintType) {
		this.constraintType = constraintType;
	}

	/**
	 * Sets the constraint type and returns this instance for method chaining.
	 *
	 * @param newConstraintType the constraint type
	 * @return this instance
	 */
	public DatabaseConstraint withConstraintType(final ConstraintType newConstraintType) {
		setConstraintType(newConstraintType);
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
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	/**
	 * Sets the column name and returns this instance for method chaining.
	 *
	 * @param newColumnName the column name
	 * @return this instance
	 */
	public DatabaseConstraint withColumnName(final String newColumnName) {
		setColumnName(newColumnName);
		return this;
	}

	/**
	 * Returns the condition.
	 *
	 * @return the condition
	 */
	public String getCondition() {
		return condition;
	}

	/**
	 * Sets the condition.
	 *
	 * @param condition the condition
	 */
	public void setCondition(String condition) {
		this.condition = condition;
	}

	/**
	 * Sets the condition and returns this instance for method chaining.
	 *
	 * @param newCondition the condition
	 * @return this instance
	 */
	public DatabaseConstraint withCondition(final String newCondition) {
		setCondition(newCondition);
		return this;
	}
	
	@Override
	public String toString() {
		return tableName + " " + constraintName + " " + constraintType + (columnName == null ? "" : " " + columnName) + (condition == null ? "" : " " + condition);
	}
}
