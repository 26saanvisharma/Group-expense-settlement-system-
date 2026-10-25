import com.expense.exception.InvalidExpenseException;
import com.expense.exception.InvalidSplitException;
import com.expense.exception.UserNotInGroupException;
import com.expense.model.Expense;
import com.expense.model.Group;
import com.expense.model.Transaction;
import com.expense.model.User;
import com.expense.service.ExpenseManager;
import com.expense.service.SettlementCalculator;
import com.expense.split.CustomSplit;
import com.expense.split.EqualSplit;
import com.expense.split.SplitStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * SplitWise Lite - Group Expense Settlement System
 * Console-based demo application tying together the OOP model,
 * split strategies, expense management, and settlement algorithm.
 */
public class Main {

    private static final Scanner sc = new Scanner(System.in);
    private static final ExpenseManager manager = new ExpenseManager();
    private static final SettlementCalculator settlementCalculator = new SettlementCalculator();
    private static final Map<String, User> allUsers = new HashMap<>();
    private static int userCounter = 0;

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("   SplitWise Lite - Expense Settlement");
        System.out.println("=========================================");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1": createGroup(); break;
                    case "2": addMember(); break;
                    case "3": addExpense(); break;
                    case "4": viewBalances(); break;
                    case "5": viewSettlementSummary(); break;
                    case "6": viewTransactionHistory(); break;
                    case "7": listGroups(); break;
                    case "0": running = false; break;
                    default: System.out.println("Invalid choice. Try again.");
                }
            } catch (Exception e) {
                // Catch-all safety net so one bad input never crashes the app
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }

        System.out.println("Goodbye!");
        sc.close();
    }

    private static void printMenu() {
        System.out.println("---------------------------------------------------");
        System.out.println("1. Create Group");
        System.out.println("2. Add Member to Group");
        System.out.println("3. Add Expense");
        System.out.println("4. View Balances");
        System.out.println("5. View Minimum Settlement Summary");
        System.out.println("6. View Transaction History");
        System.out.println("7. List All Groups");
        System.out.println("0. Exit");
        System.out.println("---------------------------------------------------");
        System.out.print("Enter choice: ");
    }

    private static void createGroup() {
        System.out.print("Enter group name: ");
        String name = sc.nextLine().trim();
        Group group = manager.createGroup(name);
        System.out.println("Created group: " + group);
    }

    private static void addMember() {
        Group group = selectGroup();
        if (group == null) return;

        System.out.print("Enter member name: ");
        String name = sc.nextLine().trim();
        User user = getOrCreateUser(name);
        manager.addMemberToGroup(group.getGroupId(), user);
        System.out.println(user.getName() + " added to " + group.getName());
    }

    private static void addExpense() {
        Group group = selectGroup();
        if (group == null) return;

        if (group.getMembers().isEmpty()) {
            System.out.println("This group has no members yet. Add members first.");
            return;
        }

        System.out.print("Expense description: ");
        String description = sc.nextLine().trim();

        System.out.print("Amount: ");
        double amount;
        try {
            amount = Double.parseDouble(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount entered.");
            return;
        }

        System.out.println("Who paid? Group members:");
        List<User> members = group.getMembers();
        for (int i = 0; i < members.size(); i++) {
            System.out.println((i + 1) + ". " + members.get(i));
        }
        int payerIndex = readIndex(members.size());
        if (payerIndex < 0) return;
        User paidBy = members.get(payerIndex);

        System.out.print("Split among all members? (y/n): ");
        String allChoice = sc.nextLine().trim().toLowerCase();
        List<User> participants = new ArrayList<>();
        if (allChoice.equals("y")) {
            participants.addAll(members);
        } else {
            System.out.println("Enter member numbers separated by commas (e.g. 1,3,4):");
            String[] parts = sc.nextLine().trim().split(",");
            for (String p : parts) {
                try {
                    int idx = Integer.parseInt(p.trim()) - 1;
                    if (idx >= 0 && idx < members.size()) {
                        participants.add(members.get(idx));
                    }
                } catch (NumberFormatException ignored) { }
            }
        }

        if (participants.isEmpty()) {
            System.out.println("No valid participants selected.");
            return;
        }

        System.out.print("Split type - Equal (E) or Custom (C)? ");
        String splitType = sc.nextLine().trim().toUpperCase();

        SplitStrategy strategy;
        Map<User, Double> customAmounts = null;

        if (splitType.equals("C")) {
            strategy = new CustomSplit();
            customAmounts = new HashMap<>();
            System.out.println("Enter share amount for each participant:");
            for (User p : participants) {
                System.out.print("  " + p.getName() + ": ");
                try {
                    double share = Double.parseDouble(sc.nextLine().trim());
                    customAmounts.put(p, share);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid share entered. Aborting expense.");
                    return;
                }
            }
        } else {
            strategy = new EqualSplit();
        }

        try {
            Expense expense = manager.addExpense(group.getGroupId(), description, amount,
                    paidBy, participants, strategy, customAmounts);
            System.out.println("Expense recorded: " + expense);
        } catch (InvalidExpenseException | InvalidSplitException | UserNotInGroupException e) {
            System.out.println("Could not add expense: " + e.getMessage());
        }
    }

    private static void viewBalances() {
        Group group = selectGroup();
        if (group == null) return;

        Map<User, Double> balances = manager.calculateBalances(group.getGroupId());
        if (balances.isEmpty()) {
            System.out.println("No members in this group.");
            return;
        }

        System.out.println("Balances for " + group.getName() + ":");
        for (Map.Entry<User, Double> entry : balances.entrySet()) {
            double bal = entry.getValue();
            if (bal > 0.01) {
                System.out.printf("  %s should RECEIVE Rs.%.2f%n", entry.getKey().getName(), bal);
            } else if (bal < -0.01) {
                System.out.printf("  %s should PAY Rs.%.2f%n", entry.getKey().getName(), -bal);
            } else {
                System.out.printf("  %s is SETTLED UP%n", entry.getKey().getName());
            }
        }
    }

    private static void viewSettlementSummary() {
        Group group = selectGroup();
        if (group == null) return;

        Map<User, Double> balances = manager.calculateBalances(group.getGroupId());
        List<Transaction> transactions = settlementCalculator.getMinimumTransactions(balances);

        if (transactions.isEmpty()) {
            System.out.println("Everyone is already settled up in " + group.getName() + "!");
            return;
        }

        System.out.println("Minimum settlement plan for " + group.getName() + " ("
                + transactions.size() + " transaction(s)):");
        for (Transaction t : transactions) {
            System.out.println("  " + t);
        }
    }

    private static void viewTransactionHistory() {
        Group group = selectGroup();
        if (group == null) return;

        List<Expense> expenses = group.getExpenses();
        if (expenses.isEmpty()) {
            System.out.println("No expenses recorded yet for " + group.getName());
            return;
        }

        System.out.println("Expense history for " + group.getName() + ":");
        for (Expense e : expenses) {
            System.out.println("  " + e);
            for (Map.Entry<User, Double> entry : e.getSplitDetails().entrySet()) {
                System.out.printf("      %s owes Rs.%.2f%n", entry.getKey().getName(), entry.getValue());
            }
        }
    }

    private static void listGroups() {
        List<Group> groups = manager.getAllGroups();
        if (groups.isEmpty()) {
            System.out.println("No groups created yet.");
            return;
        }
        System.out.println("All groups:");
        for (Group g : groups) {
            System.out.println("  " + g);
        }
    }

    // ---------- Helper utilities ----------

    private static Group selectGroup() {
        List<Group> groups = manager.getAllGroups();
        if (groups.isEmpty()) {
            System.out.println("No groups exist yet. Create one first.");
            return null;
        }
        System.out.println("Select a group:");
        for (int i = 0; i < groups.size(); i++) {
            System.out.println((i + 1) + ". " + groups.get(i));
        }
        int idx = readIndex(groups.size());
        if (idx < 0) return null;
        return groups.get(idx);
    }

    private static int readIndex(int size) {
        try {
            int idx = Integer.parseInt(sc.nextLine().trim()) - 1;
            if (idx < 0 || idx >= size) {
                System.out.println("Invalid selection.");
                return -1;
            }
            return idx;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return -1;
        }
    }

    private static User getOrCreateUser(String name) {
        // Reuse an existing user with the same name (case-insensitive) if present
        for (User u : allUsers.values()) {
            if (u.getName().equalsIgnoreCase(name)) {
                return u;
            }
        }
        String id = "U" + (++userCounter);
        User user = new User(id, name);
        allUsers.put(id, user);
        return user;
    }
}
