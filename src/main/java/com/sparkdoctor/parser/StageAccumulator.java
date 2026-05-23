package com.sparkdoctor.parser;

import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private final List<Long> taskShuffleReadBytes = new ArrayList<>();
    private long memoryBytesSpilled;
    private long diskBytesSpilled;
    private Long maxTaskMemoryBytesSpilled;
    private Long maxTaskDiskBytesSpilled;

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
        this.taskShuffleReadBytes.add(taskShuffleReadBytes);
        maxTaskShuffleReadBytes = maxTaskShuffleReadBytes == null
                ? taskShuffleReadBytes
                : Math.max(maxTaskShuffleReadBytes, taskShuffleReadBytes);
    }

    void addSpillBytes(long taskMemoryBytesSpilled, long taskDiskBytesSpilled) {
        memoryBytesSpilled += taskMemoryBytesSpilled;
        diskBytesSpilled += taskDiskBytesSpilled;
        maxTaskMemoryBytesSpilled = maxTaskMemoryBytesSpilled == null
                ? taskMemoryBytesSpilled
                : Math.max(maxTaskMemoryBytesSpilled, taskMemoryBytesSpilled);
        maxTaskDiskBytesSpilled = maxTaskDiskBytesSpilled == null
                ? taskDiskBytesSpilled
                : Math.max(maxTaskDiskBytesSpilled, taskDiskBytesSpilled);
    }

    StageAnalysis toStageAnalysis() {
        Long avgTaskDurationMillis = completedTasks == 0 ? null : totalTaskDurationMillis / completedTasks;
        List<Long> sortedTaskShuffleReadBytes = taskShuffleReadBytes.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        return new StageAnalysis(
                id,
                name,
                taskCount,
                completedTasks,
                minTaskDurationMillis,
                maxTaskDurationMillis,
                avgTaskDurationMillis,
                shuffleReadBytes,
                maxTaskShuffleReadBytes,
                median(sortedTaskShuffleReadBytes),
                percentile(sortedTaskShuffleReadBytes, 0.95),
                percentile(sortedTaskShuffleReadBytes, 0.99),
                sortedTaskShuffleReadBytes,
                memoryBytesSpilled,
                diskBytesSpilled,
                maxTaskMemoryBytesSpilled,
                maxTaskDiskBytesSpilled);
    }

    private Long median(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return null;
        }

        int middleIndex = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middleIndex);
        }

        long lower = sortedValues.get(middleIndex - 1);
        long upper = sortedValues.get(middleIndex);
        return lower + (upper - lower) / 2;
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }

        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
}
