package de.soderer.utilities.db.data;

import java.util.LinkedHashMap;

import de.soderer.utilities.db.exception.DbStructureException;
import de.soderer.utilities.db.utilities.CaseInsensitiveLinkedMap;

/**
 * Database structure consisting of schemas with their tables, columns and keys.
 * The tables of DDL scripts without schema names are stored in the schema with the empty name "".
 */
public class DbStructure {
	/**
	 * Creates a new empty structure.
	 */
	public DbStructure() {
		// Schemas are added by createSchema()
	}

	private final LinkedHashMap<String, DbSchema> schemas = new CaseInsensitiveLinkedMap<>();

	/**
	 * Returns the schemas by their case-insensitive names.
	 *
	 * @return the schemas (modifiable)
	 */
	public LinkedHashMap<String, DbSchema> getSchemas() {
		return schemas;
	}

	/**
	 * Adds a new schema.
	 *
	 * @param schemaName name of the schema (case-insensitive)
	 * @param schemaData schema definition
	 * @return this structure for method chaining
	 * @throws DbStructureException if a schema with this name already exists
	 */
	public DbStructure createSchema(final String schemaName, final DbSchema schemaData) throws DbStructureException {
		if (schemas.containsKey(schemaName)) {
			throw new DbStructureException("Cannot create schema. Schema already exists: '" + schemaName + "'");
		} else {
			schemas.put(schemaName, schemaData);
			return this;
		}
	}

	/**
	 * Removes a schema.
	 *
	 * @param schemaName name of the schema (case-insensitive)
	 * @return the removed schema definition
	 * @throws DbStructureException if there is no schema with this name
	 */
	public DbSchema dropSchema(final String schemaName) throws DbStructureException {
		if (!schemas.containsKey(schemaName)) {
			throw new DbStructureException("Cannot drop schema. No such schema: '" + schemaName + "'");
		} else {
			return schemas.remove(schemaName);
		}
	}
}
