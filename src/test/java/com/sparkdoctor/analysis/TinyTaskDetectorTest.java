package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

final class TinyTaskDetectorTest {
    private static final long MIB = 1024L * 1024L;

    private final TinyTaskDetector detector = new TinyTaskDetector();

    @Test
    void detectsTooManyTinyTasksWhenStageHasManyShortTasks() {
        StageAnalysis stage = stage(12, 100, 200L, 200L, List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("too_many_tiny_tasks", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(12, bottleneck.stageId());
        assertEquals(100, bottleneck.evidence().get("completedTasks"));
        assertEquals(200L, bottleneck.evidence().get("avgTaskDurationMillis"));
        assertEquals(200L, bottleneck.evidence().get("p95TaskDurationMillis"));
        assertEquals(0L, bottleneck.evidence().get("avgTaskShuffleReadBytes"));
    }

    @Test
    void detectsTinyTasksWhenShuffleReadPerTaskIsSmall() {
        StageAnalysis stage = stage(
                12,
                100,
                200L,
                200L,
                LongStream.range(0, 100).map(ignored -> 512L * 1024L).boxed().toList());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals(512L * 1024L, bottlenecks.get(0).evidence().get("avgTaskShuffleReadBytes"));
    }

    @Test
    void ignoresStagesWithTooFewTasks() {
        StageAnalysis stage = stage(12, 99, 200L, 200L, List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithLongAverageTaskDuration() {
        StageAnalysis stage = stage(12, 100, 501L, 501L, List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithLongP95TaskDuration() {
        StageAnalysis stage = stage(12, 100, 200L, 1001L, List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithLargeShuffleReadPerTask() {
        StageAnalysis stage = stage(
                12,
                100,
                200L,
                200L,
                LongStream.range(0, 100).map(ignored -> 2L * MIB).boxed().toList());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stage(
            int stageId,
            int completedTasks,
            long avgTaskDurationMillis,
            long p95TaskDurationMillis,
            List<Long> taskShuffleReadBytes) {
        long shuffleReadBytes = taskShuffleReadBytes.stream().mapToLong(Long::longValue).sum();
        Long maxTaskShuffleReadBytes = taskShuffleReadBytes.stream().max(Long::compareTo).orElse(null);

        return new StageAnalysis(
                stageId,
                "tiny tasks",
                completedTasks,
                completedTasks,
                100L,
                p95TaskDurationMillis,
                avgTaskDurationMillis,
                avgTaskDurationMillis,
                p95TaskDurationMillis,
                p95TaskDurationMillis,
                List.of(100L, p95TaskDurationMillis),
                shuffleReadBytes,
                maxTaskShuffleReadBytes,
                taskShuffleReadBytes.isEmpty() ? null : taskShuffleReadBytes.get(0),
                maxTaskShuffleReadBytes,
                maxTaskShuffleReadBytes,
                taskShuffleReadBytes,
                0L,
                0L,
                null,
                null);
    }
}
