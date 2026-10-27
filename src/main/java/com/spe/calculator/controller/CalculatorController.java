package com.spe.calculator.controller;

import com.spe.calculator.model.CalculationResponse;
import com.spe.calculator.service.CalculatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class CalculatorController {

    private final CalculatorService calculator;

    public CalculatorController(CalculatorService calculator) {
        this.calculator = calculator;
    }

    @GetMapping("/add")
    public CalculationResponse add(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return new CalculationResponse("add", a, b, calculator.add(a, b));
    }

    @GetMapping("/subtract")
    public CalculationResponse subtract(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return new CalculationResponse("subtract", a, b, calculator.subtract(a, b));
    }

    @GetMapping("/multiply")
    public CalculationResponse multiply(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return new CalculationResponse("multiply", a, b, calculator.multiply(a, b));
    }

    @GetMapping("/divide")
    public CalculationResponse divide(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return new CalculationResponse("divide", a, b, calculator.divide(a, b));
    }
}
