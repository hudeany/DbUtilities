package de.soderer.utilities.sql.whereclause.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.soderer.utilities.sql.whereclause.ReducedSqlWhereClauseParser;
import de.soderer.utilities.sql.whereclause.token.Expression;
import de.soderer.utilities.sql.whereclause.token.ListExpression;
import de.soderer.utilities.sql.whereclause.token.Operator;
import de.soderer.utilities.sql.whereclause.token.RulePart;
import de.soderer.utilities.sql.whereclause.token.RulePart.StringType;
import de.soderer.utilities.sql.whereclause.token.SubSelect;
import de.soderer.utilities.sql.whereclause.token.SupplementalPart;
import de.soderer.utilities.sql.whereclause.token.Value;

/**
 * Additional tests of {@link ReducedSqlWhereClauseParser} and its tokens: calculations, functions, sub selects and
 * the error handling of invalid formulas.
 */
@SuppressWarnings("static-method")
public class ReducedSqlWhereClauseParserAdditionalTest {
	private static Map<String, Value.Type> getFields() {
		final Map<String, Value.Type> fields = new HashMap<>();
		fields.put("Email", Value.Type.String);
		fields.put("text", Value.Type.String);
		fields.put("nummer", Value.Type.Number);
		fields.put("geburtstag", Value.Type.Date);
		return fields;
	}

	private static RulePart parse(final String formula) {
		return ReducedSqlWhereClauseParser.parse(formula, getFields());
	}

	private static void assertParseError(final String expectedMessage, final String formula) {
		assertEquals(expectedMessage, assertThrows(IllegalArgumentException.class, () -> parse(formula)).getMessage(), formula);
	}

	@Test
	public void testCalculationsAndNumbers() {
		assertEquals("nummer + 1.5 > 2", parse("nummer + 1.5 > 2").toString());
		assertEquals("nummer - 0.25 <= .5", parse("nummer - 0.25 <= .5").toString().replace("0.5", ".5"));
		assertEquals("nummer >= 1000", parse("nummer >= 1000.0").toString());
		assertEquals("nummer <> 3", parse("nummer <> 3").toString());
		assertEquals("nummer != 3", parse("nummer <> 3").toString(StringType.BeanShell));
		assertEquals("nummer <> 3", parse("nummer <> 3").toString(StringType.Oracle));
	}

	@Test
	public void testModulo() {
		final RulePart rule = parse("nummer % 3 = 1");
		assertEquals("mod(nummer, 3) = 1", rule.toString());
		assertEquals("MOD(nummer, 3) = 1", rule.toString(StringType.Oracle));
		assertEquals("nummer % 3 = 1", rule.toString(StringType.BeanShell));
	}

	@Test
	public void testStringFunctionsAndEmptyStrings() {
		assertEquals("lower(text) = 'abc'", parse("lower(text) = 'abc'").toString());
		assertEquals("UPPER(email) LIKE 'A%'", parse("upper(Email) like 'A%'").toString(StringType.Oracle));
		assertEquals("text = ''", parse("text = ''").toString());
		assertEquals("text = '''x'", parse("text = '''x'").toString());
		assertEquals("text IS NULL", parse("text is null").toString(StringType.MySQL));
		assertEquals("text is not null", parse("text is not null").toString(StringType.BeanShell));
	}

