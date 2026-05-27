package com.sparkdoctor.output;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.SqlExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SqlExecutionsMarkdownWriter {
    private static final String SQL_EXECUTIONS_FILE_NAME = "sql-executions.md";

    public Path write(Path outputDirectory, AnalysisReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        Path sqlExecutionsPath = outputDirectory.resolve(SQL_EXECUTIONS_FILE_NAME);
        Files.writeString(sqlExecutionsPath, markdown(report));
        return sqlExecutionsPath;
    }

    private String markdown(AnalysisReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# SparkDoctor SQL Executions\n\n");
        markdown.append("## Application\n\n");
        markdown.append("- Name: ").append(display(report.application().name())).append("\n");
        markdown.append("- ID: ").append(display(report.application().id())).append("\n");
        markdown.append("- Duration: ").append(display(report.application().durationMillis())).append(" ms\n\n");

        if (report.sqlExecutions().isEmpty()) {
            markdown.append("No SQL executions found.\n");
            return markdown.toString();
        }

        for (SqlExecution sqlExecution : report.sqlExecutions()) {
            markdown.append("## SQL Execution ").append(sqlExecution.id()).append("\n\n");
            markdown.append("- Description: ").append(display(sqlExecution.description())).append("\n");
            markdown.append("- Root execution ID: ").append(display(sqlExecution.rootExecutionId())).append("\n");
            markdown.append("- Start time millis: ").append(display(sqlExecution.startTimeMillis())).append("\n");
            markdown.append("- End time millis: ").append(display(sqlExecution.endTimeMillis())).append("\n");
            markdown.append("- Duration millis: ").append(display(sqlExecution.durationMillis())).append("\n");
            markdown.append("- Status: ").append(status(sqlExecution)).append("\n");
            if (sqlExecution.hasSparkPlanInfo()) {
                markdown.append("- DOT graph: `")
                        .append(SqlPlanDotWriter.fileName(sqlExecution))
                        .append("`\n");
            }
            markdown.append("\n");
            if (hasText(sqlExecution.details())) {
                markdown.append("### Details\n\n");
                markdown.append("```text\n");
                markdown.append(sqlExecution.details()).append("\n");
                markdown.append("```\n\n");
            }
            appendPlan(markdown, "Initial Physical Plan", sqlExecution.physicalPlanDescription());
            appendPlan(markdown, "Latest Physical Plan", sqlExecution.latestPhysicalPlanDescription());
        }

        return markdown.toString();
    }

    private void appendPlan(StringBuilder markdown, String title, String plan) {
        if (!hasText(plan)) {
            return;
        }

        markdown.append("### ").append(title).append("\n\n");
        markdown.append("```text\n");
        markdown.append(plan);
        if (!plan.endsWith("\n")) {
            markdown.append("\n");
        }
        markdown.append("```\n\n");
    }

    private String status(SqlExecution sqlExecution) {
        if (hasText(sqlExecution.errorMessage())) {
            return "failed";
        }

        return sqlExecution.endTimeMillis() == null ? "unknown" : "success";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String display(String value) {
        return hasText(value) ? value : "unknown";
    }

    private String display(Long value) {
        return value == null ? "unknown" : Long.toString(value);
    }
}
