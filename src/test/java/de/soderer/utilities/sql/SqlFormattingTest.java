package de.soderer.utilities.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.db.data.DbVendor;
import de.soderer.utilities.sql.JoinClause.JoinType;

/**
 * Tests of {@link SqlStatementFormatter} and of the select statement model with its {@link SelectStatementFormatter}.
 */
@SuppressWarnings("static-method")
public class SqlFormattingTest {
	private static final String WITH_JOIN_STATEMENT = "with x as (select a from t) select x.a, count(*) as cnt from x join y yy on x.a = yy.a left outer join z on z.b=x.a group by x.a having count(*) > 1 order by 1;";
	private static final String CASE_STATEMENT = "select case when a is null then 'n''x' when a is not null then \"q\" else `b` end as c, end_date from tab t where a in (1,2,3) and b <> 2 or c != 3 union all select 1, 2 from dual";
	private static final String DML_STATEMENTS = "insert into tab (a, b) values (1, 'x'); update tab t set a = 1 where b = 2; delete from tab where a = 1;";

	@Test
	public void testFormatWithAndJoins() {
		final String expected = "WITH\n"
				+ "\tx AS (\n"
				+ "\t\tSELECT\n"
				+ "\t\t\ta\n"
				+ "\t\tFROM\n"
				+ "\t\t\tt\n"
				+ "\t)\n"
				+ "SELECT\n"
				+ "\tx.a,\n"
				+ "\tCOUNT(*) AS cnt\n"
				+ "FROM\n"
				+ "\tx\n"
				+ "JOIN y yy ON\n"
				+ "\tx.a = yy.a\n"
				+ "LEFT OUTER JOIN z ON\n"
				+ "\tz.b = x.a\n"
				+ "GROUP BY\n"
				+ "\tx.a\n"
				+ "HAVING\n"
				+ "\tCOUNT(*) > 1\n"
				+ "ORDER BY\n"
				+ "\t1;";
		assertEquals(expected, new SqlStatementFormatter(DbVendor.MySQL, "\t", "\n").format(WITH_JOIN_STATEMENT));
		assertEquals(expected, new SqlStatementFormatter(DbVendor.PostgreSQL, "\t", "\n").format(WITH_JOIN_STATEMENT));
		assertEquals("WITH x AS (SELECT a FROM t) SELECT x.a, COUNT(*) AS cnt FROM x JOIN y yy ON x.a = yy.a LEFT OUTER JOIN z ON z.b = x.a GROUP BY x.a HAVING COUNT(*) > 1 ORDER BY 1;",
				new SqlStatementFormatter(DbVendor.MariaDB, "", "").format(WITH_JOIN_STATEMENT));
	}

	@Test
	public void testFormatOtherJoinTypes() {
		assertEquals("SELECT a FROM t INNER JOIN u ON t.a = u.a RIGHT JOIN v ON 1 = 1 FULL JOIN w ON 1 = 1",
				new SqlStatementFormatter(DbVendor.MySQL, null, null).format("select a\r\nfrom t\rinner join u on t.a=u.a right join v on 1=1 full join w on 1=1"));
	}

	@Test
	public void testFormatCaseQuotesAndUnion() {
		final String expected = "SELECT\n"
				+ "\tCASE\n"
				+ "\t\tWHEN\n"
				+ "\t\t\ta IS NULL\n"
				+ "\t\tTHEN\n"
				+ "\t\t\t'n''x'\n"
				+ "\t\tWHEN\n"
				+ "\t\t\ta IS NOT NULL\n"
				+ "\t\tTHEN\n"
				+ "\t\t\t\"q\"\n"
				+ "\t\tELSE\n"
				+ "\t\t\t`b`\n"
				+ "\tEND AS c,\n"
				+ "\tend_date\n"
				+ "FROM\n"
				+ "\ttab t\n"
				+ "WHERE\n"
				+ "\ta IN (1, 2, 3)\n"
				+ "\tAND\n"
				+ "\tb <> 2\n"
				+ "\tOR\n"
				+ "\tc != 3\n"
				+ "UNION ALL\n"
				+ "SELECT\n"
				+ "\t1,\n"
				+ "\t2\n"
				+ "FROM\n"
				+ "\tDUAL";
		assertEquals(expected, new SqlStatementFormatter().format(CASE_STATEMENT));
		assertEquals("SELECT CASE WHEN a IS NULL THEN 'n''x' WHEN a IS NOT NULL THEN \"q\" ELSE `b` END AS c, end_date FROM tab t WHERE a IN (1, 2, 3) AND b <> 2 OR c != 3 UNION ALL SELECT 1, 2 FROM DUAL",
				new SqlStatementFormatter(DbVendor.Oracle, "", "").format(CASE_STATEMENT));
	}

