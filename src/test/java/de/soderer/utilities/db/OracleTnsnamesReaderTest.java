package de.soderer.utilities.db;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.oracle.OracleTnsMapValue;
import de.soderer.utilities.db.oracle.OracleTnsStringValue;
import de.soderer.utilities.db.oracle.OracleTnsValue;
import de.soderer.utilities.db.oracle.OracleTnsnamesReader;
import de.soderer.utilities.db.utilities.MultiValueCaseInsensitiveOrderedMap;

@SuppressWarnings("static-method")
public class OracleTnsnamesReaderTest {
	@Test
	public void testExample() throws Exception {
		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(OracleTnsnamesReader.EXAMPLE.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME"));

			Assertions.assertEquals(OracleTnsnamesReader.EXAMPLE, OracleTnsnamesReader.format(tnsnamesEntries));
		}
	}

	@Test
	public void testSimple() throws Exception {
		final String tnsNamesContent =
				"TNSNAME=" + "(1=1)";

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME"));
		}
	}

	@Test
	public void testSimple2() throws Exception {
		final String tnsNamesContent =
				"TNSNAME=" + "(B=(A=1))";

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME"));
		}
	}

	@Test
	public void testSimple3() throws Exception {
		final String tnsNamesContent =
				"TNSNAME=" + "(C=(A=1)(B=1))";

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME"));
		}
	}

	@Test
	public void testSimple4() throws Exception {
		final String tnsNamesContent =
				"TNSNAME=" + "(D="
						+ "(A=1)"
						+ "(B=2)"
						+ "(C=3)"
						+ ")";

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME"));
		}
	}

	@Test
	public void testSimple5() throws Exception {
		final String tnsNamesContent =
				"TNSNAME1=" + "(A=1)\n"
						+ "TNSNAME2=" + "(B=2)";

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME1"));

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME2"));
		}
	}

	@Test
	public void test() throws Exception {
		final String description1 =
				"TNSNAME1=" + "(DESCRIPTION="
						+ "(ADDRESS=(PROTOCOL=TCPS)(HOST=serverHost1)(PORT=1521))"
						+ "(CONNECT_DATA=(SERVICE_NAME=serviceName1))"
						+ "(SECURITY=(ssl_server_cert_dn=\"trusted CN1\"))"
						+ ")";

		final String description2 =
				"TNSNAME2 = " + "(DESCRIPTION = " + "\n"
						+ "(ADDRESS = (PROTOCOL = TCP)(HOST = serverHost2)(PORT = 1522))" + "\n"
						+ "(CONNECT_DATA = (SERVICE_NAME = serviceName2))" + "\n"
						+ ")" + "\n";

		final String description3 =
				"TNSNAME3 = " + "(DESCRIPTION = " + "\n"
						+ "(ADDRESSLIST = " + "\n"
						+ "(ADDRESS = (PROTOCOL = TCP)(HOST = serverHost31)(PORT = 15231))" + "\n"
						+ "(ADDRESS = (PROTOCOL = TCP)(HOST = serverHost32)(PORT = 15232))" + "\n"
						+ ")" + "\n"
						+ "(CONNECT_DATA = (SERVICE_NAME = serviceName3))" + "\n"
						+ ")" + "\n";

		final String description4 =
				"TNSNAME4 = " + "(DESCRIPTION = " + "\n"
						+ "(ADDRESSLIST = " + "\n"
						+ "(ADDRESS = (PROTOCOL = TCP)(HOST = serverHost4)(PORT = 1524))" + "\n"
						+ ")" + "\n"
						+ "(CONNECT_DATA = (SID = sidName4))" + "\n"
						+ ")" + "\n";

		final String tnsNamesContent =
				description1
				+ description2 + "\n"
				+ description3 + "\n"
				+ description4;

		try (OracleTnsnamesReader reader = new OracleTnsnamesReader(new ByteArrayInputStream(tnsNamesContent.getBytes(StandardCharsets.UTF_8)))) {
			final Map<String, OracleTnsMapValue> tnsnamesEntries = reader.read();
			Assertions.assertNotNull(tnsnamesEntries);

			Assertions.assertNotNull(tnsnamesEntries.get("tnsname1"));
			final List<OracleTnsValue> descriptions = tnsnamesEntries.get("tnsname1").getValue().get("description");
			Assertions.assertTrue(descriptions.size() == 1);
			Assertions.assertTrue(descriptions.get(0) instanceof OracleTnsMapValue);
			final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> description_1 = ((OracleTnsMapValue) descriptions.get(0)).getValue();

			final List<OracleTnsValue> address = description_1.get("address");
			Assertions.assertTrue(address.size() == 1);
			Assertions.assertTrue(address.get(0) instanceof OracleTnsMapValue);
			final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> address1 = ((OracleTnsMapValue) address.get(0)).getValue();

			final List<OracleTnsValue> protocol = address1.get("protocol");
			Assertions.assertTrue(protocol.size() == 1);
			Assertions.assertTrue(protocol.get(0) instanceof OracleTnsStringValue);
			final String protocol1 = ((OracleTnsStringValue) protocol.get(0)).getValue();
			Assertions.assertEquals("TCPS", protocol1);

			final List<OracleTnsValue> host = address1.get("host");
			Assertions.assertTrue(host.size() == 1);
			Assertions.assertTrue(host.get(0) instanceof OracleTnsStringValue);
			final String host1 = ((OracleTnsStringValue) host.get(0)).getValue();
			Assertions.assertEquals("serverHost1", host1);

			final List<OracleTnsValue> port = address1.get("port");
			Assertions.assertTrue(port.size() == 1);
			Assertions.assertTrue(port.get(0) instanceof OracleTnsStringValue);
			final String port1 = ((OracleTnsStringValue) port.get(0)).getValue();
			Assertions.assertEquals("1521", port1);

			final List<OracleTnsValue> security = description_1.get("security");
			Assertions.assertTrue(security.size() == 1);
			Assertions.assertTrue(security.get(0) instanceof OracleTnsMapValue);
			final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> security1 = ((OracleTnsMapValue) security.get(0)).getValue();

			final List<OracleTnsValue> sslServerCertDN = security1.get("ssl_server_cert_dn");
			Assertions.assertTrue(sslServerCertDN.size() == 1);
			Assertions.assertTrue(sslServerCertDN.get(0) instanceof OracleTnsStringValue);
			final String sslServerCertDN1 = ((OracleTnsStringValue) sslServerCertDN.get(0)).getValue();
			Assertions.assertEquals("trusted CN1", sslServerCertDN1);

			Assertions.assertEquals("(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=serverHost1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=serviceName1))(SECURITY=(SSL_SERVER_CERT_DN=\"trusted CN1\")))",
					OracleTnsnamesReader.getSingleLineFormatedTnsEntryData(tnsnamesEntries.get("tnsname1")));

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME2"));

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME3"));

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME4"));

			Assertions.assertNotNull(tnsnamesEntries.get("TNSNAME4"));
			final List<OracleTnsValue> descriptions_4 = tnsnamesEntries.get("TNSNAME4").getValue().get("description");
			Assertions.assertTrue(descriptions_4.size() == 1);
			Assertions.assertTrue(descriptions_4.get(0) instanceof OracleTnsMapValue);
			final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> description_4 = ((OracleTnsMapValue) descriptions_4.get(0)).getValue();

			final List<OracleTnsValue> connectData = description_4.get("connect_data");
			Assertions.assertTrue(connectData.size() == 1);
			Assertions.assertTrue(connectData.get(0) instanceof OracleTnsMapValue);
			final MultiValueCaseInsensitiveOrderedMap<OracleTnsValue> connectdata4 = ((OracleTnsMapValue) connectData.get(0)).getValue();

			final List<OracleTnsValue> sid = connectdata4.get("SID");
			Assertions.assertTrue(sid.size() == 1);
			Assertions.assertTrue(sid.get(0) instanceof OracleTnsStringValue);
			final String sid4 = ((OracleTnsStringValue) sid.get(0)).getValue();
			Assertions.assertEquals("sidName4", sid4);
		}
	}
}
