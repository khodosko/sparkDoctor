package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void readsDirectoryFilesInStablePathOrder() throws Exception {
        Files.writeString(tempDir.resolve("part-00002"), "second\n");
        Files.writeString(tempDir.resolve("part-00001"), "first\n");

        List<String> lines = reader.readLines(tempDir);

        assertEquals(List.of("first", "second"), lines);
    }
}

