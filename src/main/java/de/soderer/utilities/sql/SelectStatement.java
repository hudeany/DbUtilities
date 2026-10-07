package de.soderer.utilities.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple object model of a SQL SELECT statement, formatted by {@link SelectStatementFormatter}.
 * <p>
 * TODO: UNION ALL
 */
public class SelectStatement {
	private List<WithDefinition> withClauses = new ArrayList<>();
	private List<NamedExpression> fields = new ArrayList<>();
	private List<NamedTableDefinition> fromTables = new ArrayList<>();
	private List<JoinClause> joinClauses;
	private String whereClause;
	private List<String> groupBy;
	private String havingClause;
	private List<String> orderBy;

	/**
	 * Returns the named subqueries of the WITH clause.
	 *
	 * @return the named subqueries of the WITH clause
	 */
	public List<WithDefinition> getWithClauses() {
		return withClauses;
	}

	/**
	 * Sets the named subqueries of the WITH clause.
	 *
	 * @param withClause the named subqueries of the WITH clause
	 * @return this instance for method chaining
	 */
	public SelectStatement setWithClauses(final List<WithDefinition> withClause) {
		withClauses = withClause;
		return this;
	}

	/**
	 * Adds a named subquery to the WITH clause.
	 *
	 * @param withDefinition named subquery
	 * @return this statement for method chaining
	 */
	public SelectStatement addWithClause(final WithDefinition withDefinition) {
		withClauses.add(withDefinition);
		return this;
	}

	/**
	 * Adds a named subquery to the WITH clause.
	 *
	 * @param name name of the subquery
	 * @param fieldDefinition SQL of the subquery without surrounding brackets
	 * @return this statement for method chaining
	 */
	public SelectStatement addWithClause(final String name, final String fieldDefinition) {
		withClauses.add(new WithDefinition(name, fieldDefinition));
		return this;
	}

	/**
	 * Returns the selected fields.
	 *
	 * @return the selected fields
	 */
	public List<NamedExpression> getFields() {
		return fields;
	}

	/**
	 * Sets the selected fields.
	 *
	 * @param fields the selected fields
	 * @return this instance for method chaining
	 */
	public SelectStatement setFields(final List<NamedExpression> fields) {
		this.fields = fields;
		return this;
	}

	/**
	 * Adds a selected field.
	 *
	 * @param namedFieldDefinition field expression with optional alias
	 * @return this statement for method chaining
	 */
	public SelectStatement addField(final NamedExpression namedFieldDefinition) {
		fields.add(namedFieldDefinition);
		return this;
	}

	/**
	 * Adds a selected field without alias.
	 *
	 * @param expression String expression or {@link SelectStatement} used as subselect
	 * @return this statement for method chaining
	 * @throws Exception if the expression is neither a String nor a SelectStatement
	 */
	public SelectStatement addField(final Object expression) throws Exception {
		fields.add(new NamedExpression(expression, null));
		return this;
	}

	/**
	 * Returns the tables of the FROM clause.
	 *
	 * @return the tables of the FROM clause
	 */
	public List<NamedTableDefinition> getFromTables() {
		return fromTables;
	}

	/**
	 * Sets the tables of the FROM clause.
	 *
	 * @param fromTables the tables of the FROM clause
	 * @return this instance for method chaining
	 */
	public SelectStatement setFromTables(final List<NamedTableDefinition> fromTables) {
		this.fromTables = fromTables;
		return this;
	}

	/**
	 * Adds a table to the FROM clause.
	 *
	 * @param fromTable table definition with optional alias
	 * @return this statement for method chaining
	 */
	public SelectStatement addFromTable(final NamedTableDefinition fromTable) {
		fromTables.add(fromTable);
		return this;
	}

	/**
	 * Adds a table without alias to the FROM clause.
	 *
	 * @param tableDefinition table name or table expression
	 * @return this statement for method chaining
	 */
	public SelectStatement addFromTable(final String tableDefinition) {
		fromTables.add(new NamedTableDefinition(tableDefinition, null));
		return this;
	}

	/**
	 * Returns the JOIN clauses.
	 *
	 * @return the JOIN clauses
	 */
	public List<JoinClause> getJoinClauses() {
		return joinClauses;
	}

	/**
	 * Sets the JOIN clauses.
	 *
	 * @param joinClauses the JOIN clauses
	 */
	public void setJoinClauses(final List<JoinClause> joinClauses) {
		this.joinClauses = joinClauses;
	}

	/**
	 * Returns the WHERE condition without the keyword WHERE.
	 *
	 * @return the WHERE condition without the keyword WHERE
	 */
	public String getWhereClause() {
		return whereClause;
	}

	/**
	 * Sets the WHERE condition without the keyword WHERE.
	 *
	 * @param whereClause the WHERE condition without the keyword WHERE
	 */
	public void setWhereClause(final String whereClause) {
		this.whereClause = whereClause;
	}

	/**
	 * Returns the GROUP BY expressions.
	 *
	 * @return the GROUP BY expressions
	 */
	public List<String> getGroupBy() {
		return groupBy;
	}

	/**
	 * Sets the GROUP BY expressions.
	 *
	 * @param groupBy the GROUP BY expressions
	 */
	public void setGroupBy(final List<String> groupBy) {
		this.groupBy = groupBy;
	}

	/**
	 * Returns the HAVING condition without the keyword HAVING.
	 *
	 * @return the HAVING condition without the keyword HAVING
	 */
	public String getHavingClause() {
		return havingClause;
	}

	/**
	 * Sets the HAVING condition without the keyword HAVING.
	 *
	 * @param havingClause the HAVING condition without the keyword HAVING
	 */
	public void setHavingClause(final String havingClause) {
		this.havingClause = havingClause;
	}

	/**
	 * Returns the ORDER BY expressions.
	 *
	 * @return the ORDER BY expressions
	 */
	public List<String> getOrderBy() {
		return orderBy;
	}

	/**
	 * Sets the ORDER BY expressions.
	 *
	 * @param orderBy the ORDER BY expressions
	 */
	public void setOrderBy(final List<String> orderBy) {
		this.orderBy = orderBy;
	}

	/**
	 * Creates a new empty SELECT statement.
	 */
	public SelectStatement() {
	}

	/**
	 * Creates a new SELECT statement.
	 *
	 * @param fields selected fields
	 * @param fromTables tables of the FROM clause
	 */
	public SelectStatement(final List<NamedExpression> fields, final List<NamedTableDefinition> fromTables) {
		this.fields = fields;
		this.fromTables = fromTables;
	}

	@Override
	public String toString() {
		return new SelectStatementFormatter().format(this);
	}
}
