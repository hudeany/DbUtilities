package de.soderer.utilities.db;

public class DbStructureException extends Exception {
	private static final long serialVersionUID = -5889099231142621241L;

	public DbStructureException(final String errorMessage) {
		super(errorMessage);
	}
}
