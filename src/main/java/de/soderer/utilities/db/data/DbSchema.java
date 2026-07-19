package de.soderer.utilities.db.data;

import java.util.LinkedHashMap;

import de.soderer.utilities.db.exception.DbStructureException;
import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

public class DbSchema {
	private String schemaName;
	private final LinkedHashMap<String, DbTable> tables = new CaseInsensitiveLinkedMap<>();
	private String schemaComment;

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(final String schemaName) {
		this.schemaName = schemaName.toLowerCase().trim();
	}

	public DbSchema withSchemaName(final String newSchemaName) {
		setSchemaName(newSchemaName);
		return this;
	}

	public LinkedHashMap<String, DbTable> getTables() {
		return tables;
	}

	public String getSchemaComment() {
		return schemaComment;
	}

	public void setSchemaComment(final String schemaComment) {
		this.schemaComment = schemaComment;
	}

	public DbSchema withSchemaComment(final String newSchemaComment) {
		setSchemaComment(newSchemaComment);
		return this;
	}

	public DbSchema createTable(final String tableName, final DbTable tableData) throws DbStructureException {
		if (tables.containsKey(tableName)) {
			throw new DbStructureException("Cannot create table. Table already exists: '" + tableName + "'");
		} else {
			tables.put(tableName, tableData);
			return this;
		}
	}

	public DbTable dropTable(final String tableName) throws DbStructureException {
		if (!tables.containsKey(tableName)) {
			throw new DbStructureException("Cannot drop table. No such table: '" + tableName + "'");
		} else {
			return tables.remove(tableName);
		}
	}
}
