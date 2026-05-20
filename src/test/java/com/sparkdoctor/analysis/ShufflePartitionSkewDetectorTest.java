package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShufflePartitionSkewDetectorTest {
    private final ShufflePartitionSkewDetector detector = new ShufflePartitionSkewDetector();

    @Test
    void detectsShufflePartitionSkewWhenMaxIsLargeAndMuchGreaterThanMedian() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "skewed shuffle",
                10,
                10,
                1000L,
                2000L,
                1000L,
                405L * 1024L * 1024L,
                300L * 1024L * 1024L,
                10L * 1024L * 1024L,
                300L * 1024L * 1024L,
                300L * 1024L * 1024L,
                List.of(
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        15L * 1024L * 1024L,
                        300L * 1024L * 1024L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("shuffle_partition_skew", bottleneck.type());
        assertEquals("high", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(10, bottleneck.evidence().get("shuffleReadingTasks"));
        assertEquals(10L * 1024L * 1024L, bottleneck.evidence().get("medianTaskShuffleReadBytes"));
        assertEquals(300L * 1024L * 1024L, bottleneck.evidence().get("maxTaskShuffleReadBytes"));
        assertEquals(30.0, bottleneck.evidence().get("skewRatio"));
        assertEquals(256L * 1024L * 1024L, bottleneck.evidence().get("thresholdBytes"));
    }

    @Test
    void ignoresSmallStagesToAvoidNoisyShuffleSkewFindings() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "small shuffle",
                2,
                2,
                1000L,
                2000L,
                1000L,
                310L * 1024L * 1024L,
                300L * 1024L * 1024L,
                10L * 1024L * 1024L,
                300L * 1024L * 1024L,
                300L * 1024L * 1024L,
                List.of(10L * 1024L * 1024L, 300L * 1024L * 1024L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresShuffleSkewBelowAbsoluteByteThreshold() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "small byte skew",
                10,
                10,
                1000L,
                2000L,
                1000L,
                76L * 1024L * 1024L,
                40L * 1024L * 1024L,
                1L * 1024L * 1024L,
                40L * 1024L * 1024L,
                40L * 1024L * 1024L,
                List.of(
                        1L * 1024L * 1024L,
                        1L * 1024L * 1024L,
                        1L * 1024L * 1024L,
                        1L * 1024L * 1024L,
                        1L * 1024L * 1024L,
                        1L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        10L * 1024L * 1024L,
                        40L * 1024L * 1024L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresShuffleSkewBelowRatioThreshold() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "balanced large shuffle",
                10,
                10,
                1000L,
                2000L,
                1000L,
                3000L * 1024L * 1024L,
                300L * 1024L * 1024L,
                100L * 1024L * 1024L,
                300L * 1024L * 1024L,
                300L * 1024L * 1024L,
                List.of(
                        100L * 1024L * 1024L,
                        100L * 1024L * 1024L,
                        100L * 1024L * 1024L,
                        100L * 1024L * 1024L,
                        100L * 1024L * 1024L,
                        100L * 1024L * 1024L,
                        300L * 1024L * 1024L,
                        300L * 1024L * 1024L,
                        300L * 1024L * 1024L,
                        300L * 1024L * 1024L));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }
}
