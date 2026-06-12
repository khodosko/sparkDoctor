package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.luben.zstd.ZstdOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.SnappyOutputStream;

final class EventLogReaderTest {
    @TempDir
    private Path tempDir;

    private final EventLogReader reader = new EventLogReader();

    @Test
    void readsNonBlankLinesFromPlainFile() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json");
        Files.writeString(eventLog, "first\n\nsecond\n");

        List<String> lines = reader.readLines(eventLog);

        assertEquals(List.of("first", "second"), lines);
    }

    @Test
    void readsLinesFromGzipFile() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json.gz");
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(eventLog))) {
            outputStream.write("compressed-first\ncompressed-second\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = reader.readLines(eventLog);

        assertEquals(List.of("compressed-first", "compressed-second"), lines);
    }

    @Test
    void readsLinesFromZstandardFile() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json.zstd");
        try (OutputStream outputStream = new ZstdOutputStream(Files.newOutputStream(eventLog))) {
            outputStream.write("zstd-first\nzstd-second\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = reader.readLines(eventLog);

        assertEquals(List.of("zstd-first", "zstd-second"), lines);
    }

    @Test
    void readsLinesFromLz4File() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json.lz4");
        try (OutputStream outputStream = new LZ4BlockOutputStream(Files.newOutputStream(eventLog))) {
            outputStream.write("lz4-first\nlz4-second\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = reader.readLines(eventLog);

        assertEquals(List.of("lz4-first", "lz4-second"), lines);
    }

    @Test
    void readsLinesFromSnappyFile() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json.snappy");
        try (OutputStream outputStream = new SnappyOutputStream(Files.newOutputStream(eventLog))) {
            outputStream.write("snappy-first\nsnappy-second\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = reader.readLines(eventLog);

        assertEquals(List.of("snappy-first", "snappy-second"), lines);
    }

    @Test
    void readsDirectoryFilesInStablePathOrder() throws Exception {
        Files.writeString(tempDir.resolve("part-00002"), "second\n");
        Files.writeString(tempDir.resolve("part-00001"), "first\n");

        List<String> lines = reader.readLines(tempDir);

        assertEquals(List.of("first", "second"), lines);
    }

    @Test
    void streamsDirectoryFilesInStablePathOrder() throws Exception {
        Files.writeString(tempDir.resolve("part-00002"), "second\n");
        Files.writeString(tempDir.resolve("part-00001"), "first\n");

        List<String> lines;
        try (var stream = reader.lines(tempDir)) {
            lines = stream.toList();
        }

        assertEquals(List.of("first", "second"), lines);
    }

    @Test
    void readsSparkFourEventLogApplicationDirectory() throws Exception {
        Path applicationDirectory = tempDir.resolve("eventlog_v2_local-123");
        Files.createDirectories(applicationDirectory);
        Files.writeString(applicationDirectory.resolve("appstatus_local-123"), "status-should-be-ignored\n");
        Files.writeString(applicationDirectory.resolve("events_1_local-123"), "event-first\nevent-second\n");

        List<String> lines = reader.readLines(applicationDirectory);

        assertEquals(List.of("event-first", "event-second"), lines);
    }

    @Test
    void readsSparkFourEventLogParentDirectory() throws Exception {
        Path eventLogRoot = tempDir.resolve("eventlog");
        Path applicationDirectory = eventLogRoot.resolve("eventlog_v2_local-123");
        Files.createDirectories(applicationDirectory);
        Files.writeString(applicationDirectory.resolve("appstatus_local-123"), "status-should-be-ignored\n");
        Files.writeString(applicationDirectory.resolve("events_1_local-123"), "event-first\nevent-second\n");

        List<String> lines = reader.readLines(eventLogRoot);

        assertEquals(List.of("event-first", "event-second"), lines);
    }

    @Test
    void rejectsParentDirectoryContainingMultipleApplicationDirectories() throws Exception {
        Path eventLogRoot = tempDir.resolve("eventlog");
        Path firstApplicationDirectory = eventLogRoot.resolve("eventlog_v2_local-123");
        Path secondApplicationDirectory = eventLogRoot.resolve("eventlog_v2_local-456");
        Files.createDirectories(firstApplicationDirectory);
        Files.createDirectories(secondApplicationDirectory);
        Files.writeString(firstApplicationDirectory.resolve("events_1_local-123"), "event-first\n");
        Files.writeString(secondApplicationDirectory.resolve("events_1_local-456"), "event-second\n");

        IOException exception = assertThrows(IOException.class, () -> reader.readLines(eventLogRoot));

        assertEquals(
                "Event log directory contains multiple Spark application logs. Point SparkDoctor at one application directory or event log file.",
                exception.getMessage());
    }

    @Test
    void readsRollingEventLogPartsInNumericOrder() throws Exception {
        Path applicationDirectory = tempDir.resolve("eventlog_v2_local-123");
        Files.createDirectories(applicationDirectory);
        Files.writeString(applicationDirectory.resolve("events_10_local-123"), "tenth\n");
        Files.writeString(applicationDirectory.resolve("events_2_local-123"), "second\n");
        Files.writeString(applicationDirectory.resolve("events_1_local-123"), "first\n");

        List<String> lines = reader.readLines(applicationDirectory);

        assertEquals(List.of("first", "second", "tenth"), lines);
    }
}