	@Test
	public void testDateFunctionsAndSysdate() {
		final RulePart rule = parse("geburtstag < sysdate");
		assertEquals("geburtstag < sysdate", rule.toString());
		assertEquals("geburtstag < SYSDATE", rule.toString(StringType.Oracle));
		assertEquals("geburtstag < SYSDATE()", rule.toString(StringType.MySQL));
		assertEquals("geburtstag < sysdate", rule.toString(StringType.BeanShell));

		final RulePart dateRule = parse("geburtstag = str_to_date('23.01.1977', '%d.%m.%Y')");
		assertEquals("geburtstag = date('23.01.1977', 'dd.mm.yyyy')", dateRule.toString());
		assertEquals("geburtstag = TO_DATE('23.01.1977', 'dd.mm.yyyy')", dateRule.toString(StringType.Oracle));
		assertEquals("geburtstag = STR_TO_DATE('23.01.1977', '%d.%m.%Y')", dateRule.toString(StringType.MySQL));

		final RulePart charRule = parse("date_format(geburtstag, '%d.%m.%y') = '23.01.77'");
		assertEquals("char(geburtstag, 'dd.mm.yy') = '23.01.77'", charRule.toString());
		assertEquals("TO_CHAR(geburtstag, 'dd.mm.yy') = '23.01.77'", charRule.toString(StringType.Oracle));
		assertEquals("DATE_FORMAT(geburtstag, '%d.%m.%y') = '23.01.77'", charRule.toString(StringType.MySQL));
	}

	@Test
	public void testListsAndSubSelects() {
		assertEquals("nummer NOT IN (1, 2)", parse("nummer not in (1, 2)").toString(StringType.Oracle));
		assertEquals("nummer not in (1, 2)", parse("nummer not in (1, 2)").toString(StringType.BeanShell));

		final RulePart subSelectRule = parse("text in (select name from tab where id > 1)");
		assertEquals("text in (select name from tab where id > 1)", subSelectRule.toString());
		assertEquals("text IN (select name from tab where id > 1)", subSelectRule.toString(StringType.Oracle));
		assertTrue(subSelectRule instanceof ListExpression);
		assertTrue(((ListExpression) subSelectRule).valueList.get(0) instanceof SubSelect);

		final RulePart nestedSubSelectRule = parse("text in (select name from tab where id in (select id from other))");
		assertEquals("text in (select name from tab where id in (select id from other))", nestedSubSelectRule.toString());
	}

	@Test
	public void testBracketsAndPriorities() {
		assertEquals("(nummer = 1 or nummer = 2) and text = 'a'", parse("(nummer = 1 or nummer = 2) and text = 'a'").toString());
		assertEquals("nummer = 1 or (nummer = 2 and text = 'a')", parse("nummer = 1 or (nummer = 2 and text = 'a')").toString());
		assertEquals("text = 'a' and (nummer = 1 or nummer = 2)", parse("text = 'a' and (nummer = 1 or nummer = 2)").toString());
		assertEquals("nummer = 1", parse("((nummer = 1))").toString());
	}

	@Test
	public void testParseErrors() {
		assertParseError("Too many closing brackets", "nummer = 1)");
		assertParseError("Too many opening brackets", "(nummer = 1");
		assertParseError("Invalid data found at position 0 starting with u", "unknown = 1");
		assertParseError("Invalid data found at position 14 starting with x", "text in ('x', xyz)");
		assertParseError("Rule cannot be evaluated", "nummer + 1");
		assertParseError("Rule cannot be evaluated", "text");
		assertParseError("Invalid definition for operator: +", "+ 1 = 2");
		assertParseError("Invalid definition for operator: %", "% 1 = 2");
		assertParseError("Invalid definition for function: mod", "mod(nummer) = 1");
		assertParseError("Invalid definition for function: lower", "lower(text, text) = 'a'");
		assertParseError("Invalid definition for unary operator: is null", "is null or nummer = 1");
		assertParseError("Invalid definition for operator: =", "= 1");
		assertParseError("Invalid definition for list operator: in", "nummer in 1");
		assertParseError("Invalid list definition", "nummer in ((1), 2)");
		assertParseError("Illegal list value: 1 = 1", "nummer in (1 = 1)");
		assertParseError("Invalid value types for function operator: mod", "mod(text, 3) = 1");
		assertParseError("Invalid value types for function operator: date", "geburtstag = date(1, 'dd')");
		assertParseError("Invalid value types for function operator: char", "char(text, 'dd') = '1'");
		assertParseError("Invalid value type for function operator: lower", "lower(geburtstag) = 'a'");
		assertParseError("Invalid value types for bool infix operator: +", "text + 1 = 2");
		assertParseError("Illegal subselect", "text in (delete tab)");
	}

