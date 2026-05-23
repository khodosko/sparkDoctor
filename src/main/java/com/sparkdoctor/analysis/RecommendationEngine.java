package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.Recommendation;
import java.util.ArrayList;
import java.util.List;

public final class RecommendationEngine {
    public List<Recommendation> recommend(List<Bottleneck> bottlenecks) {
        List<Recommendation> recommendations = new ArrayList<>();
        for (Bottleneck bottleneck : bottlenecks) {
            if ("task_duration_skew".equals(bottleneck.type())) {
                recommendations.add(taskDurationSkewRecommendation(bottleneck));
            } else if ("shuffle_partition_skew".equals(bottleneck.type())) {
                recommendations.add(shufflePartitionSkewRecommendation(bottleneck));
            } else if ("spill_pressure".equals(bottleneck.type())) {
                recommendations.add(spillPressureRecommendation(bottleneck));
            }
        }

        return recommendations;
    }

    private Recommendation taskDurationSkewRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-task-duration-skew",
                bottleneck.severity(),
                "Investigate task duration skew",
                "Stage %d has tasks running much longer than the stage average. "
                        .formatted(bottleneck.stageId())
                        + "Check task input sizes, shuffle read sizes, partition keys, and executor locality.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation shufflePartitionSkewRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "mitigate-shuffle-partition-skew",
                bottleneck.severity(),
                "Mitigate shuffle partition skew",
                "Stage %d has one or more shuffle partitions much larger than the median partition. "
                        .formatted(bottleneck.stageId())
                        + "Enable or tune Spark AQE skew join handling, inspect skewed join keys, "
                        + "consider salting hot keys, repartition by a better key, or pre-aggregate before joins.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation spillPressureRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "reduce-spill-pressure",
                bottleneck.severity(),
                "Reduce spill pressure",
                "Stage %d spilled a significant amount of data during task execution. "
                        .formatted(bottleneck.stageId())
                        + "Spill usually indicates memory pressure during shuffle, sort, join, or aggregation. "
                        + "Check for skew by comparing max task duration and spill against typical tasks, "
                        + "increase shuffle parallelism if tasks are too large, reduce per-task data before "
                        + "wide operations, and review joins, aggregations, and sorts creating large shuffle state. "
                        + "Consider executor memory changes only after confirming partition sizing and skew.",
                bottleneck.type(),
                bottleneck.stageId());
    }
}
