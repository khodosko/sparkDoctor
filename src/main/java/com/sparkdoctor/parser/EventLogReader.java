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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import org.xerial.snappy.SnappyInputStream;

public final class EventLogReader {
    private static final Pattern ROLLING_EVENT_LOG_FILE_PATTERN = Pattern.compile("^events_(\\d+)(?:_|$).*$");
    private static final String MULTIPLE_APPLICATION_LOGS_MESSAGE =
            "Event log directory contains multiple Spark application logs. "
                    + "Point SparkDoctor at one application directory or event log file.";

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
        List<Path> directEventLogFiles = directEventLogFiles(directory);
        List<Path> applicationDirectories = applicationDirectories(directory);

        if (!directEventLogFiles.isEmpty() && !applicationDirectories.isEmpty()) {
            throw new IOException(MULTIPLE_APPLICATION_LOGS_MESSAGE);
        }

        if (applicationDirectories.size() > 1) {
            throw new IOException(MULTIPLE_APPLICATION_LOGS_MESSAGE);
        }

        List<Path> files = !directEventLogFiles.isEmpty()
                ? sortedEventLogFiles(directEventLogFiles)
                : applicationDirectories.isEmpty()
                        ? List.of()
                        : eventLogFilesUnder(applicationDirectories.get(0));

        return files.stream().flatMap(this::uncheckedFileLines);
    }

    private List<Path> directEventLogFiles(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(this::isEventLogFile).toList();
        }
    }

    private List<Path> applicationDirectories(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isDirectory)
                    .filter(this::containsEventLogFiles)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private boolean containsEventLogFiles(Path directory) {
        try (var stream = Files.walk(directory)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && isEventLogFile(path));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private List<Path> eventLogFilesUnder(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return sortedEventLogFiles(stream.filter(Files::isRegularFile).filter(this::isEventLogFile).toList());
        }
    }

    private List<Path> sortedEventLogFiles(List<Path> files) {
        return files.stream().sorted(this::compareEventLogPaths).toList();
    }

    private int compareEventLogPaths(Path left, Path right) {
        long leftSequence = rollingEventSequence(left);
        long rightSequence = rollingEventSequence(right);
        if (leftSequence != rightSequence) {
            return Long.compare(leftSequence, rightSequence);
        }

        return left.toString().compareTo(right.toString());
    }

    private long rollingEventSequence(Path path) {
        Matcher matcher = ROLLING_EVENT_LOG_FILE_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Long.MAX_VALUE;
        }

        return Long.parseLong(matcher.group(1));
    }

    private boolean isEventLogFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return !fileName.startsWith(".")
                && !fileName.endsWith(".crc")
                && !fileName.startsWith("appstatus")
                && !fileName.equals("_success");
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
