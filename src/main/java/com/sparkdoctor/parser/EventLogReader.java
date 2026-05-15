package com.sparkdoctor.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class EventLogReader {
    public List<String> readLines(Path eventLogPath) throws IOException {
        if (Files.isDirectory(eventLogPath)) {
            return readDirectory(eventLogPath);
        }

        return readFile(eventLogPath);
    }

    private List<String> readDirectory(Path directory) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        List<String> lines = new ArrayList<>();
        for (Path file : files) {
            lines.addAll(readFile(file));
        }
        return lines;
    }

    private List<String> readFile(Path file) throws IOException {
        try (InputStream inputStream = openInputStream(file);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }

    private InputStream openInputStream(Path file) throws IOException {
        InputStream inputStream = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(".gz")) {
            return new GZIPInputStream(inputStream);
        }

        return inputStream;
    }
}

