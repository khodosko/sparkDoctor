package com.sparkdoctor.model;

public record SqlPlanMetric(
        String name,
        Long accumulatorId,
        String metricType) {}
