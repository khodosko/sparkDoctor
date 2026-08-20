package com.sparkdoctor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sparkdoctor.model.Bottleneck;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BottleneckScopeTest {
    @Test
    void displaysStageScope() {
        Bottleneck bottleneck = new Bottleneck("spill_pressure", "medium", 4, "spill", Map.of());

        assertEquals("stage 4", BottleneckScope.display(bottleneck));
    }

    @Test
    void displaysSqlExecutionScopeFromEvidence() {
        Bottleneck bottleneck = new Bottleneck(
                "sql_many_exchanges",
                "medium",
                -1,
                "exchanges",
                Map.of("sqlExecutionId", 9L));

        assertEquals("SQL execution 9", BottleneckScope.display(bottleneck));
    }

    @Test
    void displaysApplicationScopeWithoutSqlEvidence() {
        Bottleneck bottleneck = new Bottleneck("failed_job", "high", -1, "failed", Map.of("jobId", 3));

        assertEquals("application", BottleneckScope.display(bottleneck));
    }
}
