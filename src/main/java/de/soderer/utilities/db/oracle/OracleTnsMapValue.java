package de.soderer.utilities.db.oracle;

import de.soderer.utilities.db.utilities.MultiValueCaseInsensitiveOrderedMap;

public class OracleTnsMapValue implements OracleTnsValue {
	private final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> tnsValues;

	public OracleTnsMapValue(final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> tnsValues) {
		this.tnsValues = tnsValues;
	}

	@Override
	public MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> getValue() {
		return tnsValues;
	}
}
