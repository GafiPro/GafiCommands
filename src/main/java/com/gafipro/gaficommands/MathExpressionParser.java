package com.gafipro.gaficommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Safe allow-list parser: expressions are parsed as arithmetic, never executed as code. */
public final class MathExpressionParser {
    private final String input;
    private int position;

    private MathExpressionParser(String input) { this.input = input.toLowerCase(Locale.ROOT); }

    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("Empty expression");
        if (expression.length() > 120) throw new IllegalArgumentException("Expression is too long");
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z') || Character.isWhitespace(c) || c == '.' || c == ','
                    || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^'
                    || c == '(' || c == ')' || c == '!';
            if (!allowed) throw new IllegalArgumentException("Unsupported character");
        }
        MathExpressionParser parser = new MathExpressionParser(expression);
        double result = parser.parseAddSub();
        parser.skipSpaces();
        if (parser.position != parser.input.length()) throw new IllegalArgumentException("Unexpected trailing input");
        return requireFinite(result);
    }

    private double parseAddSub() {
        double value = parseMulDiv();
        while (true) {
            skipSpaces();
            if (consume('+')) value = requireFinite(value + parseMulDiv());
            else if (consume('-')) value = requireFinite(value - parseMulDiv());
            else return value;
        }
    }

    private double parseMulDiv() {
        double value = parseUnary();
        while (true) {
            skipSpaces();
            if (consume('*')) value = requireFinite(value * parseUnary());
            else if (consume('/')) {
                double right = parseUnary();
                if (right == 0.0) throw new ArithmeticException("Division by zero");
                value = requireFinite(value / right);
            } else if (consume('%')) {
                double right = parseUnary();
                if (right == 0.0) throw new ArithmeticException("Modulo by zero");
                value = requireFinite(value % right);
            } else return value;
        }
    }

    // Unary signs have lower precedence than powers: -2^2 == -(2^2); 2^-2 is valid.
    private double parseUnary() {
        skipSpaces();
        if (consume('+')) return parseUnary();
        if (consume('-')) return requireFinite(-parseUnary());
        return parsePower();
    }

    private double parsePower() {
        double value = parsePostfix();
        skipSpaces();
        if (consume('^')) value = requireFinite(Math.pow(value, parseUnary()));
        return value;
    }

    private double parsePostfix() {
        double value = parsePrimary();
        skipSpaces();
        while (consume('!')) {
            value = factorial(value);
            skipSpaces();
        }
        return value;
    }

    private double parsePrimary() {
        skipSpaces();
        if (consume('(')) {
            double value = parseAddSub();
            skipSpaces();
            if (!consume(')')) throw new IllegalArgumentException("Missing closing parenthesis");
            return value;
        }
        if (position < input.length()) {
            char c = input.charAt(position);
            if ((c >= '0' && c <= '9') || c == '.') return parseNumber();
            if (c >= 'a' && c <= 'z') return parseIdentifier();
        }
        throw new IllegalArgumentException("Expected a number, constant, or function");
    }

    private double parseIdentifier() {
        int start = position;
        while (position < input.length()) {
            char c = input.charAt(position);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') position++;
            else break;
        }
        String name = input.substring(start, position);
        skipSpaces();
        if (consume('(')) {
            List<Double> arguments = new ArrayList<>();
            skipSpaces();
            if (!consume(')')) {
                do {
                    arguments.add(parseAddSub());
                    skipSpaces();
                } while (consume(','));
                if (!consume(')')) throw new IllegalArgumentException("Expected ')' after function arguments");
            }
            return requireFinite(evaluateFunction(name, arguments));
        }
        return switch (name) {
            case "pi" -> Math.PI;
            case "e" -> Math.E;
            case "tau" -> Math.PI * 2.0;
            case "phi" -> (1.0 + Math.sqrt(5.0)) / 2.0;
            default -> throw new IllegalArgumentException("Unknown constant or function: " + name);
        };
    }

    private static double evaluateFunction(String name, List<Double> a) {
        return switch (name) {
            case "abs" -> { requireCount(name, a, 1); yield Math.abs(a.get(0)); }
            case "sqrt" -> {
                requireCount(name, a, 1); requireNonNegative(name, a.get(0)); yield Math.sqrt(a.get(0));
            }
            case "cbrt" -> { requireCount(name, a, 1); yield Math.cbrt(a.get(0)); }
            case "sin" -> { requireCount(name, a, 1); yield Math.sin(Math.toRadians(a.get(0))); }
            case "cos" -> { requireCount(name, a, 1); yield Math.cos(Math.toRadians(a.get(0))); }
            case "tan" -> {
                requireCount(name, a, 1);
                double radians = Math.toRadians(a.get(0));
                if (Math.abs(Math.cos(radians)) < 1.0e-12) throw new ArithmeticException("Tangent is undefined at this angle");
                yield Math.tan(radians);
            }
            case "asin" -> { requireCount(name, a, 1); requireUnitInterval(name, a.get(0)); yield Math.toDegrees(Math.asin(a.get(0))); }
            case "acos" -> { requireCount(name, a, 1); requireUnitInterval(name, a.get(0)); yield Math.toDegrees(Math.acos(a.get(0))); }
            case "atan" -> { requireCount(name, a, 1); yield Math.toDegrees(Math.atan(a.get(0))); }
            case "ln" -> { requireCount(name, a, 1); requirePositive(name, a.get(0)); yield Math.log(a.get(0)); }
            case "log", "log10" -> { requireCount(name, a, 1); requirePositive(name, a.get(0)); yield Math.log10(a.get(0)); }
            case "log2" -> { requireCount(name, a, 1); requirePositive(name, a.get(0)); yield Math.log(a.get(0)) / Math.log(2.0); }
            case "exp" -> { requireCount(name, a, 1); yield Math.exp(a.get(0)); }
            case "floor" -> { requireCount(name, a, 1); yield Math.floor(a.get(0)); }
            case "ceil" -> { requireCount(name, a, 1); yield Math.ceil(a.get(0)); }
            case "round" -> { requireCount(name, a, 1); yield Math.floor(a.get(0) + 0.5d); }
            case "sign" -> { requireCount(name, a, 1); yield Math.signum(a.get(0)); }
            case "fact", "factorial" -> { requireCount(name, a, 1); yield factorial(a.get(0)); }
            case "pow" -> { requireCount(name, a, 2); yield Math.pow(a.get(0), a.get(1)); }
            case "root" -> {
                requireCount(name, a, 2);
                double value = a.get(0), degree = a.get(1);
                if (degree == 0.0) throw new ArithmeticException("Root degree cannot be zero");
                if (value < 0.0) {
                    if (degree != Math.rint(degree) || Math.abs(degree % 2.0) != 1.0)
                        throw new ArithmeticException("A negative value needs an odd integer root degree");
                    yield Math.copySign(Math.pow(Math.abs(value), 1.0 / degree), value);
                }
                yield Math.pow(value, 1.0 / degree);
            }
            case "percent" -> { requireCount(name, a, 2); yield a.get(0) * a.get(1) / 100.0; }
            case "sum" -> { requireAtLeast(name, a, 1); yield a.stream().mapToDouble(Double::doubleValue).sum(); }
            case "avg", "average" -> { requireAtLeast(name, a, 1); yield a.stream().mapToDouble(Double::doubleValue).average().orElseThrow(); }
            case "hypot" -> { requireCount(name, a, 2); yield Math.hypot(a.get(0), a.get(1)); }
            case "min" -> { requireAtLeast(name, a, 2); yield a.stream().mapToDouble(Double::doubleValue).min().orElseThrow(); }
            case "max" -> { requireAtLeast(name, a, 2); yield a.stream().mapToDouble(Double::doubleValue).max().orElseThrow(); }
            case "clamp" -> {
                requireCount(name, a, 3);
                if (a.get(1) > a.get(2)) throw new IllegalArgumentException("clamp minimum must be <= maximum");
                yield Math.max(a.get(1), Math.min(a.get(0), a.get(2)));
            }
            default -> throw new IllegalArgumentException("Unknown function: " + name);
        };
    }

    private double parseNumber() {
        skipSpaces();
        int start = position, digits = 0, dots = 0;
        while (position < input.length()) {
            char c = input.charAt(position);
            if (c >= '0' && c <= '9') { digits++; position++; }
            else if (c == '.') {
                dots++;
                if (dots > 1) throw new IllegalArgumentException("Invalid number");
                position++;
            } else break;
        }
        if (digits == 0) throw new IllegalArgumentException("Invalid number");
        if (position < input.length() && input.charAt(position) == 'e') {
            int exponentStart = position, lookahead = position + 1;
            if (lookahead < input.length() && (input.charAt(lookahead) == '+' || input.charAt(lookahead) == '-')) lookahead++;
            int firstExponentDigit = lookahead;
            while (lookahead < input.length() && input.charAt(lookahead) >= '0' && input.charAt(lookahead) <= '9') lookahead++;
            position = lookahead > firstExponentDigit ? lookahead : exponentStart;
        }
        try {
            return requireFinite(Double.parseDouble(input.substring(start, position)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number", exception);
        }
    }

    private static double factorial(double value) {
        if (value < 0 || value != Math.rint(value) || value > 170)
            throw new ArithmeticException("Factorial requires an integer from 0 to 170");
        double result = 1.0;
        for (int i = 2; i <= (int) value; i++) result *= i;
        return requireFinite(result);
    }

    private static void requireCount(String name, List<Double> args, int count) {
        if (args.size() != count) throw new IllegalArgumentException(name + " expects " + count + " argument(s)");
    }
    private static void requireAtLeast(String name, List<Double> args, int count) {
        if (args.size() < count) throw new IllegalArgumentException(name + " expects at least " + count + " arguments");
    }
    private static void requireNonNegative(String name, double value) {
        if (value < 0) throw new ArithmeticException(name + " requires a non-negative value");
    }
    private static void requirePositive(String name, double value) {
        if (value <= 0) throw new ArithmeticException(name + " requires a positive value");
    }
    private static void requireUnitInterval(String name, double value) {
        if (value < -1.0 || value > 1.0) throw new ArithmeticException(name + " requires a value between -1 and 1");
    }
    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) { position++; return true; }
        return false;
    }
    private void skipSpaces() { while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++; }
    private static double requireFinite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Result is not finite");
        return value;
    }
}
