package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpillPressureDetector {
    private static final int MIN_COMPLETED_TASKS = 2;
    private static final long MEMORY_SPILL_THRESHOLD_BYTES = 1024L * 1024L * 1024L;
    private static final long DISK_SPILL_THRESHOLD_BYTES = 256L * 1024L * 1024L;
    private static final long HIGH_DISK_SPILL_THRESHOLD_BYTES = 1024L * 1024L * 1024L;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (hasSpillPressure(stage)) {
                bottlenecks.add(new Bottleneck(
                        "spill_pressure",
                        severity(stage),
                        stage.id(),
                        "Stage %d has spill pressure.".formatted(stage.id()),
                        Map.of(
                                "completedTasks", stage.completedTasks(),
                                "memoryBytesSpilled", stage.memoryBytesSpilled(),
                                "diskBytesSpilled", stage.diskBytesSpilled(),
                                "maxTaskMemoryBytesSpilled", valueOrZero(stage.maxTaskMemoryBytesSpilled()),
                                "maxTaskDiskBytesSpilled", valueOrZero(stage.maxTaskDiskBytesSpilled()),
                                "memorySpillThresholdBytes", MEMORY_SPILL_THRESHOLD_BYTES,
                                "diskSpillThresholdBytes", DISK_SPILL_THRESHOLD_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean hasSpillPressure(StageAnalysis stage) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS) {
            return false;
        }

        return stage.diskBytesSpilled() >= DISK_SPILL_THRESHOLD_BYTES
                || stage.memoryBytesSpilled() >= MEMORY_SPILL_THRESHOLD_BYTES;
    }

    private String severity(StageAnalysis stage) {
        return stage.diskBytesSpilled() >= HIGH_DISK_SPILL_THRESHOLD_BYTES ? "high" : "medium";
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
