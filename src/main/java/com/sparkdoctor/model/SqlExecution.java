package com.sparkdoctor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

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
        @JsonIgnore JsonNode sparkPlanInfo,
        @JsonIgnore JsonNode latestSparkPlanInfo) {
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
                null,
                null);
    }

    public boolean hasSparkPlanInfo() {
        return (latestSparkPlanInfo != null && !latestSparkPlanInfo.isNull())
                || (sparkPlanInfo != null && !sparkPlanInfo.isNull());
    }
}
