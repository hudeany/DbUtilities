package de.soderer.utilities.db.data;

/**
 * Vendor independent classification of database column types.
 */
public enum DbSimpleDataType {
	/** Integer number from -2.147.483.648 (-2^31) to 2.147.483.647 (2^31-1) */
	Integer,

	/** Integer number from -9.223.372.036.854.775.808 (-2^63) to 9.223.372.036.854.775.807 (2^63-1) */
	BigInteger,

	/** Decimal or floating point number, having a precision of relevant digits and a scale */
	Float,

	/** Date without time */
	Date,

	/** Date with time (timestamp) */
	DateTime,

	/** Character data with limited length */
	String,

	/** Large character data */
	Clob,

	/** Binary data */
	Blob,

	/** Boolean value */
	Boolean;

	/**
	 * Returns the simple data type by its name (case-insensitive).
	 *
	 * @param name name of the simple data type, e.g. "datetime"
	 * @return the simple data type
	 * @throws RuntimeException if there is no simple data type with this name
	 */
	public static DbSimpleDataType getSimpleDataTypeByName(final String name) {
		for (final DbSimpleDataType simpleDataType : DbSimpleDataType.values()) {
			if (simpleDataType.name().equalsIgnoreCase(name)) {
				return simpleDataType;
			}
		}
		throw new RuntimeException("Unknown DbSimpleDataType: " + name);
	}
}
