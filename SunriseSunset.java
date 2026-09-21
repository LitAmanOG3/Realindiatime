package fun.litmc.realindiatime;

import java.time.*;

/** Compact NOAA-style sunrise/sunset calculation. Angles are approximate and configurable via location. */
final class SunriseSunset {
    private SunriseSunset() {}

    static RealIndiaTimePlugin.SunWindow calculate(LocalDate date, double latitude, double longitude, ZoneId zone) {
        double rise = solarEvent(date, latitude, longitude, true);
        double set = solarEvent(date, latitude, longitude, false);
        LocalTime sunrise = toLocalTime(rise, date, longitude, zone);
        LocalTime sunset = toLocalTime(set, date, longitude, zone);
        return new RealIndiaTimePlugin.SunWindow(sunrise, sunset);
    }

    private static double solarEvent(LocalDate date, double lat, double lon, boolean sunrise) {
        int n = date.getDayOfYear();
        double lngHour = lon / 15.0;
        double t = n + ((sunrise ? 6.0 : 18.0) - lngHour) / 24.0;
        double M = (0.9856 * t) - 3.289;
        double L = M + (1.916 * Math.sin(Math.toRadians(M))) + (0.020 * Math.sin(Math.toRadians(2 * M))) + 282.634;
        L = normalize360(L);
        double RA = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(L))));
        RA = normalize360(RA);
        double Lquadrant = Math.floor(L / 90.0) * 90.0;
        double RAquadrant = Math.floor(RA / 90.0) * 90.0;
        RA = RA + (Lquadrant - RAquadrant);
        RA /= 15.0;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(L));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * Math.sin(Math.toRadians(lat))) / (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1) return sunrise ? 0 : 24;
        if (cosH < -1) return sunrise ? 0 : 24;
        double H = sunrise ? 360.0 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        H /= 15.0;
        double T = H + RA - (0.06571 * t) - 6.622;
        return normalize24(T - lngHour);
    }

    private static LocalTime toLocalTime(double utcHour, LocalDate date, double lon, ZoneId zone) {
        int sec = (int)Math.round(utcHour * 3600.0);
        Instant instant = date.atStartOfDay(ZoneOffset.UTC).plusSeconds(sec).toInstant();
        return instant.atZone(zone).toLocalTime().withNano(0);
    }

    private static double normalize360(double x) { x %= 360.0; return x < 0 ? x + 360 : x; }
    private static double normalize24(double x) { x %= 24.0; return x < 0 ? x + 24 : x; }
}
