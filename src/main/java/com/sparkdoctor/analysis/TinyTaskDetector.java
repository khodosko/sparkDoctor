package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TinyTaskDetector {
    private static final int MIN_COMPLETED_TASKS = 100;
    private static final long MAX_AVG_TASK_DURATION_MILLIS = 500L;
    private static final long MAX_P95_TASK_DURATION_MILLIS = 1000L;
    private static final long MAX_AVG_SHUFFLE_READ_BYTES = 1024L * 1024L;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (hasTooManyTinyTasks(stage)) {
                bottlenecks.add(new Bottleneck(
                        "too_many_tiny_tasks",
                        "medium",
                        stage.id(),
                        "Stage %d has too many tiny tasks.".formatted(stage.id()),
                        Map.of(
                                "completedTasks", stage.completedTasks(),
                                "avgTaskDurationMillis", valueOrZero(stage.avgTaskDurationMillis()),
                                "p95TaskDurationMillis", valueOrZero(stage.p95TaskDurationMillis()),
                                "avgTaskShuffleReadBytes", avgShuffleReadBytes(stage),
                                "minCompletedTasks", MIN_COMPLETED_TASKS,
                                "maxAvgTaskDurationMillis", MAX_AVG_TASK_DURATION_MILLIS,
                                "maxP95TaskDurationMillis", MAX_P95_TASK_DURATION_MILLIS,
                                "maxAvgShuffleReadBytes", MAX_AVG_SHUFFLE_READ_BYTES)));
            }
        }

        return bottlenecks;
    }

    private boolean hasTooManyTinyTasks(StageAnalysis stage) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS) {
            return false;
        }
        if (stage.avgTaskDurationMillis() == null || stage.p95TaskDurationMillis() == null) {
            return false;
        }
        if (stage.avgTaskDurationMillis() > MAX_AVG_TASK_DURATION_MILLIS) {
            return false;
        }
        if (stage.p95TaskDurationMillis() > MAX_P95_TASK_DURATION_MILLIS) {
            return false;
        }

        return stage.taskShuffleReadBytes().isEmpty() || avgShuffleReadBytes(stage) <= MAX_AVG_SHUFFLE_READ_BYTES;
    }

    private long avgShuffleReadBytes(StageAnalysis stage) {
        if (stage.taskShuffleReadBytes().isEmpty()) {
            return 0L;
        }

        return stage.shuffleReadBytes() / stage.taskShuffleReadBytes().size();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
