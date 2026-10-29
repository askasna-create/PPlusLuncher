package com.pplus.launcher;

import java.util.Calendar;

/**
 * Kiraan waktu solat luar talian (tanpa internet).
 * Parameter ala JAKIM: Subuh 18°, Isyak 18°, Asar Syafie (bayang 1x),
 * dengan ihtiyati 2 minit. Ketepatan biasanya dalam ±2 minit berbanding e-Solat.
 */
final class PrayerTimes {

    static final String[] NAMES = {"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};

    private static final double FAJR_ANGLE = 18.0;
    private static final double ISHA_ANGLE = 18.0;
    private static final double HORIZON = 0.833;
    private static final int[] IHTIYATI = {2, 0, 2, 2, 2, 2};

    private PrayerTimes() {}

    /** @return minit dari tengah malam (waktu tempatan) bagi setiap waktu dalam NAMES. */
    static int[] compute(Calendar day, double lat, double lng) {
        int y = day.get(Calendar.YEAR);
        int m = day.get(Calendar.MONTH) + 1;
        int d = day.get(Calendar.DAY_OF_MONTH);

        Calendar noon = (Calendar) day.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        double tz = day.getTimeZone().getOffset(noon.getTimeInMillis()) / 3600000.0;

        double jDate = julian(y, m, d) - lng / (15.0 * 24.0);

        double[] t = {5, 6, 12, 13, 18, 18};
        for (int iter = 0; iter < 2; iter++) {
            t = new double[]{
                    sunAngleTime(jDate, lat, FAJR_ANGLE, t[0], true),
                    sunAngleTime(jDate, lat, HORIZON, t[1], true),
                    midDay(jDate, t[2]),
                    asrTime(jDate, lat, t[3]),
                    sunAngleTime(jDate, lat, HORIZON, t[4], false),
                    sunAngleTime(jDate, lat, ISHA_ANGLE, t[5], false)
            };
        }

        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            double h = t[i] + tz - lng / 15.0;
            out[i] = (int) Math.round(h * 60.0) + IHTIYATI[i];
        }
        return out;
    }

    // ---- astronomi ----

    /** @return {deklinasi (darjah), persamaan masa (jam)} */
    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = Math.toDegrees(Math.atan2(dcos(e) * dsin(L), dcos(L))) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = Math.toDegrees(Math.asin(dsin(e) * dsin(L)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jDate, double hours) {
        double eqt = sun(jDate + hours / 24.0)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jDate, double lat, double angle, double hours, boolean before) {
        double decl = sun(jDate + hours / 24.0)[0];
        double noon = midDay(jDate, hours);
        double c = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        c = Math.max(-1, Math.min(1, c));
        double T = Math.toDegrees(Math.acos(c)) / 15.0;
        return noon + (before ? -T : T);
    }

    private static double asrTime(double jDate, double lat, double hours) {
        double decl = sun(jDate + hours / 24.0)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (1.0 + dtan(Math.abs(lat - decl)))));
        return sunAngleTime(jDate, lat, angle, hours, false);
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0);
        double B = 2 - A + Math.floor(A / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    private static double dsin(double x) { return Math.sin(Math.toRadians(x)); }
    private static double dcos(double x) { return Math.cos(Math.toRadians(x)); }
    private static double dtan(double x) { return Math.tan(Math.toRadians(x)); }
    private static double fixAngle(double a) { a %= 360; return a < 0 ? a + 360 : a; }
    private static double fixHour(double a) { a %= 24; return a < 0 ? a + 24 : a; }
}
