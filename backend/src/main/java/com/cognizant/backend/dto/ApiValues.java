package com.cognizant.backend.dto;

import java.util.Locale;

/** Converts enum constants to the lower-case values the frontend uses, e.g. NEEDS_REVIEW → needs_review. */
final class ApiValues {

    private ApiValues() {
    }

    static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
