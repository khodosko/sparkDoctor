package com.sparkdoctor.analysis;

import com.sparkdoctor.model.DuplicateSqlSubtree;
import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanSubtreeFingerprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SqlPlanDuplicateSubtreeCollector {
    private static final List<String> INTERESTING_OPERATOR_MARKERS =
            List.of("Exchange", "Join", "Aggregate", "Sort", "Scan");

    private final SqlPlanSubtreeFingerprinter fingerprinter;

    public SqlPlanDuplicateSubtreeCollector() {
        this(new SqlPlanSubtreeFingerprinter());
    }

    SqlPlanDuplicateSubtreeCollector(SqlPlanSubtreeFingerprinter fingerprinter) {
        this.fingerprinter = fingerprinter;
    }

    public List<DuplicateSqlSubtree> findDuplicates(SqlPlanNode root) {
        if (root == null) {
            return List.of();
        }

        Map<SqlPlanSubtreeFingerprint, GroupState> groups = new LinkedHashMap<>();
        root.traverse().forEach(node -> {
            SqlPlanSubtreeFingerprint fingerprint = fingerprinter.fingerprint(node);
            groups.computeIfAbsent(fingerprint, ignored -> new GroupState(node)).increment();
        });

        return groups.entrySet().stream()
                .filter(entry -> entry.getValue().count >= 2)
                .map(entry -> toDuplicateSqlSubtree(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(DuplicateSqlSubtree::subtreeSize).reversed()
                        .thenComparing(Comparator.comparingInt(DuplicateSqlSubtree::count).reversed())
                        .thenComparing(Comparator.comparingInt(DuplicateSqlSubtree::maxDepth).reversed())
                        .thenComparing(DuplicateSqlSubtree::rootNodeName))
                .toList();
    }

    private DuplicateSqlSubtree toDuplicateSqlSubtree(
            SqlPlanSubtreeFingerprint fingerprint, GroupState groupState) {
        Set<String> operatorNames = new TreeSet<>();
        Set<String> interestingOperators = new TreeSet<>();
        groupState.representative.traverse().map(SqlPlanNode::nodeName).forEach(nodeName -> {
            if (nodeName == null || nodeName.isBlank()) {
                return;
            }
            operatorNames.add(nodeName);
            if (isInterestingOperator(nodeName)) {
                interestingOperators.add(nodeName);
            }
        });

        return new DuplicateSqlSubtree(
                fingerprint,
                groupState.representative.nodeName(),
                groupState.count,
                groupState.representative.subtreeSize(),
                groupState.representative.maxDepth(),
                operatorNames,
                interestingOperators);
    }

    private boolean isInterestingOperator(String nodeName) {
        return INTERESTING_OPERATOR_MARKERS.stream().anyMatch(nodeName::contains);
    }

    private static final class GroupState {
        private final SqlPlanNode representative;
        private int count;

        private GroupState(SqlPlanNode representative) {
            this.representative = representative;
        }

        private void increment() {
            count++;
        }
    }
}
