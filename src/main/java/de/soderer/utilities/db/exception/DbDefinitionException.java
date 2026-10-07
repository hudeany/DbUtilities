package de.soderer.utilities.db.exception;

/**
 * Thrown if a database connection definition is invalid or incomplete.
 */
public class DbDefinitionException extends Exception {
	private static final long serialVersionUID = 6039775378389122712L;

	/**
	 * Creates a new exception.
	 *
	 * @param errorMessage error message
	 */
	public DbDefinitionException(final String errorMessage) {
		super(errorMessage);
	}

	/**
	 * Creates a new exception.
	 *
	 * @param errorMessage error message
	 * @param e cause of this exception
	 */
	public DbDefinitionException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
