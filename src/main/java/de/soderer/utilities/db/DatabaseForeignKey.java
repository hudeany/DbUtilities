package de.soderer.utilities.db;

public class DatabaseForeignKey {
	private String foreignKeyName;
	private String tableName;
	private String columnName;
	private String referencedTableName;
	private String referencedColumnName;

	public DatabaseForeignKey(final String foreignKeyName, final String tableName, final String columnName, final String referencedTableName, final String referencedColumnName) {
		this.foreignKeyName = foreignKeyName;
		this.tableName = tableName;
		this.columnName = columnName;
		this.referencedTableName = referencedTableName;
		this.referencedColumnName = referencedColumnName;
	}

	public String getForeignKeyName() {
		return foreignKeyName;
	}

	public void setForeignKeyName(final String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
	}

	public DatabaseForeignKey withForeignKeyName(final String newForeignKeyName) {
		setForeignKeyName(newForeignKeyName);
		return this;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(final String tableName) {
		this.tableName = tableName;
	}

	public DatabaseForeignKey withTableName(final String newTableName) {
		setTableName(newTableName);
		return this;
	}

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(final String columnName) {
		this.columnName = columnName;
	}

	public DatabaseForeignKey withColumnName(final String newColumnName) {
		setColumnName(newColumnName);
		return this;
	}

	public String getReferencedTableName() {
		return referencedTableName;
	}

	public void setReferencedTableName(final String referencedTableName) {
		this.referencedTableName = referencedTableName;
	}

	public DatabaseForeignKey withReferencedTableName(final String newReferencedTableName) {
		setReferencedTableName(newReferencedTableName);
		return this;
	}

	public String getReferencedColumnName() {
		return referencedColumnName;
	}

	public void setReferencedColumnName(final String referencedColumnName) {
		this.referencedColumnName = referencedColumnName;
	}

	public DatabaseForeignKey withReferencedColumnName(final String newReferencedColumnName) {
		setReferencedColumnName(newReferencedColumnName);
		return this;
	}
	
	@Override
	public String toString() {
		return foreignKeyName + ": " + tableName + "(" + columnName + ") references " + referencedTableName + "(" + referencedColumnName + ")";
	}
}
