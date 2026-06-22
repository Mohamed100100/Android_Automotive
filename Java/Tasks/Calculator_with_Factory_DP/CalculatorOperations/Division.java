package CalculatorOperations;

public class Division implements IOperation {
    public double doOperation(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero.");
        }
        return a / b;
    }
}
