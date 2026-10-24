package de.soderer.utilities.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.sql.DataSource;

/**
 * Minimal scriptable JDBC fake for tests without a real database: SQL statements are matched against regular
 * expressions and answered with configured result rows, update counts or errors. All executed statements and
 * their parameters are recorded, so the generated vendor specific SQL can be checked.
 *
 * Only the JDBC methods used by the tested code are implemented, all other methods return default values.
 */
public final class FakeJdbc {
	private FakeJdbc() {
		// Utility class with static methods only
	}

	/**
	 * Answer for a matched SQL statement.
	 */
	public static final class Result {
		private final List<String> columns;
		private final List<Object[]> rows;
		private final int updateCount;
		private final String errorMessage;

		private Result(final List<String> columns, final List<Object[]> rows, final int updateCount, final String errorMessage) {
			this.columns = columns;
			this.rows = rows;
			this.updateCount = updateCount;
			this.errorMessage = errorMessage;
		}

		boolean hasRows() {
			return columns != null;
		}
	}

	/**
	 * Result rows with the given column names (case insensitive).
	 */
	public static Result rows(final String[] columns, final Object[]... rows) {
		final List<String> columnList = new ArrayList<>();
		for (final String column : columns) {
			columnList.add(column);
		}
		final List<Object[]> rowList = new ArrayList<>();
		for (final Object[] row : rows) {
			rowList.add(row);
		}
		return new Result(columnList, rowList, 0, null);
	}

	/**
	 * A single row with a single value, e.g. for "SELECT COUNT(*) ...".
	 */
	public static Result value(final Object value) {
		return rows(new String[] { "value" }, new Object[] { value });
	}

	/**
	 * Update count for an update statement.
	 */
	public static Result update(final int updateCount) {
		return new Result(null, null, updateCount, null);
	}

	/**
	 * SQLException with the given message.
	 */
	public static Result error(final String errorMessage) {
		return new Result(null, null, 0, errorMessage);
	}

	private static final class Rule {
		private final Pattern pattern;
		private final List<Result> results;
		private int usage = 0;

		private Rule(final String regex, final Result[] results) {
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			this.results = new ArrayList<>();
			for (final Result result : results) {
				this.results.add(result);
			}
		}

		private Result nextResult() {
			final Result result = results.get(Math.min(usage, results.size() - 1));
			usage++;
			return result;
		}
	}

	/**
	 * Fake database of a vendor identified by its JDBC product name.
	 */
	public static final class FakeDatabase {
		private final String productName;
		private final List<Rule> rules = new ArrayList<>();
		private final List<String> executedSql = new ArrayList<>();
		private final List<List<Object>> executedParameters = new ArrayList<>();
		private int commitCount = 0;
		private int rollbackCount = 0;
		private boolean noMetaData = false;

		public FakeDatabase(final String productName) {
			this.productName = productName;
		}

		/**
		 * Answers all statements matching the regular expression (case insensitive, first match wins). Several results
		 * are returned one after the other for repeated executions, the last one is repeated.
		 * Metadata calls are matched as "META getPrimaryKeys schema.table" and "META getImportedKeys schema.table".
		 */
		public FakeDatabase on(final String regex, final Result... results) {
			rules.add(new Rule(regex, results));
			return this;
		}

		/**
		 * Lets getMetaData() return null.
		 */
		public FakeDatabase withoutMetaData() {
			noMetaData = true;
			return this;
		}

		public List<String> getExecutedSql() {
			return executedSql;
		}

		public List<List<Object>> getExecutedParameters() {
			return executedParameters;
		}

		public int getCommitCount() {
			return commitCount;
		}

		public int getRollbackCount() {
			return rollbackCount;
		}

