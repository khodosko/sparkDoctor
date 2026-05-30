package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.Recommendation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RecommendationEngineTest {
    private final RecommendationEngine recommendationEngine = new RecommendationEngine();

    @Test
    void recommendsInvestigationForTaskDurationSkew() {
        Bottleneck bottleneck = new Bottleneck(
                "task_duration_skew",
                "medium",
                4,
                "Stage 4 has task duration skew.",
                Map.of("skewRatio", 5.0));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-task-duration-skew", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate task duration skew", recommendation.title());
        assertEquals("task_duration_skew", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("shuffle read sizes"));
    }

    @Test
    void recommendsMitigationForShufflePartitionSkew() {
        Bottleneck bottleneck = new Bottleneck(
                "shuffle_partition_skew",
                "high",
                4,
                "Stage 4 has shuffle partition skew.",
                Map.of("skewRatio", 30.0));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("mitigate-shuffle-partition-skew", recommendation.id());
        assertEquals("high", recommendation.severity());
        assertEquals("Mitigate shuffle partition skew", recommendation.title());
        assertEquals("shuffle_partition_skew", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("AQE skew join"));
        assertTrue(recommendation.description().contains("salting"));
    }

    @Test
    void recommendsReductionForOversizedShufflePartitions() {
        Bottleneck bottleneck = new Bottleneck(
                "oversized_shuffle_partitions",
                "medium",
                4,
                "Stage 4 has oversized shuffle partitions.",
                Map.of("p95TaskShuffleReadBytes", 314572800L));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("reduce-oversized-shuffle-partitions", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Reduce oversized shuffle partitions", recommendation.title());
        assertEquals("oversized_shuffle_partitions", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("Increase shuffle parallelism"));
        assertTrue(recommendation.description().contains("partition sizing"));
    }

    @Test
    void recommendsIncreasingShuffleParallelismForLowShuffleParallelism() {
        Bottleneck bottleneck = new Bottleneck(
                "low_shuffle_parallelism",
                "medium",
                4,
                "Stage 4 has low shuffle parallelism.",
                Map.of("shuffleReadBytes", 1258291200L));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("increase-shuffle-parallelism", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Increase shuffle parallelism", recommendation.title());
        assertEquals("low_shuffle_parallelism", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("spark.sql.shuffle.partitions"));
        assertTrue(recommendation.description().contains("coalesce"));
    }

    @Test
    void recommendsReductionForSpillPressure() {
        Bottleneck bottleneck = new Bottleneck(
                "spill_pressure",
                "medium",
                4,
                "Stage 4 has spill pressure.",
                Map.of("diskBytesSpilled", 314572800L));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("reduce-spill-pressure", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Reduce spill pressure", recommendation.title());
        assertEquals("spill_pressure", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("shuffle, sort, join, or aggregation"));
        assertTrue(recommendation.description().contains("partition sizing and skew"));
    }

    @Test
    void recommendsInvestigationForMemorySpillSkew() {
        Bottleneck bottleneck = new Bottleneck(
                "memory_spill_skew",
                "medium",
                4,
                "Stage 4 has memory spill skew.",
                Map.of("skewRatio", 30.0));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-memory-spill-skew", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate memory spill skew", recommendation.title());
        assertEquals("memory_spill_skew", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("task spill, duration, and shuffle read sizes"));
        assertTrue(recommendation.description().contains("executor memory only after confirming the skew source"));
    }

    @Test
    void recommendsInvestigationForDiskSpillSkew() {
        Bottleneck bottleneck = new Bottleneck(
                "disk_spill_skew",
                "high",
                4,
                "Stage 4 has disk spill skew.",
                Map.of("skewRatio", 15.0));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-disk-spill-skew", recommendation.id());
        assertEquals("high", recommendation.severity());
        assertEquals("Investigate disk spill skew", recommendation.title());
        assertEquals("disk_spill_skew", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("dominate stage runtime"));
        assertTrue(recommendation.description().contains("shuffle read sizes"));
    }

    @Test
    void recommendsReductionForTooManyTinyTasks() {
        Bottleneck bottleneck = new Bottleneck(
                "too_many_tiny_tasks",
                "medium",
                4,
                "Stage 4 has too many tiny tasks.",
                Map.of("completedTasks", 100));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("reduce-tiny-task-overhead", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Reduce tiny task overhead", recommendation.title());
        assertEquals("too_many_tiny_tasks", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("spark.sql.shuffle.partitions"));
        assertTrue(recommendation.description().contains("compacting small input files"));
    }

    @Test
    void recommendsInvestigationForRetryWaste() {
        Bottleneck bottleneck = new Bottleneck(
                "retry_waste",
                "medium",
                4,
                "Stage 4 has retry waste from failed task attempts.",
                Map.of("failedTaskAttempts", 3));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-retry-waste", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate retry waste", recommendation.title());
        assertEquals("retry_waste", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("failed task reasons"));
        assertTrue(recommendation.description().contains("Fix retry instability"));
    }

    @Test
    void recommendsInvestigationForSpeculationHeavyStages() {
        Bottleneck bottleneck = new Bottleneck(
                "speculation_heavy",
                "medium",
                18,
                "Stage 18 has heavy speculative execution.",
                Map.of("speculativeTaskAttempts", 3));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-speculation-heavy-stage", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate heavy speculative execution", recommendation.title());
        assertEquals("speculation_heavy", recommendation.relatedBottleneckType());
        assertEquals(18, recommendation.stageId());
        assertTrue(recommendation.description().contains("wastes compute"));
        assertTrue(recommendation.description().contains("too aggressive"));
    }

    @Test
    void recommendsInvestigationForExecutorImbalance() {
        Bottleneck bottleneck = new Bottleneck(
                "executor_imbalance",
                "medium",
                4,
                "Stage 4 has executor imbalance.",
                Map.of("executorId", "executor-1"));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-executor-imbalance", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate executor imbalance", recommendation.title());
        assertEquals("executor_imbalance", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("dynamic allocation"));
        assertTrue(recommendation.description().contains("uneven input splits"));
    }

    @Test
    void recommendsInvestigationForHostImbalance() {
        Bottleneck bottleneck = new Bottleneck(
                "host_imbalance",
                "medium",
                4,
                "Stage 4 has host imbalance.",
                Map.of("host", "host-a"));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-host-imbalance", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate host imbalance", recommendation.title());
        assertEquals("host_imbalance", recommendation.relatedBottleneckType());
        assertEquals(4, recommendation.stageId());
        assertTrue(recommendation.description().contains("node health"));
        assertTrue(recommendation.description().contains("noisy neighbors"));
    }

    @Test
    void recommendsInvestigationForSqlPlansWithManyExchanges() {
        Bottleneck bottleneck = new Bottleneck(
                "sql_many_exchanges",
                "medium",
                -1,
                "SQL execution 3 has many exchange operators.",
                Map.of("sqlExecutionId", 3L, "exchangeCount", 4));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-sql-many-exchanges", recommendation.id());
        assertEquals("medium", recommendation.severity());
        assertEquals("Investigate SQL plan exchanges", recommendation.title());
        assertEquals("sql_many_exchanges", recommendation.relatedBottleneckType());
        assertEquals(-1, recommendation.stageId());
        assertTrue(recommendation.description().contains("Exchange operators"));
        assertTrue(recommendation.description().contains("shuffle boundaries"));
    }

    @Test
    void recommendsInvestigationForFailedJob() {
        Bottleneck bottleneck = new Bottleneck(
                "failed_job",
                "high",
                -1,
                "Job 3 failed.",
                Map.of("jobId", 3));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-failed-job", recommendation.id());
        assertEquals("high", recommendation.severity());
        assertEquals("Investigate failed Spark job", recommendation.title());
        assertEquals("failed_job", recommendation.relatedBottleneckType());
        assertEquals(-1, recommendation.stageId());
        assertTrue(recommendation.description().contains("driver logs"));
        assertTrue(recommendation.description().contains("shuffle fetch failures"));
    }

    @Test
    void recommendsInvestigationForFailedStage() {
        Bottleneck bottleneck = new Bottleneck(
                "failed_stage",
                "high",
                8,
                "Stage 8 failed.",
                Map.of("failureReason", "Fetch failed"));

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertEquals(1, recommendations.size());
        Recommendation recommendation = recommendations.get(0);
        assertEquals("investigate-failed-stage", recommendation.id());
        assertEquals("high", recommendation.severity());
        assertEquals("Investigate failed Spark stage", recommendation.title());
        assertEquals("failed_stage", recommendation.relatedBottleneckType());
        assertEquals(8, recommendation.stageId());
        assertTrue(recommendation.description().contains("stage failure reason"));
        assertTrue(recommendation.description().contains("out-of-memory"));
    }

    @Test
    void ignoresUnknownBottleneckTypes() {
        Bottleneck bottleneck = new Bottleneck(
                "unknown",
                "medium",
                4,
                "Unknown bottleneck.",
                Map.of());

        List<Recommendation> recommendations = recommendationEngine.recommend(List.of(bottleneck));

        assertTrue(recommendations.isEmpty());
    }
}
