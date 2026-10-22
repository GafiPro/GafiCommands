package com.gafipro.gaficommands;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class CalculatorFormatter {
    private static final double SCIENTIFIC_UPPER_BOUND = 1.0e12;
    private static final double SCIENTIFIC_LOWER_BOUND = 1.0e-6;
    private static final MathContext SCIENTIFIC_PRECISION = new MathContext(10, RoundingMode.HALF_UP);

    private CalculatorFormatter() {}

    public static String format(double result) {
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Calculator results must be finite");
        if (result == 0.0d) return "0";
        double magnitude = Math.abs(result);
        BigDecimal normalized = BigDecimal.valueOf(result);
        if (magnitude >= SCIENTIFIC_UPPER_BOUND || magnitude < SCIENTIFIC_LOWER_BOUND) {
            return normalized.round(SCIENTIFIC_PRECISION).stripTrailingZeros().toString().replace("E+", "E");
        }
        return normalized.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
