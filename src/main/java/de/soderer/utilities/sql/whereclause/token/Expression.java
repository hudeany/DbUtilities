package de.soderer.utilities.sql.whereclause.token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Expression of a where clause: a function call, an infix operation or a unary postfix operation.
 */
public class Expression extends Value {
	/**
	 * Names of the current date value.
	 */
	public static final Set<String> SYSDATE_VALUES = new HashSet<>(Arrays.asList(new String[] { "sysdate", "sysdate()" }));

	/**
	 * String functions with a single parameter.
	 */
	public static final Set<String> SINGLE_PARAMETER_STRING_FUNCTION_SIGNS = new HashSet<>(Arrays.asList(new String[] { "lower", "upper" }));

	/**
	 * Functions converting a string to a date (with format parameter).
	 */
	public static final Set<String> DATE_FUNCTION_SIGNS = new HashSet<>(Arrays.asList(new String[] { "date", "to_date", "str_to_date" }));

	/**
	 * Functions converting a date to a string (with format parameter).
	 */
	public static final Set<String> CHAR_FUNCTION_SIGNS = new HashSet<>(Arrays.asList(new String[] { "char", "to_char", "date_format" }));

	/**
	 * Unary postfix operators with boolean result.
	 */
	public static final Set<String> BOOL_UNARY_POSTFIX_OPERATOR_SIGNS = new HashSet<>(Arrays.asList(new String[] { "is null", "is not null" }));

	/**
	 * SQL modulo function name.
	 */
	public static final String MOD_FUNCTION_SIGN = "mod";
	/**
	 * BeanShell (Java) modulo operator.
	 */
	public static final String BEANSHELL_MOD_SIGN = "%";

	/**
	 * Comparison operators.
	 */
	public static final Set<String> COMPARE_OPERATOR_SIGNS = new HashSet<>(Arrays.asList(new String[] { "<", "<=", "=", "!=", "<>", ">=", ">" }));

	/**
	 * Arithmetic operators.
	 */
	public static final Set<String> CALCULATION_OPERATOR_SIGNS = new HashSet<>(Arrays.asList(new String[] { "-", "+" }));

	/**
	 * Comparison operators only allowed for strings.
	 */
	public static final Set<String> STRING_ONLY_COMPARE_OPERATOR_SIGNS = new HashSet<>(Arrays.asList(new String[] { "like", "not like" }));

	/**
	 * Boolean operators ordered by ascending priority.
	 */
	public static final List<String> BOOL_ONLY_OPERATOR_SIGNS = new ArrayList<>(Arrays.asList(new String[] { "or", // lower arithmetic priority
			"and" // higher arithmetic priority
	}));

	/**
	 * Function of a function call, null for operations.
	 */
	public Operator functionOperator;
	/**
	 * First operand or function parameter.
	 */
	public Value value1;
	/**
	 * Infix or unary postfix operator, null for function calls.
	 */
	public Operator infixOperator;
	/**
	 * Second operand or function parameter, null for unary operations and single parameter functions.
	 */
	public Value value2;

	/**
	 * Creates a unary postfix operation like {@code value is null}.
	 *
	 * @param value operand
	 * @param unaryPostfixOperator postfix operator
	 * @throws IllegalArgumentException if the operator is no unary postfix operator
	 */
	public Expression(Value value, Operator unaryPostfixOperator) {
		if (BOOL_UNARY_POSTFIX_OPERATOR_SIGNS.contains(unaryPostfixOperator.sign)) {
			type = Type.Bool;
			value1 = value;
			infixOperator = unaryPostfixOperator;
		} else {
			throw new IllegalArgumentException("Invalid operator for unary postfix operator: " + unaryPostfixOperator.sign);
		}
	}

