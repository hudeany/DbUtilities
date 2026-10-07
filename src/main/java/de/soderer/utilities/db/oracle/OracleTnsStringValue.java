package de.soderer.utilities.db.oracle;

/**
 * Plain string value of a tnsnames.ora entry like the value of {@code (HOST=serverHost)}.
 */
public class OracleTnsStringValue implements OracleTnsValue {
	private final String value;

	/**
	 * Creates a new string value.
	 *
	 * @param value the string value without surrounding quotes
	 */
	public OracleTnsStringValue(final String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return value;
	}
}
