public class Main {
    public static void main(String[] args) {
        GradeManager manager = new GradeManager();

        // Create students
        Student alice = new Student(1, "Alice");
        Student bob = new Student(2, "Bob");
        Student carol = new Student(3, "Carol");

        // Add students to manager
        manager.addStudent(alice);
        manager.addStudent(bob);
        manager.addStudent(carol);

        // Add grades to Alice directly (Student has its own grades list)
        alice.addGrade(new Grade("Math", 85.0));
        alice.addGrade(new Grade("Science", 92.0));
        alice.addGrade(new Grade("English", 78.0));

        // Add grades via manager
        manager.addGradeToStudent(2, new Grade("Math", 90.0));
        manager.addGradeToStudent(2, new Grade("Science", 88.0));
        manager.addGradeToStudent(2, new Grade("History", 75.0));

        manager.addGradeToStudent(3, new Grade("Math", 95.0));
        manager.addGradeToStudent(3, new Grade("Science", 98.0));

        // Print all reports
        System.out.println("=== FULL REPORT ===");
        manager.printReport();

        // Print single student
        System.out.println("=== ALICE REPORT ===");
        manager.printStudentReport(1);

        // Access directly
        System.out.println("=== DIRECT ACCESS ===");
        System.out.println("Alice's grades: " + alice.getGrades());
        System.out.println("Alice's average: " + alice.getAverage());
    }
}
