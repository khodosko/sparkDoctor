package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.DuplicateSqlSubtree;
import com.sparkdoctor.model.SqlExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqlPlanPossibleMissedExchangeReuseDetector {
    private static final int MIN_SUBTREE_SIZE = 3;
    private static final String EXCHANGE = "Exchange";

    private final SqlPlanDuplicateSubtreeCollector collector;

    public SqlPlanPossibleMissedExchangeReuseDetector() {
        this(new SqlPlanDuplicateSubtreeCollector());
    }

    SqlPlanPossibleMissedExchangeReuseDetector(SqlPlanDuplicateSubtreeCollector collector) {
        this.collector = collector;
    }

    public List<Bottleneck> detect(List<SqlExecution> sqlExecutions) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (SqlExecution sqlExecution : sqlExecutions) {
            if (sqlExecution.latestPlanRoot() == null) {
                continue;
            }

            List<DuplicateSqlSubtree> exchangeCandidates = collector.findDuplicates(sqlExecution.latestPlanRoot()).stream()
                    .filter(duplicate -> duplicate.count() >= 2)
                    .filter(duplicate -> duplicate.subtreeSize() >= MIN_SUBTREE_SIZE)
                    .filter(duplicate -> duplicate.interestingOperators().contains(EXCHANGE))
                    .toList();
            if (exchangeCandidates.isEmpty()) {
                continue;
            }

            List<DuplicateSqlSubtree> exchangeRootedCandidates = exchangeCandidates.stream()
                    .filter(duplicate -> duplicate.rootNodeName().contains(EXCHANGE))
                    .toList();
            List<DuplicateSqlSubtree> prioritizedCandidates =
                    exchangeRootedCandidates.isEmpty() ? exchangeCandidates : exchangeRootedCandidates;
            DuplicateSqlSubtree topDuplicate = prioritizedCandidates.get(0);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sqlExecutionId", sqlExecution.id());
            evidence.put("description", valueOrUnknown(sqlExecution.description()));
            evidence.put("duplicateExchangeGroups", prioritizedCandidates.size());
            evidence.put("topDuplicateRoot", topDuplicate.rootNodeName());
            evidence.put("topDuplicateCount", topDuplicate.count());
            evidence.put("topDuplicateSubtreeSize", topDuplicate.subtreeSize());
            evidence.put("topDuplicateMaxDepth", topDuplicate.maxDepth());
            evidence.put("topDuplicateInterestingOperators", List.copyOf(topDuplicate.interestingOperators()));

            bottlenecks.add(new Bottleneck(
                    "possible_missed_exchange_reuse",
                    "medium",
                    -1,
                    "SQL execution %d has repeated exchange-like physical plan subtrees.".formatted(sqlExecution.id()),
                    evidence));
        }

        return bottlenecks;
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
