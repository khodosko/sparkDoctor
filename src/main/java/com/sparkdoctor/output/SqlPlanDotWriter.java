package com.sparkdoctor.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.SqlExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SqlPlanDotWriter {
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
        appendNode(dot, sparkPlanInfo, nodeIds);
        dot.append("}\n");
        return dot.toString();
    }

    private String appendNode(StringBuilder dot, JsonNode sparkPlanInfo, NodeIdGenerator nodeIds) {
        String nodeId = nodeIds.next();
        dot.append("  ")
                .append(nodeId)
                .append(" [label=\"")
                .append(escape(label(sparkPlanInfo)))
                .append("\"];\n");

        JsonNode children = sparkPlanInfo.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                String childNodeId = appendNode(dot, child, nodeIds);
                dot.append("  ").append(nodeId).append(" -> ").append(childNodeId).append(";\n");
            }
        }

        return nodeId;
    }

    private String label(JsonNode sparkPlanInfo) {
        String nodeName = textOrUnknown(sparkPlanInfo, "nodeName");
        String simpleString = textOrNull(sparkPlanInfo, "simpleString");
        if (simpleString == null || simpleString.equals(nodeName)) {
            return nodeName;
        }

        return nodeName + "\n" + simpleString;
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
