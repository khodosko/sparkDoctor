package com.sparkdoctor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record SqlExecution(
        long id,
        Long rootExecutionId,
        String description,
        String details,
        Long startTimeMillis,
        Long endTimeMillis,
        Long durationMillis,
        String physicalPlanDescription,
        String latestPhysicalPlanDescription,
        String errorMessage,
        List<SqlPlanOperatorSummary> operatorSummaries,
        @JsonIgnore JsonNode sparkPlanInfo,
        @JsonIgnore JsonNode latestSparkPlanInfo,
        @JsonIgnore Map<Long, String> sqlMetricValues) {
    public SqlExecution {
        operatorSummaries = operatorSummaries == null ? List.of() : List.copyOf(operatorSummaries);
        sqlMetricValues = sqlMetricValues == null ? Map.of() : Map.copyOf(sqlMetricValues);
    }

    public SqlExecution(
            long id,
            Long rootExecutionId,
            String description,
            String details,
            Long startTimeMillis,
            Long endTimeMillis,
            Long durationMillis,
            String physicalPlanDescription,
            String latestPhysicalPlanDescription,
            String errorMessage) {
        this(
                id,
                rootExecutionId,
                description,
                details,
                startTimeMillis,
                endTimeMillis,
                durationMillis,
                physicalPlanDescription,
                latestPhysicalPlanDescription,
                errorMessage,
                List.of(),
                null,
                null,
                Map.of());
    }

    public SqlExecution(
            long id,
            Long rootExecutionId,
            String description,
            String details,
            Long startTimeMillis,
            Long endTimeMillis,
            Long durationMillis,
            String physicalPlanDescription,
            String latestPhysicalPlanDescription,
            String errorMessage,
            JsonNode sparkPlanInfo,
            JsonNode latestSparkPlanInfo) {
        this(
                id,
                rootExecutionId,
                description,
                details,
                startTimeMillis,
                endTimeMillis,
                durationMillis,
                physicalPlanDescription,
                latestPhysicalPlanDescription,
                errorMessage,
                List.of(),
                sparkPlanInfo,
                latestSparkPlanInfo,
                Map.of());
    }

    public SqlExecution(
            long id,
            Long rootExecutionId,
            String description,
            String details,
            Long startTimeMillis,
            Long endTimeMillis,
            Long durationMillis,
            String physicalPlanDescription,
            String latestPhysicalPlanDescription,
            String errorMessage,
            JsonNode sparkPlanInfo,
            JsonNode latestSparkPlanInfo,
            Map<Long, String> sqlMetricValues) {
        this(
                id,
                rootExecutionId,
                description,
                details,
                startTimeMillis,
                endTimeMillis,
                durationMillis,
                physicalPlanDescription,
                latestPhysicalPlanDescription,
                errorMessage,
                List.of(),
                sparkPlanInfo,
                latestSparkPlanInfo,
                sqlMetricValues);
    }

    public boolean hasSparkPlanInfo() {
        return (latestSparkPlanInfo != null && !latestSparkPlanInfo.isNull())
                || (sparkPlanInfo != null && !sparkPlanInfo.isNull());
    }
}
