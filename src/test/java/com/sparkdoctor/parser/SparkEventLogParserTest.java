package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.ParsedEventLog;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SparkEventLogParserTest {
    private final SparkEventLogParser parser = new SparkEventLogParser();

    @Test
    void parsesApplicationStartAndEndEventsFromLines() throws Exception {
        ApplicationSummary summary = parser.parseApplicationSummary(List.of(
                "{\"Event\":\"SparkListenerApplicationStart\",\"App Name\":\"daily_customer_etl\","
                        + "\"App ID\":\"app-20260515120000-0001\",\"Timestamp\":1778846400000}",
                "{\"Event\":\"SparkListenerApplicationEnd\",\"Timestamp\":1778849232000}"));

        assertEquals("app-20260515120000-0001", summary.appId());
        assertEquals("daily_customer_etl", summary.appName());
        assertEquals(1778846400000L, summary.startTimeMillis());
        assertEquals(1778849232000L, summary.endTimeMillis());
        assertEquals(2832000L, summary.durationMillis().orElseThrow());
    }

    @Test
    void parsesJobStageAndTaskCountsFromLines() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":7}",
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":7}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":11}}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":12}}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":12}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1001}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1002}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1002}}"));

        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(2, parsedEventLog.analysisSummary().stages());
        assertEquals(2, parsedEventLog.analysisSummary().tasks());
        assertEquals(0, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(11, parsedEventLog.stages().get(0).id());
        assertEquals(12, parsedEventLog.stages().get(1).id());
    }

    @Test
    void parsesStageDetailsFromStageSubmittedEvents() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"read parquet\","
                        + "\"Number of Tasks\":12}}"));

        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(4, parsedEventLog.stages().get(0).id());
        assertEquals("read parquet", parsedEventLog.stages().get(0).name());
        assertEquals(12, parsedEventLog.stages().get(0).taskCount());
    }

    @Test
    void parsesApplicationSummaryFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/minimal-eventlog.json");

        ApplicationSummary summary = parser.parseApplicationSummary(fixture);

        assertEquals("app-20260515120000-0001", summary.appId());
        assertEquals("daily_customer_etl", summary.appName());
        assertEquals(2832000L, summary.durationMillis().orElseThrow());
    }

    @Test
    void parsesSummaryCountsFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/minimal-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(2, parsedEventLog.analysisSummary().stages());
        assertEquals(3, parsedEventLog.analysisSummary().tasks());
        assertEquals(2, parsedEventLog.stages().size());
        assertEquals(0, parsedEventLog.stages().get(0).id());
        assertEquals("scan", parsedEventLog.stages().get(0).name());
        assertEquals(2, parsedEventLog.stages().get(0).taskCount());
        assertEquals(1, parsedEventLog.stages().get(1).id());
        assertEquals("aggregate", parsedEventLog.stages().get(1).name());
        assertEquals(1, parsedEventLog.stages().get(1).taskCount());
    }
}
