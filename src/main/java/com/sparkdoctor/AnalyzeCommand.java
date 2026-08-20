package com.sparkdoctor;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.ParsedEventLog;
import com.sparkdoctor.output.AnalysisJsonWriter;
import com.sparkdoctor.output.RecommendationsMarkdownWriter;
import com.sparkdoctor.output.SqlExecutionsMarkdownWriter;
import com.sparkdoctor.output.SqlPlanDotWriter;
import com.sparkdoctor.parser.SparkEventLogParser;
import com.sparkdoctor.util.BottleneckScope;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "analyze",
        description = "Analyze a local Spark event log.",
        mixinStandardHelpOptions = true)
public final class AnalyzeCommand implements Callable<Integer> {
    private final SparkEventLogParser parser;
    private final ReportWriter analysisJsonWriter;
    private final ReportWriter recommendationsMarkdownWriter;
    private final ReportWriter sqlExecutionsMarkdownWriter;
    private final SqlPlanWriter sqlPlanDotWriter;
    private final ArtifactMover artifactMover;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<event-log-path>", description = "Spark event log file or directory.")
    private Path eventLogPath;

    @Option(names = "--out", paramLabel = "<report-directory>", description = "Directory for generated report artifacts.")
    private Path outputDirectory = Path.of("sparkdoctor-report");

    public AnalyzeCommand() {
        this(
                new SparkEventLogParser(),
                new AnalysisJsonWriter()::write,
                new RecommendationsMarkdownWriter()::write,
                new SqlExecutionsMarkdownWriter()::write,
                new SqlPlanDotWriter()::write);
    }

    AnalyzeCommand(
            SparkEventLogParser parser,
            ReportWriter analysisJsonWriter,
            ReportWriter recommendationsMarkdownWriter,
            ReportWriter sqlExecutionsMarkdownWriter,
            SqlPlanWriter sqlPlanDotWriter) {
        this(
                parser,
                analysisJsonWriter,
                recommendationsMarkdownWriter,
                sqlExecutionsMarkdownWriter,
                sqlPlanDotWriter,
                AnalyzeCommand::moveReplacing);
    }

    AnalyzeCommand(
            SparkEventLogParser parser,
            ReportWriter analysisJsonWriter,
            ReportWriter recommendationsMarkdownWriter,
            ReportWriter sqlExecutionsMarkdownWriter,
            SqlPlanWriter sqlPlanDotWriter,
            ArtifactMover artifactMover) {
        this.parser = parser;
        this.analysisJsonWriter = analysisJsonWriter;
        this.recommendationsMarkdownWriter = recommendationsMarkdownWriter;
        this.sqlExecutionsMarkdownWriter = sqlExecutionsMarkdownWriter;
        this.sqlPlanDotWriter = sqlPlanDotWriter;
        this.artifactMover = artifactMover;
    }

    @Override
    public Integer call() {
        if (!Files.exists(eventLogPath)) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Event log path does not exist: %s%n", eventLogPath);
            return 2;
        }

        try {
            String conflict = inputOutputConflict(eventLogPath, outputDirectory);
            if (conflict != null) {
                spec.commandLine().getErr().println(conflict);
                return 2;
            }
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to resolve input and output paths: %s%n", displayErrorMessage(exception));
            return 2;
        }

        try {
            cleanGeneratedReportArtifacts(outputDirectory);
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to prepare output directory: %s%n", exception.getMessage());
            return 1;
        }

        ParsedEventLog parsedEventLog;
        try {
            parsedEventLog = parser.parse(eventLogPath);
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to read Spark event log: %s%n", displayErrorMessage(exception));
            err.println("SparkDoctor expects Spark event logs in JSON-lines format, with one Spark listener event per line.");
            err.println("Supported inputs: plain event log files, event log directories, .gz, .zstd/.zst, .lz4, and .snappy files.");
            err.println("If this is a rolling Spark event log, point SparkDoctor at the event-log directory.");
            err.println("No report artifacts were written.");
            return 1;
        }

        ApplicationSummary summary = parsedEventLog.applicationSummary();
        AnalysisReport report = AnalysisReport.from(parsedEventLog);
        Path analysisPath;
        Path recommendationsPath;
        Path sqlExecutionsPath = null;
        List<Path> sqlPlanDotPaths = List.of();
        Path stagingDirectory = null;
        try {
            stagingDirectory = createStagingDirectory(outputDirectory);
            WrittenArtifacts stagedArtifacts = writeReport(stagingDirectory, report);
            promoteStagedArtifacts(stagedArtifacts, outputDirectory);
            WrittenArtifacts writtenArtifacts = stagedArtifacts.relocatedTo(outputDirectory);
            analysisPath = writtenArtifacts.analysisPath();
            recommendationsPath = writtenArtifacts.recommendationsPath();
            sqlExecutionsPath = writtenArtifacts.sqlExecutionsPath();
            sqlPlanDotPaths = writtenArtifacts.sqlPlanDotPaths();
        } catch (IOException exception) {
            boolean cleanupSucceeded = cleanAfterFailedWrite(exception);
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to write analysis output: %s%n", displayErrorMessage(exception));
            if (cleanupSucceeded) {
                err.println("No report artifacts were written.");
            } else {
                err.println("Some managed report artifacts could not be removed; inspect the output directory.");
            }
            return 1;
        } finally {
            deleteStagingDirectory(stagingDirectory);
        }

