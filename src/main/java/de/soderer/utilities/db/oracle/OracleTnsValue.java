package de.soderer.utilities.db.oracle;

/**
 * Value of an entry in an Oracle tnsnames.ora file, either a plain string or a map of nested values.
 */
public interface OracleTnsValue {
	/**
	 * Returns the value.
	 *
	 * @return a String for {@link OracleTnsStringValue} or a map of nested values for {@link OracleTnsMapValue}
	 */
	Object getValue();
}
