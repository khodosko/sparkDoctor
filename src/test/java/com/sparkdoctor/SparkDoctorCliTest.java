package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class SparkDoctorCliTest {
    @Test
    void rootCommandPrintsUsageHint() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkDoctorCli(new PrintWriter(output, true)));

        int exitCode = commandLine.execute();

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("sparkdoctor --help"));
        assertTrue(output.toString().contains("sparkdoctor analyze --help"));
    }

    @Test
    void helpListsAnalyzeSubcommand() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkDoctorCli());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute("--help");

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("analyze"));
    }

    @Test
    void analyzeHelpListsOutputDirectoryOption() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkDoctorCli());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute("analyze", "--help");

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("--out"));
        assertTrue(output.toString().contains("<report-directory>"));
    }

    @Test
    void helpShowsCurrentCliVersion() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkDoctorCli());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute("--version");

        assertEquals(0, exitCode);
        assertEquals(
                "SparkDoctor " + SparkDoctorVersion.current() + System.lineSeparator(), output.toString());
    }
}