	/**
	 * Creates an infix operation like {@code value1 = value2}.
	 *
	 * @param value1 first operand
	 * @param infixOperator infix operator
	 * @param value2 second operand
	 * @throws IllegalArgumentException if the operator is unknown or the operand types do not fit
	 */
	public Expression(Value value1, Operator infixOperator, Value value2) {
		if (!COMPARE_OPERATOR_SIGNS.contains(infixOperator.sign) && !CALCULATION_OPERATOR_SIGNS.contains(infixOperator.sign) && !STRING_ONLY_COMPARE_OPERATOR_SIGNS.contains(infixOperator.sign)
				&& !BOOL_ONLY_OPERATOR_SIGNS.contains(infixOperator.sign)) {
			throw new IllegalArgumentException("Invalid operator: " + infixOperator.sign);
		} else if (CALCULATION_OPERATOR_SIGNS.contains(infixOperator.sign) && (value1.type == Type.String || value2.type != Type.Number)) {
			throw new IllegalArgumentException("Invalid value types for bool infix operator: " + infixOperator.sign);
		} else if (STRING_ONLY_COMPARE_OPERATOR_SIGNS.contains(infixOperator.sign) && (value1.type != Type.String || value2.type != Type.String)) {
			throw new IllegalArgumentException("Invalid value types for string infix operator: " + infixOperator.sign);
		} else if (BOOL_ONLY_OPERATOR_SIGNS.contains(infixOperator.sign) && (value1.type != Type.Bool || value2.type != Type.Bool)) {
			throw new IllegalArgumentException("Invalid value types for bool infix operator: " + infixOperator.sign);
		} else {
			if (CALCULATION_OPERATOR_SIGNS.contains(infixOperator.sign)) {
				type = value1.type;
			} else {
				type = Type.Bool;
			}
			this.value1 = value1;
			this.infixOperator = infixOperator;
			this.value2 = value2;
		}
	}

	/**
	 * Creates a single parameter function call like {@code lower(value1)}.
	 *
	 * @param functionOperator function
	 * @param value1 function parameter
	 * @throws IllegalArgumentException if the function is unknown or the parameter type does not fit
	 */
	public Expression(Operator functionOperator, Value value1) {
		if (!SINGLE_PARAMETER_STRING_FUNCTION_SIGNS.contains(functionOperator.sign)) {
			throw new IllegalArgumentException("Invalid operator: " + functionOperator.sign);
		} else if (SINGLE_PARAMETER_STRING_FUNCTION_SIGNS.contains(functionOperator.sign) && value1.type == Type.Date) {
			throw new IllegalArgumentException("Invalid value type for function operator: " + functionOperator.sign);
		} else {
			type = Type.String;
			this.functionOperator = functionOperator;
			this.value1 = value1;
		}
	}

	/**
	 * Creates a two parameter function call like {@code to_date(value1, value2)} or {@code mod(value1, value2)}.
	 *
	 * @param functionOperator function
	 * @param value1 first function parameter
	 * @param value2 second function parameter
	 * @throws IllegalArgumentException if the function is unknown or the parameter types do not fit
	 */
	public Expression(Operator functionOperator, Value value1, Value value2) {
		if (!DATE_FUNCTION_SIGNS.contains(functionOperator.sign) && !CHAR_FUNCTION_SIGNS.contains(functionOperator.sign) && !MOD_FUNCTION_SIGN.equals(functionOperator.sign)) {
			throw new IllegalArgumentException("Invalid operator: " + functionOperator.sign);
		} else if (DATE_FUNCTION_SIGNS.contains(functionOperator.sign) && (value1.type != Type.String || value2.type != Type.String)) {
			throw new IllegalArgumentException("Invalid value types for function operator: " + functionOperator.sign);
		} else if (CHAR_FUNCTION_SIGNS.contains(functionOperator.sign) && (value1.type != Type.Date || value2.type != Type.String)) {
			throw new IllegalArgumentException("Invalid value types for function operator: " + functionOperator.sign);
		} else if (MOD_FUNCTION_SIGN.equals(functionOperator.sign) && (value1.type != Type.Number || value2.type != Type.Number)) {
			throw new IllegalArgumentException("Invalid value types for function operator: " + functionOperator.sign);
		} else if (DATE_FUNCTION_SIGNS.contains(functionOperator.sign)) {
			functionOperator.sign = "date";
			this.functionOperator = functionOperator;
			this.value1 = value1;
			this.value2 = new Value(value2.stringValue.replace("%d", "dd").replace("%m", "mm").replace("%Y", "yyyy").replace("%y", "yy"));
			type = Type.Date;
		} else if (CHAR_FUNCTION_SIGNS.contains(functionOperator.sign)) {
			functionOperator.sign = "char";
			this.functionOperator = functionOperator;
			this.value1 = value1;
			this.value2 = new Value(value2.stringValue.replace("%d", "dd").replace("%m", "mm").replace("%Y", "yyyy").replace("%y", "yy"));
			type = Type.String;
		} else {
			this.functionOperator = functionOperator;
			this.value1 = value1;
			this.value2 = value2;
			type = Type.Number;
		}
	}

