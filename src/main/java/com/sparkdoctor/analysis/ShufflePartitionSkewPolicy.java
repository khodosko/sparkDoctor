package com.sparkdoctor.analysis;

import com.sparkdoctor.model.StageAnalysis;

final class ShufflePartitionSkewPolicy {
    static final int MIN_SHUFFLE_READING_TASKS = 10;
    static final double SKEW_RATIO_THRESHOLD = 5.0;
    static final long SKEWED_PARTITION_THRESHOLD_BYTES = 256L * 1024L * 1024L;

    private ShufflePartitionSkewPolicy() {}

    static boolean qualifies(StageAnalysis stage) {
        if (stage.taskShuffleReadBytes().size() < MIN_SHUFFLE_READING_TASKS) {
            return false;
        }
        if (stage.medianTaskShuffleReadBytes() == null || stage.maxTaskShuffleReadBytes() == null) {
            return false;
        }
        if (stage.medianTaskShuffleReadBytes() <= 0
                || stage.maxTaskShuffleReadBytes() <= SKEWED_PARTITION_THRESHOLD_BYTES) {
            return false;
        }

        return stage.maxTaskShuffleReadBytes()
                > stage.medianTaskShuffleReadBytes() * SKEW_RATIO_THRESHOLD;
    }
}
