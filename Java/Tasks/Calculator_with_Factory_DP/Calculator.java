

import CalculatorOperations.IOperation;
import CalculatorOperations.CalculatorFactory;


public class Calculator{
    public static void main(String[] args) {
        double num1 = 10;
        double num2 = 5;

        IOperation addOperation = CalculatorFactory.getOperation("+");
        System.out.println("Addition: " + addOperation.doOperation(num1, num2));

        IOperation subtractOperation = CalculatorFactory.getOperation("-");
        System.out.println("Subtraction: " + subtractOperation.doOperation(num1, num2));

        IOperation multiplyOperation = CalculatorFactory.getOperation("*");
        System.out.println("Multiplication: " + multiplyOperation.doOperation(num1, num2));

        IOperation divideOperation = CalculatorFactory.getOperation("/");
        System.out.println("Division: " + divideOperation.doOperation(num1, num2));
    }
}