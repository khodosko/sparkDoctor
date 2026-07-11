package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OversizedShufflePartitionDetectorTest {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    private final OversizedShufflePartitionDetector detector = new OversizedShufflePartitionDetector();

    @Test
    void detectsMediumOversizedShufflePartitionsWhenP95ExceedsThreshold() {
        StageAnalysis stage = stage(
                7,
                300L * MIB,
                320L * MIB,
                340L * MIB,
                List.of(280L * MIB, 300L * MIB, 320L * MIB, 340L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("oversized_shuffle_partitions", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(4, bottleneck.evidence().get("shuffleReadingTasks"));
        assertEquals(300L * MIB, bottleneck.evidence().get("medianTaskShuffleReadBytes"));
        assertEquals(320L * MIB, bottleneck.evidence().get("p95TaskShuffleReadBytes"));
        assertEquals(340L * MIB, bottleneck.evidence().get("maxTaskShuffleReadBytes"));
        assertEquals(256L * MIB, bottleneck.evidence().get("mediumP95ShuffleReadThresholdBytes"));
        assertEquals(GIB, bottleneck.evidence().get("highP95ShuffleReadThresholdBytes"));
        assertEquals(2L * GIB, bottleneck.evidence().get("highMaxShuffleReadThresholdBytes"));
    }

    @Test
    void detectsHighOversizedShufflePartitionsWhenP95ExceedsHighThreshold() {
        StageAnalysis stage = stage(
                7,
                GIB,
                GIB,
                GIB,
                List.of(GIB, GIB, GIB, GIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void detectsHighOversizedShufflePartitionsWhenMaxExceedsHighThreshold() {
        StageAnalysis stage = stage(
                7,
                512L * MIB,
                512L * MIB,
                2L * GIB,
                List.of(256L * MIB, 512L * MIB, 2L * GIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void ignoresSingleShuffleReadingTask() {
        StageAnalysis stage = stage(
                7,
                2L * GIB,
                2L * GIB,
                2L * GIB,
                List.of(2L * GIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresSmallShufflePartitions() {
        StageAnalysis stage = stage(
                7,
                64L * MIB,
                128L * MIB,
                128L * MIB,
                List.of(64L * MIB, 128L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void detectsOversizedPartitionsWhenSmallStageLooksSkewedButCannotQualifyForSkewFinding() {
        StageAnalysis stage = stage(
                7,
                10L * MIB,
                300L * MIB,
                300L * MIB,
                List.of(10L * MIB, 10L * MIB, 300L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("oversized_shuffle_partitions", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(3, bottleneck.evidence().get("shuffleReadingTasks"));
        assertEquals(10L * MIB, bottleneck.evidence().get("medianTaskShuffleReadBytes"));
        assertEquals(300L * MIB, bottleneck.evidence().get("p95TaskShuffleReadBytes"));
        assertEquals(300L * MIB, bottleneck.evidence().get("maxTaskShuffleReadBytes"));
    }

    @Test
    void detectsSkewLikeOversizedPartitionsWithNineShuffleReadingTasks() {
        StageAnalysis stage = stage(
                7,
                10L * MIB,
                300L * MIB,
                300L * MIB,
                List.of(
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        300L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("oversized_shuffle_partitions", bottlenecks.get(0).type());
        assertEquals(9, bottlenecks.get(0).evidence().get("shuffleReadingTasks"));
    }

    @Test
    void ignoresSkewLikeOversizedPartitionsAtShuffleSkewTaskMinimum() {
        StageAnalysis stage = stage(
                7,
                10L * MIB,
                300L * MIB,
                300L * MIB,
                List.of(
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        300L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void detectsOversizedPartitionsAtShuffleSkewAbsoluteThreshold() {
        StageAnalysis stage = stage(
                7,
                10L * MIB,
                256L * MIB,
                256L * MIB,
                List.of(
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        10L * MIB,
                        256L * MIB));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("oversized_shuffle_partitions", bottlenecks.get(0).type());
        assertEquals("medium", bottlenecks.get(0).severity());
    }

    private StageAnalysis stage(
            int id,
            Long medianTaskShuffleReadBytes,
            Long p95TaskShuffleReadBytes,
            Long maxTaskShuffleReadBytes,
            List<Long> taskShuffleReadBytes) {
        return new StageAnalysis(
                id,
                "shuffle",
                taskShuffleReadBytes.size(),
                taskShuffleReadBytes.size(),
                1000L,
                2000L,
                1500L,
                taskShuffleReadBytes.stream().mapToLong(Long::longValue).sum(),
                maxTaskShuffleReadBytes,
                medianTaskShuffleReadBytes,
                p95TaskShuffleReadBytes,
                p95TaskShuffleReadBytes,
                taskShuffleReadBytes,
                0L,
                0L,
                null,
                null);
    }
}
