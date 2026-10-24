package de.soderer.utilities.db.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

/**
 * Tests of the case insensitive collections, {@link MultiValueCaseInsensitiveOrderedMap} and {@link Tuple}.
 */
@SuppressWarnings("static-method")
public class DbUtilitiesCollectionsTest {
	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testCaseInsensitiveMap() {
		final CaseInsensitiveMap<Integer> map = CaseInsensitiveMap.create();
		assertNull(map.put("Key", 1));
		assertEquals(1, map.get("KEY"));
		assertTrue(map.containsKey("key"));
		assertEquals(1, map.keySet().size());
		assertEquals("key", map.entrySet().iterator().next().getKey());
		assertEquals(1, map.put("kEY", 2));
		assertEquals(1, map.size());

		assertEquals(5, map.getOrDefault("Missing", 5));
		assertEquals(2, map.getOrDefault("KEY", 5));
		assertEquals(2, map.putIfAbsent("KEY", 3));
		assertNull(map.putIfAbsent("Other", 3));
		assertEquals(3, map.computeIfAbsent("NEW", k -> Integer.valueOf(k.length())));
		assertEquals(4, map.computeIfPresent("new", (k, v) -> Integer.valueOf(v.intValue() + 1)));
		assertEquals(14, map.compute("NeW", (k, v) -> Integer.valueOf(v.intValue() + 10)));
		assertEquals(24, map.merge("NEW", 10, Integer::sum));
		assertEquals(24, map.replace("new", 25));
		assertTrue(map.replace("NEW", 25, 26));
		assertFalse(map.replace("NEW", 25, 27));
		assertFalse(map.remove("NEW", 99));
		assertTrue(map.remove("NEW", 26));
		assertEquals(3, map.remove("OTHER"));
		assertNull(map.remove("OTHER"));

		// Null and non-string keys
		map.put(null, 7);
		assertEquals(7, map.get(null));
		assertNull(map.get(Integer.valueOf(1)));
		assertFalse(map.containsKey(new Object()));

		final Map<String, Integer> source = new HashMap<>();
		source.put("A", 1);
		source.put("b", 2);
		final CaseInsensitiveMap<Integer> copy = new CaseInsensitiveMap<>(source);
		assertEquals(1, copy.get("a"));
		assertEquals(2, copy.get("B"));
		copy.putAll(Map.of("C", 3));
		assertEquals(3, copy.get("c"));
		assertTrue(new CaseInsensitiveMap<String>(4).isEmpty());
		assertTrue(new CaseInsensitiveMap<String>(4, 0.5f).isEmpty());
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testCaseInsensitiveLinkedMap() {
		final CaseInsensitiveLinkedMap<Integer> map = CaseInsensitiveLinkedMap.create();
		map.put("Zeta", 1);
		map.put("Alpha", 2);
		map.put("ZETA", 3);
		assertEquals(List.of("zeta", "alpha"), new ArrayList<>(map.keySet()));
		assertEquals(3, map.get("zeta"));
		assertTrue(map.containsKey("ALPHA"));
		assertEquals(2, map.entrySet().size());

		assertEquals(9, map.getOrDefault("missing", 9));
		assertNull(map.putIfAbsent("Beta", 4));
		assertEquals(4, map.putIfAbsent("BETA", 5));
		assertEquals(5, map.computeIfAbsent("Gamma", String::length));
		assertEquals(6, map.computeIfPresent("GAMMA", (k, v) -> Integer.valueOf(v.intValue() + 1)));
		assertEquals(7, map.compute("gamma", (k, v) -> Integer.valueOf(v.intValue() + 1)));
		assertEquals(8, map.merge("GAMMA", 1, Integer::sum));
		assertEquals(8, map.replace("Gamma", 9));
		assertTrue(map.replace("GAMMA", 9, 10));
		assertTrue(map.remove("gamma", 10));
		assertEquals(4, map.remove("BETA"));
		map.put(null, 0);
		assertEquals(0, map.get(null));
		assertNull(map.get(Integer.valueOf(3)));

		final CaseInsensitiveLinkedMap<Integer> copy = new CaseInsensitiveLinkedMap<>(Map.of("X", 1));
		assertEquals(1, copy.get("x"));
		copy.putAll(Map.of("Y", 2));
		assertEquals(2, copy.get("y"));
		assertTrue(new CaseInsensitiveLinkedMap<Integer>(2).isEmpty());
		assertTrue(new CaseInsensitiveLinkedMap<Integer>(2, 0.75f).isEmpty());

		// Access order
		final CaseInsensitiveLinkedMap<Integer> accessOrderedMap = new CaseInsensitiveLinkedMap<>(4, 0.75f, true);
		accessOrderedMap.put("a", 1);
		accessOrderedMap.put("b", 2);
		accessOrderedMap.get("A");
		assertEquals(List.of("b", "a"), new ArrayList<>(accessOrderedMap.keySet()));
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testCaseInsensitiveSet() {
		final CaseInsensitiveSet set = new CaseInsensitiveSet(new String[] { "One", "TWO" });
		assertTrue(set.contains("one"));
		assertTrue(set.contains("Two"));
		assertFalse(set.add("ONE"));
		assertTrue(set.add("three"));
		assertFalse(set.contains(Integer.valueOf(1)));
		assertTrue(set.add(null));
		assertTrue(set.contains(null));
		assertTrue(set.remove("THREE"));
		assertFalse(set.remove("three"));

		// removeAll and retainAll compare case insensitively
		final CaseInsensitiveSet removeSet = new CaseInsensitiveSet(Arrays.asList("A", "B", "C"));
		assertTrue(removeSet.removeAll(List.of("a", "x")));
		assertFalse(removeSet.removeAll(List.of("x")));
		assertEquals(2, removeSet.size());
		assertTrue(removeSet.retainAll(List.of("B")));
		assertFalse(removeSet.retainAll(List.of("b")));
		assertEquals(1, removeSet.size());
		assertTrue(removeSet.contains("b"));

		// Removing all items of itself must not fail
		assertTrue(removeSet.removeAll(removeSet));
		assertTrue(removeSet.isEmpty());

		assertTrue(new CaseInsensitiveSet().isEmpty());
		assertTrue(new CaseInsensitiveSet(8).isEmpty());
		assertTrue(new CaseInsensitiveSet(8, 0.5f).isEmpty());
	}

	@Test
	public void testMultiValueCaseInsensitiveOrderedMap() {
		final MultiValueCaseInsensitiveOrderedMap<String> map = MultiValueCaseInsensitiveOrderedMap.create();
		assertNull(map.put("Key", "a"));
		assertEquals(List.of("a"), map.put("KEY", "b"));
		map.put("Other", "c");
		assertEquals(List.of("a", "b"), map.get("key"));
		assertTrue(map.get("missing").isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> map.get("key").add("x"));
		assertEquals(List.of("key", "other"), new ArrayList<>(map.keySet()));
		assertEquals(2, map.values().size());
		int valueCount = 0;
		for (final Entry<String, ArrayList<String>> entry : map.entrySet()) {
			valueCount += entry.getValue().size();
		}
		assertEquals(3, valueCount);

		assertFalse(map.remove("missing", "a"));
		assertFalse(map.remove("KEY", "x"));
		assertTrue(map.remove("KEY", "a"));
		assertTrue(map.remove("key", "b"));
		// Empty value lists are removed
		assertFalse(map.keySet().contains("key"));
		assertEquals(List.of("c"), map.removeAll("OTHER"));
		assertTrue(map.removeAll("OTHER").isEmpty());
		assertTrue(new MultiValueCaseInsensitiveOrderedMap<Integer>().keySet().isEmpty());
	}

	@Test
	public void testTuple() {
		final Tuple<String, Integer> tuple = new Tuple<>("a", 1);
		assertEquals("a", tuple.getFirst());
		assertEquals(1, tuple.getSecond());
		assertEquals("<a, 1>", tuple.toString());
		assertEquals("<<null>, <null>>", new Tuple<>(null, null).toString());
	}
}
