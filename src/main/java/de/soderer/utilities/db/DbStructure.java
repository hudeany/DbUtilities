package de.soderer.utilities.db;

import java.util.LinkedHashMap;

import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

public class DbStructure {
	private final LinkedHashMap<String, DbSchema> schemas = new CaseInsensitiveLinkedMap<>();

	public LinkedHashMap<String, DbSchema> getSchemas() {
		return schemas;
	}

	public DbStructure createSchema(final String schemaName, final DbSchema schemaData) throws DbStructureException {
		if (schemas.containsKey(schemaName)) {
			throw new DbStructureException("Cannot create schema. Schema already exists: '" + schemaName + "'");
		} else {
			schemas.put(schemaName, schemaData);
			return this;
		}
	}

	public DbSchema dropSchema(final String schemaName) throws DbStructureException {
		if (!schemas.containsKey(schemaName)) {
			throw new DbStructureException("Cannot drop schema. No such schema: '" + schemaName + "'");
		} else {
			return schemas.remove(schemaName);
		}
	}
}
