package com.sparkdoctor.parser;

import com.github.luben.zstd.ZstdInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import org.xerial.snappy.SnappyInputStream;

public final class EventLogReader {
    public List<String> readLines(Path eventLogPath) throws IOException {
        try (Stream<String> lines = lines(eventLogPath)) {
            return lines.toList();
        }
    }

    public Stream<String> lines(Path eventLogPath) throws IOException {
        if (Files.isDirectory(eventLogPath)) {
            return directoryLines(eventLogPath);
        }

        return fileLines(eventLogPath);
    }

    private Stream<String> directoryLines(Path directory) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        return files.stream().flatMap(this::uncheckedFileLines);
    }

    private Stream<String> uncheckedFileLines(Path file) {
        try {
            return fileLines(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Stream<String> fileLines(Path file) throws IOException {
        InputStream inputStream = openInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        return reader.lines()
                .filter(line -> !line.isBlank())
                .onClose(() -> close(reader));
    }

    private InputStream openInputStream(Path file) throws IOException {
        InputStream inputStream = Files.newInputStream(file);
        try {
            return decompressingInputStream(file, inputStream);
        } catch (IOException | RuntimeException exception) {
            inputStream.close();
            throw exception;
        }
    }

    private InputStream decompressingInputStream(Path file, InputStream inputStream) throws IOException {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".gz")) {
            return new GZIPInputStream(inputStream);
        }
        if (fileName.endsWith(".zstd") || fileName.endsWith(".zst")) {
            return new ZstdInputStream(inputStream);
        }
        if (fileName.endsWith(".lz4")) {
            return new LZ4BlockInputStream(inputStream);
        }
        if (fileName.endsWith(".snappy")) {
            return new SnappyInputStream(inputStream);
        }

        return inputStream;
    }

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
