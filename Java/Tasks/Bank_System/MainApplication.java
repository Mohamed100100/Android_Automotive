
import BankingSystem.BankingSystem;

public class MainApplication {
    public static void main(String[] args) {
        BankingSystem bankingSystem = new BankingSystem();

        bankingSystem.addAccount("Bob", 500);
        bankingSystem.addAccount("Alice", 1000);

        bankingSystem.deposit("Alice", 200);
        bankingSystem.withdraw("Bob", 100);
        bankingSystem.transfer("Alice", "Bob", 300);

        bankingSystem.getBalance("Alice");
        bankingSystem.getBalance("Bob");
    }
}
