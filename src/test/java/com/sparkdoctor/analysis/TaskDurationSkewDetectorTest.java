package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TaskDurationSkewDetectorTest {
    private final TaskDurationSkewDetector detector = new TaskDurationSkewDetector();

    @Test
    void detectsTaskDurationSkewWhenMaxIsAtLeastThreeTimesAverage() {
        StageAnalysis stage = new StageAnalysis(7, "shuffle", 10, 10, 1000L, 9000L, 3000L, 0L, null);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("task_duration_skew", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(10, bottleneck.evidence().get("completedTasks"));
        assertEquals(3000L, bottleneck.evidence().get("avgTaskDurationMillis"));
        assertEquals(9000L, bottleneck.evidence().get("maxTaskDurationMillis"));
        assertEquals(3.0, bottleneck.evidence().get("skewRatio"));
    }

    @Test
    void ignoresSmallStagesToAvoidNoisySkewFindings() {
        StageAnalysis stage = new StageAnalysis(7, "tiny shuffle", 2, 2, 1000L, 9000L, 3000L, 0L, null);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesBelowSkewThreshold() {
        StageAnalysis stage = new StageAnalysis(7, "balanced shuffle", 10, 10, 1000L, 5000L, 3000L, 0L, null);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }
}
