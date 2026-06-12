package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanOperatorSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqlExecutionsMarkdownWriterTest {
    @TempDir
    private Path tempDir;

    private final SqlExecutionsMarkdownWriter writer = new SqlExecutionsMarkdownWriter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesSqlExecutionsMarkdownIntoOutputDirectory() throws Exception {
        JsonNode plan = objectMapper.readTree("{\"nodeName\":\"AdaptiveSparkPlan\",\"children\":[]}");
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        3L,
                        3L,
                        "collect",
                        "Dataset.collectToPython",
                        1100L,
                        1700L,
                        600L,
                        "Initial Plan",
                        "Final Plan",
                        "",
                        List.of(
                                new SqlPlanOperatorSummary("AdaptiveSparkPlan", 1),
                                new SqlPlanOperatorSummary("Exchange", 2),
                                new SqlPlanOperatorSummary("HashAggregate", 2),
                                new SqlPlanOperatorSummary("Sort", 1),
                                new SqlPlanOperatorSummary("SortMergeJoin", 1)),
                        plan,
                        plan,
                        null)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        Path outputDirectory = tempDir.resolve("report");

        Path sqlExecutionsPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("sql-executions.md"), sqlExecutionsPath);
        assertTrue(Files.exists(sqlExecutionsPath));
        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(markdown.contains("# SparkDoctor SQL Executions"));
        assertTrue(markdown.contains("- Name: daily_job"));
        assertTrue(markdown.contains("## SQL Execution 3"));
        assertTrue(markdown.contains("- Description: collect"));
        assertTrue(markdown.contains("- Duration millis: 600"));
        assertTrue(markdown.contains("- Status: success"));
        assertTrue(markdown.contains("- DOT graph: `sql-execution-3.dot`"));
        assertTrue(markdown.contains("### Operator Summary"));
        assertTrue(markdown.contains("- Exchanges: 2"));
        assertTrue(markdown.contains("- Sorts: 1"));
        assertTrue(markdown.contains("- HashAggregates: 2"));
        assertTrue(markdown.contains("- Joins: 1"));
        assertTrue(markdown.contains("- Scans: 0"));
        assertTrue(markdown.contains("- AQE nodes: 1"));
        assertTrue(markdown.contains("Detailed operator counts:"));
        assertTrue(markdown.contains("- Exchange: 2"));
        assertTrue(markdown.contains("- HashAggregate: 2"));
        assertTrue(markdown.contains("### Details"));
        assertTrue(markdown.contains("Dataset.collectToPython"));
        assertTrue(markdown.contains("### Initial Physical Plan"));
        assertTrue(markdown.contains("Initial Plan"));
        assertTrue(markdown.contains("### Latest Physical Plan"));
        assertTrue(markdown.contains("Final Plan"));
    }

    @Test
    void writesRepeatedSubtreesSectionWhenLatestPlanRootHasDuplicates() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        3L,
                        3L,
                        "collect",
                        "Dataset.collectToPython",
                        1100L,
                        1700L,
                        600L,
                        "Initial Plan",
                        "Final Plan",
                        "",
                        List.of(),
                        null,
                        null,
                        null,
                        repeatedExchangePlan(),
                        java.util.Map.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Path sqlExecutionsPath = writer.write(tempDir.resolve("report-with-duplicates"), report);

        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(markdown.contains("### Repeated Subtrees"));
        assertTrue(markdown.contains(
                "- Root: Exchange; count=2; subtreeSize=3; maxDepth=3; contains=Exchange, HashAggregate, Range; interesting=Exchange, HashAggregate"));
    }

    @Test
    void omitsRepeatedSubtreesSectionWhenNoDuplicatesExist() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        3L,
                        3L,
                        "collect",
                        "Dataset.collectToPython",
                        1100L,
                        1700L,
                        600L,
                        "Initial Plan",
                        "Final Plan",
                        "",
                        List.of(),
                        null,
                        null,
                        null,
                        new SqlPlanNode("Project", "Project [id#1L]", List.of(), List.of(rangeNode("Range (0, 1000)"))),
                        java.util.Map.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Path sqlExecutionsPath = writer.write(tempDir.resolve("report-without-duplicates"), report);

        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(!markdown.contains("### Repeated Subtrees"));
    }

    @Test
    void writesRepeatedSubtreesInCollectorSortOrderAndOmitsEmptyInterestingSegment() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        3L,
                        3L,
                        "collect",
                        "Dataset.collectToPython",
                        1100L,
                        1700L,
                        600L,
                        "Initial Plan",
                        "Final Plan",
                        "",
                        List.of(),
                        null,
                        null,
                        null,
                        mixedDuplicatePlan(),
                        java.util.Map.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Path sqlExecutionsPath = writer.write(tempDir.resolve("report-sorted-duplicates"), report);

        String markdown = Files.readString(sqlExecutionsPath);
        int projectIndex = markdown.indexOf("- Root: Project; count=2; subtreeSize=3; maxDepth=3; contains=Filter, Project, Range");
        int filterIndex = markdown.indexOf("- Root: Filter; count=2; subtreeSize=2; maxDepth=2; contains=Filter, Range");
        int rangeIndex = markdown.indexOf("- Root: Range; count=2; subtreeSize=1; maxDepth=1; contains=Range");
        assertTrue(projectIndex >= 0);
        assertTrue(filterIndex > projectIndex);
        assertTrue(rangeIndex > filterIndex);
        assertTrue(!markdown.contains("Range; count=2; subtreeSize=1; maxDepth=1; contains=Range; interesting="));
    }

    @Test
    void writesNoSqlExecutionsMessageWhenReportHasNoSqlExecutions() throws Exception {
        AnalysisReport report = AnalysisReport.from(new ApplicationSummary("app-1", "daily_job", 1000L, 2500L));
        Path outputDirectory = tempDir.resolve("report");

        Path sqlExecutionsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(markdown.contains("No SQL executions found."));
    }

    private SqlPlanNode repeatedExchangePlan() {
        return new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        exchangeAggregateNode(
                                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                                "HashAggregate [codegen id : 2]"),
                        exchangeAggregateNode(
                                "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                                "HashAggregate [codegen id: 9]")));
    }

    private SqlPlanNode mixedDuplicatePlan() {
        return new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        projectFilterNode("Project [group_id#1L]", "Filter (group_id#1L > 0)", "Range (0, 1000)"),
                        projectFilterNode("Project [group_id#99L]", "Filter (group_id#99L > 0)", "Range (0, 1000)")));
    }

    private SqlPlanNode exchangeAggregateNode(String exchangeSimpleString, String aggregateSimpleString) {
        return new SqlPlanNode(
                "Exchange",
                exchangeSimpleString,
                List.of(),
                List.of(new SqlPlanNode(
                        "HashAggregate",
                        aggregateSimpleString,
                        List.of(),
                        List.of(rangeNode("Range (0, 1000, step=1, splits=8)")))));
    }

    private SqlPlanNode projectFilterNode(String projectSimpleString, String filterSimpleString, String rangeSimpleString) {
        return new SqlPlanNode(
                "Project",
                projectSimpleString,
                List.of(),
                List.of(new SqlPlanNode(
                        "Filter",
                        filterSimpleString,
                        List.of(),
                        List.of(rangeNode(rangeSimpleString)))));
    }

    private SqlPlanNode rangeNode(String simpleString) {
        return new SqlPlanNode("Range", simpleString, List.of(), List.of());
    }
}
