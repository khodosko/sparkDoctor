package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RetryWasteDetectorTest {
    private final RetryWasteDetector detector = new RetryWasteDetector();

    @Test
    void detectsMediumRetryWasteWhenFailedAttemptsExceedThresholds() {
        StageAnalysis stage = stage(7, 3, 30_000L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("retry_waste", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(3, bottleneck.evidence().get("failedTaskAttempts"));
        assertEquals(30_000L, bottleneck.evidence().get("failedTaskAttemptDurationMillis"));
        assertEquals(List.of("ExceptionFailure"), bottleneck.evidence().get("failedTaskAttemptReasons"));
        assertEquals(3, bottleneck.evidence().get("minFailedTaskAttempts"));
        assertEquals(30_000L, bottleneck.evidence().get("mediumFailedAttemptDurationMillis"));
        assertEquals(300_000L, bottleneck.evidence().get("highFailedAttemptDurationMillis"));
    }

    @Test
    void detectsHighRetryWasteWhenFailedAttemptDurationIsHigh() {
        StageAnalysis stage = stage(7, 3, 300_000L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void ignoresStagesWithTooFewFailedAttempts() {
        StageAnalysis stage = stage(7, 2, 30_000L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithTooLittleFailedAttemptDuration() {
        StageAnalysis stage = stage(7, 3, 29_999L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stage(int stageId, int failedTaskAttempts, long failedTaskAttemptDurationMillis) {
        return new StageAnalysis(
                stageId,
                "retry waste",
                3,
                3,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                List.of(1000L, 1000L, 1000L),
                failedTaskAttempts,
                failedTaskAttemptDurationMillis,
                List.of("ExceptionFailure"),
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                0L,
                0L,
                null,
                null);
    }
}
