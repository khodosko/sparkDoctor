package com.sparkdoctor.util;

import com.sparkdoctor.model.Bottleneck;

public final class BottleneckScope {
    private BottleneckScope() {}

    public static String display(Bottleneck bottleneck) {
        if (bottleneck.stageId() >= 0) {
            return "stage " + bottleneck.stageId();
        }

        Object sqlExecutionId = bottleneck.evidence() == null
                ? null
                : bottleneck.evidence().get("sqlExecutionId");
        if (sqlExecutionId instanceof Number numericSqlExecutionId) {
            return "SQL execution " + numericSqlExecutionId.longValue();
        }

        return "application";
    }
}