	@Override
	public String toString() {
		boolean bracketizeValue1 = infixOperator != null && value1 instanceof Expression && ((Expression) value1).infixOperator != null
				&& BOOL_ONLY_OPERATOR_SIGNS.contains(((Expression) value1).infixOperator.sign) && !((Expression) value1).infixOperator.sign.equalsIgnoreCase(infixOperator.sign);
		boolean bracketizeValue2 = infixOperator != null && value2 != null && value2 instanceof Expression && ((Expression) value2).infixOperator != null
				&& BOOL_ONLY_OPERATOR_SIGNS.contains(((Expression) value2).infixOperator.sign) && !((Expression) value2).infixOperator.sign.equalsIgnoreCase(infixOperator.sign);

		StringBuilder returnValue = new StringBuilder();

		if (infixOperator != null) {
			if (bracketizeValue1) {
				returnValue.append("(");
			}
			returnValue.append(value1.toString());
			if (bracketizeValue1) {
				returnValue.append(")");
			}
			returnValue.append(" ");
			returnValue.append(infixOperator.toString());
			if (value2 != null) {
				returnValue.append(" ");
				if (bracketizeValue2) {
					returnValue.append("(");
				}
				returnValue.append(value2.toString());
				if (bracketizeValue2) {
					returnValue.append(")");
				}
			}
		} else {
			returnValue.append(functionOperator.toString());
			returnValue.append("(");
			returnValue.append(value1.toString());
			// Single parameter functions like lower() and upper() have no second value
			if (value2 != null) {
				returnValue.append(", ");
				returnValue.append(value2.toString());
			}
			returnValue.append(")");
		}

		return returnValue.toString();
	}

	@Override
	public String toString(RulePart.StringType stringType) {
		boolean bracketizeValue1 = infixOperator != null && value1 instanceof Expression && ((Expression) value1).infixOperator != null
				&& BOOL_ONLY_OPERATOR_SIGNS.contains(((Expression) value1).infixOperator.sign) && !((Expression) value1).infixOperator.sign.equalsIgnoreCase(infixOperator.sign);
		boolean bracketizeValue2 = infixOperator != null && value2 != null && value2 instanceof Expression && ((Expression) value2).infixOperator != null
				&& BOOL_ONLY_OPERATOR_SIGNS.contains(((Expression) value2).infixOperator.sign) && !((Expression) value2).infixOperator.sign.equalsIgnoreCase(infixOperator.sign);

		StringBuilder returnValue = new StringBuilder();

		if (stringType == StringType.BeanShell && functionOperator != null && "mod".equalsIgnoreCase(functionOperator.sign)) {
			returnValue.append(value1.toString(stringType));
			returnValue.append(" ");
			returnValue.append(BEANSHELL_MOD_SIGN);
			returnValue.append(" ");
			returnValue.append(value2.toString(stringType));
		} else if (infixOperator != null) {
			if (bracketizeValue1) {
				returnValue.append("(");
			}
			returnValue.append(value1.toString(stringType));
			if (bracketizeValue1) {
				returnValue.append(")");
			}
			returnValue.append(" ");
			returnValue.append(infixOperator.toString(stringType));
			if (value2 != null) {
				returnValue.append(" ");
				if (bracketizeValue2) {
					returnValue.append("(");
				}
				returnValue.append(value2.toString(stringType));
				if (bracketizeValue2) {
					returnValue.append(")");
				}
			}
		} else {
			returnValue.append(functionOperator.toString(stringType));
			returnValue.append("(");
			returnValue.append(value1.toString(stringType));
			// Single parameter functions like lower() and upper() have no second value
			if (value2 != null) {
				returnValue.append(", ");
				if (stringType == StringType.MySQL) {
					returnValue.append(value2.toString(stringType).replace("dd", "%d").replace("mm", "%m").replace("yyyy", "%Y").replace("yy", "%y"));
				} else {
					returnValue.append(value2.toString(stringType));
				}
			}
			returnValue.append(")");
		}

		return returnValue.toString();
	}
}
