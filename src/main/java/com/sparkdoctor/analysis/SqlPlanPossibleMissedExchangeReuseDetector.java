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
                    .filter(this::isExchangeRootedCandidate)
                    .toList();
            if (exchangeCandidates.isEmpty()) {
                continue;
            }

            DuplicateSqlSubtree topDuplicate = exchangeCandidates.get(0);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sqlExecutionId", sqlExecution.id());
            evidence.put("description", valueOrUnknown(sqlExecution.description()));
            evidence.put("duplicateExchangeGroups", exchangeCandidates.size());
            evidence.put("topDuplicateRoot", topDuplicate.rootNodeName());
            evidence.put("topDuplicateCount", topDuplicate.count());
            evidence.put("topDuplicateSubtreeSize", topDuplicate.subtreeSize());
            evidence.put("topDuplicateMaxDepth", topDuplicate.maxDepth());
            evidence.put("topDuplicateInterestingOperators", List.copyOf(topDuplicate.interestingOperators()));
            evidence.put("confidence", "low");
            evidence.put("confidenceReason", "physical-plan-only signal");
            evidence.put("validationRequired", "Validate in Spark UI and query code before making optimizer conclusions.");

            bottlenecks.add(new Bottleneck(
                    "possible_missed_exchange_reuse",
                    "medium",
                    -1,
                    "SQL execution %d has repeated exchange-like physical plan subtrees.".formatted(sqlExecution.id()),
                    evidence));
        }

        return bottlenecks;
    }

    private boolean isExchangeRootedCandidate(DuplicateSqlSubtree duplicate) {
        return EXCHANGE.equals(duplicate.rootNodeName());
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
