package com.lifeos.strategy.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Calendar dates as the user sees them: "today" depends on the user's time zone, not on the server's UTC clock. */
public final class UserDates {

    private UserDates() {
    }

    /** Today's date in the user's IANA time zone (from the profile). */
    public static LocalDate today(Clock clock, String timezone) {
        return LocalDate.now(clock.withZone(ZoneId.of(timezone)));
    }
}
