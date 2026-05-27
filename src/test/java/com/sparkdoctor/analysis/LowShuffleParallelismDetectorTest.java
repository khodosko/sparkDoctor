package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LowShuffleParallelismDetectorTest {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    private final LowShuffleParallelismDetector detector = new LowShuffleParallelismDetector();

    @Test
    void detectsMediumLowShuffleParallelismWhenLargeShuffleHasFewTasks() {
        StageAnalysis stage = stage(7, List.of(200L * MIB, 200L * MIB, 200L * MIB, 200L * MIB, 200L * MIB, 200L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("low_shuffle_parallelism", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(6, bottleneck.evidence().get("shuffleReadingTasks"));
        assertEquals(1200L * MIB, bottleneck.evidence().get("shuffleReadBytes"));
        assertEquals(200L * MIB, bottleneck.evidence().get("avgTaskShuffleReadBytes"));
        assertEquals(GIB, bottleneck.evidence().get("mediumTotalShuffleReadThresholdBytes"));
        assertEquals(10L * GIB, bottleneck.evidence().get("highTotalShuffleReadThresholdBytes"));
        assertEquals(7, bottleneck.evidence().get("mediumMaxShuffleReadingTasks"));
        assertEquals(15, bottleneck.evidence().get("highMaxShuffleReadingTasks"));
    }

    @Test
    void detectsHighLowShuffleParallelismWhenHugeShuffleHasFewTasks() {
        StageAnalysis stage = stage(
                7,
                List.of(
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB,
                        800L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void ignoresSmallShuffleVolume() {
        StageAnalysis stage = stage(7, List.of(100L * MIB, 100L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresAdequateShuffleReadingTaskCount() {
        StageAnalysis stage = stage(
                7,
                List.of(
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB,
                        100L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresOversizedShufflePartitionsToAvoidDuplicateFindings() {
        StageAnalysis stage = stage(7, List.of(300L * MIB, 300L * MIB, 300L * MIB, 300L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesWithoutShuffleRead() {
        StageAnalysis stage = stage(7, List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stage(int id, List<Long> taskShuffleReadBytes) {
        long shuffleReadBytes = taskShuffleReadBytes.stream().mapToLong(Long::longValue).sum();
        Long maxTaskShuffleReadBytes = taskShuffleReadBytes.stream().max(Long::compareTo).orElse(null);
        Long median = taskShuffleReadBytes.isEmpty()
                ? null
                : taskShuffleReadBytes.get(taskShuffleReadBytes.size() / 2);
        Long p95 = maxTaskShuffleReadBytes;
        return new StageAnalysis(
                id,
                "shuffle",
                taskShuffleReadBytes.size(),
                taskShuffleReadBytes.size(),
                1000L,
                2000L,
                1500L,
                shuffleReadBytes,
                maxTaskShuffleReadBytes,
                median,
                p95,
                p95,
                taskShuffleReadBytes,
                0L,
                0L,
                null,
                null);
    }
}
