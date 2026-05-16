package com.sparkdoctor.model;

import java.util.List;

public record AnalysisReport(
        ApplicationAnalysis application,
        AnalysisSummary summary,
        List<Object> bottlenecks,
        List<Object> recommendations) {
    public static AnalysisReport from(ParsedEventLog parsedEventLog) {
        return from(parsedEventLog.applicationSummary(), parsedEventLog.analysisSummary());
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary) {
        return from(applicationSummary, new AnalysisSummary(0, 0, 0, 0));
    }

    public static AnalysisReport from(ApplicationSummary applicationSummary, AnalysisSummary analysisSummary) {
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
                List.of(),
                List.of());
    }
}
