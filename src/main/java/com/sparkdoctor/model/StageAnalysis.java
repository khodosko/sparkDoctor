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
        List<Long> taskShuffleReadBytes,
        long memoryBytesSpilled,
        long diskBytesSpilled,
        Long maxTaskMemoryBytesSpilled,
        Long maxTaskDiskBytesSpilled,
        Long medianTaskMemoryBytesSpilled,
        Long p95TaskMemoryBytesSpilled,
        Long p99TaskMemoryBytesSpilled,
        List<Long> taskMemoryBytesSpilled,
        Long medianTaskDiskBytesSpilled,
        Long p95TaskDiskBytesSpilled,
        Long p99TaskDiskBytesSpilled,
        List<Long> taskDiskBytesSpilled) {
    public StageAnalysis {
        taskShuffleReadBytes = taskShuffleReadBytes == null ? List.of() : List.copyOf(taskShuffleReadBytes);
        taskMemoryBytesSpilled = taskMemoryBytesSpilled == null ? List.of() : List.copyOf(taskMemoryBytesSpilled);
        taskDiskBytesSpilled = taskDiskBytesSpilled == null ? List.of() : List.copyOf(taskDiskBytesSpilled);
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
            Long maxTaskShuffleReadBytes,
            Long medianTaskShuffleReadBytes,
            Long p95TaskShuffleReadBytes,
            Long p99TaskShuffleReadBytes,
            List<Long> taskShuffleReadBytes,
            long memoryBytesSpilled,
            long diskBytesSpilled,
            Long maxTaskMemoryBytesSpilled,
            Long maxTaskDiskBytesSpilled) {
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
                medianTaskShuffleReadBytes,
                p95TaskShuffleReadBytes,
                p99TaskShuffleReadBytes,
                taskShuffleReadBytes,
                memoryBytesSpilled,
                diskBytesSpilled,
                maxTaskMemoryBytesSpilled,
                maxTaskDiskBytesSpilled,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of());
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
                List.of(),
                0L,
                0L,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of());
    }
}
