package com.sparkdoctor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Provides the SparkDoctor version embedded by the Gradle build. */
public final class SparkDoctorVersion {
    private static final String VERSION_RESOURCE = "/sparkdoctor-version.properties";

    private SparkDoctorVersion() {}

    /** Returns the version embedded in this SparkDoctor build. */
    public static String current() {
        return VersionHolder.CURRENT;
    }

    private static String load() {
        try (InputStream input = SparkDoctorVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing version resource: " + VERSION_RESOURCE);
            }

            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version", "").trim();
            if (version.isEmpty() || version.contains("${")) {
                throw new IllegalStateException("Invalid SparkDoctor version resource");
            }
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read SparkDoctor version resource", exception);
        }
    }

    private static final class VersionHolder {
        private static final String CURRENT = load();
    }
}
