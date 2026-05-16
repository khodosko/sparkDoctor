package com.sparkdoctor.model;

import java.util.List;

public record ParsedEventLog(
        ApplicationSummary applicationSummary,
        AnalysisSummary analysisSummary,
        List<StageAnalysis> stages,
        List<Bottleneck> bottlenecks) {}
