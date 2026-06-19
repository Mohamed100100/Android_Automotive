import java.util.ArrayList;
import java.util.List;

public class GradeManager {
    private List<Student> students;

    public GradeManager() {
        this.students = new ArrayList<>();
    }

    public void addStudent(Student student) {
        students.add(student);
    }

    public Student findStudent(int id) {
        for (Student s : students) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    public void addGradeToStudent(int studentId, Grade grade) {
        Student student = findStudent(studentId);
        if (student != null) {
            student.addGrade(grade);
        } else {
            System.out.println("Student not found: " + studentId);
        }
    }

    public void printReport() {
        for (Student student : students) {
            System.out.println("" + student);
            for (Grade g : student.getGrades()) {
                System.out.println("  " + g);
            }
            System.out.println("  Average: " + String.format("%.2f", student.getAverage()));
        }
    }

    public void printStudentReport(int studentId) {
        Student student = findStudent(studentId);
        if (student != null) {
            System.out.println(student);
            for (Grade g : student.getGrades()) {
                System.out.println("  " + g);
            }
            System.out.println("  Average: " + String.format("%.2f", student.getAverage()));
        } else {
            System.out.println("Student not found: " + studentId);
        }
    }
}
