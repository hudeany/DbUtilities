package de.soderer.utilities.db;

import java.util.List;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Index of a database table as read by {@link DbUtilities#getIndices(java.sql.Connection, String)}.
 */
public class DatabaseIndex {
	private String tableName;
	private String indexName;
	private List<String> indexedColumns;

	/**
	 * Creates a new index.
	 *
	 * @param tableName name of the table
	 * @param indexName name of the index
	 * @param indexedColumns names of the indexed columns
	 */
	public DatabaseIndex(final String tableName, final String indexName, final List<String> indexedColumns) {
		this.tableName = tableName;
		this.indexName = indexName;
		this.indexedColumns = indexedColumns;
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
	public DatabaseIndex withTableName(final String newTableName) {
		setTableName(newTableName);
		return this;
	}

	/**
	 * Returns the index name.
	 *
	 * @return the index name
	 */
	public String getIndexName() {
		return indexName;
	}

	/**
	 * Sets the index name.
	 *
	 * @param indexName the index name
	 */
	public void setIndexName(final String indexName) {
		this.indexName = indexName;
	}

	/**
	 * Sets the index name and returns this instance for method chaining.
	 *
	 * @param newIndexName the index name
	 * @return this instance
	 */
	public DatabaseIndex withIndexName(final String newIndexName) {
		setIndexName(newIndexName);
		return this;
	}

	/**
	 * Returns the indexed columns.
	 *
	 * @return the indexed columns
	 */
	public List<String> getIndexedColumns() {
		return indexedColumns;
	}

	/**
	 * Sets the indexed columns.
	 *
	 * @param indexedColumns the indexed columns
	 */
	public void setIndexedColumns(final List<String> indexedColumns) {
		this.indexedColumns = indexedColumns;
	}

	/**
	 * Sets the indexed columns and returns this instance for method chaining.
	 *
	 * @param newIndexedColumns the indexed columns
	 * @return this instance
	 */
	public DatabaseIndex withIndexedColumns(final List<String> newIndexedColumns) {
		setIndexedColumns(newIndexedColumns);
		return this;
	}

	@Override
	public String toString() {
		return tableName + " " + indexName + " (" + Utilities.join(indexedColumns, ", ") + ")";
	}
}
