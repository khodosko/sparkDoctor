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
