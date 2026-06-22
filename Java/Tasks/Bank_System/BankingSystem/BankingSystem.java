package BankingSystem;

import java.util.ArrayList;


public class BankingSystem {

    private ArrayList<Account> accounts;

    public BankingSystem(){
        accounts = new ArrayList<>();
    }

    public void withdraw(String accountName, double ammount){
        for(Account account : accounts){
            if(account.getName().equals(accountName)){
                account.withdraw(ammount);
                System.out.println("New balance: " + account.getBalance());
                return;
            }else{
                System.out.println("Failed to withdraw. Account not found.");
                System.out.println(accountName);
            }
        }
    }

    public void deposit(String accountName, double ammount){
        for(Account account : accounts){
            if(account.getName().equals(accountName)){
                account.deposit(ammount);
                System.out.println("New balance: " + account.getBalance());
                return;
            }else{
                System.out.println("Failed to deposit. Account not found.");
            }
        }
    }

    public void addAccount(String name, double balance){
        for(Account account : accounts){
            if(account.getName().equals(name)){
                System.out.println("Account with this name already exists.");
                return;
            }
        }

        accounts.add(new Account(name,balance));
        System.out.println("Account added: " + name);
    }

    public void transfer(String fromAccountName, String toAccountName, double ammount){
        Account fromAccount = null;
        Account toAccount = null;

        for(Account account : accounts){
            if(account.getName().equals(fromAccountName)){
                fromAccount = account;
            }
            if(account.getName().equals(toAccountName)){
                toAccount = account;
            }
        }

        if(fromAccount == null || toAccount == null){
            System.out.println("One or both accounts not found.");
            return;
        }

        if(fromAccount.getBalance() < ammount){
            System.out.println("Insufficient funds in the source account.");
            return;
        }

        fromAccount.withdraw(ammount);
        toAccount.deposit(ammount);
        System.out.println("Successfully transferred " + ammount + " from " + fromAccountName + " to " + toAccountName);
    }


    public void getBalance(String accountName){
        for(Account account : accounts){
            if(account.getName().equals(accountName)){
                System.out.println("Balance for " + accountName + ": " + account.getBalance());
                return;
            }
        }
        System.out.println("Failed to get balance. Account not found.");
    }
}