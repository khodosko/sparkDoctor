package com.sparkdoctor.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.SqlExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SqlPlanDotWriter {
    private static final int MAX_METRICS_PER_NODE = 5;
    private static final int MAX_SIMPLE_STRING_LENGTH = 120;
    private static final DecimalFormat WHOLE_NUMBER_FORMAT = new DecimalFormat("#,##0");

    public List<Path> write(Path outputDirectory, AnalysisReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        List<Path> dotPaths = new ArrayList<>();
        for (SqlExecution sqlExecution : report.sqlExecutions()) {
            JsonNode sparkPlanInfo = sparkPlanInfo(sqlExecution);
            if (sparkPlanInfo == null || sparkPlanInfo.isNull()) {
                continue;
            }

            Path dotPath = outputDirectory.resolve(fileName(sqlExecution));
            Files.writeString(dotPath, dot(sqlExecution, sparkPlanInfo));
            dotPaths.add(dotPath);
        }

        return dotPaths;
    }

    public static String fileName(SqlExecution sqlExecution) {
        return "sql-execution-" + sqlExecution.id() + ".dot";
    }

    private JsonNode sparkPlanInfo(SqlExecution sqlExecution) {
        return sqlExecution.latestSparkPlanInfo() == null
                ? sqlExecution.sparkPlanInfo()
                : sqlExecution.latestSparkPlanInfo();
    }

    private String dot(SqlExecution sqlExecution, JsonNode sparkPlanInfo) {
        StringBuilder dot = new StringBuilder();
        NodeIdGenerator nodeIds = new NodeIdGenerator();
        dot.append("digraph sql_execution_").append(sqlExecution.id()).append(" {\n");
        dot.append("  graph [rankdir=TB];\n");
        dot.append("  node [shape=box, style=\"rounded,filled\", fillcolor=\"#f8fafc\", color=\"#64748b\"];\n");
        dot.append("  edge [color=\"#64748b\"];\n");
        appendNode(dot, sparkPlanInfo, nodeIds, sqlExecution.sqlMetricValues());
        dot.append("}\n");
        return dot.toString();
    }

    private String appendNode(
            StringBuilder dot,
            JsonNode sparkPlanInfo,
            NodeIdGenerator nodeIds,
            Map<Long, String> sqlMetricValues) {
        String nodeId = nodeIds.next();
        dot.append("  ")
                .append(nodeId)
                .append(" [label=\"")
                .append(escape(label(nodeId, sparkPlanInfo, sqlMetricValues)))
                .append("\"];\n");

        JsonNode children = sparkPlanInfo.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                String childNodeId = appendNode(dot, child, nodeIds, sqlMetricValues);
                dot.append("  ").append(nodeId).append(" -> ").append(childNodeId).append(";\n");
            }
        }

        return nodeId;
    }

    private String label(String nodeId, JsonNode sparkPlanInfo, Map<Long, String> sqlMetricValues) {
        String nodeName = textOrUnknown(sparkPlanInfo, "nodeName");
        String simpleString = textOrNull(sparkPlanInfo, "simpleString");
        StringBuilder label = new StringBuilder(nodeId).append("\n").append(nodeName);
        if (simpleString != null && !simpleString.equals(nodeName)) {
            label.append("\n").append(abbreviate(simpleString));
        }

        List<String> metricLabels = metricLabels(sparkPlanInfo, sqlMetricValues);
        if (!metricLabels.isEmpty()) {
            label.append("\n\nmetrics:");
            for (String metricLabel : metricLabels) {
                label.append("\n- ").append(metricLabel);
            }
        }

        return label.toString();
    }

    private String abbreviate(String value) {
        if (value.length() <= MAX_SIMPLE_STRING_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_SIMPLE_STRING_LENGTH - 3) + "...";
    }

    private List<String> metricLabels(JsonNode sparkPlanInfo, Map<Long, String> sqlMetricValues) {
        JsonNode metrics = sparkPlanInfo.get("metrics");
        if (metrics == null || !metrics.isArray() || metrics.isEmpty()) {
            return List.of();
        }

        List<String> metricLabels = new ArrayList<>();
        for (JsonNode metric : metrics) {
            String metricName = textOrNull(metric, "name");
            if (metricName == null || metricName.isBlank()) {
                continue;
            }
            metricLabels.add(metricLabel(metric, metricName, sqlMetricValues));
            if (metricLabels.size() == MAX_METRICS_PER_NODE) {
                break;
            }
        }

        if (metrics.size() > MAX_METRICS_PER_NODE) {
            metricLabels.add("+" + (metrics.size() - MAX_METRICS_PER_NODE) + " more metrics");
        }

        return metricLabels;
    }

    private String metricLabel(JsonNode metric, String metricName, Map<Long, String> sqlMetricValues) {
        Long accumulatorId = longOrNull(metric, "accumulatorId");
        if (accumulatorId == null) {
            return metricName;
        }

        String value = sqlMetricValues.get(accumulatorId);
        if (value == null) {
            return metricName;
        }

        String metricType = textOrNull(metric, "metricType");
        return metricName + ": " + formatMetricValue(value, metricType);
    }

    private String formatMetricValue(String value, String metricType) {
        Long numericValue = parseLongOrNull(value);
        if (numericValue == null) {
            return value;
        }

        if ("size".equals(metricType)) {
            return formatBytes(numericValue);
        }
        if ("timing".equals(metricType)) {
            return WHOLE_NUMBER_FORMAT.format(numericValue) + " ms";
        }
        if ("nsTiming".equals(metricType)) {
            return WHOLE_NUMBER_FORMAT.format(numericValue) + " ns";
        }

        return WHOLE_NUMBER_FORMAT.format(numericValue);
    }

    private String formatBytes(long bytes) {
        long kib = 1024L;
        long mib = kib * 1024L;
        long gib = mib * 1024L;
        if (bytes >= gib) {
            return oneDecimal(bytes, gib) + " GiB";
        }
        if (bytes >= mib) {
            return oneDecimal(bytes, mib) + " MiB";
        }
        if (bytes >= kib) {
            return oneDecimal(bytes, kib) + " KiB";
        }

        return WHOLE_NUMBER_FORMAT.format(bytes) + " B";
    }

    private String oneDecimal(long value, long divisor) {
        double scaledValue = (double) value / divisor;
        if (scaledValue == Math.rint(scaledValue)) {
            return WHOLE_NUMBER_FORMAT.format((long) scaledValue);
        }

        return new DecimalFormat("#,##0.0").format(scaledValue);
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    private String textOrUnknown(JsonNode node, String fieldName) {
        String value = textOrNull(node, fieldName);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private static final class NodeIdGenerator {
        private int nextId;

        private String next() {
            String nodeId = "n" + nextId;
            nextId++;
            return nodeId;
        }
    }
}
