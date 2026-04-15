package com.cocoding.playstate.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component("playDurationFormat")
public class PlayDurationFormat {

    public String forLibrarySumMinutes(long totalMinutes) {
        if (totalMinutes <= 0) {
            return "—";
        }
        long hours = totalMinutes / 60;
        if (hours > 9999) {
            return "9999+";
        }
        return formatPositiveMinutes(totalMinutes);
    }

    private static String formatPositiveMinutes(long total) {
        long h = total / 60;
        long m = total % 60;
        if (h == 0) {
            return m + "M";
        }
        if (m == 0) {
            return h + "H";
        }
        return h + "H " + m + "M";
    }

    
    public String forLogMinutesCompactLower(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }
        long h = minutes / 60L;
        long m = minutes % 60L;
        if (h == 0) {
            return m + "m";
        }
        if (m == 0) {
            return h + "h";
        }
        return h + "h " + m + "m";
    }

    /**
     * Compact duration for play log rows in the playthrough list (e.g. {@code 5H 30m}, {@code 8H},
     * {@code 25m}).
     */
    public String forLogMinutesRowDisplay(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }
        long h = minutes / 60L;
        long m = minutes % 60L;
        if (h == 0) {
            return m + "m";
        }
        if (m == 0) {
            return h + "H";
        }
        return h + "H " + m + "m";
    }

    
    public String forRecordTotalPlaytimeRead(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "0h";
        }
        return forLogMinutesCompactLower(totalMinutes);
    }

    public String forRecordHoursInputValue(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "";
        }
        return BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
