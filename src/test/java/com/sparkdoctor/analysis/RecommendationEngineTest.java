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

