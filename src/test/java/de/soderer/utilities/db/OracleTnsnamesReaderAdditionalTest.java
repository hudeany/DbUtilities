package de.soderer.utilities.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.oracle.OracleTnsMapValue;
import de.soderer.utilities.db.oracle.OracleTnsStringValue;
import de.soderer.utilities.db.oracle.OracleTnsValue;
import de.soderer.utilities.db.oracle.OracleTnsnamesReader;
import de.soderer.utilities.db.utilities.MultiValueCaseInsensitiveOrderedMap;

/**
 * Additional tests of {@link OracleTnsnamesReader}: comments, quoted values, single line formatting and errors.
 */
@SuppressWarnings("static-method")
public class OracleTnsnamesReaderAdditionalTest {
	private static Map<String, OracleTnsMapValue> read(final String data) throws Exception {
		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(data.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.ISO_8859_1)) {
			return reader.read();
		}
	}

	private static String readError(final String data) {
		return assertThrows(Exception.class, () -> read(data)).getMessage();
	}

	@Test
	public void testCommentsAndQuotedValues() throws Exception {
		final Map<String, OracleTnsMapValue> entries = read("# Comment line\n"
				+ "# Second comment (with brackets)\n"
				+ "DB1 =\n"
				+ "  (DESCRIPTION =\n"
				+ "    (ADDRESS = (PROTOCOL = TCP)(HOST = 'host1')(PORT = 1521))\n"
				+ "    (CONNECT_DATA = (SERVICE_NAME = \"svc 1\"))\n"
				+ "  )\n"
				+ "\n"
				+ "  # indented comment\n"
				+ "db2=(DESCRIPTION=(ADDRESS=(HOST=h2)))\n");
		assertEquals(2, entries.size());
		final OracleTnsMapValue description = (OracleTnsMapValue) entries.get("db1").getValue().get("description").get(0);
		final OracleTnsMapValue address = (OracleTnsMapValue) description.getValue().get("ADDRESS").get(0);
		assertEquals("host1", ((OracleTnsStringValue) address.getValue().get("host").get(0)).getValue());
		final OracleTnsMapValue connectData = (OracleTnsMapValue) description.getValue().get("connect_data").get(0);
		assertEquals("svc 1", ((OracleTnsStringValue) connectData.getValue().get("SERVICE_NAME").get(0)).getValue());

		assertEquals("(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=\"svc 1\")))",
				OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(entries.get("DB1")));
		assertEquals("(DESCRIPTION=(ADDRESS=(HOST=h2)))", OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(entries.get("DB2")));
		assertEquals("plain", OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(new OracleTnsStringValue("plain")));

		final String formatted = OracleTnsnamesReader.format(entries);
		assertTrue(formatted.startsWith("DB1=\n\t(DESCRIPTION=\n\t\t(ADDRESS=\n\t\t\t(PROTOCOL=TCP)\n"), formatted);
		assertTrue(formatted.contains("\t\t\t(SERVICE_NAME=\"svc 1\")\n"), formatted);
		assertTrue(formatted.contains("\nDB2=\n"), formatted);
		// The formatted data can be read again
		assertEquals(OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(entries.get("DB1")), OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(read(formatted).get("DB1")));
	}

	@Test
	public void testFormatTopLevelValues() throws Exception {
		final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> values = new MultiValueCaseInsensitiveOrderedMap<>();
		values.put("simple", new OracleTnsStringValue("value"));
		values.put("special", new OracleTnsStringValue("a=b"));
		values.put("special", new OracleTnsStringValue("x(y)"));
		final Map<String, OracleTnsMapValue> data = new LinkedHashMap<>();
		data.put("entry", new OracleTnsMapValue(values));
		assertEquals("ENTRY=\n\t(SIMPLE=value)\n\t(SPECIAL=\"a=b\")\n\t(SPECIAL=\"x(y)\")\n", OracleTnsnamesReader.format(data));
		assertEquals("(SIMPLE=value)(SPECIAL=\"a=b\")(SPECIAL=\"x(y)\")", OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(data.get("entry")));

		final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> nested = new MultiValueCaseInsensitiveOrderedMap<>();
		nested.put("tab", new OracleTnsStringValue("a\tb"));
		nested.put("newline", new OracleTnsStringValue("a\nb"));
		nested.put("cr", new OracleTnsStringValue("a\rb"));
		final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> outer = new MultiValueCaseInsensitiveOrderedMap<>();
		outer.put("inner", new OracleTnsMapValue(nested));
		final Map<String, OracleTnsMapValue> nestedData = new LinkedHashMap<>();
		nestedData.put("n", new OracleTnsMapValue(outer));
		assertEquals("N=\n\t(INNER=\n\t\t(TAB=\"a\tb\")\n\t\t(NEWLINE=\"a\nb\")\n\t\t(CR=\"a\rb\")\n\t)\n", OracleTnsnamesReader.format(nestedData));
		assertEquals("(INNER=(TAB=\"a\tb\")(NEWLINE=\"a\nb\")(CR=\"a\rb\"))", OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(nestedData.get("n")));
	}

	@Test
	public void testUnknownValueTypes() {
		final OracleTnsValue unknownValue = () -> "unknown";
		assertThrows(Exception.class, () -> OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(unknownValue));

		final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> values = new MultiValueCaseInsensitiveOrderedMap<>();
		values.put("x", unknownValue);
		final OracleTnsMapValue mapValue = new OracleTnsMapValue(values);
		assertThrows(Exception.class, () -> OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(mapValue));
		final Map<String, OracleTnsMapValue> data = new LinkedHashMap<>();
		data.put("e", mapValue);
		assertThrows(Exception.class, () -> OracleTnsnamesReader.format(data));

		final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> outer = new MultiValueCaseInsensitiveOrderedMap<>();
		outer.put("inner", mapValue);
		final Map<String, OracleTnsMapValue> nestedData = new LinkedHashMap<>();
		nestedData.put("e", new OracleTnsMapValue(outer));
		assertThrows(Exception.class, () -> OracleTnsnamesReader.format(nestedData));
	}

	@Test
	public void testReadErrors() throws Exception {
		assertNull(read(""));
		assertNull(read("# only a comment\n"));
		assertEquals("Multiple entry for the same TNS entry name: DB", readError("db=(A=1)\nDB=(A=2)"));
		assertEquals("Premature end of data when reading TNS value", readError("db=value"));
		assertEquals("Unexpected data when reading TNS value", readError("db=(A=(B=1) x)"));
		assertThrows(IllegalStateException.class, () -> new OracleTnsnamesReader(null));

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream("db=(A=1)".getBytes(StandardCharsets.UTF_8)))) {
			assertEquals(1, reader.read().size());
			assertTrue(assertThrows(Exception.class, reader::read).getMessage().contains("already initialized"));
		}
	}
}
