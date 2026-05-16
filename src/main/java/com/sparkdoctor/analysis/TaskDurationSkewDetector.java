package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TaskDurationSkewDetector {
    private static final int MIN_COMPLETED_TASKS = 10;
    private static final double SKEW_RATIO_THRESHOLD = 3.0;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (isSkewed(stage)) {
                double skewRatio = (double) stage.maxTaskDurationMillis() / stage.avgTaskDurationMillis();
                bottlenecks.add(new Bottleneck(
                        "task_duration_skew",
                        "medium",
                        stage.id(),
                        "Stage %d has task duration skew.".formatted(stage.id()),
                        Map.of(
                                "completedTasks", stage.completedTasks(),
                                "avgTaskDurationMillis", stage.avgTaskDurationMillis(),
                                "maxTaskDurationMillis", stage.maxTaskDurationMillis(),
                                "skewRatio", rounded(skewRatio))));
            }
        }

        return bottlenecks;
    }

    private boolean isSkewed(StageAnalysis stage) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS) {
            return false;
        }
        if (stage.avgTaskDurationMillis() == null || stage.maxTaskDurationMillis() == null) {
            return false;
        }
        if (stage.avgTaskDurationMillis() <= 0) {
            return false;
        }

        return stage.maxTaskDurationMillis() >= stage.avgTaskDurationMillis() * SKEW_RATIO_THRESHOLD;
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

