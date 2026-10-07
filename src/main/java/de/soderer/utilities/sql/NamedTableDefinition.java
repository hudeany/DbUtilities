package de.soderer.utilities.sql;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Table definition in the FROM clause of a {@link SelectStatement} with an optional alias name.
 */
public class NamedTableDefinition {
	private String definition;

	private String name;

	/**
	 * Creates a new table definition.
	 *
	 * @param definition table name or table expression
	 * @param name optional alias name, may be null
	 */
	public NamedTableDefinition(final String definition, final String name) {
		setDefinition(definition);
		setName(name);
	}

	/**
	 * Returns the definition.
	 *
	 * @return the definition
	 */
	public String getDefinition() {
		return definition;
	}

	/**
	 * Sets the definition.
	 *
	 * @param definition the definition
	 */
	public void setDefinition(final String definition) {
		this.definition = Utilities.trim(definition);
	}

	/**
	 * Returns the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 *
	 * @param name the name
	 */
	public void setName(final String name) {
		this.name = Utilities.trim(name);
	}

	@Override
	public String toString() {
		final StringBuilder returnValue = new StringBuilder(definition);
		if (Utilities.isNotBlank(name)) {
			returnValue.append(" ").append(name);
		}
		return returnValue.toString();
	}
}
