package com.sparkdoctor.model;

import com.sparkdoctor.SparkDoctorVersion;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AnalysisReport(
        String schemaVersion,
        AnalysisProducer producer,
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
        List<Bottleneck> identifiedBottlenecks = identifyBottlenecks(bottlenecks);
        List<Recommendation> correlatedRecommendations =
                correlateRecommendations(recommendations, identifiedBottlenecks);
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
                new AnalysisProducer("SparkDoctor", SparkDoctorVersion.current()),
                application,
                new AnalysisSummary(
                        analysisSummary.jobs(),
                        analysisSummary.jobsCompleted(),
                        analysisSummary.jobsFailed(),
                        analysisSummary.stages(),
                        analysisSummary.stagesCompleted(),
                        analysisSummary.stagesFailed(),
                        analysisSummary.tasks(),
                        identifiedBottlenecks.size()),
                stages,
                sqlExecutions,
                failedJobs,
                failedStages,
                identifiedBottlenecks,
                correlatedRecommendations);
    }

    private static List<Bottleneck> identifyBottlenecks(List<Bottleneck> bottlenecks) {
        Set<String> usedIds = new HashSet<>();
        for (Bottleneck bottleneck : bottlenecks) {
            if (bottleneck.instanceId() != null
                    && !bottleneck.instanceId().isBlank()
                    && !usedIds.add(bottleneck.instanceId())) {
                throw new IllegalArgumentException(
                        "Duplicate bottleneck instance ID: " + bottleneck.instanceId());
            }
        }

        List<Bottleneck> identified = new java.util.ArrayList<>();
        int nextId = 1;
        for (Bottleneck bottleneck : bottlenecks) {
            String instanceId = bottleneck.instanceId();
            if (instanceId == null || instanceId.isBlank()) {
                do {
                    instanceId = "bottleneck-" + nextId++;
                } while (usedIds.contains(instanceId));
                usedIds.add(instanceId);
            }
            identified.add(new Bottleneck(
                    bottleneck.type(),
                    bottleneck.severity(),
                    bottleneck.stageId(),
                    bottleneck.message(),
                    bottleneck.evidence(),
                    instanceId));
        }
        return List.copyOf(identified);
    }

    private static List<Recommendation> correlateRecommendations(
            List<Recommendation> recommendations, List<Bottleneck> bottlenecks) {
        Map<BottleneckKey, ArrayDeque<String>> availableBottleneckIds = new LinkedHashMap<>();
        Map<String, Bottleneck> bottlenecksById = new LinkedHashMap<>();
        for (Bottleneck bottleneck : bottlenecks) {
            BottleneckKey key = new BottleneckKey(bottleneck.type(), bottleneck.stageId());
            availableBottleneckIds
                    .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                    .addLast(bottleneck.instanceId());
            bottlenecksById.put(bottleneck.instanceId(), bottleneck);
        }

        List<Recommendation> correlated = new java.util.ArrayList<>();
        for (Recommendation recommendation : recommendations) {
            if (recommendation.relatedBottleneckId() != null) {
                Bottleneck origin = bottlenecksById.get(recommendation.relatedBottleneckId());
                if (origin == null) {
                    throw new IllegalArgumentException(
                            "Recommendation references a bottleneck outside this report: "
                                    + recommendation.id());
                }
                if (!origin.type().equals(recommendation.relatedBottleneckType())
                        || origin.stageId() != recommendation.stageId()) {
                    throw new IllegalArgumentException(
                            "Recommendation metadata does not match its related bottleneck: "
                                    + recommendation.id());
                }
                availableBottleneckIds
                        .get(new BottleneckKey(origin.type(), origin.stageId()))
                        .remove(origin.instanceId());
                correlated.add(recommendation);
                continue;
            }
            ArrayDeque<String> matchingIds = availableBottleneckIds.get(
                    new BottleneckKey(recommendation.relatedBottleneckType(), recommendation.stageId()));
            if (matchingIds == null || matchingIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Recommendation does not match a bottleneck in this report: " + recommendation.id());
            }
            String relatedBottleneckId = matchingIds.removeFirst();
            correlated.add(new Recommendation(
                    recommendation.id(),
                    recommendation.severity(),
                    recommendation.title(),
                    recommendation.description(),
                    recommendation.relatedBottleneckType(),
                    recommendation.stageId(),
                    relatedBottleneckId));
        }
        return List.copyOf(correlated);
    }

    private record BottleneckKey(String type, int stageId) {}
}
