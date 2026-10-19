import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class CopyRiskScanner {

    public static Map<String, Object> scan(Path repository) throws IOException {
        Path repoPath = repository.toAbsolutePath().normalize();
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalArgumentException("Error: Invalid repository path: " + repoPath);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());

        List<Map<String, Object>> methods = new ArrayList<>();
        int filesScanned = 0;
        int parseErrors = 0;

        try (Stream<Path> paths = Files.walk(repoPath)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\.git\\"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .toList();

            for (Path file : javaFiles) {
                filesScanned++;

                try {
                    ParseResult<CompilationUnit> result = parser.parse(file);

                    if (result.getResult().isEmpty()) {
                        parseErrors++;
                        continue;
                    }

                    CompilationUnit unit = result.getResult().get();

                    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("file", repoPath.relativize(file).toString());
                        entry.put("methodName", method.getNameAsString());
                        entry.put("parameters", method.getParameters().stream()
                                .map(p -> p.getNameAsString())
                                .toList());
                        entry.put("startLine", method.getBegin().map(pos -> pos.line).orElse(-1));
                        entry.put("endLine", method.getEnd().map(pos -> pos.line).orElse(-1));
                        entry.put("sourceCode", method.toString());

                        methods.add(entry);
                    }
                } catch (Exception exception) {
                    parseErrors++;
                    System.err.println("Could not parse " + file + ": " + exception.getMessage());
                }
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("filesScanned", filesScanned);
        report.put("methodsExtracted", methods.size());
        report.put("parseErrors", parseErrors);
        report.put("methods", methods);

        List<Map<String, Object>> duplicates = new ArrayList<>();
        Map<String, List<Map<String, Object>>> methodsByCode = new HashMap<>();

        for (Map<String, Object> method : methods) {
            String sourceCode = (String) method.get("sourceCode");
            String normalizedCode = sourceCode.replaceAll("\\s+", " ").trim();

            @SuppressWarnings("unchecked")
            List<String> parameterNames = (List<String>) method.get("parameters");
            if (parameterNames != null) {
                for (String parameterName : parameterNames) {
                    normalizedCode = normalizedCode.replaceAll(
                            "\\b" + java.util.regex.Pattern.quote(parameterName) + "\\b",
                            "VAR"
                    );
                }
            }

            methodsByCode
                    .computeIfAbsent(normalizedCode, key -> new ArrayList<>())
                    .add(method);
        }

        for (List<Map<String, Object>> matches : methodsByCode.values()) {
            if (matches.size() > 1) {
                for (int i = 0; i < matches.size(); i++) {
                    for (int j = i + 1; j < matches.size(); j++) {
                        Map<String, Object> first = matches.get(i);
                        Map<String, Object> second = matches.get(j);

                        if (!first.get("file").equals(second.get("file"))) {
                            Map<String, Object> duplicate = new LinkedHashMap<>();
                            duplicate.put("methodName1", first.get("methodName"));
                            duplicate.put("file1", first.get("file"));
                            duplicate.put("startLine1", first.get("startLine"));
                            duplicate.put("methodName2", second.get("methodName"));
                            duplicate.put("file2", second.get("file"));
                            duplicate.put("startLine2", second.get("startLine"));

                            duplicates.add(duplicate);
                        }
                    }
                }
            }
        }

        report.put("duplicates", duplicates);
        report.put("duplicatePairs", duplicates.size());

        return report;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: CopyRiskScanner <repository-path>");
            return;
        }

        Path repository = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(repository)) {
            System.out.println("Error: Invalid repository path.");
            return;
        }

        Map<String, Object> report = scan(repository);

        Path output = Paths.get("copyrisk-report.json").toAbsolutePath();
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), report);

        System.out.println("\nCopyRisk scan complete!");
        System.out.println("Files scanned: " + report.get("filesScanned"));
        System.out.println("Methods extracted: " + report.get("methodsExtracted"));
        System.out.println("Parse errors: " + report.get("parseErrors"));
        System.out.println("Report saved to: " + output);
    }
}
