package com.sparkdoctor.model;

import java.util.OptionalLong;

public record ApplicationSummary(String appId, String appName, Long startTimeMillis, Long endTimeMillis) {
    public OptionalLong durationMillis() {
        if (startTimeMillis == null || endTimeMillis == null) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(endTimeMillis - startTimeMillis);
    }
}

