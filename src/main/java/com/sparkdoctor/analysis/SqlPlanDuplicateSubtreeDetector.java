package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.DuplicateSqlSubtree;
import com.sparkdoctor.model.SqlExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqlPlanDuplicateSubtreeDetector {
    private static final int MIN_SUBTREE_SIZE = 3;

    private final SqlPlanDuplicateSubtreeCollector collector;

    public SqlPlanDuplicateSubtreeDetector() {
        this(new SqlPlanDuplicateSubtreeCollector());
    }

    SqlPlanDuplicateSubtreeDetector(SqlPlanDuplicateSubtreeCollector collector) {
        this.collector = collector;
    }

    public List<Bottleneck> detect(List<SqlExecution> sqlExecutions) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (SqlExecution sqlExecution : sqlExecutions) {
            if (sqlExecution.latestPlanRoot() == null) {
                continue;
            }

            List<DuplicateSqlSubtree> qualifyingDuplicates = collector.findDuplicates(sqlExecution.latestPlanRoot()).stream()
                    .filter(duplicate -> duplicate.count() >= 2)
                    .filter(duplicate -> duplicate.subtreeSize() >= MIN_SUBTREE_SIZE)
                    .filter(duplicate -> !duplicate.interestingOperators().isEmpty())
                    .toList();
            if (qualifyingDuplicates.isEmpty()) {
                continue;
            }

            DuplicateSqlSubtree topDuplicate = qualifyingDuplicates.get(0);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sqlExecutionId", sqlExecution.id());
            evidence.put("description", valueOrUnknown(sqlExecution.description()));
            evidence.put("duplicateGroups", qualifyingDuplicates.size());
            evidence.put("topDuplicateRoot", topDuplicate.rootNodeName());
            evidence.put("topDuplicateCount", topDuplicate.count());
            evidence.put("topDuplicateSubtreeSize", topDuplicate.subtreeSize());
            evidence.put("topDuplicateMaxDepth", topDuplicate.maxDepth());
            evidence.put("topDuplicateInterestingOperators", List.copyOf(topDuplicate.interestingOperators()));

            bottlenecks.add(new Bottleneck(
                    "duplicate_sql_subtree",
                    "medium",
                    -1,
                    "SQL execution %d has repeated physical plan subtrees.".formatted(sqlExecution.id()),
                    evidence));
        }

        return bottlenecks;
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
