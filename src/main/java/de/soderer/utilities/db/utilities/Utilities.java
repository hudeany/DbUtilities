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

public class Utilities {
	public static boolean isEmpty(final String value) {
		return value == null || value.length() == 0;
	}

	public static boolean isNotEmpty(final String value) {
		return !isEmpty(value);
	}

	public static boolean isEmpty(final Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	public static boolean isNotEmpty(final Collection<?> collection) {
		return !isEmpty(collection);
	}

	public static boolean isBlank(final String value) {
		return value == null || value.length() == 0 || value.trim().length() == 0;
	}

	public static boolean isNotBlank(final String value) {
		return !isBlank(value);
	}

	public static boolean isEmpty(final char[] value) {
		return value == null || value.length == 0;
	}

	public static boolean isNotEmpty(final char[] value) {
		return !isEmpty(value);
	}

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

	public static boolean isNotBlank(final char[] value) {
		return !isBlank(value);
	}

	public static String repeat(final char valueChar, final int count) {
		return repeat(Character.toString(valueChar), count, null);
	}

	public static String repeat(final String value, final int count) {
		return repeat(value, count, null);
	}

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

	public static String join(final char[] array, String glue) {
		if (array == null) {
			return null;
		} else if (array.length == 0) {
			return "";
		} else {
			if (glue == null) {
				glue = "";
			}

			String returnValue = "";
			boolean isFirst = true;
			for (final char nextChar : array) {
				if (!isFirst) {
					returnValue += glue;
				}
				returnValue += nextChar;
				isFirst = false;
			}
			return returnValue;
		}
	}

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

	public static String replaceUsersHome(final String filePath) {
		if (filePath == null) {
			return filePath;
		}
		final String homePath = System.getProperty("user.home");
		return filePath
				.replace("~", homePath)
				.replace("$HOME", homePath)
				.replace("${HOME}", homePath);
	}

	/**
	 * Check for a integer value without decimals
	 *
	 * @param value
	 * @return
	 */
	public static boolean isInteger(final String value) {
		try {
			Integer.parseInt(value);
			return true;
		} catch (@SuppressWarnings("unused") final NumberFormatException e) {
			return false;
		}
	}

	public static String trim(final String value) {
		if (value == null) {
			return null;
		} else {
			return value.trim();
		}
	}

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
	 * Only trim the value when the surrounding occurs on both ends
	 * @param value
	 * @param prefix
	 * @return
	 */
	public static String trimSimultaneously(final String value, final String sourrounding) {
		if (value == null) {
			return null;
		} else if (isEmpty(sourrounding)) {
			return value;
		} else if (value.startsWith(sourrounding) && value.endsWith(sourrounding)) {
			return value.substring(sourrounding.length(), value.length() - sourrounding.length());
		} else {
			return value;
		}
	}

	/**
	 * Get a collection like a set as a ordered list
	 *
	 * @param c
	 * @return
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

	public static String shortenStringToMaxLengthCutRight(final String value, final int maxLength, final String cutSign) {
		if (value != null && value.length() > maxLength) {
			return value.substring(0, maxLength - 4) + cutSign;
		} else {
			return value;
		}
	}

	/**
	 * Read a directory and return all files fitting to a regex pattern
	 *
	 * @param startDirectory
	 * @param patternString
	 * @param traverseCompletely
	 * @return
	 */
	public static List<File> getFilesByPattern(final File startDirectory, final String patternString, final boolean traverseCompletely) {
		return getFilesByPattern(startDirectory, Pattern.compile(patternString), traverseCompletely);
	}

	/**
	 * Read a directory and return all files fitting to a regex pattern
	 *
	 * @param startDirectory
	 * @param pattern
	 * @param traverseCompletely
	 * @return
	 */
	public static List<File> getFilesByPattern(final File startDirectory, final Pattern pattern, final boolean traverseCompletely) {
		final List<File> files = new ArrayList<>();
		if (startDirectory.isDirectory()) {
			for (final File file : startDirectory.listFiles()) {
				if (file.isDirectory() && traverseCompletely) {
					if (traverseCompletely) {
						files.addAll(getFilesByPattern(file, pattern, traverseCompletely));
					}
				} else if (file.isFile() && pattern.matcher(file.getName()).matches()) {
					files.add(file);
				}
			}
		}
		return files;
	}

	public static boolean delete(final File file) {
		if (file.isDirectory()) {
			for (final File subFile : file.listFiles()) {
				if (!delete(subFile)) {
					return false;
				}
			}
		}
		return file.delete();
	}

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

	public static LocalDate parseLocalDate(final String dateFormatPattern, final String dateString) {
		final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateFormatPattern);
		final LocalDate localDate = LocalDate.parse(dateString, dateTimeFormatter);
		return localDate;
	}

	public static LocalDateTime parseLocalDateTime(final String dateTimeFormatPattern, final String dateTimeString) {
		final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormatPattern);
		final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, dateTimeFormatter);
		return localDateTime;
	}
}
