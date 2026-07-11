package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OversizedShufflePartitionDetector {
    private static final int MIN_SHUFFLE_READING_TASKS = 2;
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final long MEDIUM_P95_SHUFFLE_READ_BYTES =
            ShufflePartitionSkewPolicy.SKEWED_PARTITION_THRESHOLD_BYTES;
    private static final long HIGH_P95_SHUFFLE_READ_BYTES = GIB;
    private static final long HIGH_MAX_SHUFFLE_READ_BYTES = 2L * GIB;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (hasOversizedShufflePartitions(stage)) {
                bottlenecks.add(new Bottleneck(
                        "oversized_shuffle_partitions",
                        severity(stage),
                        stage.id(),
                        "Stage %d has oversized shuffle partitions.".formatted(stage.id()),
                        Map.of(
                                "shuffleReadingTasks", stage.taskShuffleReadBytes().size(),
                                "medianTaskShuffleReadBytes", valueOrZero(stage.medianTaskShuffleReadBytes()),
                                "p95TaskShuffleReadBytes", valueOrZero(stage.p95TaskShuffleReadBytes()),
                                "maxTaskShuffleReadBytes", valueOrZero(stage.maxTaskShuffleReadBytes()),
                                "mediumP95ShuffleReadThresholdBytes", MEDIUM_P95_SHUFFLE_READ_BYTES,
                                "highP95ShuffleReadThresholdBytes", HIGH_P95_SHUFFLE_READ_BYTES,
                                "highMaxShuffleReadThresholdBytes", HIGH_MAX_SHUFFLE_READ_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean hasOversizedShufflePartitions(StageAnalysis stage) {
        if (stage.taskShuffleReadBytes().size() < MIN_SHUFFLE_READING_TASKS) {
            return false;
        }
        if (stage.p95TaskShuffleReadBytes() == null || stage.maxTaskShuffleReadBytes() == null) {
            return false;
        }
        if (ShufflePartitionSkewPolicy.qualifies(stage)) {
            return false;
        }

        return stage.p95TaskShuffleReadBytes() >= MEDIUM_P95_SHUFFLE_READ_BYTES
                || stage.maxTaskShuffleReadBytes() >= HIGH_MAX_SHUFFLE_READ_BYTES;
    }

    private String severity(StageAnalysis stage) {
        if (stage.p95TaskShuffleReadBytes() >= HIGH_P95_SHUFFLE_READ_BYTES
                || stage.maxTaskShuffleReadBytes() >= HIGH_MAX_SHUFFLE_READ_BYTES) {
            return "high";
        }

        return "medium";
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
