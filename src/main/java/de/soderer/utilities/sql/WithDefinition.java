package de.soderer.utilities.sql;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Named subquery of the WITH clause of a {@link SelectStatement}: {@code name AS (definition)}.
 */
public class WithDefinition {
	private String name;

	private String definition;

	/**
	 * Creates a new WITH definition.
	 *
	 * @param name name of the subquery
	 * @param definition SQL of the subquery without surrounding brackets
	 */
	public WithDefinition(final String name, final String definition) {
		setName(name);
		setDefinition(definition);
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
		final StringBuilder returnValue = new StringBuilder(name);
		returnValue.append(" AS (").append(definition).append(")");
		return returnValue.toString();
	}
}
