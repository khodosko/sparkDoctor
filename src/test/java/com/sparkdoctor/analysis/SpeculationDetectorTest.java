package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SpeculationDetectorTest {
    private final SpeculationDetector detector = new SpeculationDetector();

    @Test
    void detectsMediumSpeculationHeavyStages() {
        StageAnalysis stage = stage(10, 3, 3000L, 3);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("speculation_heavy", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(18, bottleneck.stageId());
        assertEquals(10, bottleneck.evidence().get("completedTasks"));
        assertEquals(3, bottleneck.evidence().get("speculativeTaskAttempts"));
        assertEquals(3, bottleneck.evidence().get("duplicateSuccessfulTaskAttempts"));
        assertEquals(3000L, bottleneck.evidence().get("speculativeTaskAttemptDurationMillis"));
        assertEquals(0.3, bottleneck.evidence().get("speculativeAttemptShare"));
    }

    @Test
    void detectsHighSpeculationHeavyStagesWhenShareIsVeryHigh() {
        StageAnalysis stage = stage(10, 5, 5000L, 5);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void ignoresSmallStagesToAvoidNoisyFindings() {
        StageAnalysis stage = stage(9, 3, 3000L, 3);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithFewSpeculativeAttempts() {
        StageAnalysis stage = stage(10, 2, 2000L, 2);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stage(
            int completedTasks,
            int speculativeTaskAttempts,
            long speculativeTaskAttemptDurationMillis,
            int duplicateSuccessfulTaskAttempts) {
        return new StageAnalysis(
                18,
                "speculation heavy",
                completedTasks,
                completedTasks,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                List.of(1000L),
                0,
                0L,
                List.of(),
                speculativeTaskAttempts,
                speculativeTaskAttemptDurationMillis,
                duplicateSuccessfulTaskAttempts,
                List.of(),
                List.of(),
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
