package com.sparkdoctor.model;

import java.util.List;

public record ParsedEventLog(
        ApplicationSummary applicationSummary,
        AnalysisSummary analysisSummary,
        List<StageAnalysis> stages,
        List<SqlExecution> sqlExecutions,
        List<FailedJob> failedJobs,
        List<FailedStage> failedStages,
        List<Bottleneck> bottlenecks,
        List<Recommendation> recommendations) {
    public ParsedEventLog(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<Bottleneck> bottlenecks,
            List<Recommendation> recommendations) {
        this(applicationSummary, analysisSummary, stages, List.of(), List.of(), List.of(), bottlenecks, recommendations);
    }

    public ParsedEventLog(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<FailedJob> failedJobs,
            List<FailedStage> failedStages,
            List<Bottleneck> bottlenecks,
            List<Recommendation> recommendations) {
        this(applicationSummary, analysisSummary, stages, List.of(), failedJobs, failedStages, bottlenecks, recommendations);
    }
}
