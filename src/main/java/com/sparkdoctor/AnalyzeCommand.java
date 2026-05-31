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
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final AnalysisJsonWriter analysisJsonWriter;
    private final RecommendationsMarkdownWriter recommendationsMarkdownWriter;
    private final SqlExecutionsMarkdownWriter sqlExecutionsMarkdownWriter;
    private final SqlPlanDotWriter sqlPlanDotWriter;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<event-log-path>", description = "Spark event log file or directory.")
    private Path eventLogPath;

    @Option(names = "--out", paramLabel = "<report-directory>", description = "Directory for generated report artifacts.")
    private Path outputDirectory = Path.of("sparkdoctor-report");

    public AnalyzeCommand() {
        this(
                new SparkEventLogParser(),
                new AnalysisJsonWriter(),
                new RecommendationsMarkdownWriter(),
                new SqlExecutionsMarkdownWriter(),
                new SqlPlanDotWriter());
    }

    AnalyzeCommand(
            SparkEventLogParser parser,
            AnalysisJsonWriter analysisJsonWriter,
            RecommendationsMarkdownWriter recommendationsMarkdownWriter,
            SqlExecutionsMarkdownWriter sqlExecutionsMarkdownWriter,
            SqlPlanDotWriter sqlPlanDotWriter) {
        this.parser = parser;
        this.analysisJsonWriter = analysisJsonWriter;
        this.recommendationsMarkdownWriter = recommendationsMarkdownWriter;
        this.sqlExecutionsMarkdownWriter = sqlExecutionsMarkdownWriter;
        this.sqlPlanDotWriter = sqlPlanDotWriter;
    }

    @Override
    public Integer call() {
        if (!Files.exists(eventLogPath)) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Event log path does not exist: %s%n", eventLogPath);
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
        try {
            analysisPath = analysisJsonWriter.write(outputDirectory, report);
            recommendationsPath = recommendationsMarkdownWriter.write(outputDirectory, report);
            if (!report.sqlExecutions().isEmpty()) {
                sqlPlanDotPaths = sqlPlanDotWriter.write(outputDirectory, report);
                sqlExecutionsPath = sqlExecutionsMarkdownWriter.write(outputDirectory, report);
            }
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to write analysis output: %s%n", exception.getMessage());
            return 1;
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
            parsedEventLog.bottlenecks().stream()
                    .limit(3)
                    .forEach(bottleneck -> out.printf(
                            "- [%s] %s (%s): %s%n",
                            bottleneck.severity(),
                            bottleneck.type(),
                            bottleneckLocation(bottleneck.stageId()),
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

    private String bottleneckLocation(int stageId) {
        return stageId < 0 ? "application" : "stage " + stageId;
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

        Files.deleteIfExists(outputDirectory.resolve("analysis.json"));
        Files.deleteIfExists(outputDirectory.resolve("recommendations.md"));
        Files.deleteIfExists(outputDirectory.resolve("sql-executions.md"));

        try (Stream<Path> paths = Files.list(outputDirectory)) {
            for (Path path : paths.filter(this::isSqlPlanDotFile).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean isSqlPlanDotFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("sql-execution-") && fileName.endsWith(".dot");
    }

    private String displayErrorMessage(IOException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
