package com.sparkdoctor.model;

import java.util.List;

public record AnalysisReport(
        String schemaVersion,
        ApplicationAnalysis application,
        AnalysisSummary summary,
        List<StageAnalysis> stages,
        List<SqlExecution> sqlExecutions,
        List<FailedJob> failedJobs,
        List<FailedStage> failedStages,
        List<Bottleneck> bottlenecks,
        List<Recommendation> recommendations) {
    public static final String SCHEMA_VERSION = "1";

    public static AnalysisReport from(ParsedEventLog parsedEventLog) {
        return from(
                parsedEventLog.applicationSummary(),
                parsedEventLog.analysisSummary(),
                parsedEventLog.stages(),
                parsedEventLog.sqlExecutions(),
                parsedEventLog.failedJobs(),
                parsedEventLog.failedStages(),
                parsedEventLog.bottlenecks(),
                parsedEventLog.recommendations());
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary) {
        return from(applicationSummary, new AnalysisSummary(0, 0, 0, 0), List.of(), List.of(), List.of());
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary, AnalysisSummary analysisSummary) {
        return from(applicationSummary, analysisSummary, List.of(), List.of(), List.of());
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages) {
        return from(applicationSummary, analysisSummary, stages, List.of(), List.of());
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<Bottleneck> bottlenecks) {
        return from(applicationSummary, analysisSummary, stages, bottlenecks, List.of());
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<Bottleneck> bottlenecks,
            List<Recommendation> recommendations) {
        return from(
                applicationSummary,
                analysisSummary,
                stages,
                List.of(),
                List.of(),
                bottlenecks,
                recommendations);
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<FailedJob> failedJobs,
            List<FailedStage> failedStages,
            List<Bottleneck> bottlenecks,
            List<Recommendation> recommendations) {
        return from(
                applicationSummary,
                analysisSummary,
                stages,
                List.of(),
                failedJobs,
                failedStages,
                bottlenecks,
                recommendations);
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages,
            List<SqlExecution> sqlExecutions,
            List<FailedJob> failedJobs,
            List<FailedStage> failedStages,
            List<Bottleneck> bottlenecks,
            List<Recommendation> recommendations) {
        ApplicationAnalysis application = new ApplicationAnalysis(
                applicationSummary.appId(),
                applicationSummary.appName(),
                applicationSummary.startTimeMillis(),
                applicationSummary.endTimeMillis(),
                applicationSummary.durationMillis().isPresent()
                        ? applicationSummary.durationMillis().getAsLong()
                        : null);

        return new AnalysisReport(
                SCHEMA_VERSION,
                application,
                new AnalysisSummary(
                        analysisSummary.jobs(),
                        analysisSummary.jobsCompleted(),
                        analysisSummary.jobsFailed(),
                        analysisSummary.stages(),
                        analysisSummary.stagesCompleted(),
                        analysisSummary.stagesFailed(),
                        analysisSummary.tasks(),
                        bottlenecks.size()),
                stages,
                sqlExecutions,
                failedJobs,
                failedStages,
                bottlenecks,
                recommendations);
    }
}
