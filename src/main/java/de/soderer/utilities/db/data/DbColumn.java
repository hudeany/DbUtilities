package de.soderer.utilities.db.data;

public class DbColumn {
	private String columnName;
	private DbColumnType columnType;
	private String columnComment;

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(final String columnName) {
		this.columnName = columnName.toLowerCase().trim();
	}

	public DbColumn withColumnName(final String newColumnName) {
		setColumnName(newColumnName);
		return this;
	}

	public DbColumnType getColumnType() {
		return columnType;
	}

	public void setColumnType(final DbColumnType columnType) {
		this.columnType = columnType;
	}

	public DbColumn withColumnType(final DbColumnType newColumnType) {
		setColumnType(newColumnType);
		return this;
	}

	public String getColumnComment() {
		return columnComment;
	}

	public void setColumnComment(final String columnComment) {
		this.columnComment = columnComment;
	}

	public DbColumn withColumnComment(final String newColumnComment) {
		setColumnComment(newColumnComment);
		return this;
	}
}
