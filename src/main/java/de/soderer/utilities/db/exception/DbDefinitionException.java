package de.soderer.utilities.db.exception;

public class DbDefinitionException extends Exception {
	private static final long serialVersionUID = 6039775378389122712L;

	public DbDefinitionException(final String errorMessage) {
		super(errorMessage);
	}

	public DbDefinitionException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
