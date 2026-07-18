package de.soderer.utilities.db.data;

public enum DbVendor {
	Oracle("oracle.jdbc.OracleDriver", 1521, "SELECT 1 FROM DUAL"),
	MySQL("com.mysql.cj.jdbc.Driver", 3306, "SELECT 1"),
	MariaDB("org.mariadb.jdbc.Driver", 3306, "SELECT 1"),
	PostgreSQL("org.postgresql.Driver", 5432, "SELECT 1"),
	Firebird("org.firebirdsql.jdbc.FBDriver", 3050, "SELECT 1 FROM RDB$RELATION_FIELDS ROWS 1"),
	SQLite("org.sqlite.JDBC", 0, "SELECT 1"),
	Derby("org.apache.derby.jdbc.EmbeddedDriver", 0, "SELECT 1 FROM SYSIBM.SYSDUMMY1"),
	HSQL("org.hsqldb.jdbc.JDBCDriver", 0, "SELECT 1"),
	Cassandra("com.simba.cassandra.jdbc42.Driver", 9042, ""),
	MsSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "SELECT 1");

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

	public String getDriverClassName() {
		return driverClassName;
	}

	public int getDefaultPort() {
		return defaultPort;
	}

	public String getTestStatement() {
		return testStatement;
	}
}
