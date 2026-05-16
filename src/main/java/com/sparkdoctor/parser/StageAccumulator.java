package com.sparkdoctor.parser;

import com.sparkdoctor.model.StageAnalysis;

final class StageAccumulator {
    private final int id;
    private String name;
    private Integer taskCount;
    private int completedTasks;
    private long totalTaskDurationMillis;
    private Long minTaskDurationMillis;
    private Long maxTaskDurationMillis;
    private long shuffleReadBytes;
    private Long maxTaskShuffleReadBytes;

    StageAccumulator(int id) {
        this.id = id;
    }

    void updateDetails(String name, Integer taskCount) {
        this.name = name;
        this.taskCount = taskCount;
    }

    void addTaskDuration(long durationMillis) {
        completedTasks++;
        totalTaskDurationMillis += durationMillis;
        minTaskDurationMillis = minTaskDurationMillis == null
                ? durationMillis
                : Math.min(minTaskDurationMillis, durationMillis);
        maxTaskDurationMillis = maxTaskDurationMillis == null
                ? durationMillis
                : Math.max(maxTaskDurationMillis, durationMillis);
    }

    void addShuffleReadBytes(long taskShuffleReadBytes) {
        shuffleReadBytes += taskShuffleReadBytes;
        maxTaskShuffleReadBytes = maxTaskShuffleReadBytes == null
                ? taskShuffleReadBytes
                : Math.max(maxTaskShuffleReadBytes, taskShuffleReadBytes);
    }

    StageAnalysis toStageAnalysis() {
        Long avgTaskDurationMillis = completedTasks == 0 ? null : totalTaskDurationMillis / completedTasks;
        return new StageAnalysis(
                id,
                name,
                taskCount,
                completedTasks,
                minTaskDurationMillis,
                maxTaskDurationMillis,
                avgTaskDurationMillis,
                shuffleReadBytes,
                maxTaskShuffleReadBytes);
    }
}
