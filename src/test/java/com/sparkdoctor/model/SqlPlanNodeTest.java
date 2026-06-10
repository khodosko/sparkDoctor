package com.sparkdoctor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPlanNodeTest {
    @Test
    void traversesPlanTreeAndComputesShapeMetrics() {
        SqlPlanNode plan = new SqlPlanNode(
                "AdaptiveSparkPlan",
                "AdaptiveSparkPlan isFinalPlan=true",
                List.of(),
                List.of(new SqlPlanNode(
                        "Exchange",
                        "Exchange hashpartitioning(id, 4)",
                        List.of(new SqlPlanMetric("shuffle bytes written", 44L, "size")),
                        List.of(new SqlPlanNode("Range", "Range (0, 1000, step=1, splits=8)", List.of(), List.of())))));

        assertEquals(3, plan.subtreeSize());
        assertEquals(3, plan.maxDepth());
        assertEquals(1, plan.countByName("Exchange"));
        assertEquals(
                List.of("AdaptiveSparkPlan", "Exchange", "Range"),
                plan.traverse().map(SqlPlanNode::nodeName).toList());
        assertEquals("shuffle bytes written", plan.children().get(0).metrics().get(0).name());
    }
}
