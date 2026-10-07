package de.soderer.utilities.sql;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Formats a {@link SelectStatement} as SQL text.
 */
public class SelectStatementFormatter {
	private final String indentation;
	private final String lineBreak;

	/**
	 * Creates a new formatter using tab indentation and "\n" line breaks.
	 */
	public SelectStatementFormatter() {
		this("\t", "\n");
	}

	/**
	 * Creates a new formatter.
	 * If indentation and line break are both empty, the statement is formatted as single line.
	 *
	 * @param indentation indentation string, e.g. "\t"
	 * @param lineBreak line break string, e.g. "\n"
	 */
	public SelectStatementFormatter(final String indentation, final String lineBreak) {
		this.indentation = indentation;
		this.lineBreak = lineBreak;
	}

	/**
	 * Formats a SELECT statement.
	 *
	 * @param selectStatement statement to format
	 * @return SQL text of the statement
	 */
	public String format(final SelectStatement selectStatement) {
		if (Utilities.isEmpty(indentation) && Utilities.isEmpty(lineBreak)) {
			final StringBuilder returnValue = new StringBuilder();
			if (selectStatement.getWithClauses() != null && selectStatement.getWithClauses().size() > 0) {
				returnValue.append("WITH ");
				returnValue.append(Utilities.join(selectStatement.getWithClauses(), ","));
				returnValue.append(" ");
			}
			returnValue.append("SELECT ");
			boolean isFirstNamedExpression = true;
			for (final NamedExpression namedFieldDefinition : selectStatement.getFields()) {
				if (!isFirstNamedExpression) {
					returnValue.append(",");
				}
				returnValue.append(format(namedFieldDefinition));
				isFirstNamedExpression = false;
			}
			returnValue.append(" FROM ");
			returnValue.append(Utilities.join(selectStatement.getFromTables(), ","));
			if (selectStatement.getJoinClauses() != null && selectStatement.getJoinClauses().size() > 0) {
				returnValue.append(" ");
				returnValue.append(Utilities.join(selectStatement.getJoinClauses(), " "));
			}
			if (Utilities.isNotBlank(selectStatement.getWhereClause())) {
				returnValue.append(" WHERE ");
				returnValue.append(selectStatement.getWhereClause());
			}
			if (selectStatement.getGroupBy() != null && selectStatement.getGroupBy().size() > 0) {
				returnValue.append(" GROUP BY ");
				returnValue.append(Utilities.join(selectStatement.getGroupBy(), ", "));
			}
			if (Utilities.isNotBlank(selectStatement.getHavingClause())) {
				returnValue.append(" HAVING ");
				returnValue.append(selectStatement.getHavingClause());
			}
			if (selectStatement.getOrderBy() != null && selectStatement.getOrderBy().size() > 0) {
				returnValue.append(" ORDER BY ");
				returnValue.append(Utilities.join(selectStatement.getOrderBy(), ", "));
			}
			return returnValue.toString();
		} else {
			final StringBuilder returnValue = new StringBuilder();
			if (selectStatement.getWithClauses() != null && selectStatement.getWithClauses().size() > 0) {
				returnValue.append("WITH");
				returnValue.append(lineBreak);
				returnValue.append(indentation);
				returnValue.append(Utilities.join(selectStatement.getWithClauses(), "," + lineBreak + indentation));
				returnValue.append(lineBreak);
			}
			returnValue.append("SELECT");
			returnValue.append(lineBreak);
			returnValue.append(indentation);
			boolean isFirstNamedExpression = true;
			for (final NamedExpression namedFieldDefinition : selectStatement.getFields()) {
				if (!isFirstNamedExpression) {
					returnValue.append("," + lineBreak + indentation);
				}
				returnValue.append(format(namedFieldDefinition));
				isFirstNamedExpression = false;
			}
			returnValue.append(lineBreak);
			returnValue.append("FROM");
			returnValue.append(lineBreak);
			returnValue.append(indentation);
			returnValue.append(Utilities.join(selectStatement.getFromTables(), "," + lineBreak + indentation));
			if (selectStatement.getJoinClauses() != null && selectStatement.getJoinClauses().size() > 0) {
				returnValue.append(lineBreak);
				returnValue.append(Utilities.join(selectStatement.getJoinClauses(), lineBreak));
			}
			if (Utilities.isNotBlank(selectStatement.getWhereClause())) {
				returnValue.append(lineBreak);
				returnValue.append("WHERE ");
				returnValue.append(selectStatement.getWhereClause());
			}
			if (selectStatement.getGroupBy() != null && selectStatement.getGroupBy().size() > 0) {
				returnValue.append(lineBreak);
				returnValue.append("GROUP BY");
				returnValue.append(lineBreak);
				returnValue.append(indentation);
				returnValue.append(Utilities.join(selectStatement.getGroupBy(), "," + lineBreak + indentation));
			}
			if (Utilities.isNotBlank(selectStatement.getHavingClause())) {
				returnValue.append(lineBreak);
				returnValue.append("HAVING ");
				returnValue.append(selectStatement.getHavingClause());
			}
			if (selectStatement.getOrderBy() != null && selectStatement.getOrderBy().size() > 0) {
				returnValue.append(lineBreak);
				returnValue.append("ORDER BY");
				returnValue.append(lineBreak);
				returnValue.append(indentation);
				returnValue.append(Utilities.join(selectStatement.getOrderBy(), "," + lineBreak + indentation));
			}
			return returnValue.toString();
		}
	}

	private String format(final NamedExpression namedFieldDefinition) {
		String expressionString;
		if (namedFieldDefinition.getExpression() instanceof SelectStatement) {
			// A subselect used as field must be enclosed in brackets
			expressionString = "(" + format((SelectStatement) namedFieldDefinition.getExpression()) + ")";
		} else {
			expressionString = (String) namedFieldDefinition.getExpression();
		}
		final StringBuilder returnValue = new StringBuilder(expressionString);
		if (Utilities.isNotBlank(namedFieldDefinition.getName())) {
			returnValue.append(" AS \"").append(namedFieldDefinition.getName()).append("\"");
		}
		return returnValue.toString();
	}
}
