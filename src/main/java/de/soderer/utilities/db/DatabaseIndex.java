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

	public DatabaseIndex setTableName(final String tableName) {
		this.tableName = tableName;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public DatabaseIndex setIndexName(final String indexName) {
		this.indexName = indexName;
		return this;
	}

	public List<String> getIndexedColumns() {
		return indexedColumns;
	}

	public DatabaseIndex setIndexedColumns(final List<String> indexedColumns) {
		this.indexedColumns = indexedColumns;
		return this;
	}

	@Override
	public String toString() {
		return tableName + " " + indexName + " (" + Utilities.join(indexedColumns, ", ") + ")";
	}
}
