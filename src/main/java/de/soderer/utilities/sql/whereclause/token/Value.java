package de.soderer.utilities.sql.whereclause.token;

import java.math.BigDecimal;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Value of a where clause: a literal string or number, a field reference or (in subclasses) an expression.
 */
public class Value extends RulePart {
	/**
	 * Data type of a value.
	 */
	public enum Type {
		/** Boolean value, e.g. the result of a comparison */
		Bool,

		/** String value */
		String,

		/** Numeric value */
		Number,

		/** Date value */
		Date
	}

	/**
	 * Data type of this value.
	 */
	public Type type;

	/**
	 * Value of a string literal, null for other values.
	 */
	public String stringValue;
	/**
	 * Value of a number literal.
	 */
	public double numberValue;

	/**
	 * Lowercased name of a referenced field, null for literals.
	 */
	public String fieldName;

	/**
	 * Creates a new value without type, used by subclasses.
	 */
	protected Value() {
	}

	/**
	 * Creates a new string literal.
	 *
	 * @param stringValue string value without quotes
	 */
	public Value(final String stringValue) {
		type = Type.String;
		this.stringValue = stringValue;
	}

	/**
	 * Creates a new number literal.
	 *
	 * @param numberValue numeric value
	 */
	public Value(final double numberValue) {
		type = Type.Number;
		this.numberValue = numberValue;
	}

	/**
	 * Creates a new field reference.
	 *
	 * @param fieldName name of the field (stored lowercased)
	 * @param fieldType data type of the field
	 */
	public Value(final String fieldName, final Type fieldType) {
		this.fieldName = fieldName.toLowerCase();
		type = fieldType;
	}

	@Override
	public String toString() {
		if (Utilities.isNotEmpty(fieldName)) {
			return fieldName;
		} else {
			switch (type) {
				case String:
					final StringBuilder returnValue = new StringBuilder();
					returnValue.append("'");
					returnValue.append(stringValue.replace("'", "''"));
					returnValue.append("'");
					return returnValue.toString();
				case Number:
					// Locale independent output without grouping separators, e.g. "1000.5" instead of "1.000,5" for German locale
					return BigDecimal.valueOf(numberValue).stripTrailingZeros().toPlainString();
				case Bool:
					throw new RuntimeException("Invalid value type for output: " + type);
				case Date:
					throw new RuntimeException("Invalid value type for output: " + type);
				default:
					throw new RuntimeException("Invalid value type for output: " + type);
			}
		}
	}

	@Override
	public String toString(final RulePart.StringType stringType) {
		if (Utilities.isNotEmpty(fieldName) && Expression.SYSDATE_VALUES.contains(fieldName)) {
			if (stringType == StringType.Oracle) {
				return "SYSDATE";
			} else if (stringType == StringType.MySQL) {
				return "SYSDATE()";
			} else {
				return "sysdate";
			}
		} else {
			return toString();
		}
	}
}
