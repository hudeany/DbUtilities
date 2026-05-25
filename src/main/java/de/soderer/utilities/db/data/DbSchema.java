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

	public DbSchema setSchemaName(final String schemaName) {
		this.schemaName = schemaName.toLowerCase().trim();
		return this;
	}

	public LinkedHashMap<String, DbTable> getTables() {
		return tables;
	}

	public String getSchemaComment() {
		return schemaComment;
	}

	public DbSchema setSchemaComment(final String schemaComment) {
		this.schemaComment = schemaComment;
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
