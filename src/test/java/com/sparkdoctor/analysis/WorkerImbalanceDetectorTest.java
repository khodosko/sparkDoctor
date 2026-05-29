package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import com.sparkdoctor.model.WorkerSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WorkerImbalanceDetectorTest {
    private final WorkerImbalanceDetector detector = new WorkerImbalanceDetector();

    @Test
    void detectsExecutorImbalanceWhenOneExecutorCarriesMostWork() {
        StageAnalysis stage = stage(
                List.of(
                        new WorkerSummary("executor-1", 8, 8000L, 0.8, 0.8),
                        new WorkerSummary("executor-2", 2, 2000L, 0.2, 0.2)),
                List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("executor_imbalance", bottleneck.type());
        assertEquals("medium", bottleneck.severity());
        assertEquals(17, bottleneck.stageId());
        assertEquals(10, bottleneck.evidence().get("completedTasks"));
        assertEquals(2, bottleneck.evidence().get("executorCount"));
        assertEquals("executor-1", bottleneck.evidence().get("executorId"));
        assertEquals(8, bottleneck.evidence().get("workerTaskCount"));
        assertEquals(8000L, bottleneck.evidence().get("workerTaskDurationMillis"));
        assertEquals(0.8, bottleneck.evidence().get("workerTaskShare"));
        assertEquals(0.8, bottleneck.evidence().get("workerDurationShare"));
    }

    @Test
    void detectsHostImbalanceWhenOneHostCarriesMostWork() {
        StageAnalysis stage = stage(
                List.of(),
                List.of(
                        new WorkerSummary("host-a", 8, 8000L, 0.8, 0.8),
                        new WorkerSummary("host-b", 2, 2000L, 0.2, 0.2)));

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertEquals(1, bottlenecks.size());
        Bottleneck bottleneck = bottlenecks.get(0);
        assertEquals("host_imbalance", bottleneck.type());
        assertEquals("host-a", bottleneck.evidence().get("host"));
        assertEquals(2, bottleneck.evidence().get("hostCount"));
    }

    @Test
    void ignoresSmallStagesToAvoidNoisyWorkerImbalanceFindings() {
        StageAnalysis stage = stage(
                9,
                List.of(
                        new WorkerSummary("executor-1", 8, 8000L, 0.8889, 0.8889),
                        new WorkerSummary("executor-2", 1, 1000L, 0.1111, 0.1111)),
                List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    void ignoresBalancedWorkerDistribution() {
        StageAnalysis stage = stage(
                List.of(
                        new WorkerSummary("executor-1", 5, 5000L, 0.5, 0.5),
                        new WorkerSummary("executor-2", 5, 5000L, 0.5, 0.5)),
                List.of());

        List<Bottleneck> bottlenecks = detector.detect(List.of(stage));

        assertTrue(bottlenecks.isEmpty());
    }

    private StageAnalysis stage(List<WorkerSummary> executorSummaries, List<WorkerSummary> hostSummaries) {
        return stage(10, executorSummaries, hostSummaries);
    }

    private StageAnalysis stage(
            int completedTasks,
            List<WorkerSummary> executorSummaries,
            List<WorkerSummary> hostSummaries) {
        return new StageAnalysis(
                17,
                "worker imbalance",
                completedTasks,
                completedTasks,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                1000L,
                List.of(1000L),
                0,
                0L,
                List.of(),
                executorSummaries,
                hostSummaries,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                0L,
                0L,
                null,
                null);
    }
}
