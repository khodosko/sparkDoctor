package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanOperatorSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPlanExchangeDetectorTest {
    private final SqlPlanExchangeDetector detector = new SqlPlanExchangeDetector();

    @Test
    void detectsSqlPlansWithManyExchangeOperators() {
        SqlExecution sqlExecution = sqlExecution(List.of(
                new SqlPlanOperatorSummary("Exchange", 4),
                new SqlPlanOperatorSummary("HashAggregate", 2)));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("sql_many_exchanges", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(-1, bottleneck.stageId());
        assertEquals(3L, bottleneck.evidence().get("sqlExecutionId"));
        assertEquals(4, bottleneck.evidence().get("exchangeCount"));
        assertEquals(4, bottleneck.evidence().get("minExchangeOperators"));
    }

    @Test
    void ignoresSqlPlansWithOnlyAFewExchangeOperators() {
        SqlExecution sqlExecution = sqlExecution(List.of(new SqlPlanOperatorSummary("Exchange", 2)));

        List<Bottleneck> bottlenecks = detector.detect(List.of(sqlExecution));

        assertTrue(bottlenecks.isEmpty());
    }

    private SqlExecution sqlExecution(List<SqlPlanOperatorSummary> operatorSummaries) {
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
                operatorSummaries,
                null,
                null,
                null);
    }
}
