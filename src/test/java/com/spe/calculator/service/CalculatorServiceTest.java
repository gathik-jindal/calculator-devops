package com.spe.calculator.service;

import com.spe.calculator.exception.DivisionByZeroException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorServiceTest {

    private final CalculatorService calculator = new CalculatorService();

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({"2, 3, 5", "-4, 1.5, -2.5", "0.1, 0.2, 0.3", "0, 0, 0"})
    void add(String a, String b, String expected) {
        assertThat(calculator.add(n(a), n(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} - {1} = {2}")
    @CsvSource({"10, 4, 6", "4, 10, -6", "1.5, 1.5, 0", "-2, -3, 1"})
    void subtract(String a, String b, String expected) {
        assertThat(calculator.subtract(n(a), n(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} * {1} = {2}")
    @CsvSource({"6, 7, 42", "-3, 4, -12", "2.5, 0.4, 1", "123, 0, 0"})
    void multiply(String a, String b, String expected) {
        assertThat(calculator.multiply(n(a), n(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource({"10, 2, 5", "7, 2, 3.5", "-9, 3, -3", "0, 5, 0", "1, 3, 0.3333333333333333"})
    void divide(String a, String b, String expected) {
        assertThat(calculator.divide(n(a), n(b))).isEqualByComparingTo(expected);
    }

    @Test
    void divideByZeroThrows() {
        assertThatThrownBy(() -> calculator.divide(n("5"), BigDecimal.ZERO))
                .isInstanceOf(DivisionByZeroException.class)
                .hasMessage("Division by zero is not allowed");
    }

    @Test
    void divideByZeroWithScaleThrows() {
        assertThatThrownBy(() -> calculator.divide(n("5"), n("0.00")))
                .isInstanceOf(DivisionByZeroException.class);
    }

    @Test
    void resultsHaveNoTrailingZeros() {
        assertThat(calculator.multiply(n("2.50"), n("4")).toPlainString()).isEqualTo("10");
        assertThat(calculator.subtract(n("1.0"), n("1.0")).toPlainString()).isEqualTo("0");
    }
}