	@Test
	public void testFormatDmlStatements() {
		final String expected = "INSERT INTO\n"
				+ "\ttab (a, b)\n"
				+ "VALUES\n"
				+ "\t(1, 'x');\n"
				+ "UPDATE\n"
				+ "\ttab t\n"
				+ "SET\n"
				+ "\ta = 1\n"
				+ "WHERE\n"
				+ "\tb = 2;\n"
				+ "DELETE FROM\n"
				+ "\ttab\n"
				+ "WHERE\n"
				+ "\ta = 1;";
		assertEquals(expected, new SqlStatementFormatter(DbVendor.PostgreSQL, "\t", "\n").format(DML_STATEMENTS));
		assertEquals("INSERT INTO tab (a, b) VALUES (1, 'x'); UPDATE tab t SET a = 1 WHERE b = 2; DELETE FROM tab WHERE a = 1;",
				new SqlStatementFormatter(DbVendor.Oracle, "", "").format(DML_STATEMENTS));
	}

	@Test
	public void testFormatSubselectAndOperators() {
		final String formatted = new SqlStatementFormatter(DbVendor.MySQL, "  ", "\n").format("select * from t where e = (select max(x) from u) and g = h + 1 and k<=2 and l||m = 'ab' and n ! 1");
		final String expected = "SELECT\n"
				+ "  *\n"
				+ "FROM\n"
				+ "  t\n"
				+ "WHERE\n"
				+ "  e = (\n"
				+ "    SELECT\n"
				+ "      MAX(x)\n"
				+ "    FROM\n"
				+ "      u\n"
				+ "  )\n"
				+ "  AND\n"
				+ "  g = h + 1\n"
				+ "  AND\n"
				+ "  k <= 2\n"
				+ "  AND\n"
				+ "  l || m = 'ab'\n"
				+ "  AND\n"
				+ "  n\n"
				+ "  !\n"
				+ "  1";
		assertEquals(expected, formatted);
		// Unquoted backticks with the alternative accent character
		assertEquals("SELECT \u00b4a b\u00b4 FROM t", new SqlStatementFormatter(DbVendor.MySQL, "", "").format("select \u00b4a b\u00b4 from t"));
	}

	@Test
	public void testFormatFunctionsAndInterval() {
		assertEquals("SELECT fn(a), fn2(a, b), fn3(a, b, c), MAX(x) FROM t", new SqlStatementFormatter(DbVendor.MySQL, "", "").format("select fn(a), fn2(a, b), fn3(a, b, c), max(x) from t"));
		assertEquals("SELECT date_add(d, INTERVAL 1 DAY) FROM t", new SqlStatementFormatter(DbVendor.MySQL, "", "").format("select date_add(d, interval 1 day) from t"));
		assertEquals("SELECT a FROM t WHERE (a = 1 OR b = 2) AND c IN (SELECT c FROM u)",
				new SqlStatementFormatter(DbVendor.MySQL, "", "").format("select a from t where (a = 1 or b = 2) and c in (select c from u)"));
	}

