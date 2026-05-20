package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShufflePartitionSkewDetector {
    private static final int MIN_SHUFFLE_READING_TASKS = 10;
    private static final double SKEW_RATIO_THRESHOLD = 5.0;
    private static final long SKEWED_PARTITION_THRESHOLD_BYTES = 256L * 1024L * 1024L;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (isSkewed(stage)) {
                double skewRatio =
                        (double) stage.maxTaskShuffleReadBytes() / stage.medianTaskShuffleReadBytes();
                bottlenecks.add(new Bottleneck(
                        "shuffle_partition_skew",
                        "high",
                        stage.id(),
                        "Stage %d has shuffle partition skew.".formatted(stage.id()),
                        Map.of(
                                "shuffleReadingTasks", stage.taskShuffleReadBytes().size(),
                                "medianTaskShuffleReadBytes", stage.medianTaskShuffleReadBytes(),
                                "maxTaskShuffleReadBytes", stage.maxTaskShuffleReadBytes(),
                                "skewRatio", rounded(skewRatio),
                                "thresholdBytes", SKEWED_PARTITION_THRESHOLD_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean isSkewed(StageAnalysis stage) {
        if (stage.taskShuffleReadBytes().size() < MIN_SHUFFLE_READING_TASKS) {
            return false;
        }
        if (stage.medianTaskShuffleReadBytes() == null || stage.maxTaskShuffleReadBytes() == null) {
            return false;
        }
        if (stage.medianTaskShuffleReadBytes() <= 0) {
            return false;
        }
        if (stage.maxTaskShuffleReadBytes() <= SKEWED_PARTITION_THRESHOLD_BYTES) {
            return false;
        }

        return stage.maxTaskShuffleReadBytes() > stage.medianTaskShuffleReadBytes() * SKEW_RATIO_THRESHOLD;
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
