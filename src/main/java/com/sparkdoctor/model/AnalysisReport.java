package com.sparkdoctor.model;

import java.util.List;

public record AnalysisReport(
        ApplicationAnalysis application,
        AnalysisSummary summary,
        List<Object> bottlenecks,
        List<Object> recommendations) {
    public static AnalysisReport from(ApplicationSummary applicationSummary) {
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
                new AnalysisSummary(0, 0, 0, 0),
                List.of(),
                List.of());
    }
}

