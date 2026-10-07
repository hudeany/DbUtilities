package de.soderer.utilities.sql;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * Field expression of a {@link SelectStatement} with an optional alias name.
 * The expression is either a String or a {@link SelectStatement} used as subselect.
 */
public class NamedExpression {
	private Object expression;

	private String name;

	/**
	 * Creates a new field expression.
	 *
	 * @param expression String expression or {@link SelectStatement}
	 * @param name optional alias name, may be null
	 * @throws Exception if the expression is neither a String nor a SelectStatement
	 */
	public NamedExpression(final Object expression, final String name) throws Exception {
		setExpression(expression);
		setName(name);
	}

	/**
	 * Returns the expression.
	 *
	 * @return the expression
	 */
	public Object getExpression() {
		return expression;
	}

	/**
	 * Sets the expression. String expressions are trimmed.
	 *
	 * @param expression String expression or {@link SelectStatement}
	 * @throws Exception if the expression is neither a String nor a SelectStatement
	 */
	public void setExpression(final Object expression) throws Exception {
		if (expression instanceof String) {
			this.expression = Utilities.trim((String) expression);
		} else if (expression instanceof SelectStatement) {
			this.expression = expression;
		} else {
			throw new Exception("Invalid expression type. Only String and SelectStatement are allowed");
		}
	}

	/**
	 * Returns the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 *
	 * @param name the name
	 */
	public void setName(final String name) {
		this.name = Utilities.trim(name);
	}
}