        PrintWriter out = spec.commandLine().getOut();
        String durationDisplay = summary.durationMillis().isPresent()
                ? Long.toString(summary.durationMillis().getAsLong())
                : "unknown";
        out.printf("SparkDoctor analyzed %s%n", eventLogPath);
        out.printf("Application: %s%n", display(summary.appName()));
        out.printf("Application ID: %s%n", display(summary.appId()));
        out.printf("Duration: %s ms%n", durationDisplay);
        out.printf("Jobs: %d%n", parsedEventLog.analysisSummary().jobs());
        out.printf("Jobs completed: %d%n", parsedEventLog.analysisSummary().jobsCompleted());
        out.printf("Jobs failed: %d%n", parsedEventLog.analysisSummary().jobsFailed());
        out.printf("Stages: %d%n", parsedEventLog.analysisSummary().stages());
        out.printf("Stages completed: %d%n", parsedEventLog.analysisSummary().stagesCompleted());
        out.printf("Stages failed: %d%n", parsedEventLog.analysisSummary().stagesFailed());
        out.printf("Tasks: %d%n", parsedEventLog.analysisSummary().tasks());
        out.printf("SQL executions: %d%n", parsedEventLog.sqlExecutions().size());
        out.printf("Issues detected: %d%n", parsedEventLog.analysisSummary().issuesDetected());
        out.printf("Severity summary: %s%n", severitySummary(parsedEventLog.bottlenecks()));
        out.printf("Recommendations: %d%n", parsedEventLog.recommendations().size());
        if (!parsedEventLog.bottlenecks().isEmpty()) {
            out.println("Top bottlenecks:");
            topBottlenecks(parsedEventLog.bottlenecks()).stream()
                    .forEach(bottleneck -> out.printf(
                            "- [%s] %s (%s): %s%n",
                            bottleneck.severity(),
                            bottleneck.type(),
                            BottleneckScope.display(bottleneck),
                            bottleneck.message()));
        }
        out.printf("Output directory: %s%n", outputDirectory);
        out.printf("Analysis JSON: %s%n", analysisPath);
        out.printf("Recommendations Markdown: %s%n", recommendationsPath);
        if (sqlExecutionsPath != null) {
            out.printf("SQL Executions Markdown: %s%n", sqlExecutionsPath);
            out.println("See SQL Executions Markdown for full SQL plan output.");
        }
        for (Path sqlPlanDotPath : sqlPlanDotPaths) {
            out.printf("SQL Plan DOT: %s%n", sqlPlanDotPath);
        }
        return 0;
    }

    private String display(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value;
    }

    static List<Bottleneck> topBottlenecks(List<Bottleneck> bottlenecks) {
        return bottlenecks.stream()
                .sorted(Comparator.comparingInt(AnalyzeCommand::severityRank))
                .limit(3)
                .toList();
    }

    private static int severityRank(Bottleneck bottleneck) {
        return switch (bottleneck.severity()) {
            case "high" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 3;
        };
    }

    private String severitySummary(List<Bottleneck> bottlenecks) {
        if (bottlenecks.isEmpty()) {
            return "none";
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("high", 0L);
        counts.put("medium", 0L);
        counts.put("low", 0L);
        for (var bottleneck : bottlenecks) {
            counts.merge(bottleneck.severity(), 1L, Long::sum);
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private void cleanGeneratedReportArtifacts(Path outputDirectory) throws IOException {
        if (!Files.isDirectory(outputDirectory)) {
            return;
        }

        deleteManagedArtifact(outputDirectory.resolve("analysis.json"));
        deleteManagedArtifact(outputDirectory.resolve("recommendations.md"));
        deleteManagedArtifact(outputDirectory.resolve("sql-executions.md"));

        try (Stream<Path> paths = Files.list(outputDirectory)) {
            for (Path path : paths.filter(this::isSqlPlanDotFile).toList()) {
                deleteManagedArtifact(path);
            }
        }
    }

    private String inputOutputConflict(Path inputPath, Path outputPath) throws IOException {
        Path canonicalInput = inputPath.toRealPath();
        Path canonicalInputLocation = canonicalizePathLocation(inputPath);
        Path canonicalOutput = canonicalizePotentialPath(outputPath);
        if (canonicalInput.equals(canonicalOutput) || canonicalInputLocation.equals(canonicalOutput)) {
            return "Event log input and report output must not refer to the same path: " + inputPath;
        }
        if (Files.isDirectory(canonicalInput) && canonicalOutput.startsWith(canonicalInput)) {
            return "Report output must not be inside the event log input directory: " + outputPath;
        }
        if (isManagedReportArtifact(canonicalInput, canonicalOutput)
                || isManagedReportArtifact(canonicalInputLocation, canonicalOutput)) {
            return "Event log input must not be a managed report artifact under the output directory: "
                    + inputPath;
        }
        return null;
    }

    private Path canonicalizePathLocation(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent == null || absolutePath.getFileName() == null) {
            return absolutePath;
        }
        return canonicalizePotentialPath(parent).resolve(absolutePath.getFileName()).normalize();
    }

    private Path canonicalizePotentialPath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path existingAncestor = absolutePath;
        while (existingAncestor != null && !Files.exists(existingAncestor)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            return absolutePath;
        }
        return existingAncestor
                .toRealPath()
                .resolve(existingAncestor.relativize(absolutePath))
                .normalize();
    }

    private boolean isManagedReportArtifact(Path inputPath, Path outputPath) {
        if (!outputPath.equals(inputPath.getParent()) || inputPath.getFileName() == null) {
            return false;
        }
        String fileName = inputPath.getFileName().toString();
        return fileName.equals("analysis.json")
                || fileName.equals("recommendations.md")
                || fileName.equals("sql-executions.md")
                || isSqlPlanDotFile(inputPath);
    }

    private Path createStagingDirectory(Path outputPath) throws IOException {
        Path absoluteOutput = outputPath.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null) {
            throw new IOException("Report output must have a parent directory: " + outputPath);
        }
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, ".sparkdoctor-report-");
    }

    private WrittenArtifacts writeReport(Path stagingDirectory, AnalysisReport report) throws IOException {
        Path analysisPath = analysisJsonWriter.write(stagingDirectory, report);
        Path recommendationsPath = recommendationsMarkdownWriter.write(stagingDirectory, report);
        if (report.sqlExecutions().isEmpty()) {
            return new WrittenArtifacts(analysisPath, recommendationsPath, null, List.of());
        }
        List<Path> sqlPlanDotPaths = sqlPlanDotWriter.write(stagingDirectory, report);
        Path sqlExecutionsPath = sqlExecutionsMarkdownWriter.write(stagingDirectory, report);
        return new WrittenArtifacts(
                analysisPath,
                recommendationsPath,
                sqlExecutionsPath,
                sqlPlanDotPaths);
    }

    private void promoteStagedArtifacts(WrittenArtifacts artifacts, Path outputPath) throws IOException {
        Files.createDirectories(outputPath);
        for (Path stagedPath : artifacts.paths()) {
            Path destination = outputPath.resolve(stagedPath.getFileName());
            if (Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to replace directory with report artifact: " + destination);
            }
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                    && !isReplaceableManagedArtifact(destination)) {
                throw new IOException("Refusing to replace special file with report artifact: " + destination);
            }
        }
        for (Path stagedPath : artifacts.paths()) {
            Path destination = outputPath.resolve(stagedPath.getFileName());
            artifactMover.move(stagedPath, destination);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean cleanAfterFailedWrite(IOException writeException) {
        try {
            cleanGeneratedReportArtifacts(outputDirectory);
            return true;
        } catch (IOException cleanupException) {
            writeException.addSuppressed(cleanupException);
            return false;
        }
    }

    private void deleteStagingDirectory(Path stagingDirectory) {
        if (stagingDirectory == null || Files.notExists(stagingDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(stagingDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A failed best-effort cleanup must not hide the report result.
        }
    }

    private boolean isSqlPlanDotFile(Path path) {
        if (path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.startsWith("sql-execution-") && fileName.endsWith(".dot");
    }

    private void deleteManagedArtifact(Path path) throws IOException {
        if (isReplaceableManagedArtifact(path)) {
            Files.deleteIfExists(path);
        }
    }

    private boolean isReplaceableManagedArtifact(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path);
    }

    private String displayErrorMessage(IOException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message.replace('\n', ' ').replace('\r', ' ');
    }

    @FunctionalInterface
    interface ReportWriter {
        Path write(Path outputDirectory, AnalysisReport report) throws IOException;
    }

    @FunctionalInterface
    interface SqlPlanWriter {
        List<Path> write(Path outputDirectory, AnalysisReport report) throws IOException;
    }

    @FunctionalInterface
    interface ArtifactMover {
        void move(Path source, Path destination) throws IOException;
    }

    private record WrittenArtifacts(
            Path analysisPath,
            Path recommendationsPath,
            Path sqlExecutionsPath,
            List<Path> sqlPlanDotPaths) {
        private List<Path> paths() {
            List<Path> paths = new ArrayList<>();
            paths.add(analysisPath);
            paths.add(recommendationsPath);
            if (sqlExecutionsPath != null) {
                paths.addAll(sqlPlanDotPaths);
                paths.add(sqlExecutionsPath);
            }
            return List.copyOf(paths);
        }

        private WrittenArtifacts relocatedTo(Path outputDirectory) {
            return new WrittenArtifacts(
                    outputDirectory.resolve(analysisPath.getFileName()),
                    outputDirectory.resolve(recommendationsPath.getFileName()),
                    sqlExecutionsPath == null
                            ? null
                            : outputDirectory.resolve(sqlExecutionsPath.getFileName()),
                    sqlPlanDotPaths.stream()
                            .map(path -> outputDirectory.resolve(path.getFileName()))
                            .toList());
        }
    }
}
