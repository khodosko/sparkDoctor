package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.FailedJob;
import com.sparkdoctor.model.FailedStage;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FailureDetectorTest {
    private final FailureDetector detector = new FailureDetector();

    @Test
    void detectsFailedJobsAsHighSeverityBottlenecks() {
        List<Bottleneck> bottlenecks = detector.detect(
                List.of(new FailedJob(3, "JobFailed")),
                List.of());

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("failed_job", bottleneck.type());
        assertEquals("high", bottleneck.severity());
        assertEquals(-1, bottleneck.stageId());
        assertEquals("Job 3 failed.", bottleneck.message());
        assertEquals(3, bottleneck.evidence().get("jobId"));
        assertEquals("JobFailed", bottleneck.evidence().get("result"));
    }

    @Test
    void detectsFailedStagesAsHighSeverityBottlenecks() {
        List<Bottleneck> bottlenecks = detector.detect(
                List.of(),
                List.of(new FailedStage(8, "shuffle", "Fetch failed")));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("failed_stage", bottleneck.type());
        assertEquals("high", bottleneck.severity());
        assertEquals(8, bottleneck.stageId());
        assertEquals("Stage 8 failed.", bottleneck.message());
        assertEquals(8, bottleneck.evidence().get("stageId"));
        assertEquals("shuffle", bottleneck.evidence().get("stageName"));
        assertEquals("Fetch failed", bottleneck.evidence().get("failureReason"));
    }

    @Test
    void ignoresEmptyFailures() {
        List<Bottleneck> bottlenecks = detector.detect(List.of(), List.of());

        assertTrue(bottlenecks.isEmpty());
    }
}
