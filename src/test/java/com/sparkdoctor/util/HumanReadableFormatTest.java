package com.sparkdoctor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HumanReadableFormatTest {
    @Test
    void formatsBytesWithBinaryUnits() {
        assertEquals("0 B", HumanReadableFormat.bytes(0));
        assertEquals("512 B", HumanReadableFormat.bytes(512));
        assertEquals("1 KiB", HumanReadableFormat.bytes(1024));
        assertEquals("1.5 KiB", HumanReadableFormat.bytes(1536));
        assertEquals("256 MiB", HumanReadableFormat.bytes(256L * 1024L * 1024L));
        assertEquals("1 GiB", HumanReadableFormat.bytes(1024L * 1024L * 1024L));
        assertEquals("1.5 TiB", HumanReadableFormat.bytes(1536L * 1024L * 1024L * 1024L));
    }

    @Test
    void formatsMillisWithTimeUnits() {
        assertEquals("0 ms", HumanReadableFormat.millis(0));
        assertEquals("999 ms", HumanReadableFormat.millis(999));
        assertEquals("1 s", HumanReadableFormat.millis(1000));
        assertEquals("4.5 s", HumanReadableFormat.millis(4500));
        assertEquals("30 s", HumanReadableFormat.millis(30_000));
        assertEquals("1.5 min", HumanReadableFormat.millis(90_000));
        assertEquals("1 hr", HumanReadableFormat.millis(3_600_000));
        assertEquals("2.5 hr", HumanReadableFormat.millis(9_000_000));
    }
}
