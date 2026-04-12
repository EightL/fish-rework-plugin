package com.fishrework.util;

import java.util.Locale;

public final class FormatUtil {

    private FormatUtil() {
    }

    public static String format(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }
}
