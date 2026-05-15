package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sparkdoctor.model.ApplicationSummary;
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
    void parsesApplicationSummaryFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/minimal-eventlog.json");

        ApplicationSummary summary = parser.parseApplicationSummary(fixture);

        assertEquals("app-20260515120000-0001", summary.appId());
        assertEquals("daily_customer_etl", summary.appName());
        assertEquals(2832000L, summary.durationMillis().orElseThrow());
    }
}

