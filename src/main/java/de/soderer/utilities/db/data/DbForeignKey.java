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

	public DbForeignKey setForeignKeyName(final String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
		return this;
	}

	public List<String> getColumnNames() {
		return columnNames;
	}

	public DbForeignKey setColumnNames(final List<String> columnNames) {
		this.columnNames = columnNames;
		return this;
	}

	public String getReferencedTableName() {
		return referencedTableName;
	}

	public DbForeignKey setReferencedTableName(final String referencedTableName) {
		this.referencedTableName = referencedTableName;
		return this;
	}

	public List<String> getReferencedColumnNames() {
		return referencedColumnNames;
	}

	public DbForeignKey setReferencedColumnNames(final List<String> referencedColumnNames) {
		this.referencedColumnNames = referencedColumnNames;
		return this;
	}
}
