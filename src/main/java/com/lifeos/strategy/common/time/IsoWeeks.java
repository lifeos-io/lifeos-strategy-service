package com.lifeos.strategy.common.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ISO-8601 week labels such as {@code 2026-W46} (weeks start on Monday; week 1 holds the year's first Thursday). */
public final class IsoWeeks {

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-W(\\d{2})");

    private IsoWeeks() {
    }

    /** The Monday of the week, or empty when the label is malformed or the week does not exist (e.g. W53). */
    public static Optional<LocalDate> monday(String isoWeek) {
        Matcher matcher = isoWeek == null ? null : FORMAT.matcher(isoWeek);
        if (matcher == null || !matcher.matches()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        int weeksInYear = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > weeksInYear) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.of(year, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(DayOfWeek.MONDAY));
    }

    /** The label of the week a date falls in, e.g. 2026-11-08 → {@code 2026-W45}. */
    public static String of(LocalDate date) {
        return "%d-W%02d".formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
