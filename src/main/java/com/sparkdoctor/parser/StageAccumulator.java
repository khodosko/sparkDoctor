package com.sparkdoctor.parser;

import com.sparkdoctor.model.StageAnalysis;
import com.sparkdoctor.model.WorkerSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StageAccumulator {
    private final int id;
    private String name;
    private Integer taskCount;
    private int completedTasks;
    private long totalTaskDurationMillis;
    private Long minTaskDurationMillis;
    private Long maxTaskDurationMillis;
    private final List<Long> taskDurationMillis = new ArrayList<>();
    private int failedTaskAttempts;
    private long failedTaskAttemptDurationMillis;
    private final List<String> failedTaskAttemptReasons = new ArrayList<>();
    private int speculativeTaskAttempts;
    private long speculativeTaskAttemptDurationMillis;
    private int duplicateSuccessfulTaskAttempts;
    private final Map<String, WorkerStats> executorStats = new LinkedHashMap<>();
    private final Map<String, WorkerStats> hostStats = new LinkedHashMap<>();
    private long shuffleReadBytes;
    private Long maxTaskShuffleReadBytes;
    private final List<Long> taskShuffleReadBytes = new ArrayList<>();
    private long memoryBytesSpilled;
    private long diskBytesSpilled;
    private Long maxTaskMemoryBytesSpilled;
    private Long maxTaskDiskBytesSpilled;
    private final List<Long> taskMemoryBytesSpilled = new ArrayList<>();
    private final List<Long> taskDiskBytesSpilled = new ArrayList<>();

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
        taskDurationMillis.add(durationMillis);
        minTaskDurationMillis = minTaskDurationMillis == null
                ? durationMillis
                : Math.min(minTaskDurationMillis, durationMillis);
        maxTaskDurationMillis = maxTaskDurationMillis == null
                ? durationMillis
                : Math.max(maxTaskDurationMillis, durationMillis);
    }

    void addFailedTaskAttempt(Long durationMillis, String reason) {
        failedTaskAttempts++;
        if (durationMillis != null) {
            failedTaskAttemptDurationMillis += durationMillis;
        }
        if (reason != null && !reason.isBlank() && !failedTaskAttemptReasons.contains(reason)) {
            failedTaskAttemptReasons.add(reason);
        }
    }

    void replaceFailedTaskAttemptsWith(StageAccumulator totals) {
        if (totals == null || totals == this) {
            return;
        }

        failedTaskAttempts = totals.failedTaskAttempts;
        failedTaskAttemptDurationMillis = totals.failedTaskAttemptDurationMillis;
        failedTaskAttemptReasons.clear();
        failedTaskAttemptReasons.addAll(totals.failedTaskAttemptReasons);
    }

    void addSpeculativeTaskAttempt(Long durationMillis) {
        speculativeTaskAttempts++;
        if (durationMillis != null) {
            speculativeTaskAttemptDurationMillis += durationMillis;
        }
    }

    void addDuplicateSuccessfulTaskAttempt() {
        duplicateSuccessfulTaskAttempts++;
    }

    void addWorkerTask(Long durationMillis, String executorId, String host) {
        if (durationMillis == null) {
            return;
        }
        if (executorId != null && !executorId.isBlank()) {
            executorStats.computeIfAbsent(executorId, WorkerStats::new).addTask(durationMillis);
        }
        if (host != null && !host.isBlank()) {
            hostStats.computeIfAbsent(host, WorkerStats::new).addTask(durationMillis);
        }
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
        this.taskMemoryBytesSpilled.add(taskMemoryBytesSpilled);
        this.taskDiskBytesSpilled.add(taskDiskBytesSpilled);
        maxTaskMemoryBytesSpilled = maxTaskMemoryBytesSpilled == null
                ? taskMemoryBytesSpilled
                : Math.max(maxTaskMemoryBytesSpilled, taskMemoryBytesSpilled);
        maxTaskDiskBytesSpilled = maxTaskDiskBytesSpilled == null
                ? taskDiskBytesSpilled
                : Math.max(maxTaskDiskBytesSpilled, taskDiskBytesSpilled);
    }

    StageAnalysis toStageAnalysis() {
        Long avgTaskDurationMillis = completedTasks == 0 ? null : totalTaskDurationMillis / completedTasks;
        List<Long> sortedTaskDurationMillis = taskDurationMillis.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Long> sortedTaskShuffleReadBytes = taskShuffleReadBytes.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Long> sortedTaskMemoryBytesSpilled = taskMemoryBytesSpilled.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Long> sortedTaskDiskBytesSpilled = taskDiskBytesSpilled.stream()
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
                median(sortedTaskDurationMillis),
                percentile(sortedTaskDurationMillis, 0.95),
                percentile(sortedTaskDurationMillis, 0.99),
                sortedTaskDurationMillis,
                failedTaskAttempts,
                failedTaskAttemptDurationMillis,
                failedTaskAttemptReasons,
                speculativeTaskAttempts,
                speculativeTaskAttemptDurationMillis,
                duplicateSuccessfulTaskAttempts,
                workerSummaries(executorStats),
                workerSummaries(hostStats),
                shuffleReadBytes,
                maxTaskShuffleReadBytes,
                median(sortedTaskShuffleReadBytes),
                percentile(sortedTaskShuffleReadBytes, 0.95),
                percentile(sortedTaskShuffleReadBytes, 0.99),
                sortedTaskShuffleReadBytes,
                memoryBytesSpilled,
                diskBytesSpilled,
                maxTaskMemoryBytesSpilled,
                maxTaskDiskBytesSpilled,
                median(sortedTaskMemoryBytesSpilled),
                percentile(sortedTaskMemoryBytesSpilled, 0.95),
                percentile(sortedTaskMemoryBytesSpilled, 0.99),
                sortedTaskMemoryBytesSpilled,
                median(sortedTaskDiskBytesSpilled),
                percentile(sortedTaskDiskBytesSpilled, 0.95),
                percentile(sortedTaskDiskBytesSpilled, 0.99),
                sortedTaskDiskBytesSpilled);
    }

    int failedTaskAttempts() {
        return failedTaskAttempts;
    }

    long failedTaskAttemptDurationMillis() {
        return failedTaskAttemptDurationMillis;
    }

    List<String> failedTaskAttemptReasons() {
        return List.copyOf(failedTaskAttemptReasons);
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

    private List<WorkerSummary> workerSummaries(Map<String, WorkerStats> workers) {
        if (workers.isEmpty() || completedTasks == 0 || totalTaskDurationMillis == 0) {
            return List.of();
        }

        return workers.values().stream()
                .map(worker -> new WorkerSummary(
                        worker.id(),
                        worker.taskCount(),
                        worker.taskDurationMillis(),
                        rounded((double) worker.taskCount() / completedTasks),
                        rounded((double) worker.taskDurationMillis() / totalTaskDurationMillis)))
                .sorted(Comparator.comparingLong(WorkerSummary::taskDurationMillis).reversed())
                .toList();
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static final class WorkerStats {
        private final String id;
        private int taskCount;
        private long taskDurationMillis;

        private WorkerStats(String id) {
            this.id = id;
        }

        private void addTask(long durationMillis) {
            taskCount++;
            taskDurationMillis += durationMillis;
        }

        private String id() {
            return id;
        }

        private int taskCount() {
            return taskCount;
        }

        private long taskDurationMillis() {
            return taskDurationMillis;
        }
    }
}
