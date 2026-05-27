package com.sparkdoctor;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.ParsedEventLog;
import com.sparkdoctor.output.AnalysisJsonWriter;
import com.sparkdoctor.output.RecommendationsMarkdownWriter;
import com.sparkdoctor.output.SqlExecutionsMarkdownWriter;
import com.sparkdoctor.parser.SparkEventLogParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
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
                new SqlExecutionsMarkdownWriter());
    }

    AnalyzeCommand(
            SparkEventLogParser parser,
            AnalysisJsonWriter analysisJsonWriter,
            RecommendationsMarkdownWriter recommendationsMarkdownWriter,
            SqlExecutionsMarkdownWriter sqlExecutionsMarkdownWriter) {
        this.parser = parser;
        this.analysisJsonWriter = analysisJsonWriter;
        this.recommendationsMarkdownWriter = recommendationsMarkdownWriter;
        this.sqlExecutionsMarkdownWriter = sqlExecutionsMarkdownWriter;
    }

    @Override
    public Integer call() {
        if (!Files.exists(eventLogPath)) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Event log path does not exist: %s%n", eventLogPath);
            return 2;
        }

        ParsedEventLog parsedEventLog;
        try {
            parsedEventLog = parser.parse(eventLogPath);
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to read Spark event log: %s%n", exception.getMessage());
            return 1;
        }

        ApplicationSummary summary = parsedEventLog.applicationSummary();
        AnalysisReport report = AnalysisReport.from(parsedEventLog);
        Path analysisPath;
        Path recommendationsPath;
        Path sqlExecutionsPath = null;
        try {
            analysisPath = analysisJsonWriter.write(outputDirectory, report);
            recommendationsPath = recommendationsMarkdownWriter.write(outputDirectory, report);
            if (!report.sqlExecutions().isEmpty()) {
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
}
