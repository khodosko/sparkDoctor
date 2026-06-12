package com.sparkdoctor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.sparkdoctor.model.SqlPlanMetric;
import com.sparkdoctor.model.SqlPlanNode;
import com.sparkdoctor.model.SqlPlanSubtreeFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPlanSubtreeFingerprinterTest {
    private final SqlPlanSubtreeFingerprinter fingerprinter = new SqlPlanSubtreeFingerprinter();

    @Test
    void normalizesSparkGeneratedAttributeIds() {
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("Project [group_id#1L, value#12]"),
                SqlPlanSubtreeFingerprinter.normalize("Project [group_id#99L, value#123]"));
    }

    @Test
    void normalizesPlanIds() {
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("Exchange hashpartitioning(group_id#1L, 4), [plan_id=18]"),
                SqlPlanSubtreeFingerprinter.normalize("Exchange hashpartitioning(group_id#99L, 4), [plan_id=67]"));
    }

    @Test
    void normalizesCodegenIds() {
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("HashAggregate [codegen id : 2]"),
                SqlPlanSubtreeFingerprinter.normalize("HashAggregate [codegen id: 9]"));
    }

    @Test
    void normalizesWholeStageCodegenAndQueryStageNumbers() {
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("WholeStageCodegen (3)"),
                SqlPlanSubtreeFingerprinter.normalize("WholeStageCodegen (9)"));
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("ShuffleQueryStage 4"),
                SqlPlanSubtreeFingerprinter.normalize("ShuffleQueryStage 17"));
        assertEquals(
                SqlPlanSubtreeFingerprinter.normalize("ResultQueryStage 2"),
                SqlPlanSubtreeFingerprinter.normalize("ResultQueryStage 11"));
    }

    @Test
    void keepsSemanticPartitionCountsDistinct() {
        assertNotEquals(
                SqlPlanSubtreeFingerprinter.normalize("Exchange hashpartitioning(group_id, 4)"),
                SqlPlanSubtreeFingerprinter.normalize("Exchange hashpartitioning(group_id, 200)"));
    }

    @Test
    void keepsSemanticRangeBoundsDistinct() {
        assertNotEquals(
                SqlPlanSubtreeFingerprinter.normalize("Range (0, 1000)"),
                SqlPlanSubtreeFingerprinter.normalize("Range (0, 2000)"));
    }

    @Test
    void fingerprintsEquivalentSubtreesWithDifferentSparkNoiseTheSame() {
        SqlPlanSubtreeFingerprint left = fingerprinter.fingerprint(projectSubtree(
                "Project [(id#1L % 10) AS group_id#1L]",
                exchangeSubtree(
                        "Exchange hashpartitioning(group_id#1L, 4), REPARTITION_BY_NUM, [plan_id=18]",
                        rangeSubtree("Range (0, 1000, step=1, splits=8)"))));
        SqlPlanSubtreeFingerprint right = fingerprinter.fingerprint(projectSubtree(
                "Project [(id#99L % 10) AS group_id#99L]",
                exchangeSubtree(
                        "Exchange hashpartitioning(group_id#99L, 4), REPARTITION_BY_NUM, [plan_id=67]",
                        rangeSubtree("Range (0, 1000, step=1, splits=8)"))));

        assertEquals(left, right);
    }

    @Test
    void fingerprintsDifferentSemanticSubtreesDifferently() {
        SqlPlanSubtreeFingerprint left = fingerprinter.fingerprint(exchangeSubtree(
                "Exchange hashpartitioning(group_id, 4), REPARTITION_BY_NUM",
                rangeSubtree("Range (0, 1000, step=1, splits=8)")));
        SqlPlanSubtreeFingerprint right = fingerprinter.fingerprint(exchangeSubtree(
                "Exchange hashpartitioning(group_id, 200), REPARTITION_BY_NUM",
                rangeSubtree("Range (0, 1000, step=1, splits=8)")));

        assertNotEquals(left, right);
    }

    @Test
    void fingerprintsDifferentChildOrderDifferently() {
        SqlPlanSubtreeFingerprint left = fingerprinter.fingerprint(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(rangeSubtree("Range (0, 1000)"), rangeSubtree("Range (0, 2000)"))));
        SqlPlanSubtreeFingerprint right = fingerprinter.fingerprint(new SqlPlanNode(
                "Union",
                "Union",
                List.of(),
                List.of(rangeSubtree("Range (0, 2000)"), rangeSubtree("Range (0, 1000)"))));

        assertNotEquals(left, right);
    }

    @Test
    void buildsReadableCanonicalFingerprintText() {
        SqlPlanSubtreeFingerprint fingerprint = fingerprinter.fingerprint(projectSubtree(
                "Project [(id#1L % 10) AS group_id#1L]",
                rangeSubtree("Range (0, 1000, step=1, splits=8)")));

        assertEquals(
                "Project|Project [(id % 10) AS group_id]|[Range|Range (0, 1000, step=1, splits=8)|[]]",
                fingerprint.canonicalText());
    }

    @Test
    void handlesBlankSimpleStringDeterministically() {
        SqlPlanSubtreeFingerprint fingerprint =
                fingerprinter.fingerprint(new SqlPlanNode("Range", "   ", List.of(new SqlPlanMetric("rows", 1L, "sum")), List.of()));

        assertEquals("Range||[]", fingerprint.canonicalText());
    }

    private SqlPlanNode projectSubtree(String simpleString, SqlPlanNode child) {
        return new SqlPlanNode("Project", simpleString, List.of(), List.of(child));
    }

    private SqlPlanNode exchangeSubtree(String simpleString, SqlPlanNode child) {
        return new SqlPlanNode("Exchange", simpleString, List.of(), List.of(child));
    }

    private SqlPlanNode rangeSubtree(String simpleString) {
        return new SqlPlanNode("Range", simpleString, List.of(), List.of());
    }
}