	@Test
	public void testFormatterSettings() {
		final SqlStatementFormatter formatter = new SqlStatementFormatter();
		assertNull(formatter.format(null));
		assertNull(formatter.format(" \n\t"));
		assertSame(formatter, formatter.setDbVendor(DbVendor.MySQL));
		assertSame(formatter, formatter.setIndentation(""));
		assertSame(formatter, formatter.setLineBreak(""));
		assertEquals("SELECT a FROM t JOIN u ON t.a = u.a", formatter.format("select a from t join u on t.a = u.a"));
		assertThrows(IllegalArgumentException.class, () -> formatter.setDbVendor(null));
		formatter.setLineBreak("\n");
		assertEquals("SELECT\na\nFROM\nt", formatter.format("select a from t"));
		assertTrue(SqlStatementFormatter.ADDITIONALLY_RESERVED_WORDS_FOR_FORMATING.contains("DUAL"));
	}

	@Test
	public void testJoinClause() {
		final JoinClause joinClause = new JoinClause("tab", "t", JoinType.InnerJoin, "t.id = x.id");
		assertEquals("JOIN tab t ON t.id = x.id", joinClause.toString());
		joinClause.setJoinType(JoinType.LeftOutterJoin);
		joinClause.setJoinTable("other");
		joinClause.setJoinTableAlias(null);
		joinClause.setJoinCondition("1 = 1");
		assertEquals(JoinType.LeftOutterJoin, joinClause.getJoinType());
		assertEquals("other", joinClause.getJoinTable());
		assertNull(joinClause.getJoinTableAlias());
		assertEquals("1 = 1", joinClause.getJoinCondition());
		assertEquals("LEFT JOIN other ON 1 = 1", joinClause.toString());
		assertEquals("RIGHT JOIN r", new JoinClause("r", " ", JoinType.RightOutterJoin, null).toString());
		assertEquals("FULL OUTER JOIN f x ON a = b", new JoinClause("f", "x", JoinType.FullOutterJoin, "a = b").toString());
		assertThrows(NullPointerException.class, () -> new JoinClause("f", "x", null, "a = b").toString());
	}

	@Test
	public void testNamedDefinitions() throws Exception {
		final NamedTableDefinition tableDefinition = new NamedTableDefinition(" tab ", " t ");
		assertEquals("tab", tableDefinition.getDefinition());
		assertEquals("t", tableDefinition.getName());
		assertEquals("tab t", tableDefinition.toString());
		tableDefinition.setName(null);
		assertEquals("tab", tableDefinition.toString());

		final WithDefinition withDefinition = new WithDefinition(" w ", " select 1 ");
		assertEquals("w", withDefinition.getName());
		assertEquals("select 1", withDefinition.getDefinition());
		assertEquals("w AS (select 1)", withDefinition.toString());
		withDefinition.setName("v");
		withDefinition.setDefinition("select 2");
		assertEquals("v AS (select 2)", withDefinition.toString());

		final NamedExpression namedExpression = new NamedExpression(" a + 1 ", " sum ");
		assertEquals("a + 1", namedExpression.getExpression());
		assertEquals("sum", namedExpression.getName());
		final SelectStatement subSelect = new SelectStatement();
		namedExpression.setExpression(subSelect);
		assertSame(subSelect, namedExpression.getExpression());
		assertThrows(Exception.class, () -> namedExpression.setExpression(Integer.valueOf(1)));
		assertThrows(Exception.class, () -> new NamedExpression(null, "x"));
	}

	private static SelectStatement createFullSelectStatement() throws Exception {
		final SelectStatement subSelect = new SelectStatement().addField("MAX(b)").addFromTable("other");
		final SelectStatement selectStatement = new SelectStatement()
				.addWithClause("w1", "SELECT 1 AS x FROM DUAL")
				.addWithClause(new WithDefinition("w2", "SELECT 2 AS x FROM DUAL"))
				.addField("a")
				.addField(new NamedExpression("COUNT(*)", "cnt"))
				.addField(new NamedExpression(subSelect, "maxb"))
				.addFromTable("tab")
				.addFromTable(new NamedTableDefinition("w1", "x"));
		final List<JoinClause> joinClauses = new ArrayList<>();
		joinClauses.add(new JoinClause("j", "jj", JoinType.InnerJoin, "jj.a = tab.a"));
		joinClauses.add(new JoinClause("k", null, JoinType.LeftOutterJoin, "k.a = tab.a"));
		selectStatement.setJoinClauses(joinClauses);
		selectStatement.setWhereClause("a > 1");
		selectStatement.setGroupBy(List.of("a", "b"));
		selectStatement.setHavingClause("COUNT(*) > 1");
		selectStatement.setOrderBy(List.of("a DESC", "cnt"));
		return selectStatement;
	}