	@Test
	public void testTokens() {
		assertEquals("(", new SupplementalPart(SupplementalPart.Type.OpeningBracket).toString());
		assertEquals(")", new SupplementalPart(SupplementalPart.Type.ClosingBracket).toString());
		assertEquals(", ", new SupplementalPart(SupplementalPart.Type.Separator).toString());
		assertEquals("(", new SupplementalPart(SupplementalPart.Type.OpeningBracket).toString(StringType.Oracle));
		assertEquals(")", new SupplementalPart(SupplementalPart.Type.ClosingBracket).toString(StringType.MySQL));
		assertEquals(", ", new SupplementalPart(SupplementalPart.Type.Separator).toString(StringType.BeanShell));

		final Operator operator = new Operator("TO_CHAR");
		assertEquals("to_char", operator.toString());
		assertEquals("TO_CHAR", operator.toString(StringType.Oracle));
		assertEquals("DATE_FORMAT", operator.toString(StringType.MySQL));
		assertEquals("STR_TO_DATE", new Operator("date").toString(StringType.MySQL));
		assertEquals("%", new Operator("MOD").toString(StringType.BeanShell));
		assertThrows(IllegalArgumentException.class, () -> new Operator("like").toString(StringType.BeanShell));
		assertEquals(null, new Operator(null).sign);

		assertEquals("'it''s'", new Value("it's").toString());
		assertEquals("1.5", new Value(1.5).toString(StringType.Oracle));
		assertEquals("field", new Value("FIELD", Value.Type.Number).toString(StringType.MySQL));
		final Expression boolExpression = new Expression(new Value("a"), new Operator("="), new Value("b"));
		assertEquals(Value.Type.Bool, boolExpression.type);
		final Value boolValue = new Value("x");
		boolValue.type = Value.Type.Bool;
		assertThrows(RuntimeException.class, boolValue::toString);
		final Value dateValue = new Value("x");
		dateValue.type = Value.Type.Date;
		assertThrows(RuntimeException.class, dateValue::toString);

		assertThrows(IllegalArgumentException.class, () -> new Expression(new Value("a"), new Operator("=")));
		assertThrows(IllegalArgumentException.class, () -> new Expression(new Value("a"), new Operator("mod"), new Value("b")));
		assertThrows(IllegalArgumentException.class, () -> new Expression(new Operator("upper"), new Value("a"), new Value("b")));
		assertThrows(IllegalArgumentException.class, () -> new Expression(new Operator("mod"), new Value("a")));
		assertThrows(IllegalArgumentException.class, () -> new Expression(new Value("a"), new Operator("like"), new Value(1)));
		assertThrows(IllegalArgumentException.class, () -> new Expression(new Value("a"), new Operator("and"), new Value("b")));
		assertThrows(IllegalArgumentException.class, () -> new SubSelect("select x"));

		final List<Value> values = new ArrayList<>();
		values.add(new Value(1));
		assertThrows(IllegalArgumentException.class, () -> new ListExpression(new Value("a"), new Operator("in"), values));
	}

	@Test
	public void testPrintJoinedParts() {
		final List<RulePart> parts = new ArrayList<>();
		parts.add(new Value("text", Value.Type.String));
		parts.add(new Operator("="));
		parts.add(new Value("abc"));
		final PrintStream originalOut = System.out;
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (PrintStream capturingStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setOut(capturingStream);
			ReducedSqlWhereClauseParser.printJoinedParts(parts);
		} finally {
			System.setOut(originalOut);
		}
		final String printed = output.toString(StandardCharsets.UTF_8);
		assertTrue(printed.contains("text = 'abc'"), printed);
		assertTrue(printed.startsWith("0    1 2"), printed);
		assertNotNull(new ReducedSqlWhereClauseParser());
	}
}
