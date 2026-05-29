package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import com.sparkdoctor.model.WorkerSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WorkerImbalanceDetector {
    private static final int MIN_COMPLETED_TASKS = 10;
    private static final int MIN_WORKERS = 2;
    private static final double MIN_DURATION_SHARE = 0.75;
    private static final double MIN_TASK_SHARE = 0.50;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            Bottleneck executorImbalance = detectImbalance(
                    stage,
                    "executor_imbalance",
                    "executorId",
                    "executorCount",
                    "Stage %d has executor imbalance.".formatted(stage.id()),
                    stage.executorSummaries());
            if (executorImbalance != null) {
                bottlenecks.add(executorImbalance);
            }

            Bottleneck hostImbalance = detectImbalance(
                    stage,
                    "host_imbalance",
                    "host",
                    "hostCount",
                    "Stage %d has host imbalance.".formatted(stage.id()),
                    stage.hostSummaries());
            if (hostImbalance != null) {
                bottlenecks.add(hostImbalance);
            }
        }

        return bottlenecks;
    }

    private Bottleneck detectImbalance(
            StageAnalysis stage,
            String type,
            String workerIdEvidenceKey,
            String workerCountEvidenceKey,
            String message,
            List<WorkerSummary> workers) {
        if (stage.completedTasks() < MIN_COMPLETED_TASKS || workers.size() < MIN_WORKERS) {
            return null;
        }

        WorkerSummary heaviestWorker = workers.get(0);
        if (heaviestWorker.durationShare() < MIN_DURATION_SHARE || heaviestWorker.taskShare() < MIN_TASK_SHARE) {
            return null;
        }

        return new Bottleneck(
                type,
                "medium",
                stage.id(),
                message,
                Map.of(
                        "completedTasks", stage.completedTasks(),
                        workerCountEvidenceKey, workers.size(),
                        workerIdEvidenceKey, heaviestWorker.id(),
                        "workerTaskCount", heaviestWorker.taskCount(),
                        "workerTaskDurationMillis", heaviestWorker.taskDurationMillis(),
                        "workerTaskShare", heaviestWorker.taskShare(),
                        "workerDurationShare", heaviestWorker.durationShare(),
                        "minDurationShare", MIN_DURATION_SHARE,
                        "minTaskShare", MIN_TASK_SHARE));
    }
}
