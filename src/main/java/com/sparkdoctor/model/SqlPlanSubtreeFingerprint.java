package com.sparkdoctor.model;

public record SqlPlanSubtreeFingerprint(String canonicalText) {
    public SqlPlanSubtreeFingerprint {
        canonicalText = canonicalText == null ? "" : canonicalText;
    }
}
