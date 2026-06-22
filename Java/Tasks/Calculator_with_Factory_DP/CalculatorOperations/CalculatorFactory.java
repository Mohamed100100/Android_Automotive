package CalculatorOperations;

public class CalculatorFactory {
    public static IOperation getOperation(String operator) {
        switch (operator) {
            case "+":
                return new Add();
            case "-":
                return new Subtraction();
            case "*":
                return new Multiplication();
            case "/":
                return new Division();
            default:
                throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }
}
