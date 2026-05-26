package com.sparkdoctor.model;

public record AnalysisSummary(
        int jobs,
        int jobsCompleted,
        int jobsFailed,
        int stages,
        int stagesCompleted,
        int stagesFailed,
        int tasks,
        int issuesDetected) {
    public AnalysisSummary(int jobs, int stages, int tasks, int issuesDetected) {
        this(jobs, 0, 0, stages, 0, 0, tasks, issuesDetected);
    }
}
