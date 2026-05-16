package com.sparkdoctor.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sparkdoctor.model.AnalysisReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AnalysisJsonWriter {
    private static final String ANALYSIS_FILE_NAME = "analysis.json";

    private final ObjectMapper objectMapper;

    public AnalysisJsonWriter() {
        this(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    AnalysisJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path write(Path outputDirectory, AnalysisReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        Path analysisPath = outputDirectory.resolve(ANALYSIS_FILE_NAME);
        objectMapper.writeValue(analysisPath.toFile(), report);
        return analysisPath;
    }
}

