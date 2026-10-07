package de.soderer.utilities.db.exception;

/**
 * Thrown if a file based database (e.g. SQLite, Derby, HSQL) does not exist.
 */
public class DbNotExistsException extends Exception {
	private static final long serialVersionUID = -3769530565881724303L;

	/**
	 * Creates a new exception without message.
	 */
	public DbNotExistsException() {
		super();
	}

	/**
	 * Creates a new exception.
	 *
	 * @param message error message
	 * @param cause cause of this exception
	 * @param enableSuppression whether suppression is enabled
	 * @param writableStackTrace whether the stack trace should be writable
	 */
	public DbNotExistsException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * Creates a new exception.
	 *
	 * @param message error message
	 * @param cause cause of this exception
	 */
	public DbNotExistsException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new exception.
	 *
	 * @param message error message
	 */
	public DbNotExistsException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception.
	 *
	 * @param cause cause of this exception
	 */
	public DbNotExistsException(Throwable cause) {
		super(cause);
	}
}
