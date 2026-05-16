package com.sparkdoctor.model;

import java.util.List;

public record AnalysisReport(
        ApplicationAnalysis application,
        AnalysisSummary summary,
        List<StageAnalysis> stages,
        List<Bottleneck> bottlenecks,
        List<Recommendation> recommendations) {
    public static AnalysisReport from(ParsedEventLog parsedEventLog) {
        return from(
                parsedEventLog.applicationSummary(),
                parsedEventLog.analysisSummary(),
                parsedEventLog.stages(),
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
        ApplicationAnalysis application = new ApplicationAnalysis(
                applicationSummary.appId(),
                applicationSummary.appName(),
                applicationSummary.startTimeMillis(),
                applicationSummary.endTimeMillis(),
                applicationSummary.durationMillis().isPresent()
                        ? applicationSummary.durationMillis().getAsLong()
                        : null);

        return new AnalysisReport(
                application,
                new AnalysisSummary(
                        analysisSummary.jobs(),
                        analysisSummary.stages(),
                        analysisSummary.tasks(),
                        bottlenecks.size()),
                stages,
                bottlenecks,
                recommendations);
    }
}