		/**
		 * Checks whether a statement matching the regular expression was executed.
		 */
		public boolean executed(final String regex) {
			final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			for (final String sql : executedSql) {
				if (pattern.matcher(sql).find()) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Returns the first executed statement matching the regular expression.
		 */
		public String findExecuted(final String regex) {
			final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			for (final String sql : executedSql) {
				if (pattern.matcher(sql).find()) {
					return sql;
				}
			}
			throw new AssertionError("No statement matching '" + regex + "' executed. Executed: " + executedSql);
		}

		private Result answer(final String sql, final List<Object> parameters) throws SQLException {
			executedSql.add(sql);
			executedParameters.add(parameters);
			for (final Rule rule : rules) {
				if (rule.pattern.matcher(sql).find()) {
					final Result result = rule.nextResult();
					if (result.errorMessage != null) {
						throw new SQLException(result.errorMessage);
					}
					return result;
				}
			}
			return null;
		}

		public Connection connection() {
			return proxy(Connection.class, new ConnectionHandler(this));
		}

		public DataSource dataSource() {
			return proxy(DataSource.class, (proxy, method, args) -> {
				if ("getConnection".equals(method.getName())) {
					return connection();
				} else {
					return defaultValue(method);
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static Object defaultValue(final Method method) {
		final Class<?> returnType = method.getReturnType();
		if (returnType == boolean.class) {
			return Boolean.FALSE;
		} else if (returnType == int.class) {
			return Integer.valueOf(0);
		} else if (returnType == long.class) {
			return Long.valueOf(0);
		} else if (returnType == double.class) {
			return Double.valueOf(0);
		} else if (returnType == float.class) {
			return Float.valueOf(0);
		} else if (returnType == short.class) {
			return Short.valueOf((short) 0);
		} else if (returnType == byte.class) {
			return Byte.valueOf((byte) 0);
		} else if ("toString".equals(method.getName())) {
			return "FakeJdbc";
		} else {
			return null;
		}
	}

	private static final class ConnectionHandler implements InvocationHandler {
		private final FakeDatabase database;

		private ConnectionHandler(final FakeDatabase database) {
			this.database = database;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			switch (method.getName()) {
				case "getMetaData":
					return database.noMetaData ? null : proxy(DatabaseMetaData.class, new MetaDataHandler(database));
				case "createStatement":
					return proxy(CallableStatement.class, new StatementHandler(database, null));
				case "prepareStatement":
				case "prepareCall":
					return proxy(CallableStatement.class, new StatementHandler(database, (String) args[0]));
				case "commit":
					database.commitCount++;
					return null;
				case "rollback":
					database.rollbackCount++;
					return null;
				case "getCatalog":
					return "catalog";
				case "getAutoCommit":
					return Boolean.FALSE;
				case "isClosed":
					return Boolean.FALSE;
				case "hashCode":
					return Integer.valueOf(System.identityHashCode(proxy));
				case "equals":
					return Boolean.valueOf(proxy == args[0]);
				default:
					return defaultValue(method);
			}
		}
	}

	private static final class MetaDataHandler implements InvocationHandler {
		private final FakeDatabase database;

		private MetaDataHandler(final FakeDatabase database) {
			this.database = database;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			switch (method.getName()) {
				case "getDatabaseProductName":
					return database.productName;
				case "getURL":
					return "jdbc:fake:" + database.productName;
				case "getPrimaryKeys":
				case "getImportedKeys":
					final String sql = "META " + method.getName() + " " + (args[1] == null ? "" : args[1] + ".") + args[2];
					return createResultSet(database.answer(sql, new ArrayList<>()));
				default:
					return defaultValue(method);
			}
		}
	}

	private static ResultSet createResultSet(final Result result) {
		final Result rowResult = result != null && result.hasRows() ? result : rows(new String[0]);
		return proxy(ResultSet.class, new ResultSetHandler(rowResult));
	}

	private static final class StatementHandler implements InvocationHandler {
		private final FakeDatabase database;
		private final String preparedSql;
		private final List<Object> parameters = new ArrayList<>();
		private Result lastResult;

		private StatementHandler(final FakeDatabase database, final String preparedSql) {
			this.database = database;
			this.preparedSql = preparedSql;
		}

		private Result execute(final Object[] args) throws SQLException {
			final String sql = args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : preparedSql;
			lastResult = database.answer(sql, new ArrayList<>(parameters));
			return lastResult;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, Object[] args) throws Throwable {
			if (args == null) {
				args = new Object[] {};
			}
			final String name = method.getName();
			if (name.startsWith("set") && args.length >= 2 && args[0] instanceof Integer && !"setFetchSize".equals(name)) {
				final int index = ((Integer) args[0]).intValue();
				while (parameters.size() < index) {
					parameters.add(null);
				}
				parameters.set(index - 1, args[1]);
				return null;
			}
			switch (name) {
				case "executeQuery":
					return createResultSet(execute(args));
				case "executeUpdate": {
					final Result result = execute(args);
					return Integer.valueOf(result == null ? 0 : result.updateCount);
				}
				case "execute": {
					final Result result = execute(args);
					return Boolean.valueOf(result != null && result.hasRows());
				}
				case "getResultSet":
					return createResultSet(lastResult);
				case "getString":
				case "getInt":
				case "getLong":
				case "getObject": {
					// Out parameters of callable statements are taken from the first result row
					final Object value = lastResult == null || !lastResult.hasRows() || lastResult.rows.isEmpty() ? null : lastResult.rows.get(0)[((Integer) args[0]).intValue() - 1];
					return convert(value, method.getReturnType());
				}
				case "getConnection":
					return database.connection();
				case "hashCode":
					return Integer.valueOf(System.identityHashCode(proxy));
				case "equals":
					return Boolean.valueOf(proxy == args[0]);
				default:
					return defaultValue(method);
			}
		}
	}

	private static Object convert(final Object value, final Class<?> returnType) {
		if (returnType == int.class) {
			return Integer.valueOf(value == null ? 0 : value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString()));
		} else if (returnType == long.class) {
			return Long.valueOf(value == null ? 0 : value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString()));
		} else if (returnType == boolean.class) {
			return Boolean.valueOf(value != null && (value instanceof Boolean ? ((Boolean) value).booleanValue() : "true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString())));
		} else if (returnType == String.class) {
			return value == null ? null : value.toString();
		} else {
			return value;
		}
	}

	private static final class ResultSetHandler implements InvocationHandler {
		private final Result result;
		private int rowIndex = -1;
		private boolean lastWasNull = false;

		private ResultSetHandler(final Result result) {
			this.result = result;
		}

		private int columnIndex(final Object column) throws SQLException {
			if (column instanceof Integer) {
				return ((Integer) column).intValue() - 1;
			} else {
				final String columnName = ((String) column).toLowerCase(Locale.ROOT);
				for (int i = 0; i < result.columns.size(); i++) {
					if (result.columns.get(i).toLowerCase(Locale.ROOT).equals(columnName)) {
						return i;
					}
				}
				throw new SQLException("Unknown column: " + column);
			}
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			switch (method.getName()) {
				case "next":
					rowIndex++;
					return Boolean.valueOf(rowIndex < result.rows.size());
				case "wasNull":
					return Boolean.valueOf(lastWasNull);
				case "getString":
				case "getNString":
				case "getInt":
				case "getLong":
				case "getObject":
				case "getBoolean": {
					final Object value = result.rows.get(rowIndex)[columnIndex(args[0])];
					lastWasNull = value == null;
					return convert(value, method.getReturnType());
				}
				case "getMetaData":
					return proxy(ResultSetMetaData.class, (metaProxy, metaMethod, metaArgs) -> {
						switch (metaMethod.getName()) {
							case "getColumnCount":
								return Integer.valueOf(result.columns.size());
							case "getColumnName":
							case "getColumnLabel":
								return result.columns.get(((Integer) metaArgs[0]).intValue() - 1);
							case "getColumnType":
								return Integer.valueOf(Types.VARCHAR);
							default:
								return defaultValue(metaMethod);
						}
					});
				case "hashCode":
					return Integer.valueOf(System.identityHashCode(proxy));
				case "equals":
					return Boolean.valueOf(proxy == args[0]);
				default:
					return defaultValue(method);
			}
		}
	}
}