	@Test
	public void testSelectStatementSingleLine() throws Exception {
		final SelectStatement selectStatement = createFullSelectStatement();
		assertEquals(2, selectStatement.getWithClauses().size());
		assertEquals(3, selectStatement.getFields().size());
		assertEquals(2, selectStatement.getFromTables().size());
		assertEquals(2, selectStatement.getJoinClauses().size());
		assertEquals("a > 1", selectStatement.getWhereClause());
		assertEquals(List.of("a", "b"), selectStatement.getGroupBy());
		assertEquals("COUNT(*) > 1", selectStatement.getHavingClause());
		assertEquals(List.of("a DESC", "cnt"), selectStatement.getOrderBy());

		assertEquals("WITH w1 AS (SELECT 1 AS x FROM DUAL),w2 AS (SELECT 2 AS x FROM DUAL) SELECT a,COUNT(*) AS \"cnt\",(SELECT MAX(b) FROM other) AS \"maxb\" FROM tab,w1 x"
				+ " JOIN j jj ON jj.a = tab.a LEFT JOIN k ON k.a = tab.a WHERE a > 1 GROUP BY a, b HAVING COUNT(*) > 1 ORDER BY a DESC, cnt",
				new SelectStatementFormatter("", "").format(selectStatement));
		assertEquals("SELECT 1 FROM DUAL", new SelectStatementFormatter(null, null).format(new SelectStatement().addField("1").addFromTable("DUAL")));
	}

	@Test
	public void testSelectStatementMultiLine() throws Exception {
		final String expected = "WITH\n"
				+ "\tw1 AS (SELECT 1 AS x FROM DUAL),\n"
				+ "\tw2 AS (SELECT 2 AS x FROM DUAL)\n"
				+ "SELECT\n"
				+ "\ta,\n"
				+ "\tCOUNT(*) AS \"cnt\",\n"
				+ "\t(SELECT\n"
				+ "\tMAX(b)\n"
				+ "FROM\n"
				+ "\tother) AS \"maxb\"\n"
				+ "FROM\n"
				+ "\ttab,\n"
				+ "\tw1 x\n"
				+ "JOIN j jj ON jj.a = tab.a\n"
				+ "LEFT JOIN k ON k.a = tab.a\n"
				+ "WHERE a > 1\n"
				+ "GROUP BY\n"
				+ "\ta,\n"
				+ "\tb\n"
				+ "HAVING COUNT(*) > 1\n"
				+ "ORDER BY\n"
				+ "\ta DESC,\n"
				+ "\tcnt";
		final SelectStatement selectStatement = createFullSelectStatement();
		assertEquals(expected, selectStatement.toString());
		assertEquals(expected, new SelectStatementFormatter().format(selectStatement));

		// Minimal statement without optional clauses
		final SelectStatement minimalStatement = new SelectStatement(new ArrayList<>(List.of(new NamedExpression("x", null))), new ArrayList<>(List.of(new NamedTableDefinition("t", null))));
		minimalStatement.setWithClauses(new ArrayList<>());
		minimalStatement.setFields(minimalStatement.getFields());
		minimalStatement.setFromTables(minimalStatement.getFromTables());
		minimalStatement.setJoinClauses(new ArrayList<>());
		minimalStatement.setGroupBy(new ArrayList<>());
		minimalStatement.setOrderBy(new ArrayList<>());
		minimalStatement.setWhereClause(" ");
		minimalStatement.setHavingClause(null);
		assertEquals("SELECT\n  x\nFROM\n  t", new SelectStatementFormatter("  ", "\n").format(minimalStatement));
		assertEquals("SELECT x FROM t", new SelectStatementFormatter("", "").format(minimalStatement));
	}
}
