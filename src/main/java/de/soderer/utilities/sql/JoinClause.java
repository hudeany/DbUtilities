package de.soderer.utilities.sql;

import de.soderer.utilities.db.utilities.Utilities;

/**
 * JOIN clause of a {@link SelectStatement}.
 */
public class JoinClause {
	/**
	 * Type of a JOIN clause. The constant names contain a historical misspelling ("Outter") that is kept for compatibility.
	 */
	public enum JoinType {
		/**
		 * Inner join ("JOIN")
		 */
		InnerJoin,
		/**
		 * Left outer join ("LEFT JOIN")
		 */
		LeftOutterJoin,
		/**
		 * Right outer join ("RIGHT JOIN")
		 */
		RightOutterJoin,
		/**
		 * Full outer join ("FULL OUTER JOIN")
		 */
		FullOutterJoin,
	}

	private String joinTable;
	private String joinTableAlias;
	private JoinType joinType;
	private String joinCondition;

	/**
	 * Returns the join table.
	 *
	 * @return the join table
	 */
	public String getJoinTable() {
		return joinTable;
	}

	/**
	 * Sets the join table.
	 *
	 * @param joinTable the join table
	 */
	public void setJoinTable(final String joinTable) {
		this.joinTable = joinTable;
	}

	/**
	 * Returns the join table alias.
	 *
	 * @return the join table alias
	 */
	public String getJoinTableAlias() {
		return joinTableAlias;
	}

	/**
	 * Sets the join table alias.
	 *
	 * @param joinTableAlias the join table alias
	 */
	public void setJoinTableAlias(final String joinTableAlias) {
		this.joinTableAlias = joinTableAlias;
	}

	/**
	 * Returns the join type.
	 *
	 * @return the join type
	 */
	public JoinType getJoinType() {
		return joinType;
	}

	/**
	 * Sets the join type.
	 *
	 * @param joinType the join type
	 */
	public void setJoinType(final JoinType joinType) {
		this.joinType = joinType;
	}

	/**
	 * Returns the join condition.
	 *
	 * @return the join condition
	 */
	public String getJoinCondition() {
		return joinCondition;
	}

	/**
	 * Sets the join condition.
	 *
	 * @param joinCondition the join condition
	 */
	public void setJoinCondition(final String joinCondition) {
		this.joinCondition = joinCondition;
	}

	/**
	 * Creates a new JOIN clause.
	 *
	 * @param joinTable joined table
	 * @param joinTableAlias optional alias of the joined table
	 * @param joinType type of the join
	 * @param joinCondition optional join condition used after "ON"
	 */
	public JoinClause(final String joinTable, final String joinTableAlias, final JoinType joinType, final String joinCondition) {
		this.joinTable = joinTable;
		this.joinTableAlias = joinTableAlias;
		this.joinType = joinType;
		this.joinCondition = joinCondition;
	}

	@Override
	public String toString() {
		final StringBuilder returnValue = new StringBuilder();

		switch (joinType) {
			case InnerJoin:
				returnValue.append("JOIN");
				break;
			case LeftOutterJoin:
				returnValue.append("LEFT JOIN");
				break;
			case RightOutterJoin:
				returnValue.append("RIGHT JOIN");
				break;
			case FullOutterJoin:
				returnValue.append("FULL OUTER JOIN");
				break;
			default:
				throw new RuntimeException("Invalid missing Join type");
		}

		returnValue.append(" ");
		returnValue.append(joinTable);
		if (Utilities.isNotBlank(joinTableAlias)) {
			returnValue.append(" ");
			returnValue.append(joinTableAlias);
		}
		if (Utilities.isNotBlank(joinCondition)) {
			returnValue.append(" ON ");
			returnValue.append(joinCondition);
		}

		return returnValue.toString();
	}
}
