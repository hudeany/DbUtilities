package de.soderer.utilities.db.data;

/**
 * Data type of a database column with its size, nullability, auto increment and default value.
 */
public class DbColumnType {
	private final String typeName;
	private final long characterByteSize; // only for VARCHAR and VARCHAR2 types
	private final int numericPrecision; // only for numeric types
	private final int numericScale; // only for numeric types
	private final boolean nullable;
	private final boolean autoIncrement;
	private final String defaultValue;

	/**
	 * Creates a new column type.
	 *
	 * @param typeName vendor specific type name, e.g. "VARCHAR" or "NUMBER"
	 * @param characterByteSize maximum length of character types (-1 or 0 if not applicable)
	 * @param numericPrecision precision of numeric types (-1 or 0 if not applicable)
	 * @param numericScale scale of numeric types (-1 or 0 if not applicable)
	 * @param nullable true if the column accepts NULL values
	 * @param autoIncrement true if the column values are generated automatically
	 * @param defaultValue default value of the column without surrounding quotes, or null
	 */
	public DbColumnType(final String typeName, final long characterByteSize, final int numericPrecision, final int numericScale, final boolean nullable, final boolean autoIncrement, final String defaultValue) {
		this.typeName = typeName;
		this.characterByteSize = characterByteSize;
		this.numericPrecision = numericPrecision;
		this.numericScale = numericScale;
		this.nullable = nullable;
		this.autoIncrement = autoIncrement;
		this.defaultValue = defaultValue;
	}

	/**
	 * Returns the type name.
	 *
	 * @return the type name
	 */
	public String getTypeName() {
		return typeName;
	}

	/**
	 * Returns the character byte size.
	 *
	 * @return the character byte size
	 */
	public long getCharacterByteSize() {
		return characterByteSize;
	}

	/**
	 * Returns the numeric precision.
	 *
	 * @return the numeric precision
	 */
	public int getNumericPrecision() {
		return numericPrecision;
	}

	/**
	 * Returns the numeric scale.
	 *
	 * @return the numeric scale
	 */
	public int getNumericScale() {
		return numericScale;
	}

	/**
	 * Returns whether the column accepts NULL values.
	 *
	 * @return true if the column is nullable
	 */
	public boolean isNullable() {
		return nullable;
	}

	/**
	 * Returns whether the column values are generated automatically (auto increment, identity, serial).
	 *
	 * @return true if the column is auto incremented
	 */
	public boolean isAutoIncrement() {
		return autoIncrement;
	}

	/**
	 * Returns the default value.
	 *
	 * @return the default value
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	/**
	 * Classifies the vendor specific type name as vendor independent simple data type.
	 * Unknown types (e.g. "NUMBER", "DECIMAL", "REAL") are classified as {@link DbSimpleDataType#Float}.
	 *
	 * @return the simple data type
	 */
	public DbSimpleDataType getSimpleDataType() {
		final String lowerTypeName = typeName == null ? "" : typeName.toLowerCase().trim();
		if (lowerTypeName.contains("time")) {
			return DbSimpleDataType.DateTime;
		} else if (lowerTypeName.contains("date")) {
			return DbSimpleDataType.Date;
		} else if (lowerTypeName.contains("clob") || lowerTypeName.contains("text")) {
			return DbSimpleDataType.Clob;
		} else if (lowerTypeName.startsWith("varchar") || lowerTypeName.startsWith("char") || lowerTypeName.startsWith("character")
				|| lowerTypeName.startsWith("nvarchar") || lowerTypeName.startsWith("nchar") || lowerTypeName.startsWith("national char")) {
			return DbSimpleDataType.String;
		} else if (lowerTypeName.contains("blob") || "bytea".equals(lowerTypeName) || lowerTypeName.contains("binary") || "raw".equals(lowerTypeName) || "long raw".equals(lowerTypeName) || "image".equals(lowerTypeName)) {
			return DbSimpleDataType.Blob;
		} else if (lowerTypeName.contains("bigint")) {
			return DbSimpleDataType.BigInteger;
		} else if (lowerTypeName.contains("int") && !lowerTypeName.startsWith("interval") && !lowerTypeName.contains("point")) {
			return DbSimpleDataType.Integer;
		} else if (lowerTypeName.contains("bool")) {
			return DbSimpleDataType.Boolean;
		} else {
			// e.g.: PostgreSQL "REAL"
			return DbSimpleDataType.Float;
		}
	}

	@Override
	public String toString() {
		final DbSimpleDataType simpleDataType = getSimpleDataType();
		return typeName
				+ (simpleDataType == DbSimpleDataType.String ? "(" + characterByteSize + ")" : "")
				+ (simpleDataType == DbSimpleDataType.Float ? "(" + numericPrecision + ", " + numericScale + ")" : "")
				+ (nullable ? " nullable": " not nullable")
				+ (autoIncrement ? " autoIncrement": "")
				+ (defaultValue != null ? " default(" + defaultValue + ")" : "");
	}
}
