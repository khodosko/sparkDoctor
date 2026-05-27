package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LowShuffleParallelismDetector {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final long MEDIUM_TOTAL_SHUFFLE_READ_BYTES = GIB;
    private static final long HIGH_TOTAL_SHUFFLE_READ_BYTES = 10L * GIB;
    private static final int MEDIUM_MAX_SHUFFLE_READING_TASKS = 7;
    private static final int HIGH_MAX_SHUFFLE_READING_TASKS = 15;
    private static final long OVERSIZED_AVG_SHUFFLE_READ_BYTES = 256L * MIB;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (hasLowShuffleParallelism(stage)) {
                long avgShuffleReadBytes = avgShuffleReadBytes(stage);
                bottlenecks.add(new Bottleneck(
                        "low_shuffle_parallelism",
                        severity(stage),
                        stage.id(),
                        "Stage %d has low shuffle parallelism.".formatted(stage.id()),
                        Map.of(
                                "shuffleReadingTasks", stage.taskShuffleReadBytes().size(),
                                "shuffleReadBytes", stage.shuffleReadBytes(),
                                "avgTaskShuffleReadBytes", avgShuffleReadBytes,
                                "mediumTotalShuffleReadThresholdBytes", MEDIUM_TOTAL_SHUFFLE_READ_BYTES,
                                "highTotalShuffleReadThresholdBytes", HIGH_TOTAL_SHUFFLE_READ_BYTES,
                                "mediumMaxShuffleReadingTasks", MEDIUM_MAX_SHUFFLE_READING_TASKS,
                                "highMaxShuffleReadingTasks", HIGH_MAX_SHUFFLE_READING_TASKS)));
            }
        }

        return bottlenecks;
    }

    private boolean hasLowShuffleParallelism(StageAnalysis stage) {
        int shuffleReadingTasks = stage.taskShuffleReadBytes().size();
        if (shuffleReadingTasks == 0) {
            return false;
        }
        if (stage.shuffleReadBytes() < MEDIUM_TOTAL_SHUFFLE_READ_BYTES) {
            return false;
        }
        if (stage.shuffleReadBytes() >= HIGH_TOTAL_SHUFFLE_READ_BYTES
                && shuffleReadingTasks <= HIGH_MAX_SHUFFLE_READING_TASKS) {
            return true;
        }
        if (avgShuffleReadBytes(stage) >= OVERSIZED_AVG_SHUFFLE_READ_BYTES) {
            return false;
        }

        return shuffleReadingTasks <= MEDIUM_MAX_SHUFFLE_READING_TASKS;
    }

    private String severity(StageAnalysis stage) {
        int shuffleReadingTasks = stage.taskShuffleReadBytes().size();
        if (stage.shuffleReadBytes() >= HIGH_TOTAL_SHUFFLE_READ_BYTES
                && shuffleReadingTasks <= HIGH_MAX_SHUFFLE_READING_TASKS) {
            return "high";
        }

        return "medium";
    }

    private long avgShuffleReadBytes(StageAnalysis stage) {
        int shuffleReadingTasks = stage.taskShuffleReadBytes().size();
        if (shuffleReadingTasks == 0) {
            return 0L;
        }

        return stage.shuffleReadBytes() / shuffleReadingTasks;
    }
}
