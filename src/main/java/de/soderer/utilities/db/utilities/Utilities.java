package de.soderer.utilities.db.utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * General helper methods for strings, collections, files, dates and network connections used by the database utilities.
 */
public class Utilities {
	/**
	 * Creates a new instance. All methods are static, so this is only needed for compatibility.
	 */
	public Utilities() {
		// Only static methods
	}

	/**
	 * Checks if a String is null or has length 0.
	 *
	 * @param value value to check
	 * @return true if the value is null or empty
	 */
	public static boolean isEmpty(final String value) {
		return value == null || value.length() == 0;
	}

	/**
	 * Checks if a String is not null and has a length greater than 0.
	 *
	 * @param value value to check
	 * @return true if the value is not empty
	 */
	public static boolean isNotEmpty(final String value) {
		return !isEmpty(value);
	}

	/**
	 * Checks if a collection is null or has no items.
	 *
	 * @param collection collection to check
	 * @return true if the collection is null or empty
	 */
	public static boolean isEmpty(final Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	/**
	 * Checks if a collection is not null and has at least one item.
	 *
	 * @param collection collection to check
	 * @return true if the collection is not empty
	 */
	public static boolean isNotEmpty(final Collection<?> collection) {
		return !isEmpty(collection);
	}

	/**
	 * Checks if a String is null, empty or contains only whitespace.
	 *
	 * @param value value to check
	 * @return true if the value is blank
	 */
	public static boolean isBlank(final String value) {
		return value == null || value.length() == 0 || value.trim().length() == 0;
	}

	/**
	 * Checks if a String contains at least one non whitespace character.
	 *
	 * @param value value to check
	 * @return true if the value is not blank
	 */
	public static boolean isNotBlank(final String value) {
		return !isBlank(value);
	}

	/**
	 * Checks if a char array is null or has length 0.
	 *
	 * @param value value to check
	 * @return true if the value is null or empty
	 */
	public static boolean isEmpty(final char[] value) {
		return value == null || value.length == 0;
	}

	/**
	 * Checks if a char array is not null and has a length greater than 0.
	 *
	 * @param value value to check
	 * @return true if the value is not empty
	 */
	public static boolean isNotEmpty(final char[] value) {
		return !isEmpty(value);
	}

	/**
	 * Checks if a char array is null, empty or contains only whitespace.
	 *
	 * @param value value to check
	 * @return true if the value is blank
	 */
	public static boolean isBlank(final char[] value) {
		if (value == null || value.length == 0) {
			return true;
		} else {
			for (final char character : value) {
				if (!Character.isWhitespace(character)) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Checks if a char array contains at least one non whitespace character.
	 *
	 * @param value value to check
	 * @return true if the value is not blank
	 */
	public static boolean isNotBlank(final char[] value) {
		return !isBlank(value);
	}

	/**
	 * Repeats a character.
	 *
	 * @param valueChar character to repeat
	 * @param count number of repetitions
	 * @return String of the repeated character
	 */
	public static String repeat(final char valueChar, final int count) {
		return repeat(Character.toString(valueChar), count, null);
	}

	/**
	 * Repeats a String without separator.
	 *
	 * @param value String to repeat
	 * @param count number of repetitions
	 * @return repeated String, or null if value is null
	 */
	public static String repeat(final String value, final int count) {
		return repeat(value, count, null);
	}

	/**
	 * Repeats a String with an optional separator between the repetitions.
	 *
	 * @param value String to repeat
	 * @param count number of repetitions
	 * @param separatorString separator between the repetitions, may be null
	 * @return repeated String, or null if value is null
	 */
	public static String repeat(final String value, final int count, final String separatorString) {
		if (value == null) {
			return null;
		} else if (value.length() == 0 || count == 0) {
			return "";
		} else {
			final StringBuilder returnValue = new StringBuilder();
			for (int i = 0; i < count; i++) {
				if (separatorString != null && returnValue.length() > 0) {
					returnValue.append(separatorString);
				}
				returnValue.append(value);
			}
			return returnValue.toString();
		}
	}

	/**
	 * Joins the characters of an array.
	 *
	 * @param array characters to join
	 * @param glue separator between the characters, may be null
	 * @return joined String, or null if the array is null
	 */
	public static String join(final char[] array, String glue) {
		if (array == null) {
			return null;
		} else if (array.length == 0) {
			return "";
		} else {
			if (glue == null) {
				glue = "";
			}

			final StringBuilder returnValue = new StringBuilder();
			boolean isFirst = true;
			for (final char nextChar : array) {
				if (!isFirst) {
					returnValue.append(glue);
				}
				returnValue.append(nextChar);
				isFirst = false;
			}
			return returnValue.toString();
		}
	}

	/**
	 * Joins the String representations of an array. Null items are represented as empty String.
	 *
	 * @param array items to join
	 * @param glue separator between the items, may be null
	 * @return joined String, or null if the array is null
	 */
	public static String join(final Object[] array, String glue) {
		if (array == null) {
			return null;
		} else if (array.length == 0) {
			return "";
		} else {
			if (glue == null) {
				glue = "";
			}

			final StringBuilder returnValue = new StringBuilder();
			boolean isFirst = true;
			for (Object object : array) {
				if (!isFirst) {
					returnValue.append(glue);
				}
				if (object == null) {
					object = "";
				}
				returnValue.append(object.toString());
				isFirst = false;
			}
			return returnValue.toString();
		}
	}

	/**
	 * Joins the String representations of an Iterable. Null items are represented as empty String.
	 *
	 * @param iterableObject items to join
	 * @param glue separator between the items, may be null
	 * @return joined String, or null if the iterable is null
	 */
	public static String join(final Iterable<?> iterableObject, String glue) {
		if (iterableObject == null) {
			return null;
		} else {
			if (glue == null) {
				glue = "";
			}

			final StringBuilder returnValue = new StringBuilder();
			boolean isFirst = true;
			for (Object object : iterableObject) {
				if (!isFirst) {
					returnValue.append(glue);
				}
				if (object == null) {
					object = "";
				}
				returnValue.append(object.toString());
				isFirst = false;
			}
			return returnValue.toString();
		}
	}

	/**
	 * Replaces a leading "~" and the placeholders "${HOME}" and "$HOME" by the user's home directory.
	 *
	 * @param filePath file path to process
	 * @return file path with replaced home directory, or null if filePath is null
	 */
	public static String replaceUsersHome(String filePath) {
		if (filePath == null) {
			return filePath;
		}
		final String homePath = System.getProperty("user.home");

		// "~" only stands for the home directory at the start of the path, otherwise it is a normal file name character
		if ("~".equals(filePath)) {
			filePath = homePath;
		} else if (filePath.startsWith("~/") || filePath.startsWith("~" + File.separator)) {
			filePath = homePath + filePath.substring(1);
		}

		return filePath
				.replace("${HOME}", homePath)
				.replace("$HOME", homePath);
	}

	/**
	 * Checks if the value is an integer number without decimals that fits into an {@code int}.
	 *
	 * @param value value to check
	 * @return true if the value can be parsed as {@code int}, false otherwise (also for null)
	 */
	public static boolean isInteger(final String value) {
		try {
			Integer.parseInt(value);
			return true;
		} catch (@SuppressWarnings("unused") final NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Null safe variant of {@link String#trim()}.
	 *
	 * @param value value to trim
	 * @return trimmed value, or null if value is null
	 */
	public static String trim(final String value) {
		if (value == null) {
			return null;
		} else {
			return value.trim();
		}
	}

	/**
	 * Removes all occurrences of a character from the start and the end of a String.
	 *
	 * @param value value to trim
	 * @param trimChar character to remove
	 * @return trimmed value, or null if value is null
	 */
	public static String trim(String value, final char trimChar) {
		while (value != null && value.startsWith(Character.toString(trimChar))) {
			value = value.substring(1);
		}

		while (value != null && value.endsWith(Character.toString(trimChar))) {
			value = value.substring(0, value.length() - 1);
		}

		return value;
	}

	/**
	 * Removes the surrounding string from both ends of the value, but only if it occurs on both ends.
	 * Only one occurrence is removed on each end.
	 *
	 * @param value value to trim
	 * @param surrounding string expected at the start and the end of the value
	 * @return the value without the surrounding string, or the unchanged value if it is not surrounded by it
	 */
	public static String trimSimultaneously(final String value, final String surrounding) {
		if (value == null) {
			return null;
		} else if (isEmpty(surrounding)) {
			return value;
		} else if (value.length() >= surrounding.length() * 2 && value.startsWith(surrounding) && value.endsWith(surrounding)) {
			return value.substring(surrounding.length(), value.length() - surrounding.length());
		} else {
			return value;
		}
	}

	/**
	 * Returns the items of a collection (like a set) as sorted list, with the given items placed first.
	 * The first items keep their given order, all other items are sorted by their natural order.
	 *
	 * @param <T> type of the items
	 * @param collection items to sort
	 * @param firstItems items to put at the start of the list in this order
	 * @return new sorted list
	 */
	@SafeVarargs
	public static <T extends Comparable<? super T>> List<T> sortButPutItemsFirst(final Collection<T> collection, final T... firstItems) {
		final List<T> firstItemsList = new ArrayList<>(Arrays.asList(firstItems));
		final List<T> list = new ArrayList<>(collection);
		Collections.sort(list, new Comparator<T>() {
			@Override
			public int compare(final T o1, final T o2) {
				if (o1.equals(o2)) {
					return 0;
				} else if (firstItemsList.contains(o1)) {
					if (firstItemsList.contains(o2)) {
						return firstItemsList.indexOf(o1) < firstItemsList.indexOf(o2) ? -1 : 1;
					} else {
						return -1;
					}
				} else if (firstItemsList.contains(o2)) {
					return 1;
				} else {
					return o1.compareTo(o2);
				}
			}
		});
		return list;
	}

	/**
	 * Splits a String at the separator characters, ignoring separators within single or double quoted parts.
	 * The parts are trimmed, empty parts are omitted.
	 *
	 * @param stringList String to split
	 * @param separatorChars separator characters
	 * @return trimmed non empty parts
	 */
	public static List<String> splitAndTrimListQuoted(final String stringList, final char... separatorChars) {
		final List<String> returnList = new ArrayList<>();
		StringBuilder nextLine = new StringBuilder();
		boolean quotedBySingleQoute = false;
		boolean quotedByDoubleQoute = false;
		for (final char nextChar : stringList.toCharArray()) {
			if ('\'' == nextChar) {
				if (!quotedBySingleQoute && !quotedByDoubleQoute) {
					quotedBySingleQoute = true;
				} else if (quotedBySingleQoute) {
					quotedBySingleQoute = false;
				}
			} else if ('"' == nextChar) {
				if (!quotedBySingleQoute && !quotedByDoubleQoute) {
					quotedByDoubleQoute = true;
				} else if (quotedByDoubleQoute) {
					quotedByDoubleQoute = false;
				}
			}

			boolean splitFound = false;
			for (final char separatorChar : separatorChars) {
				if (separatorChar == nextChar && !quotedBySingleQoute && !quotedByDoubleQoute) {
					final String line = nextLine.toString().trim();
					if (line.length() > 0) {
						returnList.add(line);
						splitFound = true;
					}
					nextLine = new StringBuilder();
					break;
				}
			}

			if (!splitFound) {
				nextLine.append(nextChar);
			}
		}
		final String line = nextLine.toString().trim();
		if (line.length() > 0) {
			returnList.add(line);
		}
		return returnList;
	}

	/**
	 * Shortens a String to a maximum length by cutting it at the right end.
	 *
	 * @param value value to shorten
	 * @param maxLength maximum length of the result including the cut sign
	 * @param cutSign sign appended to shortened values (e.g. "..."), may be null
	 * @return shortened value, or the unchanged value if it is not longer than maxLength
	 */
	public static String shortenStringToMaxLengthCutRight(final String value, final int maxLength, final String cutSign) {
		if (value != null && value.length() > maxLength) {
			final int cutSignLength = cutSign == null ? 0 : cutSign.length();
			final int keepLength = Math.max(0, maxLength - cutSignLength);
			return value.substring(0, keepLength) + (cutSign == null ? "" : cutSign);
		} else {
			return value;
		}
	}

	/**
	 * Returns all files of a directory whose names match a regex pattern.
	 *
	 * @param startDirectory directory to search in
	 * @param patternString regex the whole file name must match
	 * @param traverseCompletely true to also search all subdirectories recursively
	 * @return matching files, empty if startDirectory is no directory
	 */
	public static List<File> getFilesByPattern(final File startDirectory, final String patternString, final boolean traverseCompletely) {
		return getFilesByPattern(startDirectory, Pattern.compile(patternString), traverseCompletely);
	}

	/**
	 * Returns all files of a directory whose names match a regex pattern.
	 *
	 * @param startDirectory directory to search in
	 * @param pattern regex the whole file name must match
	 * @param traverseCompletely true to also search all subdirectories recursively
	 * @return matching files, empty if startDirectory is no directory
	 */
	public static List<File> getFilesByPattern(final File startDirectory, final Pattern pattern, final boolean traverseCompletely) {
		final List<File> files = new ArrayList<>();
		if (startDirectory.isDirectory()) {
			// listFiles() returns null if the directory cannot be read (e.g. missing permissions or I/O error)
			final File[] directoryFiles = startDirectory.listFiles();
			if (directoryFiles != null) {
				for (final File file : directoryFiles) {
					if (file.isDirectory()) {
						if (traverseCompletely) {
							files.addAll(getFilesByPattern(file, pattern, traverseCompletely));
						}
					} else if (file.isFile() && pattern.matcher(file.getName()).matches()) {
						files.add(file);
					}
				}
			}
		}
		return files;
	}

	/**
	 * Deletes a file or a directory with all its content.
	 *
	 * @param file file or directory to delete
	 * @return true if everything was deleted, false if something could not be deleted
	 */
	public static boolean delete(final File file) {
		if (file.isDirectory()) {
			// listFiles() returns null if the directory cannot be read, so its content cannot be deleted either
			final File[] subFiles = file.listFiles();
			if (subFiles == null) {
				return false;
			}
			for (final File subFile : subFiles) {
				if (!delete(subFile)) {
					return false;
				}
			}
		}
		return file.delete();
	}

	/**
	 * Creates a truststore file containing the TLS server certificate of a host ("trust on first use").
	 * <p>
	 * Watch out: The certificate is accepted without any validation, so this offers no protection against
	 * a man-in-the-middle at the time of the creation.
	 *
	 * @param hostnameOrIpAndPort hostname or IP with optional port, e.g. "dbserver:2484"
	 * @param defaultPort port used if hostnameOrIpAndPort contains no port
	 * @param trustStoreFile file to create, must not exist
	 * @param trustStorePassword password of the truststore, may be null for an empty password
	 * @param proxy proxy for the connection, may be null for a direct connection
	 * @throws Exception if the file already exists, the port is invalid or the certificate cannot be read
	 */
	public static void createTrustStoreFile(final String hostnameOrIpAndPort, final int defaultPort, final File trustStoreFile, final char[] trustStorePassword, final Proxy proxy) throws Exception {
		if (trustStoreFile.exists()) {
			throw new Exception("File '" + trustStoreFile.getAbsolutePath() + "' already exists");
		}

		String hostnameOrIp;
		int port;
		final String[] hostParts = hostnameOrIpAndPort.split(":");
		if (hostParts.length == 2) {
			hostnameOrIp = hostParts[0];
			try {
				port = Integer.parseInt(hostParts[1]);
			} catch (@SuppressWarnings("unused") final Exception e) {
				throw new Exception("Invalid port: " + hostParts[1]);
			}
		} else {
			hostnameOrIp = hostnameOrIpAndPort;
			port = defaultPort;
		}

		final X509Certificate certificate = getServerTlsCertificate(hostnameOrIp, port, proxy);
		if (certificate == null) {
			throw new Exception("Cannot get TLS certificate for '" + hostnameOrIp + ":" + port + "'");
		}

		final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null);

		final char[] password = trustStorePassword == null ? new char[0] : trustStorePassword;
		keyStore.setCertificateEntry(hostnameOrIp, certificate);

		try (OutputStream javaKeyStoreOutputStream = new FileOutputStream(trustStoreFile)) {
			keyStore.store(javaKeyStoreOutputStream, password);
		}
	}

	/**
	 * Reads the TLS server certificate of a host without validating it.
	 * The first certificate with subject alternative names is preferred.
	 *
	 * @param hostnameOrIp hostname or IP
	 * @param port TLS port
	 * @param proxy proxy for the connection, may be null for a direct connection
	 * @return server certificate, or null if the server sent no X.509 certificate
	 * @throws Exception if the connection fails
	 */
	public static X509Certificate getServerTlsCertificate(final String hostnameOrIp, final int port, final Proxy proxy) throws Exception {
		final HttpsURLConnection urlConnection = (HttpsURLConnection) URI.create("https://" + hostnameOrIp + ":" + port).toURL().openConnection(proxy == null ? Proxy.NO_PROXY : proxy);
		final SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, new TrustManager[] { createTrustAllTrustManager() }, new java.security.SecureRandom());
		final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
		urlConnection.setSSLSocketFactory(sslSocketFactory);
		final HostnameVerifier TRUSTALLHOSTNAMES_HOSTNAMEVERIFIER = (hostname, session) -> true;
		urlConnection.setHostnameVerifier(TRUSTALLHOSTNAMES_HOSTNAMEVERIFIER);
		urlConnection.connect();
		Certificate[] certificates;
		try {
			certificates = urlConnection.getServerCertificates();
		} finally {
			urlConnection.disconnect();
		}
		for (final Certificate certificate : certificates) {
			if (certificate instanceof X509Certificate) {
				// Take the first certificate with alternative names
				if (((X509Certificate)certificate).getSubjectAlternativeNames() != null) {
					return (X509Certificate) certificate;
				}
			}
		}
		for (final Certificate certificate : certificates) {
			if (certificate instanceof X509Certificate) {
				// Take the first X509Certificate available, even without alternative names
				return (X509Certificate) certificate;
			}
		}
		return null;
	}

	/**
	 * Creates a trust manager accepting all certificates.
	 * <p>
	 * Watch out: Only use it to read untrusted certificates, never for connections transferring sensitive data.
	 *
	 * @return trust manager accepting all certificates
	 */
	public static X509TrustManager createTrustAllTrustManager() {
		return new X509TrustManager() {
			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(final java.security.cert.X509Certificate[] certificates, final String authType) {
				// nothing to do
			}

			@Override
			public void checkServerTrusted(final java.security.cert.X509Certificate[] certificates, final String authType) {
				// nothing to do
			}
		};
	}

	/**
	 * Tests whether a TCP connection to a host and port can be established within 2 seconds.
	 *
	 * @param hostname hostname or IP
	 * @param port TCP port
	 * @return true if the connection was established
	 * @throws Exception if the hostname cannot be resolved or the connection fails
	 */
	public static boolean testConnection(final String hostname, final int port) throws Exception {
		try (Socket socket = new Socket()) {
			final InetSocketAddress endPoint = new InetSocketAddress(hostname, port);
			final int timeout = 2000; // 2 seconds
			if (endPoint.isUnresolved()) {
				throw new Exception("Cannot resolve hostname '" + hostname + "'");
			} else {
				try {
					socket.connect(endPoint, timeout);
					return true;
				} catch (final IOException ioe) {
					throw new Exception("Cannot connect to host '" + hostname + "' on port " + port + ": " + ioe.getClass().getSimpleName() + ": " + ioe.getMessage());
				}
			}
		}
	}

	/**
	 * Checks if a String ends with a suffix, ignoring the case.
	 *
	 * @param data String to check
	 * @param suffix expected suffix
	 * @return true if data ends with suffix (also true if both are null or suffix is null)
	 */
	public static boolean endsWithIgnoreCase(final String data, final String suffix) {
		if (data == suffix) {
			// both null or same object
			return true;
		} else if (data == null) {
			// data is null but suffix is not
			return false;
		} else if (suffix == null) {
			// suffix is null but data is not
			return true;
		} else if (data.toLowerCase().endsWith(suffix.toLowerCase())) {
			// both are set, so ignore the case for standard endsWith-method
			return true;
		} else {
			// anything else
			return false;
		}
	}

	/**
	 * Parses a date String.
	 *
	 * @param dateFormatPattern {@link DateTimeFormatter} pattern
	 * @param dateString date String
	 * @return parsed date
	 * @throws java.time.format.DateTimeParseException if the date String does not match the pattern
	 */
	public static LocalDate parseLocalDate(final String dateFormatPattern, final String dateString) {
		final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateFormatPattern);
		final LocalDate localDate = LocalDate.parse(dateString, dateTimeFormatter);
		return localDate;
	}

	/**
	 * Parses a date time String.
	 *
	 * @param dateTimeFormatPattern {@link DateTimeFormatter} pattern
	 * @param dateTimeString date time String
	 * @return parsed date time
	 * @throws java.time.format.DateTimeParseException if the date time String does not match the pattern
	 */
	public static LocalDateTime parseLocalDateTime(final String dateTimeFormatPattern, final String dateTimeString) {
		final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormatPattern);
		final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, dateTimeFormatter);
		return localDateTime;
	}
}
