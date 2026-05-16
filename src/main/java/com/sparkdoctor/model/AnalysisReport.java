package com.sparkdoctor.model;

import java.util.List;

public record AnalysisReport(
        ApplicationAnalysis application,
        AnalysisSummary summary,
        List<StageAnalysis> stages,
        List<Object> bottlenecks,
        List<Object> recommendations) {
    public static AnalysisReport from(ParsedEventLog parsedEventLog) {
        return from(parsedEventLog.applicationSummary(), parsedEventLog.analysisSummary(), parsedEventLog.stages());
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary) {
        return from(applicationSummary, new AnalysisSummary(0, 0, 0, 0), List.of());
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary, AnalysisSummary analysisSummary) {
        return from(applicationSummary, analysisSummary, List.of());
    }

    public static AnalysisReport from(
            ApplicationSummary applicationSummary,
            AnalysisSummary analysisSummary,
            List<StageAnalysis> stages) {
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
                analysisSummary,
                stages,
                List.of(),
                List.of());
    }
}
