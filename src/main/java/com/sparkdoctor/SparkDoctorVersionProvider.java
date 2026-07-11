package com.sparkdoctor;

import picocli.CommandLine.IVersionProvider;

public final class SparkDoctorVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[] {"SparkDoctor " + SparkDoctorVersion.current()};
    }
}
