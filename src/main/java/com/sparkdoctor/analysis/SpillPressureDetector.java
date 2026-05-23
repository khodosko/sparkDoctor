package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpillPressureDetector {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final long MEDIUM_DISK_SPILL_BYTES = 256L * MIB;
    private static final long HIGH_DISK_SPILL_BYTES = GIB;
    private static final long MEDIUM_MEMORY_SPILL_BYTES = GIB;
    private static final long SINGLE_TASK_HIGH_MEMORY_SPILL_BYTES = 4L * GIB;
    private static final long HIGH_MAX_TASK_DISK_SPILL_BYTES = 512L * MIB;

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
                                "mediumMemorySpillThresholdBytes", MEDIUM_MEMORY_SPILL_BYTES,
                                "mediumDiskSpillThresholdBytes", MEDIUM_DISK_SPILL_BYTES,
                                "highDiskSpillThresholdBytes", HIGH_DISK_SPILL_BYTES,
                                "highMaxTaskDiskSpillThresholdBytes", HIGH_MAX_TASK_DISK_SPILL_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean hasSpillPressure(StageAnalysis stage) {
        if (stage.completedTasks() == 1) {
            return stage.diskBytesSpilled() >= HIGH_DISK_SPILL_BYTES
                    || stage.memoryBytesSpilled() >= SINGLE_TASK_HIGH_MEMORY_SPILL_BYTES;
        }
        if (stage.completedTasks() >= 2) {
            return stage.diskBytesSpilled() >= MEDIUM_DISK_SPILL_BYTES
                    || stage.memoryBytesSpilled() >= MEDIUM_MEMORY_SPILL_BYTES;
        }

        return false;
    }

    private String severity(StageAnalysis stage) {
        if (stage.diskBytesSpilled() >= HIGH_DISK_SPILL_BYTES
                || valueOrZero(stage.maxTaskDiskBytesSpilled()) >= HIGH_MAX_TASK_DISK_SPILL_BYTES) {
            return "high";
        }

        return "medium";
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
