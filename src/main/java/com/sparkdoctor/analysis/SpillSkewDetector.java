package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpillSkewDetector {
    private static final int MIN_COMPLETED_TASKS = 10;
    private static final double SKEW_RATIO_THRESHOLD = 5.0;
    private static final long MIB = 1024L * 1024L;
    private static final long MEMORY_SPILL_SKEW_THRESHOLD_BYTES = 256L * MIB;
    private static final long DISK_SPILL_SKEW_THRESHOLD_BYTES = 128L * MIB;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (isMemorySpillSkewed(stage)) {
                double skewRatio =
                        (double) stage.maxTaskMemoryBytesSpilled() / stage.medianTaskMemoryBytesSpilled();
                bottlenecks.add(new Bottleneck(
                        "memory_spill_skew",
                        "medium",
                        stage.id(),
                        "Stage %d has memory spill skew.".formatted(stage.id()),
                        Map.of(
                                "completedTasks", stage.completedTasks(),
                                "medianTaskMemoryBytesSpilled", stage.medianTaskMemoryBytesSpilled(),
                                "p95TaskMemoryBytesSpilled", valueOrZero(stage.p95TaskMemoryBytesSpilled()),
                                "maxTaskMemoryBytesSpilled", stage.maxTaskMemoryBytesSpilled(),
                                "skewRatio", rounded(skewRatio),
                                "thresholdBytes", MEMORY_SPILL_SKEW_THRESHOLD_BYTES)));
            }
            if (isDiskSpillSkewed(stage)) {
                double skewRatio = (double) stage.maxTaskDiskBytesSpilled() / stage.medianTaskDiskBytesSpilled();
                bottlenecks.add(new Bottleneck(
                        "disk_spill_skew",
                        "high",
                        stage.id(),
                        "Stage %d has disk spill skew.".formatted(stage.id()),
                        Map.of(
                                "completedTasks", stage.completedTasks(),
                                "medianTaskDiskBytesSpilled", stage.medianTaskDiskBytesSpilled(),
                                "p95TaskDiskBytesSpilled", valueOrZero(stage.p95TaskDiskBytesSpilled()),
                                "maxTaskDiskBytesSpilled", stage.maxTaskDiskBytesSpilled(),
                                "skewRatio", rounded(skewRatio),
                                "thresholdBytes", DISK_SPILL_SKEW_THRESHOLD_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean isMemorySpillSkewed(StageAnalysis stage) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS) {
            return false;
        }
        if (stage.medianTaskMemoryBytesSpilled() == null || stage.maxTaskMemoryBytesSpilled() == null) {
            return false;
        }
        if (stage.medianTaskMemoryBytesSpilled() <= 0) {
            return false;
        }
        if (stage.maxTaskMemoryBytesSpilled() <= MEMORY_SPILL_SKEW_THRESHOLD_BYTES) {
            return false;
        }

        return stage.maxTaskMemoryBytesSpilled()
                > stage.medianTaskMemoryBytesSpilled() * SKEW_RATIO_THRESHOLD;
    }

    private boolean isDiskSpillSkewed(StageAnalysis stage) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS) {
            return false;
        }
        if (stage.medianTaskDiskBytesSpilled() == null || stage.maxTaskDiskBytesSpilled() == null) {
            return false;
        }
        if (stage.medianTaskDiskBytesSpilled() <= 0) {
            return false;
        }
        if (stage.maxTaskDiskBytesSpilled() <= DISK_SPILL_SKEW_THRESHOLD_BYTES) {
            return false;
        }

        return stage.maxTaskDiskBytesSpilled() > stage.medianTaskDiskBytesSpilled() * SKEW_RATIO_THRESHOLD;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
