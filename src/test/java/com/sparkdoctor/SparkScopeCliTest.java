package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class SparkScopeCliTest {
    @Test
    void rootCommandPrintsUsageHint() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkScopeCli(new PrintWriter(output, true)));

        int exitCode = commandLine.execute();

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("sparkscope --help"));
    }

    @Test
    void helpListsAnalyzeSubcommand() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new SparkScopeCli());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute("--help");

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("analyze"));
    }
}

