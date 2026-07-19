package de.soderer.utilities.db.data;

import java.io.File;

import de.soderer.utilities.db.exception.DbDefinitionException;
import de.soderer.utilities.db.utilities.Utilities;

public class DbConnectionDefinition {
	/** The database vendor. */
	protected DbVendor dbVendor = null;

	/** The hostname. */
	protected String hostnameAndPort;

	/** The database name. */
	protected String dbName;

	/** The username. */
	protected String username;

	/** The password, may be entered interactivly */
	protected char[] password;

	protected boolean secureConnection = false;

	protected File trustStoreFile = null;

	protected char[] trustStorePassword = null;

	public DbConnectionDefinition() {
		// do nothing
	}

	public DbConnectionDefinition(final DbVendor dbVendor, final String hostnameAndPort, final String dbName, final String username, final char[] password) {
		this.dbVendor = dbVendor;
		this.hostnameAndPort = hostnameAndPort;
		this.dbName = dbName;
		this.username = username;
		this.password = password;
	}

	public DbConnectionDefinition(final DbVendor dbVendor, final String hostnameAndPort, final String dbName, final String username, final char[] password, final boolean secureConnection, final File trustStoreFile, final char[] trustStorePassword) {
		this.dbVendor = dbVendor;
		this.hostnameAndPort = hostnameAndPort;
		this.dbName = dbName;
		this.username = username;
		this.password = password;
		this.secureConnection = secureConnection;
		this.trustStoreFile = trustStoreFile;
		this.trustStorePassword = trustStorePassword;
	}

	public DbVendor getDbVendor() {
		return dbVendor;
	}

	public void setDbVendor(final DbVendor dbVendor) {
		this.dbVendor = dbVendor;
	}

	public DbConnectionDefinition withDbVendor(final DbVendor newDbVendor) {
		setDbVendor(newDbVendor);
		return this;
	}

	public String getHostnameAndPort() {
		return hostnameAndPort;
	}

	public void setHostnameAndPort(final String hostnameAndPort) {
		this.hostnameAndPort = hostnameAndPort;
	}

	public DbConnectionDefinition withHostnameAndPort(final String newHostnameAndPort) {
		setHostnameAndPort(newHostnameAndPort);
		return this;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(final String dbName) {
		this.dbName = dbName;
	}

	public DbConnectionDefinition withDbName(final String newDbName) {
		setDbName(newDbName);
		return this;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(final String username) {
		this.username = username;
	}

	public DbConnectionDefinition withUsername(final String newUsername) {
		setUsername(newUsername);
		return this;
	}

	public char[] getPassword() {
		return password;
	}

	public void setPassword(final char[] password) {
		this.password = password;
	}

	public DbConnectionDefinition withPassword(final char[] newPassword) {
		setPassword(newPassword);
		return this;
	}

	public boolean isSecureConnection() {
		return secureConnection;
	}

	public void setSecureConnection(final boolean secureConnection) {
		this.secureConnection = secureConnection;
	}

	public DbConnectionDefinition withSecureConnection(final boolean newSecureConnection) {
		setSecureConnection(newSecureConnection);
		return this;
	}

	public File getTrustStoreFile() {
		return trustStoreFile;
	}

	public void setTrustStoreFile(final File trustStoreFile) {
		this.trustStoreFile = trustStoreFile;
	}

	public DbConnectionDefinition withTrustStoreFile(final File newTrustStoreFile) {
		setTrustStoreFile(newTrustStoreFile);
		return this;
	}

	public char[] getTrustStorePassword() {
		return trustStorePassword;
	}

	public void setTrustStorePassword(final char[] trustStorePassword) {
		this.trustStorePassword = trustStorePassword;
	}

	public DbConnectionDefinition withTrustStorePassword(final char[] newTrustStorePassword) {
		setTrustStorePassword(newTrustStorePassword);
		return this;
	}

	public void checkParameters() throws Exception {
		if (dbVendor == DbVendor.SQLite) {
			if (Utilities.isNotBlank(hostnameAndPort)) {
				throw new DbDefinitionException("SQLite database connections do not support the hostname parameter");
			} else if (Utilities.isNotBlank(username)) {
				throw new DbDefinitionException("SQLite database connections do not support the username parameter");
			} else if (Utilities.isNotBlank(password)) {
				throw new DbDefinitionException("SQLite database connections do not support the password parameter");
			}
		} else if (dbVendor == DbVendor.Derby) {
			if (Utilities.isNotBlank(hostnameAndPort)) {
				throw new DbDefinitionException("Derby ddatabaseb connections do not support the hostname parameter");
			} else if (Utilities.isNotBlank(username)) {
				throw new DbDefinitionException("Derby database connections do not support the username parameter");
			} else if (Utilities.isNotBlank(password)) {
				throw new DbDefinitionException("Derby database connections do not support the password parameter");
			}
		} else if (dbVendor == DbVendor.HSQL) {
			dbName = Utilities.replaceUsersHome(dbName);
			if (dbName.startsWith("/")) {
				if (Utilities.isNotBlank(hostnameAndPort)) {
					throw new DbDefinitionException("HSQL file database connections do not support the hostname parameter");
				} else if (Utilities.isNotBlank(username)) {
					throw new DbDefinitionException("HSQL file database connections do not support the username parameter");
				} else if (Utilities.isNotBlank(password)) {
					throw new DbDefinitionException("HSQL file database connections do not support the password parameter");
				}
			}
		} else if (dbVendor == DbVendor.Cassandra) {
			if (Utilities.isBlank(hostnameAndPort)) {
				throw new DbDefinitionException("Missing or invalid hostname");
			}
			// username and password may be left empty
		} else {
			if (Utilities.isBlank(hostnameAndPort)) {
				throw new DbDefinitionException("Missing or invalid hostname");
			} else {
				final String[] hostParts = hostnameAndPort.split(":");
				if (hostParts.length == 2) {
					if (!Utilities.isInteger(hostParts[1])) {
						throw new DbDefinitionException("Invalid port in hostname: " + hostnameAndPort);
					}
				} else if (hostParts.length > 2) {
					throw new DbDefinitionException("Invalid hostname: " + hostnameAndPort);
				}
			}
			if (Utilities.isBlank(username)) {
				throw new DbDefinitionException("Missing or invalid username");
			}
			if (Utilities.isBlank(password)) {
				throw new DbDefinitionException("Missing or invalid empty password");
			}
		}
	}

	public void importParameters(final DbConnectionDefinition otherDbConnectionDefinition) {
		if (otherDbConnectionDefinition != null) {
			dbVendor = otherDbConnectionDefinition.getDbVendor();
			hostnameAndPort = otherDbConnectionDefinition.getHostnameAndPort();
			dbName = otherDbConnectionDefinition.getDbName();
			username = otherDbConnectionDefinition.getUsername();
			password = otherDbConnectionDefinition.getPassword();
			secureConnection = otherDbConnectionDefinition.isSecureConnection();
			trustStoreFile = otherDbConnectionDefinition.getTrustStoreFile();
			trustStorePassword = otherDbConnectionDefinition.getTrustStorePassword();
		} else {
			dbVendor = null;
			hostnameAndPort = null;
			dbName = null;
			username = null;
			password = null;
			secureConnection = false;
			trustStoreFile = null;
			trustStorePassword = null;
		}
	}
}
