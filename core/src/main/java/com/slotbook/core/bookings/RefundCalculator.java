package com.slotbook.core.bookings;

/**
 * Cancellation policy: 90% back if the booking is cancelled at least 24 hours before the class starts,
 * otherwise 50%.
 */
public final class RefundCalculator {

    static final double EARLY_REFUND = 0.9;
    static final double LATE_REFUND = 0.5;

    private RefundCalculator() {
    }

    /** The refund for an amount paid, in rupees, rounded to the nearest paisa. */
    public static double refundFor(double paid, boolean early) {
        double refund = paid * (early ? EARLY_REFUND : LATE_REFUND);
        return Math.round(refund * 100.0) / 100.0;
    }
}
