package de.soderer.utilities.db.data;

public enum DbSimpleDataType {
	Integer, // -2.147.483.648 (-2^31) to 2.147.483.647 (-2^31-1)
	BigInteger, // -9.223.372.036.854.775.808 (-2^63) to 9.223.372.036.854.775.807 (2^63-1)
	Float, // Having a precision of releveant dibits and scale of exponent
	Date,
	DateTime,
	String,
	Clob,
	Blob,
	Boolean;

	public static DbSimpleDataType getSimpleDataTypeByName(final String name) {
		for (final DbSimpleDataType simpleDataType : DbSimpleDataType.values()) {
			if (simpleDataType.name().equalsIgnoreCase(name)) {
				return simpleDataType;
			}
		}
		throw new RuntimeException("Unknown SimpleDataType: " + name);
	}
}
