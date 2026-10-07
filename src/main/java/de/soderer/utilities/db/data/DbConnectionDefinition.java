package de.soderer.utilities.db.data;

import java.io.File;

import de.soderer.utilities.db.exception.DbDefinitionException;
import de.soderer.utilities.db.utilities.Utilities;

/**
 * Parameters of a database connection, used by {@link de.soderer.utilities.db.DbUtilities#createConnection(DbConnectionDefinition, boolean)}.
 */
public class DbConnectionDefinition {
	/** The database vendor. */
	protected DbVendor dbVendor = null;

	/** The hostname with optional port, e.g. "localhost:5432". */
	protected String hostnameAndPort;

	/** The database name. */
	protected String dbName;

	/** The username. */
	protected String username;

	/** The password, may be entered interactively. */
	protected char[] password;

	/**
	 * Use a secure (TLS) connection.
	 */
	protected boolean secureConnection = false;

	/**
	 * Optional JKS truststore file for secure connections.
	 */
	protected File trustStoreFile = null;

	/**
	 * Optional password of the truststore file.
	 */
	protected char[] trustStorePassword = null;

	/**
	 * Creates a new empty connection definition.
	 */
	public DbConnectionDefinition() {
		// do nothing
	}

	/**
	 * Creates a new connection definition for an unsecured connection.
	 *
	 * @param dbVendor database vendor
	 * @param hostnameAndPort hostname with optional port, e.g. "localhost:5432" (not used for file databases)
	 * @param dbName database name, or the path for file databases
	 * @param username username
	 * @param password password
	 */
	public DbConnectionDefinition(final DbVendor dbVendor, final String hostnameAndPort, final String dbName, final String username, final char[] password) {
		this.dbVendor = dbVendor;
		this.hostnameAndPort = hostnameAndPort;
		this.dbName = dbName;
		this.username = username;
		this.password = password;
	}

	/**
	 * Creates a new connection definition.
	 *
	 * @param dbVendor database vendor
	 * @param hostnameAndPort hostname with optional port, e.g. "localhost:5432" (not used for file databases)
	 * @param dbName database name, or the path for file databases
	 * @param username username
	 * @param password password
	 * @param secureConnection true to use a secure (TLS) connection
	 * @param trustStoreFile optional JKS truststore file for secure connections
	 * @param trustStorePassword optional password of the truststore file
	 */
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

	/**
	 * Returns the database vendor.
	 *
	 * @return the database vendor
	 */
	public DbVendor getDbVendor() {
		return dbVendor;
	}

	/**
	 * Sets the database vendor.
	 *
	 * @param dbVendor the database vendor
	 */
	public void setDbVendor(final DbVendor dbVendor) {
		this.dbVendor = dbVendor;
	}

	/**
	 * Sets the database vendor and returns this instance for method chaining.
	 *
	 * @param newDbVendor the database vendor
	 * @return this instance
	 */
	public DbConnectionDefinition withDbVendor(final DbVendor newDbVendor) {
		setDbVendor(newDbVendor);
		return this;
	}

	/**
	 * Returns the hostname and port.
	 *
	 * @return the hostname and port
	 */
	public String getHostnameAndPort() {
		return hostnameAndPort;
	}

	/**
	 * Sets the hostname and port.
	 *
	 * @param hostnameAndPort the hostname and port
	 */
	public void setHostnameAndPort(final String hostnameAndPort) {
		this.hostnameAndPort = hostnameAndPort;
	}

	/**
	 * Sets the hostname and port and returns this instance for method chaining.
	 *
	 * @param newHostnameAndPort the hostname and port
	 * @return this instance
	 */
	public DbConnectionDefinition withHostnameAndPort(final String newHostnameAndPort) {
		setHostnameAndPort(newHostnameAndPort);
		return this;
	}

	/**
	 * Returns the database name.
	 *
	 * @return the database name
	 */
	public String getDbName() {
		return dbName;
	}

	/**
	 * Sets the database name.
	 *
	 * @param dbName the database name
	 */
	public void setDbName(final String dbName) {
		this.dbName = dbName;
	}

	/**
	 * Sets the database name and returns this instance for method chaining.
	 *
	 * @param newDbName the database name
	 * @return this instance
	 */
	public DbConnectionDefinition withDbName(final String newDbName) {
		setDbName(newDbName);
		return this;
	}

