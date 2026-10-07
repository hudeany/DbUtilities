package de.soderer.utilities.sql.whereclause.token;

/**
 * Syntactical token of a where clause: opening or closing bracket or list separator.
 */
public class SupplementalPart extends RulePart {
	/**
	 * Type of a syntactical token.
	 */
	public enum Type {
		/** Opening bracket "(" */
		OpeningBracket,

		/** Closing bracket ")" */
		ClosingBracket,

		/** List separator "," */
		Separator
	}

	/**
	 * Type of this token.
	 */
	public Type type;

	/**
	 * Creates a new token.
	 *
	 * @param type type of the token
	 */
	public SupplementalPart(final Type type) {
		this.type = type;
	}

	@Override
	public String toString() {
		switch (type) {
			case OpeningBracket:
				return "(";
			case ClosingBracket:
				return ")";
			case Separator:
				return ", ";
			default:
				return ", ";
		}
	}

	@Override
	public String toString(final RulePart.StringType stringType) {
		switch (type) {
			case OpeningBracket:
				return "(";
			case ClosingBracket:
				return ")";
			case Separator:
				return ", ";
			default:
				return ", ";
		}
	}
}
