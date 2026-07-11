package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShufflePartitionSkewDetector {
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
                                "thresholdBytes", ShufflePartitionSkewPolicy.SKEWED_PARTITION_THRESHOLD_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean isSkewed(StageAnalysis stage) {
        return ShufflePartitionSkewPolicy.qualifies(stage);
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
