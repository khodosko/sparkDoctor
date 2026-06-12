package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanNode;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPlanDuplicateSubtreeDetectorTest {
    private final SqlPlanDuplicateSubtreeDetector detector = new SqlPlanDuplicateSubtreeDetector();

    @Test
    void skipsExecutionsWithoutLatestPlanRoot() {
        SqlExecution sqlExecution = new SqlExecution(
                3L,
                3L,
                "collect",
                "Dataset.collectToPython",
                1000L,
                2000L,
                1000L,
                "Initial Plan",
                "Final Plan",
                "");

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresDuplicateGroupsThatAreTooSmall() {
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        new SqlPlanNode("Project", "Project [value#1L]", List.of(), List.of()),
                        new SqlPlanNode("Project", "Project [value#99L]", List.of(), List.of()))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresDuplicateGroupsWithoutInterestingOperators() {
        SqlPlanNode duplicate = new SqlPlanNode(
                "Filter",
                "Filter (group_id#1L > 0)",
                List.of(),
                List.of(new SqlPlanNode("Project", "Project [group_id#1L]", List.of(), List.of())));
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        duplicate,
                        new SqlPlanNode(
                                "Filter",
                                "Filter (group_id#99L > 0)",
                                List.of(),
                                List.of(new SqlPlanNode("Project", "Project [group_id#99L]", List.of(), List.of()))))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void emitsOneAggregatedBottleneckPerExecution() {
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                                "HashAggregate [codegen id : 2]"),
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                                "HashAggregate [codegen id: 9]"))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("duplicate_sql_subtree", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(-1, bottleneck.stageId());
        assertEquals(3L, bottleneck.evidence().get("sqlExecutionId"));
        assertEquals(1, bottleneck.evidence().get("duplicateGroups"));
        assertEquals("Exchange", bottleneck.evidence().get("topDuplicateRoot"));
        assertEquals(2, bottleneck.evidence().get("topDuplicateCount"));
        assertEquals(3, bottleneck.evidence().get("topDuplicateSubtreeSize"));
        assertEquals(3, bottleneck.evidence().get("topDuplicateMaxDepth"));
        assertEquals(List.of("Exchange", "HashAggregate"), bottleneck.evidence().get("topDuplicateInterestingOperators"));
    }

    @Test
    void usesTopQualifyingGroupWhenMultipleQualifyingGroupsExist() {
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                                "HashAggregate [codegen id : 2]"),
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                                "HashAggregate [codegen id: 9]"),
                        exchangeRange("Exchange hashpartitioning(id#1L, 4), [plan_id=18]"),
                        exchangeRange("Exchange hashpartitioning(id#99L, 4), [plan_id=67]"))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertEquals(1, bottlenecks.size());
        assertEquals("Exchange", bottlenecks.get(0).evidence().get("topDuplicateRoot"));
        assertEquals(3, bottlenecks.get(0).evidence().get("topDuplicateSubtreeSize"));
    }

    private SqlExecution sqlExecutionWithLatestPlan(SqlPlanNode latestPlanRoot) {
        return new SqlExecution(
                3L,
                3L,
                "collect",
                "Dataset.collectToPython",
                1000L,
                2000L,
                1000L,
                "Initial Plan",
                "Final Plan",
                "",
                List.of(),
                null,
                null,
                null,
                latestPlanRoot,
                java.util.Map.of());
    }

    private SqlPlanNode exchangeAggregateRange(String exchangeSimpleString, String aggregateSimpleString) {
        return new SqlPlanNode(
                "Exchange",
                exchangeSimpleString,
                List.of(),
                List.of(new SqlPlanNode(
                        "HashAggregate",
                        aggregateSimpleString,
                        List.of(),
                        List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())))));
    }

    private SqlPlanNode exchangeRange(String exchangeSimpleString) {
        return new SqlPlanNode(
                "Exchange",
                exchangeSimpleString,
                List.of(),
                List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())));
    }
}
