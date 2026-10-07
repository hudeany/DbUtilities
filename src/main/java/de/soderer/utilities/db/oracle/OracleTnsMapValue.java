package de.soderer.utilities.db.oracle;

import de.soderer.utilities.db.utilities.MultiValueCaseInsensitiveOrderedMap;

/**
 * Nested value of a tnsnames.ora entry like {@code (DESCRIPTION=(ADDRESS=...)(CONNECT_DATA=...))}.
 * Keys may occur multiple times, e.g. several ADDRESS entries.
 */
public class OracleTnsMapValue implements OracleTnsValue {
	private final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> tnsValues;

	/**
	 * Creates a new map value.
	 *
	 * @param tnsValues nested values by their case-insensitive keys
	 */
	public OracleTnsMapValue(final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> tnsValues) {
		this.tnsValues = tnsValues;
	}

	@Override
	public MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> getValue() {
		return tnsValues;
	}
}