	/**
	 * Returns the username.
	 *
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the username.
	 *
	 * @param username the username
	 */
	public void setUsername(final String username) {
		this.username = username;
	}

	/**
	 * Sets the username and returns this instance for method chaining.
	 *
	 * @param newUsername the username
	 * @return this instance
	 */
	public DbConnectionDefinition withUsername(final String newUsername) {
		setUsername(newUsername);
		return this;
	}

	/**
	 * Returns the password.
	 *
	 * @return the password
	 */
	public char[] getPassword() {
		return password;
	}

	/**
	 * Sets the password.
	 *
	 * @param password the password
	 */
	public void setPassword(final char[] password) {
		this.password = password;
	}

	/**
	 * Sets the password and returns this instance for method chaining.
	 *
	 * @param newPassword the password
	 * @return this instance
	 */
	public DbConnectionDefinition withPassword(final char[] newPassword) {
		setPassword(newPassword);
		return this;
	}

	/**
	 * Returns whether a secure (TLS) connection is used.
	 *
	 * @return true if a secure connection is used
	 */
	public boolean isSecureConnection() {
		return secureConnection;
	}

	/**
	 * Sets the secure connection.
	 *
	 * @param secureConnection the secure connection
	 */
	public void setSecureConnection(final boolean secureConnection) {
		this.secureConnection = secureConnection;
	}

	/**
	 * Sets the secure connection and returns this instance for method chaining.
	 *
	 * @param newSecureConnection the secure connection
	 * @return this instance
	 */
	public DbConnectionDefinition withSecureConnection(final boolean newSecureConnection) {
		setSecureConnection(newSecureConnection);
		return this;
	}

	/**
	 * Returns the trust store file.
	 *
	 * @return the trust store file
	 */
	public File getTrustStoreFile() {
		return trustStoreFile;
	}

	/**
	 * Sets the trust store file.
	 *
	 * @param trustStoreFile the trust store file
	 */
	public void setTrustStoreFile(final File trustStoreFile) {
		this.trustStoreFile = trustStoreFile;
	}

	/**
	 * Sets the trust store file and returns this instance for method chaining.
	 *
	 * @param newTrustStoreFile the trust store file
	 * @return this instance
	 */
	public DbConnectionDefinition withTrustStoreFile(final File newTrustStoreFile) {
		setTrustStoreFile(newTrustStoreFile);
		return this;
	}

	/**
	 * Returns the trust store password.
	 *
	 * @return the trust store password
	 */
	public char[] getTrustStorePassword() {
		return trustStorePassword;
	}

	/**
	 * Sets the trust store password.
	 *
	 * @param trustStorePassword the trust store password
	 */
	public void setTrustStorePassword(final char[] trustStorePassword) {
		this.trustStorePassword = trustStorePassword;
	}

	/**
	 * Sets the trust store password and returns this instance for method chaining.
	 *
	 * @param newTrustStorePassword the trust store password
	 * @return this instance
	 */
	public DbConnectionDefinition withTrustStorePassword(final char[] newTrustStorePassword) {
		setTrustStorePassword(newTrustStorePassword);
		return this;
	}

	/**
	 * Checks whether the parameters are complete and valid for the database vendor.
	 * File databases (SQLite, Derby, HSQL file) must not have hostname, username or password.
	 *
	 * @throws Exception ({@link DbDefinitionException}) if a parameter is missing or invalid
	 */
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
				throw new DbDefinitionException("Derby database connections do not support the hostname parameter");
			} else if (Utilities.isNotBlank(username)) {
				throw new DbDefinitionException("Derby database connections do not support the username parameter");
			} else if (Utilities.isNotBlank(password)) {
				throw new DbDefinitionException("Derby database connections do not support the password parameter");
			}
		} else if (dbVendor == DbVendor.HSQL) {
			dbName = Utilities.replaceUsersHome(dbName);
			if (Utilities.isBlank(dbName)) {
				throw new DbDefinitionException("Missing or invalid dbName");
			} else if (dbName.startsWith("/")) {
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

	/**
	 * Copies all parameters of another connection definition into this one.
	 *
	 * @param otherDbConnectionDefinition definition to copy, or null to reset all parameters
	 */
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
