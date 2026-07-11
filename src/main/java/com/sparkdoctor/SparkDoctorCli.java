package com.sparkdoctor;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "sparkdoctor",
        mixinStandardHelpOptions = true,
        versionProvider = SparkDoctorVersionProvider.class,
        description = "Analyze Spark event logs and generate local performance reports.",
        subcommands = {AnalyzeCommand.class})
public final class SparkDoctorCli implements Callable<Integer> {
    private final PrintWriter out;

    public SparkDoctorCli() {
        this(new PrintWriter(System.out, true));
    }

    SparkDoctorCli(PrintWriter out) {
        this.out = out;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SparkDoctorCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        out.println("Run `sparkdoctor --help` for commands.");
        out.println("Run `sparkdoctor analyze --help` for analyze options.");
        return 0;
    }
}
