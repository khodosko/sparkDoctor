package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sparkdoctor.model.Bottleneck;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnalyzeCommandPresentationTest {
    @Test
    void topBottlenecksPrioritizeHighSeverityFindings() {
        Bottleneck mediumOne = bottleneck("medium-one", "medium");
        Bottleneck mediumTwo = bottleneck("medium-two", "medium");
        Bottleneck mediumThree = bottleneck("medium-three", "medium");
        Bottleneck high = bottleneck("failed-stage", "high");

        List<Bottleneck> top = AnalyzeCommand.topBottlenecks(
                List.of(mediumOne, mediumTwo, mediumThree, high));

        assertEquals(List.of(high, mediumOne, mediumTwo), top);
    }

    private Bottleneck bottleneck(String type, String severity) {
        return new Bottleneck(type, severity, -1, type, Map.of());
    }
}
