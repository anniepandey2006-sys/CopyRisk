import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Initial automated edge-case baseline test suite.
 * Evaluates the baseline scanner against 5 initial edge-case categories.
 * Fixtures are isolated in temporary test directories per test method.
 */
public class CopyRiskScannerBaselineTest {

    @Test
    @DisplayName("Case 1: Exact duplicate methods across files (Expected: Exact clone)")
    void testExactDuplicateMethods(@TempDir Path tempDir) throws IOException {
        // Setup two files with identical method declarations
        String codeA = """
            public class ServiceA {
                public int calculateSum(int a, int b) {
                    return a + b;
                }
            }
            """;
        String codeB = """
            public class ServiceB {
                public int calculateSum(int a, int b) {
                    return a + b;
                }
            }
            """;
        Files.writeString(tempDir.resolve("ServiceA.java"), codeA);
        Files.writeString(tempDir.resolve("ServiceB.java"), codeB);

        Map<String, Object> report = CopyRiskScanner.scan(tempDir);

        assertEquals(2, report.get("filesScanned"));
        assertEquals(2, report.get("methodsExtracted"));
        assertEquals(0, report.get("parseErrors"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicates = (List<Map<String, Object>>) report.get("duplicates");
        assertNotNull(duplicates);
        assertEquals(1, report.get("duplicatePairs"), "Baseline should detect exact duplicate methods");
        assertEquals(1, duplicates.size());
    }

    @Test
    @DisplayName("Case 2: Whitespace and formatting differences (Expected: Exact clone / Type-1)")
    void testWhitespaceAndFormattingDifferences(@TempDir Path tempDir) throws IOException {
        // Setup two files with identical code but different formatting/newlines/indentation
        String codeA = """
            public class ValidatorA {
                public boolean passed(int marks) {
                    return marks >= 40;
                }
            }
            """;
        String codeB = """
            public class ValidatorB {
                public boolean passed(int marks)
                {

                    return marks >= 40;

                }
            }
            """;
        Files.writeString(tempDir.resolve("ValidatorA.java"), codeA);
        Files.writeString(tempDir.resolve("ValidatorB.java"), codeB);

        Map<String, Object> report = CopyRiskScanner.scan(tempDir);

        assertEquals(2, report.get("filesScanned"));
        assertEquals(2, report.get("methodsExtracted"));
        assertEquals(0, report.get("parseErrors"));
        assertEquals(1, report.get("duplicatePairs"), "Baseline whitespace normalization should detect formatting-only differences");
    }

    @Test
    @DisplayName("Case 3: Renamed parameter identifiers (Expected: Normalized syntactic clone / Type-2)")
    void testRenamedParameters(@TempDir Path tempDir) throws IOException {
        // Setup methods where only parameter names differ
        String codeA = """
            public class StudentA {
                public boolean passed(int marks) {
                    return marks >= 40;
                }
            }
            """;
        String codeB = """
            public class StudentB {
                public boolean passed(int score) {
                    return score >= 40;
                }
            }
            """;
        Files.writeString(tempDir.resolve("StudentA.java"), codeA);
        Files.writeString(tempDir.resolve("StudentB.java"), codeB);

        Map<String, Object> report = CopyRiskScanner.scan(tempDir);

        assertEquals(2, report.get("filesScanned"));
        assertEquals(2, report.get("methodsExtracted"));
        assertEquals(0, report.get("parseErrors"));
        assertEquals(1, report.get("duplicatePairs"), "Baseline parameter normalization (VAR) should detect renamed parameter duplicates");
    }

    @Test
    @DisplayName("Case 4: Changed numeric constants and comparison thresholds (Expected: Not a match)")
    void testChangedThresholdsNotMatch(@TempDir Path tempDir) throws IOException {
        // Setup methods where business logic / numeric threshold differs (40 vs 50)
        String codeA = """
            public class ThresholdA {
                public boolean passed(int marks) {
                    return marks >= 40;
                }
            }
            """;
        String codeB = """
            public class ThresholdB {
                public boolean passed(int marks) {
                    return marks >= 50;
                }
            }
            """;
        Files.writeString(tempDir.resolve("ThresholdA.java"), codeA);
        Files.writeString(tempDir.resolve("ThresholdB.java"), codeB);

        Map<String, Object> report = CopyRiskScanner.scan(tempDir);

        assertEquals(2, report.get("filesScanned"));
        assertEquals(2, report.get("methodsExtracted"));
        assertEquals(0, report.get("parseErrors"));
        assertEquals(0, report.get("duplicatePairs"), "Different comparison threshold (40 vs 50) changes semantics and must NOT be flagged as duplicate");
    }

    @Test
    @DisplayName("Case 5: Arithmetic with different cast placement (Expected: Candidate for further similarity analysis, Baseline: Missed)")
    void testDifferentCastPlacement(@TempDir Path tempDir) throws IOException {
        // Setup methods with different cast placement: totalMarks / (double) subjects vs (double) totalMarks / subjects
        String codeA = """
            public class MathA {
                public double calculateAverage(int totalMarks, int subjects) {
                    return totalMarks / (double) subjects;
                }
            }
            """;
        String codeB = """
            public class MathB {
                public double calculateAverage(int totalMarks, int subjects) {
                    return (double) totalMarks / subjects;
                }
            }
            """;
        Files.writeString(tempDir.resolve("MathA.java"), codeA);
        Files.writeString(tempDir.resolve("MathB.java"), codeB);

        Map<String, Object> report = CopyRiskScanner.scan(tempDir);

        assertEquals(2, report.get("filesScanned"));
        assertEquals(2, report.get("methodsExtracted"));
        assertEquals(0, report.get("parseErrors"));

        // Baseline documentable behavior:
        // The baseline scanner uses naive token replacement, so it treats `totalMarks / (double) subjects`
        // and `(double) totalMarks / subjects` as different strings and does NOT detect the clone.
        int duplicatePairs = (Integer) report.get("duplicatePairs");
        assertEquals(0, duplicatePairs, "Baseline limitation: string-based matching misses commutative cast placement differences");
    }
}
