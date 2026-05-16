package com.sparkdoctor.model;

public record StageAnalysis(
        int id,
        String name,
        Integer taskCount,
        int completedTasks,
        Long minTaskDurationMillis,
        Long maxTaskDurationMillis,
        Long avgTaskDurationMillis,
        long shuffleReadBytes,
        Long maxTaskShuffleReadBytes) {}
