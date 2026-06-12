package com.sparkdoctor.model;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record DuplicateSqlSubtree(
        SqlPlanSubtreeFingerprint fingerprint,
        String rootNodeName,
        int count,
        int subtreeSize,
        int maxDepth,
        Set<String> operatorNames,
        Set<String> interestingOperators) {
    public DuplicateSqlSubtree {
        rootNodeName = rootNodeName == null ? "" : rootNodeName;
        operatorNames = sortedCopy(operatorNames);
        interestingOperators = sortedCopy(interestingOperators);
    }

    private static Set<String> sortedCopy(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
