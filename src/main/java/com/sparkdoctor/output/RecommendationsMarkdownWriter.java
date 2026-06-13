package com.sparkdoctor.output;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.Recommendation;
import com.sparkdoctor.model.StageAnalysis;
import com.sparkdoctor.util.HumanReadableFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecommendationsMarkdownWriter {
    private static final String RECOMMENDATIONS_FILE_NAME = "recommendations.md";
    private static final int MAX_HOTSPOTS = 5;

    public Path write(Path outputDirectory, AnalysisReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        Path recommendationsPath = outputDirectory.resolve(RECOMMENDATIONS_FILE_NAME);
        Files.writeString(recommendationsPath, markdown(report));
        return recommendationsPath;
    }

    private String markdown(AnalysisReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# SparkDoctor Recommendations\n\n");
        markdown.append("## Application\n\n");
        markdown.append("- Name: ").append(display(report.application().name())).append("\n");
        markdown.append("- ID: ").append(display(report.application().id())).append("\n");
        markdown.append("- Duration: ").append(display(report.application().durationMillis())).append(" ms\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Jobs: ").append(report.summary().jobs()).append("\n");
        markdown.append("- Jobs completed: ").append(report.summary().jobsCompleted()).append("\n");
        markdown.append("- Jobs failed: ").append(report.summary().jobsFailed()).append("\n");
        markdown.append("- Stages: ").append(report.summary().stages()).append("\n");
        markdown.append("- Stages completed: ").append(report.summary().stagesCompleted()).append("\n");
        markdown.append("- Stages failed: ").append(report.summary().stagesFailed()).append("\n");
        markdown.append("- Tasks: ").append(report.summary().tasks()).append("\n");
        markdown.append("- Issues detected: ").append(report.summary().issuesDetected()).append("\n");
        markdown.append("- Severity summary: ").append(severitySummary(report.bottlenecks())).append("\n\n");
        appendStageHotspots(markdown, report);

        if (report.recommendations().isEmpty()) {
            markdown.append("## Recommendations\n\n");
            markdown.append("No recommendations generated.\n");
            return markdown.toString();
        }

        markdown.append("## Recommendations\n\n");
        for (Recommendation recommendation : report.recommendations()) {
            markdown.append("### ").append(recommendation.title()).append("\n\n");
            markdown.append("- Severity: ").append(recommendation.severity()).append("\n");
            if (recommendation.stageId() < 0) {
                markdown.append("- Scope: application\n");
            } else {
                markdown.append("- Stage ID: ").append(recommendation.stageId()).append("\n");
            }
            markdown.append("- Related bottleneck: ")
                    .append(recommendation.relatedBottleneckType())
                    .append("\n\n");
            appendEvidence(markdown, recommendation, report);
            markdown.append(recommendation.description()).append("\n\n");
        }

        return markdown.toString();
    }

    private void appendEvidence(StringBuilder markdown, Recommendation recommendation, AnalysisReport report) {
        for (Bottleneck bottleneck : report.bottlenecks()) {
            if (!matchesRecommendation(bottleneck, recommendation)
                    || bottleneck.evidence() == null
                    || bottleneck.evidence().isEmpty()) {
                continue;
            }

            markdown.append("Evidence:\n\n");
            bottleneck.evidence().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> markdown.append("- ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(displayEvidence(entry.getKey(), entry.getValue()))
                            .append("\n"));
            markdown.append("\n");
            return;
        }
    }

    private boolean matchesRecommendation(Bottleneck bottleneck, Recommendation recommendation) {
        return bottleneck.stageId() == recommendation.stageId()
                && bottleneck.type().equals(recommendation.relatedBottleneckType());
    }

    private String severitySummary(List<Bottleneck> bottlenecks) {
        if (bottlenecks.isEmpty()) {
            return "none";
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("high", 0L);
        counts.put("medium", 0L);
        counts.put("low", 0L);
        for (Bottleneck bottleneck : bottlenecks) {
            counts.merge(bottleneck.severity(), 1L, Long::sum);
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private void appendStageHotspots(StringBuilder markdown, AnalysisReport report) {
        List<StageHotspot> hotspots = stageHotspots(report);
        if (hotspots.isEmpty()) {
            return;
        }

        markdown.append("## Stage Hotspots\n\n");
        for (StageHotspot hotspot : hotspots) {
            StageAnalysis stage = hotspot.stage();
            markdown.append("- Stage ")
                    .append(stage.id())
                    .append(" (")
                    .append(display(stage.name()))
                    .append("): ");
            markdown.append("issues=").append(hotspot.issueCount());
            if (hotspot.failed()) {
                markdown.append(", failed=true");
            }
            markdown.append(", completedTasks=").append(stage.completedTasks());
            markdown.append(", avgTaskDurationMillis=").append(display(stage.avgTaskDurationMillis()));
            markdown.append(", maxTaskDurationMillis=").append(display(stage.maxTaskDurationMillis()));
            markdown.append(", shuffleReadBytes=").append(stage.shuffleReadBytes());
            markdown.append(", memoryBytesSpilled=").append(stage.memoryBytesSpilled());
            markdown.append(", diskBytesSpilled=").append(stage.diskBytesSpilled());
            markdown.append("\n");
        }
        markdown.append("\n");
    }

    private List<StageHotspot> stageHotspots(AnalysisReport report) {
        Map<Integer, Long> issueCounts = report.bottlenecks().stream()
                .filter(bottleneck -> bottleneck.stageId() >= 0)
                .collect(Collectors.groupingBy(Bottleneck::stageId, Collectors.counting()));
        Set<Integer> failedStageIds = report.failedStages().stream()
                .map(failedStage -> failedStage.id())
                .collect(Collectors.toSet());

        return report.stages().stream()
                .map(stage -> new StageHotspot(
                        stage,
                        issueCounts.getOrDefault(stage.id(), 0L),
                        failedStageIds.contains(stage.id())))
                .filter(StageHotspot::shouldShow)
                .sorted(Comparator.comparing(StageHotspot::failed).reversed()
                        .thenComparing(Comparator.comparingLong(StageHotspot::issueCount).reversed())
                        .thenComparing(Comparator.comparingLong(StageHotspot::maxTaskDurationMillis).reversed())
                        .thenComparing(Comparator.comparingLong(StageHotspot::shuffleReadBytes).reversed())
                        .thenComparing(Comparator.comparingLong(StageHotspot::spillBytes).reversed())
                        .thenComparing(hotspot -> hotspot.stage().id()))
                .limit(MAX_HOTSPOTS)
                .toList();
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String display(Long value) {
        return value == null ? "unknown" : Long.toString(value);
    }

    private String displayEvidence(String key, Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof Number number) {
            long longValue = number.longValue();
            if (key.contains("Bytes")) {
                return value + " (" + HumanReadableFormat.bytes(longValue) + ")";
            }
            if (key.contains("Millis")) {
                return value + " (" + HumanReadableFormat.millis(longValue) + ")";
            }
        }

        return value.toString();
    }

    private record StageHotspot(StageAnalysis stage, long issueCount, boolean failed) {
        private boolean shouldShow() {
            return failed
                    || issueCount > 0
                    || maxTaskDurationMillis() > 0
                    || shuffleReadBytes() > 0
                    || spillBytes() > 0;
        }

        private long maxTaskDurationMillis() {
            return valueOrZero(stage.maxTaskDurationMillis());
        }

        private long shuffleReadBytes() {
            return stage.shuffleReadBytes();
        }

        private long spillBytes() {
            return stage.memoryBytesSpilled() + stage.diskBytesSpilled();
        }

        private long valueOrZero(Long value) {
            return value == null ? 0L : value;
        }
    }
}
