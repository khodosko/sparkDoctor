package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpeculationDetector {
    private static final int MIN_COMPLETED_TASKS = 10;
    private static final int MIN_SPECULATIVE_TASK_ATTEMPTS = 3;
    private static final double MEDIUM_SPECULATIVE_ATTEMPT_SHARE = 0.20;
    private static final double HIGH_SPECULATIVE_ATTEMPT_SHARE = 0.50;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (stage.completedTasks() < MIN_COMPLETED_TASKS
                    || stage.speculativeTaskAttempts() < MIN_SPECULATIVE_TASK_ATTEMPTS) {
                continue;
            }

            double speculativeAttemptShare = rounded((double) stage.speculativeTaskAttempts() / stage.completedTasks());
            if (speculativeAttemptShare < MEDIUM_SPECULATIVE_ATTEMPT_SHARE) {
                continue;
            }

            bottlenecks.add(new Bottleneck(
                    "speculation_heavy",
                    severity(speculativeAttemptShare),
                    stage.id(),
                    "Stage %d has heavy speculative execution.".formatted(stage.id()),
                    Map.of(
                            "completedTasks", stage.completedTasks(),
                            "speculativeTaskAttempts", stage.speculativeTaskAttempts(),
                            "duplicateSuccessfulTaskAttempts", stage.duplicateSuccessfulTaskAttempts(),
                            "speculativeTaskAttemptDurationMillis", stage.speculativeTaskAttemptDurationMillis(),
                            "speculativeAttemptShare", speculativeAttemptShare,
                            "minCompletedTasks", MIN_COMPLETED_TASKS,
                            "minSpeculativeTaskAttempts", MIN_SPECULATIVE_TASK_ATTEMPTS,
                            "mediumSpeculativeAttemptShare", MEDIUM_SPECULATIVE_ATTEMPT_SHARE,
                            "highSpeculativeAttemptShare", HIGH_SPECULATIVE_ATTEMPT_SHARE)));
        }

        return bottlenecks;
    }

    private String severity(double speculativeAttemptShare) {
        return speculativeAttemptShare >= HIGH_SPECULATIVE_ATTEMPT_SHARE ? "high" : "medium";
    }

    private double rounded(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
