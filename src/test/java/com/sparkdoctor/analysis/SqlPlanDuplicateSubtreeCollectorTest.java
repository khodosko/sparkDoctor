package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.DuplicateSqlSubtree;
import com.sparkdoctor.model.SqlPlanNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SqlPlanDuplicateSubtreeCollectorTest {
    private final SqlPlanDuplicateSubtreeCollector collector = new SqlPlanDuplicateSubtreeCollector();

    @Test
    void returnsEmptyListWhenNoDuplicateSubtreesExist() {
        List<DuplicateSqlSubtree> duplicates = collector.findDuplicates(new SqlPlanNode(
                "Project",
                "Project [id#1L]",
                List.of(),
                List.of(new SqlPlanNode("Range", "Range (0, 1000)", List.of(), List.of()))));

        assertEquals(List.of(), duplicates);
    }

    @Test
    void returnsOneDuplicateGroupForRepeatedSubtree() {
        SqlPlanNode duplicate = exchangeAggregateRange(
                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                "HashAggregate [codegen id : 2]");
        SqlPlanNode root = new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(duplicate, exchangeAggregateRange(
                        "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                        "HashAggregate [codegen id: 9]")));

        List<DuplicateSqlSubtree> duplicates = collector.findDuplicates(root);

        assertEquals(3, duplicates.size());
        assertEquals("Exchange", duplicates.get(0).rootNodeName());
        assertEquals(2, duplicates.get(0).count());
        assertEquals(3, duplicates.get(0).subtreeSize());
        assertEquals(3, duplicates.get(0).maxDepth());
    }

    @Test
    void collectsOperatorNamesAndInterestingOperatorsDeterministically() {
        SqlPlanNode duplicate = exchangeAggregateRange(
                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                "HashAggregate [codegen id : 2]");
        SqlPlanNode root = new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(duplicate, exchangeAggregateRange(
                        "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                        "HashAggregate [codegen id: 9]")));

        DuplicateSqlSubtree subtree = collector.findDuplicates(root).get(0);

        assertEquals(Set.of("Exchange", "HashAggregate", "Range"), subtree.operatorNames());
        assertEquals(Set.of("Exchange", "HashAggregate"), subtree.interestingOperators());
    }

    @Test
    void returnsNonInterestingDuplicatesToo() {
        SqlPlanNode duplicate = new SqlPlanNode("Project", "Project [value#1L]", List.of(), List.of());
        SqlPlanNode root = new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        duplicate,
                        new SqlPlanNode("Project", "Project [value#99L]", List.of(), List.of())));

        List<DuplicateSqlSubtree> duplicates = collector.findDuplicates(root);

        assertEquals(1, duplicates.size());
        assertTrue(duplicates.get(0).interestingOperators().isEmpty());
        assertEquals(Set.of("Project"), duplicates.get(0).operatorNames());
    }

    @Test
    void sortsDuplicateGroupsByImpactishOrder() {
        SqlPlanNode largeDuplicate = exchangeAggregateRange(
                "Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]",
                "HashAggregate [codegen id : 2]");
        SqlPlanNode deepDuplicate = new SqlPlanNode(
                "Project",
                "Project [group_id#1L]",
                List.of(),
                List.of(new SqlPlanNode(
                        "Filter",
                        "Filter (group_id#1L > 0)",
                        List.of(),
                        List.of(new SqlPlanNode("Range", "Range (0, 1000)", List.of(), List.of())))));
        SqlPlanNode shallowDuplicate = new SqlPlanNode("Range", "Range (0, 2000)", List.of(), List.of());
        SqlPlanNode root = new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(
                        largeDuplicate,
                        exchangeAggregateRange(
                                "Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]",
                                "HashAggregate [codegen id: 9]"),
                        deepDuplicate,
                        new SqlPlanNode(
                                "Project",
                                "Project [group_id#99L]",
                                List.of(),
                                List.of(new SqlPlanNode(
                                        "Filter",
                                        "Filter (group_id#99L > 0)",
                                        List.of(),
                                        List.of(new SqlPlanNode("Range", "Range (0, 1000)", List.of(), List.of()))))),
                        shallowDuplicate,
                        new SqlPlanNode("Range", "Range (0, 2000)", List.of(), List.of()),
                        new SqlPlanNode("Range", "Range (0, 2000)", List.of(), List.of())));

        List<DuplicateSqlSubtree> duplicates = collector.findDuplicates(root);

        assertEquals(
                List.of("Exchange", "Project", "Filter", "HashAggregate", "Range", "Range", "Range"),
                duplicates.stream().map(DuplicateSqlSubtree::rootNodeName).toList());
        assertEquals(List.of(2, 2, 2, 2, 3, 2, 2), duplicates.stream().map(DuplicateSqlSubtree::count).toList());
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
}
