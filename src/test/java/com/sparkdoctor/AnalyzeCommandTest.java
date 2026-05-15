package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class AnalyzeCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void analyzeAcceptsExistingEventLogPath() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json");
        Files.writeString(eventLog, "{}\n");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(eventLog.toString(), "--out", tempDir.resolve("report").toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("SparkDoctor analyzed"));
    }

    @Test
    void analyzeRejectsMissingEventLogPath() {
        Path missingEventLog = tempDir.resolve("missing-eventlog.json");
        StringWriter errorOutput = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setErr(new PrintWriter(errorOutput, true));

        int exitCode = commandLine.execute(missingEventLog.toString());

        assertEquals(2, exitCode);
        assertTrue(errorOutput.toString().contains("Event log path does not exist"));
    }

    @Test
    void analyzePrintsParsedApplicationSummary() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/minimal-eventlog.json",
                "--out",
                tempDir.resolve("report").toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: daily_customer_etl"));
        assertTrue(output.toString().contains("Application ID: app-20260515120000-0001"));
        assertTrue(output.toString().contains("Duration: 2832000 ms"));
    }
}
