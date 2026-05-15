package com.sparkdoctor;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "sparkscope",
        mixinStandardHelpOptions = true,
        version = "sparkscope 0.1.0",
        description = "Analyze Spark event logs and generate local performance reports.",
        subcommands = {AnalyzeCommand.class})
public final class SparkScopeCli implements Callable<Integer> {
    private final PrintWriter out;

    public SparkScopeCli() {
        this(new PrintWriter(System.out, true));
    }

    SparkScopeCli(PrintWriter out) {
        this.out = out;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SparkScopeCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        out.println("Run `sparkscope --help` for usage.");
        return 0;
    }
}

