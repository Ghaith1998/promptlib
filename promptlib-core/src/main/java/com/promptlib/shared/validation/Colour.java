package com.promptlib.shared.validation;

import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;

import java.util.regex.Pattern;

/** Optional decorative colour, stored as #RRGGBB. */
public final class Colour {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private Colour() {
    }

    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!HEX.matcher(value).matches()) {
            throw new ValidationFailed(ProblemCode.INVALID_COLOUR);
        }
        return value.toUpperCase();
    }
}
