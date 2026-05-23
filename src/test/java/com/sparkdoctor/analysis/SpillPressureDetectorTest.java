package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SpillPressureDetectorTest {
    private final SpillPressureDetector detector = new SpillPressureDetector();

    @Test
    void detectsMediumSpillPressureWhenDiskSpillExceedsThreshold() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "spill",
                2,
                2,
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                128L * 1024L * 1024L,
                300L * 1024L * 1024L,
                96L * 1024L * 1024L,
                200L * 1024L * 1024L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("spill_pressure", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(7, bottleneck.stageId());
        assertEquals(2, bottleneck.evidence().get("completedTasks"));
        assertEquals(128L * 1024L * 1024L, bottleneck.evidence().get("memoryBytesSpilled"));
        assertEquals(300L * 1024L * 1024L, bottleneck.evidence().get("diskBytesSpilled"));
        assertEquals(96L * 1024L * 1024L, bottleneck.evidence().get("maxTaskMemoryBytesSpilled"));
        assertEquals(200L * 1024L * 1024L, bottleneck.evidence().get("maxTaskDiskBytesSpilled"));
    }

    @Test
    void detectsHighSpillPressureWhenDiskSpillExceedsHighThreshold() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "severe spill",
                2,
                2,
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                128L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L,
                96L * 1024L * 1024L,
                1024L * 1024L * 1024L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("high", bottlenecks.get(0).severity());
    }

    @Test
    void detectsSpillPressureWhenMemorySpillExceedsThresholdWithoutDiskSpill() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "memory spill",
                2,
                2,
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                2L * 1024L * 1024L * 1024L,
                0L,
                1024L * 1024L * 1024L,
                0L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        assertEquals("medium", bottlenecks.get(0).severity());
    }

    @Test
    void ignoresSmallStagesToAvoidNoisySpillFindings() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "single task spill",
                1,
                1,
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                2L * 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresStagesBelowSpillThresholds() {
        StageAnalysis stage = new StageAnalysis(
                7,
                "small spill",
                2,
                2,
                1000L,
                2000L,
                1500L,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                512L * 1024L * 1024L,
                128L * 1024L * 1024L,
                256L * 1024L * 1024L,
                64L * 1024L * 1024L);

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }
}
