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
            } else if ("oversized_shuffle_partitions".equals(bottleneck.type())) {
                recommendations.add(oversizedShufflePartitionsRecommendation(bottleneck));
            } else if ("low_shuffle_parallelism".equals(bottleneck.type())) {
                recommendations.add(lowShuffleParallelismRecommendation(bottleneck));
            } else if ("spill_pressure".equals(bottleneck.type())) {
                recommendations.add(spillPressureRecommendation(bottleneck));
            } else if ("memory_spill_skew".equals(bottleneck.type())) {
                recommendations.add(memorySpillSkewRecommendation(bottleneck));
            } else if ("disk_spill_skew".equals(bottleneck.type())) {
                recommendations.add(diskSpillSkewRecommendation(bottleneck));
            } else if ("too_many_tiny_tasks".equals(bottleneck.type())) {
                recommendations.add(tinyTaskRecommendation(bottleneck));
            } else if ("retry_waste".equals(bottleneck.type())) {
                recommendations.add(retryWasteRecommendation(bottleneck));
            } else if ("executor_imbalance".equals(bottleneck.type())) {
                recommendations.add(executorImbalanceRecommendation(bottleneck));
            } else if ("host_imbalance".equals(bottleneck.type())) {
                recommendations.add(hostImbalanceRecommendation(bottleneck));
            } else if ("sql_many_exchanges".equals(bottleneck.type())) {
                recommendations.add(sqlManyExchangesRecommendation(bottleneck));
            } else if ("failed_job".equals(bottleneck.type())) {
                recommendations.add(failedJobRecommendation(bottleneck));
            } else if ("failed_stage".equals(bottleneck.type())) {
                recommendations.add(failedStageRecommendation(bottleneck));
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

    private Recommendation oversizedShufflePartitionsRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "reduce-oversized-shuffle-partitions",
                bottleneck.severity(),
                "Reduce oversized shuffle partitions",
                "Stage %d has shuffle-reading tasks processing large partitions. "
                        .formatted(bottleneck.stageId())
                        + "Increase shuffle parallelism, repartition before expensive wide operations, "
                        + "reduce data before joins or aggregations, and review filters or projections that could "
                        + "run before the shuffle. Treat executor memory increases as a secondary option after "
                        + "confirming partition sizing is appropriate.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation lowShuffleParallelismRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "increase-shuffle-parallelism",
                bottleneck.severity(),
                "Increase shuffle parallelism",
                "Stage %d read a large amount of shuffle data with relatively few shuffle-reading tasks. "
                        .formatted(bottleneck.stageId())
                        + "Increase spark.sql.shuffle.partitions or repartition before wide joins and aggregations, "
                        + "avoid unnecessary coalesce calls before expensive shuffles, and verify output-file "
                        + "requirements are not forcing low parallelism.",
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

    private Recommendation memorySpillSkewRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-memory-spill-skew",
                bottleneck.severity(),
                "Investigate memory spill skew",
                "Stage %d has one or more tasks spilling much more memory data than typical tasks. "
                        .formatted(bottleneck.stageId())
                        + "Compare task spill, duration, and shuffle read sizes to find skewed keys or oversized "
                        + "partitions. Repartition or salt hot keys before wide joins and aggregations, reduce "
                        + "per-task data, and consider executor memory only after confirming the skew source.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation diskSpillSkewRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-disk-spill-skew",
                bottleneck.severity(),
                "Investigate disk spill skew",
                "Stage %d has one or more tasks spilling much more data to disk than typical tasks. "
                        .formatted(bottleneck.stageId())
                        + "Disk spill skew is often expensive because a small number of tasks can dominate stage "
                        + "runtime. Inspect skewed partition keys, shuffle read sizes, joins, aggregations, and sort "
                        + "operators before changing executor memory.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation tinyTaskRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "reduce-tiny-task-overhead",
                bottleneck.severity(),
                "Reduce tiny task overhead",
                "Stage %d ran many very short tasks. ".formatted(bottleneck.stageId())
                        + "Scheduler overhead may be a meaningful part of runtime. Consider reducing "
                        + "spark.sql.shuffle.partitions, coalescing after heavy filters, compacting small input "
                        + "files, and avoiding unnecessary repartition calls that create many tiny partitions.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation retryWasteRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-retry-waste",
                bottleneck.severity(),
                "Investigate retry waste",
                "Stage %d spent meaningful time in failed task attempts before successful work completed. "
                        .formatted(bottleneck.stageId())
                        + "Inspect failed task reasons and executor logs for executor loss, out-of-memory errors, "
                        + "shuffle fetch failures, bad input records, preemption, or unstable worker nodes. "
                        + "Fix retry instability before normal performance tuning.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation executorImbalanceRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-executor-imbalance",
                bottleneck.severity(),
                "Investigate executor imbalance",
                "Stage %d had one executor carrying most of the successful task work. "
                        .formatted(bottleneck.stageId())
                        + "Check whether partition placement, data locality, dynamic allocation, executor loss, "
                        + "or uneven input splits caused work to concentrate on one executor.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation hostImbalanceRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-host-imbalance",
                bottleneck.severity(),
                "Investigate host imbalance",
                "Stage %d had one host carrying most of the successful task work. "
                        .formatted(bottleneck.stageId())
                        + "Check cluster placement, data locality, node health, noisy neighbors, and whether "
                        + "available executors were spread evenly across hosts.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation sqlManyExchangesRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-sql-many-exchanges",
                bottleneck.severity(),
                "Investigate SQL plan exchanges",
                "SQL execution %s has many Exchange operators in its physical plan. "
                        .formatted(bottleneck.evidence().get("sqlExecutionId"))
                        + "Exchange operators usually indicate shuffle boundaries. Review joins, aggregations, "
                        + "sorts, repartition calls, and whether filters or projections can reduce data before "
                        + "wide operations.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation failedJobRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-failed-job",
                bottleneck.severity(),
                "Investigate failed Spark job",
                "A Spark job failed before the application completed successfully. "
                        + "Inspect the Spark event log, driver logs, and failed stage details for executor loss, "
                        + "shuffle fetch failures, task exceptions, or resource exhaustion.",
                bottleneck.type(),
                bottleneck.stageId());
    }

    private Recommendation failedStageRecommendation(Bottleneck bottleneck) {
        return new Recommendation(
                "investigate-failed-stage",
                bottleneck.severity(),
                "Investigate failed Spark stage",
                "Stage %d failed. ".formatted(bottleneck.stageId())
                        + "Start with the stage failure reason and failed task logs, then check for shuffle fetch "
                        + "failures, executor loss, out-of-memory errors, bad input records, or repeated task failures.",
                bottleneck.type(),
                bottleneck.stageId());
    }
}
