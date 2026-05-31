package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.FailedJob;
import com.sparkdoctor.model.FailedStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FailureDetector {
    private static final int NO_STAGE_ID = -1;

    public List<Bottleneck> detect(List<FailedJob> failedJobs, List<FailedStage> failedStages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (FailedJob failedJob : failedJobs) {
            bottlenecks.add(new Bottleneck(
                    "failed_job",
                    "high",
                    NO_STAGE_ID,
                    "Job %d failed.".formatted(failedJob.id()),
                    Map.of(
                            "jobId", failedJob.id(),
                            "result", display(failedJob.result()))));
        }
        for (FailedStage failedStage : failedStages) {
            bottlenecks.add(new Bottleneck(
                    "failed_stage",
                    "high",
                    failedStage.id(),
                    "Stage %d failed.".formatted(failedStage.id()),
                    Map.of(
                            "stageId", failedStage.id(),
                            "stageName", display(failedStage.name()),
                            "failureReason", display(failedStage.failureReason()),
                            "failedTaskAttempts", failedStage.failedTaskAttempts(),
                            "failedTaskAttemptDurationMillis", failedStage.failedTaskAttemptDurationMillis(),
                            "failedTaskAttemptReasons", failedStage.failedTaskAttemptReasons())));
        }

        return bottlenecks;
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
