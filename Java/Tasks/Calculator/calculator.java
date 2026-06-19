class Calculator {
    public static void main(String[] args) {

        System.out.println("Welcome to the Gemy Calculator!");
        while (true) {
            try {

                System.out.println("Please enter the first number:");
                int num1 = Integer.parseInt(System.console().readLine());

                System.out.println("Please enter the second number:");
                int num2 = Integer.parseInt(System.console().readLine());

                System.out.println("Please choose an operation: +, -, *, /");
                String operation = System.console().readLine();

                Calculator calculator = new Calculator();
                int result;

                switch (operation) {
                    case "+":
                        result = calculator.add(num1, num2);
                        break;
                    case "-":
                        result = calculator.subtract(num1, num2);
                        break;
                    case "*":
                        result = calculator.multiply(num1, num2);
                        break;
                    case "/":
                        try {
                            result = calculator.divide(num1, num2);
                        } catch (IllegalArgumentException e) {
                            System.out.println(e.getMessage());
                            continue;
                        }
                        break;
                    default:
                        System.out.println("Invalid operation. Please try again.");
                        continue;
                }

                System.out.println("The result is: " + result);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }
    

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return a / b;
    }
}