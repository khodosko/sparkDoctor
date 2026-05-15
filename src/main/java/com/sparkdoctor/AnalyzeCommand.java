package com.sparkdoctor;

import com.sparkdoctor.model.ApplicationSummary;
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

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<event-log-path>", description = "Spark event log file or directory.")
    private Path eventLogPath;

    @Option(names = "--out", paramLabel = "<report-directory>", description = "Directory for generated report artifacts.")
    private Path outputDirectory = Path.of("sparkscope-report");

    public AnalyzeCommand() {
        this(new SparkEventLogParser());
    }

    AnalyzeCommand(SparkEventLogParser parser) {
        this.parser = parser;
    }

    @Override
    public Integer call() {
        if (!Files.exists(eventLogPath)) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Event log path does not exist: %s%n", eventLogPath);
            return 2;
        }

        try {
            ApplicationSummary summary = parser.parseApplicationSummary(eventLogPath);
            PrintWriter out = spec.commandLine().getOut();
            String durationDisplay = summary.durationMillis().isPresent()
                    ? Long.toString(summary.durationMillis().getAsLong())
                    : "unknown";
            out.printf("SparkScope analyzed %s%n", eventLogPath);
            out.printf("Application: %s%n", display(summary.appName()));
            out.printf("Application ID: %s%n", display(summary.appId()));
            out.printf("Duration: %s ms%n", durationDisplay);
            out.printf("Output directory: %s%n", outputDirectory);
            return 0;
        } catch (IOException exception) {
            PrintWriter err = spec.commandLine().getErr();
            err.printf("Failed to read Spark event log: %s%n", exception.getMessage());
            return 1;
        }
    }

    private String display(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value;
    }
}
