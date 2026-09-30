package edu.vit.results.service;

/**
 * How a subject result is worked out.
 *
 * <p>MSE and ESE are each entered out of 100. The subject total (out of 100) weights
 * MSE at 30% and ESE at 70%. A student passes a subject with a total of at least 40
 * <em>and</em> at least 40 in the ESE. Grades use a 10-point scale, and the SGPA is the
 * credit-weighted average of grade points.
 */
public final class GradingPolicy {

    public static final double MAX_MARKS = 100;
    public static final int MSE_WEIGHT_PERCENT = 30;
    public static final int ESE_WEIGHT_PERCENT = 70;
    public static final double PASS_TOTAL = 40;
    public static final double PASS_ESE = 40;

    public enum Grade {
        O("O", 10, 90),
        A_PLUS("A+", 9, 80),
        A("A", 8, 70),
        B_PLUS("B+", 7, 60),
        B("B", 6, 50),
        C("C", 5, 45),
        P("P", 4, 40),
        F("F", 0, 0);

        public final String label;
        public final int points;
        public final double minTotal;

        Grade(String label, int points, double minTotal) {
            this.label = label;
            this.points = points;
            this.minTotal = minTotal;
        }
    }

    private GradingPolicy() {}

    /** Weighted total out of 100, rounded to 2 decimals. Integer weights avoid 0.3 / 0.7 float drift. */
    public static double total(double mse, double ese) {
        return round2((mse * MSE_WEIGHT_PERCENT + ese * ESE_WEIGHT_PERCENT) / 100.0);
    }

    public static Grade grade(double total, double ese) {
        if (ese < PASS_ESE || total < PASS_TOTAL) return Grade.F;
        for (Grade g : Grade.values()) {
            if (g != Grade.F && total >= g.minTotal) return g;
        }
        return Grade.F;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
