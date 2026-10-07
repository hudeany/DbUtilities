package de.soderer.utilities.sql.whereclause.token;

/**
 * Common base class of all rule tokens and their aggregations created by
 * {@link de.soderer.utilities.sql.whereclause.ReducedSqlWhereClauseParser}.
 */
public abstract class RulePart {
	/**
	 * Creates a new rule part.
	 */
	protected RulePart() {
		// Nothing to initialize
	}

	/**
	 * Output syntax of {@link RulePart#toString(StringType)}.
	 */
	public enum StringType {
		/** Oracle SQL syntax (e.g. TO_DATE, TO_CHAR, SYSDATE) */
		Oracle,

		/** MySQL SQL syntax (e.g. STR_TO_DATE, DATE_FORMAT, SYSDATE()) */
		MySQL,

		/** BeanShell (Java) expression syntax (e.g. "%" for modulo, "!=" for "&lt;&gt;") */
		BeanShell
	}

	/**
	 * Returns the generic text representation of this rule part.
	 *
	 * @return text representation
	 */
	@Override
	public abstract String toString();

	/**
	 * Returns the text representation of this rule part in the given syntax.
	 *
	 * @param type output syntax
	 * @return text representation
	 */
	public abstract String toString(StringType type);
}
