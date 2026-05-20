package com.sparkdoctor.model;

import java.util.List;

public record StageAnalysis(
        int id,
        String name,
        Integer taskCount,
        int completedTasks,
        Long minTaskDurationMillis,
        Long maxTaskDurationMillis,
        Long avgTaskDurationMillis,
        long shuffleReadBytes,
        Long maxTaskShuffleReadBytes,
        Long medianTaskShuffleReadBytes,
        Long p95TaskShuffleReadBytes,
        Long p99TaskShuffleReadBytes,
        List<Long> taskShuffleReadBytes) {
    public StageAnalysis {
        taskShuffleReadBytes = taskShuffleReadBytes == null ? List.of() : List.copyOf(taskShuffleReadBytes);
    }

    public StageAnalysis(
            int id,
            String name,
            Integer taskCount,
            int completedTasks,
            Long minTaskDurationMillis,
            Long maxTaskDurationMillis,
            Long avgTaskDurationMillis,
            long shuffleReadBytes,
            Long maxTaskShuffleReadBytes) {
        this(
                id,
                name,
                taskCount,
                completedTasks,
                minTaskDurationMillis,
                maxTaskDurationMillis,
                avgTaskDurationMillis,
                shuffleReadBytes,
                maxTaskShuffleReadBytes,
                null,
                null,
                null,
                List.of());
    }
}
