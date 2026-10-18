public class studentA {
    public double calculateAverage(int totalMarks, int subjects) {
        return totalMarks / (double) subjects;
    }

    public boolean passed(int marks) {
        return marks >= 40;
    }
}
