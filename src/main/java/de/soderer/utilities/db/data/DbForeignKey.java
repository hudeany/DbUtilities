package de.soderer.utilities.db.data;

import java.util.List;

public class DbForeignKey {
	private String foreignKeyName;
	private List<String> columnNames;
	private String referencedTableName;
	private List<String> referencedColumnNames;

	public String getForeignKeyName() {
		return foreignKeyName;
	}

	public void setForeignKeyName(final String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
	}

	public DbForeignKey withForeignKeyName(final String newForeignKeyName) {
		setForeignKeyName(newForeignKeyName);
		return this;
	}

	public List<String> getColumnNames() {
		return columnNames;
	}

	public void setColumnNames(final List<String> columnNames) {
		this.columnNames = columnNames;
	}

	public DbForeignKey withColumnNames(final List<String> newColumnNames) {
		setColumnNames(newColumnNames);
		return this;
	}

	public String getReferencedTableName() {
		return referencedTableName;
	}

	public void setReferencedTableName(final String referencedTableName) {
		this.referencedTableName = referencedTableName;
	}

	public DbForeignKey withReferencedTableName(final String newReferencedTableName) {
		setReferencedTableName(newReferencedTableName);
		return this;
	}

	public List<String> getReferencedColumnNames() {
		return referencedColumnNames;
	}

	public void setReferencedColumnNames(final List<String> referencedColumnNames) {
		this.referencedColumnNames = referencedColumnNames;
	}

	public DbForeignKey withReferencedColumnNames(final List<String> newReferencedColumnNames) {
		setReferencedColumnNames(newReferencedColumnNames);
		return this;
	}
}
