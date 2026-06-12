package com.sparkdoctor.analysis;

import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanSubtreeFingerprint;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SqlPlanSubtreeFingerprinter {
    private static final Pattern ATTRIBUTE_ID_PATTERN = Pattern.compile("#\\d+L?");
    private static final Pattern PLAN_ID_PATTERN = Pattern.compile("\\s*\\[plan_id=\\d+\\]");
    private static final Pattern CODEGEN_ID_PATTERN =
            Pattern.compile("\\s*\\[codegen id\\s*:\\s*\\d+\\]|codegen id\\s*:\\s*\\d+");
    private static final Pattern WHOLE_STAGE_CODEGEN_PATTERN = Pattern.compile("WholeStageCodegen \\(\\d+\\)");
    private static final Pattern QUERY_STAGE_NUMBER_PATTERN =
            Pattern.compile("\\b(ShuffleQueryStage|ResultQueryStage)\\s+\\d+\\b");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    public SqlPlanSubtreeFingerprint fingerprint(SqlPlanNode node) {
        return new SqlPlanSubtreeFingerprint(fingerprintText(node));
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = ATTRIBUTE_ID_PATTERN.matcher(value).replaceAll("");
        normalized = PLAN_ID_PATTERN.matcher(normalized).replaceAll("");
        normalized = CODEGEN_ID_PATTERN.matcher(normalized).replaceAll("");
        normalized = WHOLE_STAGE_CODEGEN_PATTERN.matcher(normalized).replaceAll("WholeStageCodegen");
        normalized = QUERY_STAGE_NUMBER_PATTERN.matcher(normalized).replaceAll("$1");
        normalized = normalized.replaceAll("\\s+,", ",");
        normalized = normalized.replaceAll("\\(\\s+", "(");
        normalized = normalized.replaceAll("\\s+\\)", ")");
        normalized = WHITESPACE_PATTERN.matcher(normalized.trim()).replaceAll(" ");
        return normalized;
    }

    private String fingerprintText(SqlPlanNode node) {
        String children = node.children().stream()
                .map(this::fingerprintText)
                .collect(Collectors.joining(","));
        return normalize(node.nodeName()) + "|" + normalize(node.simpleString()) + "|[" + children + "]";
    }
}
