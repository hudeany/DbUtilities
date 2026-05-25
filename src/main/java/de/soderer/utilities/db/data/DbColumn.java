package de.soderer.utilities.db.data;

public class DbColumn {
	private String columnName;
	private DbColumnType columnType;
	private String columnComment;

	public String getColumnName() {
		return columnName;
	}

	public DbColumn setColumnName(final String columnName) {
		this.columnName = columnName.toLowerCase().trim();
		return this;
	}

	public DbColumnType getColumnType() {
		return columnType;
	}

	public DbColumn setColumnType(final DbColumnType columnType) {
		this.columnType = columnType;
		return this;
	}

	public String getColumnComment() {
		return columnComment;
	}

	public DbColumn setColumnComment(final String columnComment) {
		this.columnComment = columnComment;
		return this;
	}
}
