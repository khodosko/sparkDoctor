package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.SqlPlanOperatorSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SqlPlanExchangeDetector {
    private static final int MIN_EXCHANGE_OPERATORS = 4;

    public List<Bottleneck> detect(List<SqlExecution> sqlExecutions) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (SqlExecution sqlExecution : sqlExecutions) {
            int exchangeCount = operatorCount(sqlExecution, "Exchange");
            if (exchangeCount < MIN_EXCHANGE_OPERATORS) {
                continue;
            }

            bottlenecks.add(new Bottleneck(
                    "sql_many_exchanges",
                    "medium",
                    -1,
                    "SQL execution %d has many exchange operators.".formatted(sqlExecution.id()),
                    Map.of(
                            "sqlExecutionId", sqlExecution.id(),
                            "exchangeCount", exchangeCount,
                            "minExchangeOperators", MIN_EXCHANGE_OPERATORS,
                            "description", valueOrUnknown(sqlExecution.description()))));
        }

        return bottlenecks;
    }

    private int operatorCount(SqlExecution sqlExecution, String operatorName) {
        return sqlExecution.operatorSummaries().stream()
                .filter(operator -> operatorName.equals(operator.name()))
                .mapToInt(SqlPlanOperatorSummary::count)
                .findFirst()
                .orElse(0);
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
