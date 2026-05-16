package com.sparkdoctor.model;

public record ApplicationAnalysis(
        String id,
        String name,
        Long startTimeMillis,
        Long endTimeMillis,
        Long durationMillis) {}

