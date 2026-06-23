package com.sparkdoctor.analysis;

import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanSubtreeFingerprint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

        String normalized = normalizePushedFilters(value);
        normalized = ATTRIBUTE_ID_PATTERN.matcher(normalized).replaceAll("");
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

    private static String normalizePushedFilters(String value) {
        String label = "PushedFilters: [";
        int start = value.indexOf(label);
        if (start < 0) {
            return value;
        }

        int contentStart = start + label.length();
        int end = findBracketEnd(value, contentStart);
        if (end < 0) {
            return value;
        }

        List<String> filters = splitTopLevelCommaSeparatedEntries(value.substring(contentStart, end));
        List<String> normalizedFilters = filters.stream()
                .map(String::trim)
                .filter(filter -> !filter.isEmpty())
                .toList();
        if (normalizedFilters.isEmpty()) {
            return value.substring(0, contentStart) + value.substring(end);
        }

        List<String> sortedFilters = new ArrayList<>(normalizedFilters);
        Collections.sort(sortedFilters);
        return value.substring(0, contentStart)
                + String.join(", ", sortedFilters)
                + value.substring(end);
    }

    private static int findBracketEnd(String value, int contentStart) {
        int bracketDepth = 1;
        for (int i = contentStart; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
                if (bracketDepth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static List<String> splitTopLevelCommaSeparatedEntries(String value) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenthesisDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ',' && parenthesisDepth == 0 && bracketDepth == 0) {
                entries.add(current.toString());
                current.setLength(0);
                continue;
            }

            if (character == '(') {
                parenthesisDepth++;
            } else if (character == ')') {
                parenthesisDepth = Math.max(0, parenthesisDepth - 1);
            } else if (character == '[') {
                bracketDepth++;
            } else if (character == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
            current.append(character);
        }

        entries.add(current.toString());
        return entries;
    }
}
