package de.soderer.utilities.db.utilities;

import java.util.Collection;
import java.util.Locale;

/**
 * Generic String Set that ignores the String case.
 * Items are stored lowercased, so iterating the set returns the lowercased variants.
 */
public class CaseInsensitiveSet extends AbstractHashSet<String> {
	private static final long serialVersionUID = 9146520978777587374L;

	/**
	 * Creates a new empty set with default initial capacity and load factor.
	 */
	public CaseInsensitiveSet() {
		super();
	}

	/**
	 * Creates a new empty set.
	 *
	 * @param initialCapacity initial capacity
	 * @param loadFactor load factor
	 */
	public CaseInsensitiveSet(final int initialCapacity, final float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	/**
	 * Creates a new empty set with default load factor.
	 *
	 * @param initialCapacity initial capacity
	 */
	public CaseInsensitiveSet(final int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * Creates a new set containing all items of the given collection.
	 *
	 * @param collection items to add
	 */
	public CaseInsensitiveSet(final Collection<? extends String> collection) {
		super(collection);
	}

	/**
	 * Creates a new set containing all given values.
	 *
	 * @param values items to add
	 */
	public CaseInsensitiveSet(final String[] values) {
		for (final String value : values) {
			add(value);
		}
	}

	/**
	 * Sentinel value used for lookups with a non-String item. It is never stored as an actual item (this set
	 * only ever stores lowercased Strings), so passing it to the underlying HashSet always reports "not found"
	 * instead of the previous behavior of converting any Object via toString(), which could cause false-positive
	 * matches (e.g. an Integer 5 matching a stored String "5").
	 */
	private static final String NON_STRING_ITEM_SENTINEL = "\u0000non-string-item-sentinel-" + java.util.UUID.randomUUID();

	@Override
	protected String convertItem(final Object item) {
		if (item == null) {
			return null;
		} else if (item instanceof String) {
			return ((String) item).toLowerCase(Locale.ROOT);
		} else {
			return NON_STRING_ITEM_SENTINEL;
		}
	}
}
