package de.soderer.utilities.db.data;

/**
 * Supported database vendors with their JDBC driver class, default port and connection test statement.
 */
public enum DbVendor {
	/**
	 * Oracle database
	 */
	Oracle("oracle.jdbc.OracleDriver", 1521, "SELECT 1 FROM DUAL"),
	/**
	 * MySQL database
	 */
	MySQL("com.mysql.cj.jdbc.Driver", 3306, "SELECT 1"),
	/**
	 * MariaDB database
	 */
	MariaDB("org.mariadb.jdbc.Driver", 3306, "SELECT 1"),
	/**
	 * PostgreSQL database
	 */
	PostgreSQL("org.postgresql.Driver", 5432, "SELECT 1"),
	/**
	 * Firebird database
	 */
	Firebird("org.firebirdsql.jdbc.FBDriver", 3050, "SELECT 1 FROM RDB$RELATION_FIELDS ROWS 1"),
	/**
	 * SQLite file database
	 */
	SQLite("org.sqlite.JDBC", 0, "SELECT 1"),
	/**
	 * Apache Derby embedded database
	 */
	Derby("org.apache.derby.jdbc.EmbeddedDriver", 0, "SELECT 1 FROM SYSIBM.SYSDUMMY1"),
	/**
	 * HyperSQL (HSQLDB) file, memory or server database
	 */
	HSQL("org.hsqldb.jdbc.JDBCDriver", 0, "SELECT 1"),
	/**
	 * Apache Cassandra database (via Simba JDBC driver)
	 */
	Cassandra("com.simba.cassandra.jdbc42.Driver", 9042, ""),
	/**
	 * Microsoft SQL Server database
	 */
	MsSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "SELECT 1");

	/**
	 * Returns the database vendor by its name (case-insensitive). The aliases "postgres" and "hypersql" are also accepted.
	 *
	 * @param dbVendorName name of the database vendor
	 * @return the database vendor
	 * @throws Exception if there is no database vendor with this name
	 */
	public static DbVendor getDbVendorByName(final String dbVendorName) throws Exception {
		for (final DbVendor dbVendor : DbVendor.values()) {
			if (dbVendor.toString().equalsIgnoreCase(dbVendorName)) {
				return dbVendor;
			}
		}
		if ("postgres".equalsIgnoreCase(dbVendorName)) {
			return DbVendor.PostgreSQL;
		} else if ("hypersql".equalsIgnoreCase(dbVendorName)) {
			return DbVendor.HSQL;
		} else {
			throw new Exception("Invalid database vendor: " + dbVendorName);
		}
	}

	private final String driverClassName;
	private final int defaultPort;
	private final String testStatement;

	DbVendor(final String driverClassName, final int defaultPort, final String testStatement) {
		this.driverClassName = driverClassName;
		this.defaultPort = defaultPort;
		this.testStatement = testStatement;
	}

	/**
	 * Returns the class name of the JDBC driver.
	 *
	 * @return the driver class name
	 */
	public String getDriverClassName() {
		return driverClassName;
	}

	/**
	 * Returns the default TCP port of the database server.
	 *
	 * @return the default port, or 0 for file and embedded databases
	 */
	public int getDefaultPort() {
		return defaultPort;
	}

	/**
	 * Returns a simple SQL statement to test an open connection.
	 *
	 * @return the test statement (empty for Cassandra)
	 */
	public String getTestStatement() {
		return testStatement;
	}
}
