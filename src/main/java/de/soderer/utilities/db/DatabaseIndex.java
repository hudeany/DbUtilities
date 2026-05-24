package de.soderer.utilities.db;

import java.util.List;

import de.soderer.utilities.db.utilities.Utilities;

public class DatabaseIndex {
	private String tableName;
	private String indexName;
	private List<String> indexedColumns;

	public DatabaseIndex(final String tableName, final String indexName, final List<String> indexedColumns) {
		this.tableName = tableName;
		this.indexName = indexName;
		this.indexedColumns = indexedColumns;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(final String tableName) {
		this.tableName = tableName;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(final String indexName) {
		this.indexName = indexName;
	}

	public List<String> getIndexedColumns() {
		return indexedColumns;
	}

	public void setIndexedColumns(final List<String> indexedColumns) {
		this.indexedColumns = indexedColumns;
	}

	@Override
	public String toString() {
		return tableName + " " + indexName + " (" + Utilities.join(indexedColumns, ", ") + ")";
	}
}
