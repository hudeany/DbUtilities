package de.soderer.utilities.db.exception;

/**
 * Thrown if a database structure operation is invalid, e.g. a table is created twice or a non existing column is dropped.
 */
public class DbStructureException extends Exception {
	private static final long serialVersionUID = -5889099231142621241L;

	/**
	 * Creates a new exception.
	 *
	 * @param errorMessage error message
	 */
	public DbStructureException(final String errorMessage) {
		super(errorMessage);
	}
}
