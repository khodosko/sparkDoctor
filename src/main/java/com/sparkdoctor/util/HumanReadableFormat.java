package com.sparkdoctor.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class HumanReadableFormat {
    private static final long KIB = 1024L;
    private static final long MIB = 1024L * KIB;
    private static final long GIB = 1024L * MIB;
    private static final long TIB = 1024L * GIB;
    private static final long SECOND_MILLIS = 1000L;
    private static final long MINUTE_MILLIS = 60L * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;

    private HumanReadableFormat() {}

    public static String bytes(long bytes) {
        if (bytes < KIB) {
            return bytes + " B";
        }
        if (bytes < MIB) {
            return formatScaled(bytes, KIB) + " KiB";
        }
        if (bytes < GIB) {
            return formatScaled(bytes, MIB) + " MiB";
        }
        if (bytes < TIB) {
            return formatScaled(bytes, GIB) + " GiB";
        }

        return formatScaled(bytes, TIB) + " TiB";
    }

    public static String millis(long millis) {
        if (millis < SECOND_MILLIS) {
            return millis + " ms";
        }
        if (millis < MINUTE_MILLIS) {
            return formatScaled(millis, SECOND_MILLIS) + " s";
        }
        if (millis < HOUR_MILLIS) {
            return formatScaled(millis, MINUTE_MILLIS) + " min";
        }

        return formatScaled(millis, HOUR_MILLIS) + " hr";
    }

    private static String formatScaled(long value, long unit) {
        double scaledValue = (double) value / unit;
        DecimalFormat decimalFormat = new DecimalFormat(
                "#,##0.#",
                DecimalFormatSymbols.getInstance(Locale.US));
        return decimalFormat.format(scaledValue);
    }
}
