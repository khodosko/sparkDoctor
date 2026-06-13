package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanNode;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPlanPossibleMissedExchangeReuseDetectorTest {
    private final SqlPlanPossibleMissedExchangeReuseDetector detector =
            new SqlPlanPossibleMissedExchangeReuseDetector();

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
    void ignoresDuplicateGroupsWithoutExchangeInInterestingOperators() {
        SqlPlanNode duplicate = new SqlPlanNode(
                "HashAggregate",
                "HashAggregate [codegen id : 2]",
                List.of(),
                List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())));
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        duplicate,
                        new SqlPlanNode(
                                "HashAggregate",
                                "HashAggregate [codegen id: 9]",
                                List.of(),
                                List.of(new SqlPlanNode(
                                        "Range",
                                        "Range (0, 1000, step=1, splits=8)",
                                        List.of(),
                                        List.of()))))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void prefersExchangeRootedGroupsOverNonRootExchangeGroups() {
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        nonRootExchangeSubtree(),
                        nonRootExchangeSubtreeVariant(),
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                                "HashAggregate [codegen id : 2]"),
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                                "HashAggregate [codegen id: 9]"))));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertEquals(1, bottlenecks.size());
        assertEquals("possible_missed_exchange_reuse", bottlenecks.get(0).type());
        assertEquals("Exchange", bottlenecks.get(0).evidence().get("topDuplicateRoot"));
        assertEquals(1, bottlenecks.get(0).evidence().get("duplicateExchangeGroups"));
    }

    @Test
    void fallsBackToExchangeContainingGroupsWhenNoExchangeRootedCandidateExists() {
        SqlExecution sqlExecution = sqlExecutionWithLatestPlan(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(nonRootExchangeSubtree(), nonRootExchangeSubtreeVariant())));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("possible_missed_exchange_reuse", bottleneck.type());
        assertEquals("Project", bottleneck.evidence().get("topDuplicateRoot"));
        assertEquals(1, bottleneck.evidence().get("duplicateExchangeGroups"));
        assertEquals(List.of("Exchange"), bottleneck.evidence().get("topDuplicateInterestingOperators"));
    }

    @Test
    void emitsOneAggregatedBottleneckPerQualifyingExecution() {
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
        assertEquals("possible_missed_exchange_reuse", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(-1, bottleneck.stageId());
        assertEquals(3L, bottleneck.evidence().get("sqlExecutionId"));
        assertEquals(1, bottleneck.evidence().get("duplicateExchangeGroups"));
        assertEquals("Exchange", bottleneck.evidence().get("topDuplicateRoot"));
        assertEquals(2, bottleneck.evidence().get("topDuplicateCount"));
        assertEquals(3, bottleneck.evidence().get("topDuplicateSubtreeSize"));
        assertEquals(3, bottleneck.evidence().get("topDuplicateMaxDepth"));
        assertEquals(List.of("Exchange", "HashAggregate"), bottleneck.evidence().get("topDuplicateInterestingOperators"));
        assertEquals("low", bottleneck.evidence().get("confidence"));
        assertEquals("physical-plan-only signal", bottleneck.evidence().get("confidenceReason"));
        assertEquals(
                "Validate in Spark UI and query code before making optimizer conclusions.",
                bottleneck.evidence().get("validationRequired"));
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

    private SqlPlanNode nonRootExchangeSubtree() {
        return new SqlPlanNode(
                "Project",
                "Project [group_id#1L]",
                List.of(),
                List.of(new SqlPlanNode(
                        "Exchange",
                        "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                        List.of(),
                        List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())))));
    }

    private SqlPlanNode nonRootExchangeSubtreeVariant() {
        return new SqlPlanNode(
                "Project",
                "Project [group_id#99L]",
                List.of(),
                List.of(new SqlPlanNode(
                        "Exchange",
                        "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                        List.of(),
                        List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())))));
    }
}
