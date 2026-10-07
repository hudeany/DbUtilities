package de.soderer.utilities.db.utilities;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Base class for HashSet variants that transform their items on the way in and out via {@link #convertItem(Object)}
 * (e.g. case-insensitive lookup by lowercasing String items).
 *
 * @param <V> type of the items
 */
public abstract class AbstractHashSet<V> extends HashSet<V> {
	private static final long serialVersionUID = -8774751629113337123L;

	/**
	 * Creates a new empty set with default initial capacity and load factor.
	 */
	public AbstractHashSet() {
		super();
	}

	/**
	 * Creates a new empty set.
	 *
	 * @param initialCapacity initial capacity
	 * @param loadFactor load factor
	 */
	public AbstractHashSet(final int initialCapacity, final float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	/**
	 * Creates a new empty set with default load factor.
	 *
	 * @param initialCapacity initial capacity
	 */
	public AbstractHashSet(final int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * Creates a new set containing all items of the given collection. The items are converted on insertion.
	 *
	 * @param collection items to add
	 */
	public AbstractHashSet(final Collection<? extends V> collection) {
		super(collection.size());
		addAll(collection);
	}

	@Override
	public boolean contains(final Object item) {
		return super.contains(convertItem(item));
	}

	@Override
	public boolean add(final V item) {
		return super.add(convertItem(item));
	}

	@Override
	public boolean remove(final Object item) {
		return super.remove(convertItem(item));
	}

	@Override
	public boolean removeAll(final Collection<?> collection) {
		// Not overriding this would fall back to AbstractCollection's implementation, which checks
		// collection.contains(ownElement) for each of our (already converted, e.g. lowercased) elements -
		// against the *unconverted* values in "collection". That silently behaves case-sensitively instead
		// of routing through convertItem() like every other mutating method here.
		//
		// Building a converted lookup set first and then mutating only via our own iterator (instead of
		// iterating "collection" directly and calling remove(item) on ourselves inside that loop) also
		// avoids a ConcurrentModificationException when collection is this very set itself (e.g.
		// set.removeAll(set)), since iterating and mutating the same underlying set at the same time is
		// what triggers that exception.
		final java.util.Set<V> convertedItemsToRemove = new HashSet<>();
		for (final Object item : collection) {
			convertedItemsToRemove.add(convertItem(item));
		}
		boolean result = false;
		final Iterator<V> iterator = iterator();
		while (iterator.hasNext()) {
			if (convertedItemsToRemove.contains(iterator.next())) {
				iterator.remove();
				result = true;
			}
		}
		return result;
	}

	@Override
	public boolean retainAll(final Collection<?> collection) {
		// Same reasoning as removeAll(): convert the given collection's items the same way our own elements
		// are, so membership comparisons use the same normalized form instead of comparing against the
		// collection's original (unconverted) values.
		final java.util.Set<V> convertedItemsToRetain = new HashSet<>();
		for (final Object item : collection) {
			convertedItemsToRetain.add(convertItem(item));
		}
		boolean result = false;
		final Iterator<V> iterator = iterator();
		while (iterator.hasNext()) {
			if (!convertedItemsToRetain.contains(iterator.next())) {
				iterator.remove();
				result = true;
			}
		}
		return result;
	}

	/**
	 * Converts an item before it is stored or looked up, e.g. by lowercasing it.
	 *
	 * @param item item as given by the caller (may be null or of any type)
	 * @return converted item used internally
	 */
	protected abstract V convertItem(Object item);
}
