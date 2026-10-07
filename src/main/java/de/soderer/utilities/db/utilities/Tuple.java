package de.soderer.utilities.db.utilities;

/**
 * Simple immutable pair of two values.
 *
 * @param <T1> type of the first value
 * @param <T2> type of the second value
 */
public class Tuple<T1, T2> {
	private T1 value1;
	private T2 value2;

	/**
	 * Creates a new tuple.
	 *
	 * @param value1 first value
	 * @param value2 second value
	 */
	public Tuple(T1 value1, T2 value2) {
		this.value1 = value1;
		this.value2 = value2;
	}

	/**
	 * Returns the first value.
	 *
	 * @return the first value
	 */
	public T1 getFirst() {
		return value1;
	}

	/**
	 * Returns the second value.
	 *
	 * @return the second value
	 */
	public T2 getSecond() {
		return value2;
	}

	@Override
	public String toString() {
		StringBuilder returnString = new StringBuilder("<");

		if (value1 != null) {
			returnString.append(value1.toString());
		} else {
			returnString.append("<null>");
		}

		returnString.append(", ");

		if (value2 != null) {
			returnString.append(value2.toString());
		} else {
			returnString.append("<null>");
		}

		returnString.append(">");
		return returnString.toString();
	}
}
