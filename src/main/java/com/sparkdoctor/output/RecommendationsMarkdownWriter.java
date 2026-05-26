package com.sparkdoctor.output;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.Recommendation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecommendationsMarkdownWriter {
    private static final String RECOMMENDATIONS_FILE_NAME = "recommendations.md";

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
        markdown.append("- Issues detected: ").append(report.summary().issuesDetected()).append("\n\n");

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
            markdown.append(recommendation.description()).append("\n\n");
        }

        return markdown.toString();
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String display(Long value) {
        return value == null ? "unknown" : Long.toString(value);
    }
}
