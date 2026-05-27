package com.sparkdoctor.model;

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
        String errorMessage) {}
