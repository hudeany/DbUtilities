package de.soderer.utilities.db.data;

public class DbColumnType {
	private final String typeName;
	private final long characterByteSize; // only for VARCHAR and VARCHAR2 types
	private final int numericPrecision; // only for numeric types
	private final int numericScale; // only for numeric types
	private final boolean nullable;
	private final boolean autoIncrement;
	private final String defaultValue;

	public DbColumnType(final String typeName, final long characterByteSize, final int numericPrecision, final int numericScale, final boolean nullable, final boolean autoIncrement, final String defaultValue) {
		this.typeName = typeName;
		this.characterByteSize = characterByteSize;
		this.numericPrecision = numericPrecision;
		this.numericScale = numericScale;
		this.nullable = nullable;
		this.autoIncrement = autoIncrement;
		this.defaultValue = defaultValue;
	}

	public String getTypeName() {
		return typeName;
	}

	public long getCharacterByteSize() {
		return characterByteSize;
	}

	public int getNumericPrecision() {
		return numericPrecision;
	}

	public int getNumericScale() {
		return numericScale;
	}

	public boolean isNullable() {
		return nullable;
	}

	public boolean isAutoIncrement() {
		return autoIncrement;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public DbSimpleDataType getSimpleDataType() {
		if (typeName.toLowerCase().contains("time")) {
			return DbSimpleDataType.DateTime;
		} else if (typeName.toLowerCase().contains("date")) {
			return DbSimpleDataType.Date;
		} else if (typeName.toLowerCase().contains("clob") || typeName.toLowerCase().contains("text")) {
			return DbSimpleDataType.Clob;
		} else if (typeName.toLowerCase().startsWith("varchar") || typeName.toLowerCase().startsWith("char") || typeName.toLowerCase().startsWith("character")) {
			return DbSimpleDataType.String;
		} else if (typeName.toLowerCase().contains("blob") ||"bytea".equals( typeName.toLowerCase())) {
			return DbSimpleDataType.Blob;
		} else if (typeName.toLowerCase().contains("bigint")) {
			return DbSimpleDataType.BigInteger;
		} else if (typeName.toLowerCase().contains("int")) {
			return DbSimpleDataType.Integer;
		} else if (typeName.toLowerCase().contains("bool")) {
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
