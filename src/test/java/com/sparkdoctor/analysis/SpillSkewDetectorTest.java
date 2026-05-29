package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SpillSkewDetectorTest {
    private static final long MIB = 1024L * 1024L;

    private final SpillSkewDetector detector = new SpillSkewDetector();

    @Test
    void detectsMemorySpillSkewWhenMaxIsLargeAndMuchGreaterThanMedian() {
        StageAnalysis stage = stageWithSpill(
                7,
                List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 20L, 300L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("memory_spill_skew", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(10, bottleneck.evidence().get("completedTasks"));
        assertEquals(10L * MIB, bottleneck.evidence().get("medianTaskMemoryBytesSpilled"));
        assertEquals(300L * MIB, bottleneck.evidence().get("maxTaskMemoryBytesSpilled"));
        assertEquals(30.0, bottleneck.evidence().get("skewRatio"));
        assertEquals(256L * MIB, bottleneck.evidence().get("thresholdBytes"));
    }

    @Test
    void detectsDiskSpillSkewWhenMaxIsLargeAndMuchGreaterThanMedian() {
        StageAnalysis stage = stageWithSpill(
                8,
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
                List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 20L, 150L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("disk_spill_skew", bottleneck.type());
        assertEquals("high", bottleneck.severity());
        assertEquals(8, bottleneck.stageId());
        assertEquals(10L * MIB, bottleneck.evidence().get("medianTaskDiskBytesSpilled"));
        assertEquals(150L * MIB, bottleneck.evidence().get("maxTaskDiskBytesSpilled"));
        assertEquals(15.0, bottleneck.evidence().get("skewRatio"));
        assertEquals(128L * MIB, bottleneck.evidence().get("thresholdBytes"));
    }

    @Test
    void ignoresSmallStagesToAvoidNoisySpillSkewFindings() {
        StageAnalysis stage = stageWithSpill(7, List.of(10L, 300L), List.of(10L, 150L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresSpillSkewWhenMedianIsZero() {
        StageAnalysis stage = stageWithSpill(
                7,
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 300L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 150L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresSpillSkewBelowAbsoluteThreshold() {
        StageAnalysis stage = stageWithSpill(
                7,
                List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 20L, 200L),
                List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L, 10L, 20L, 100L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stageWithSpill(int stageId, List<Long> memorySpillMiB, List<Long> diskSpillMiB) {
        List<Long> memorySpillBytes = memorySpillMiB.stream().map(value -> value * MIB).sorted().toList();
        List<Long> diskSpillBytes = diskSpillMiB.stream().map(value -> value * MIB).sorted().toList();
        long memoryBytesSpilled = memorySpillBytes.stream().mapToLong(Long::longValue).sum();
        long diskBytesSpilled = diskSpillBytes.stream().mapToLong(Long::longValue).sum();

        return new StageAnalysis(
                stageId,
                "spill skew",
                memorySpillBytes.size(),
                memorySpillBytes.size(),
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                memoryBytesSpilled,
                diskBytesSpilled,
                memorySpillBytes.stream().max(Long::compareTo).orElse(null),
                diskSpillBytes.stream().max(Long::compareTo).orElse(null),
                median(memorySpillBytes),
                percentile(memorySpillBytes, 0.95),
                percentile(memorySpillBytes, 0.99),
                memorySpillBytes,
                median(diskSpillBytes),
                percentile(diskSpillBytes, 0.95),
                percentile(diskSpillBytes, 0.99),
                diskSpillBytes);
    }

    private Long median(List<Long> sortedValues) {
        int middleIndex = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middleIndex);
        }

        long lower = sortedValues.get(middleIndex - 1);
        long upper = sortedValues.get(middleIndex);
        return lower + (upper - lower) / 2;
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
}
