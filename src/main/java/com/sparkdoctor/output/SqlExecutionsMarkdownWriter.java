package com.sparkdoctor.output;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanOperatorSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public final class SqlExecutionsMarkdownWriter {
    private static final String SQL_EXECUTIONS_FILE_NAME = "sql-executions.md";
    private static final List<OperatorCategory> OPERATOR_CATEGORIES = List.of(
            new OperatorCategory("Exchanges", operator -> operator.name().contains("Exchange")),
            new OperatorCategory("Sorts", operator -> "Sort".equals(operator.name())),
            new OperatorCategory("HashAggregates", operator -> operator.name().contains("HashAggregate")),
            new OperatorCategory("Joins", operator -> operator.name().contains("Join")),
            new OperatorCategory("Scans", operator -> operator.name().contains("Scan")),
            new OperatorCategory("AQE nodes", operator -> isAqeOperator(operator.name())));

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
            appendOperatorSummary(markdown, sqlExecution);
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

    private void appendOperatorSummary(StringBuilder markdown, SqlExecution sqlExecution) {
        if (sqlExecution.operatorSummaries().isEmpty()) {
            return;
        }

        markdown.append("### Operator Summary\n\n");
        for (OperatorCategory category : OPERATOR_CATEGORIES) {
            markdown.append("- ")
                    .append(category.label())
                    .append(": ")
                    .append(category.count(sqlExecution.operatorSummaries()))
                    .append("\n");
        }
        markdown.append("\n");

        markdown.append("Detailed operator counts:\n\n");
        for (SqlPlanOperatorSummary operator : sqlExecution.operatorSummaries()) {
            markdown.append("- ")
                    .append(operator.name())
                    .append(": ")
                    .append(operator.count())
                    .append("\n");
        }
        markdown.append("\n");
    }

    private static boolean isAqeOperator(String name) {
        return name.equals("AdaptiveSparkPlan")
                || name.startsWith("AQE")
                || name.endsWith("QueryStage");
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

    private record OperatorCategory(String label, Predicate<SqlPlanOperatorSummary> matches) {
        private int count(List<SqlPlanOperatorSummary> operatorSummaries) {
            return operatorSummaries.stream()
                    .filter(matches)
                    .mapToInt(SqlPlanOperatorSummary::count)
                    .sum();
        }
    }
}
