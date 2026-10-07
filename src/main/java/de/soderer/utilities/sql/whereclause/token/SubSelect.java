package de.soderer.utilities.sql.whereclause.token;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Subselect used within an IN list, e.g. {@code id in (select id from other)}.
 * The subselect text is kept as is and not parsed.
 */
public class SubSelect extends Value {
	/**
	 * Keywords starting a subselect.
	 */
	public static final Set<String> SIGNS = new HashSet<>(Arrays.asList(new String[] { "select" }));

	/**
	 * SQL text of the subselect without surrounding brackets.
	 */
	public String value;

	/**
	 * Creates a new subselect.
	 *
	 * @param subSelectString SQL text of the subselect, must start with "select " and contain " from "
	 * @throws IllegalArgumentException if the text is no valid subselect
	 */
	public SubSelect(String subSelectString) {
		if (!subSelectString.trim().toLowerCase().startsWith("select ") || !subSelectString.toLowerCase().contains(" from ")) {
			throw new IllegalArgumentException("Illegal subselect");
		}
		value = subSelectString;
	}

	@Override
	public String toString() {
		return value.trim();
	}

	@Override
	public String toString(RulePart.StringType stringType) {
		return toString();
	}
}
