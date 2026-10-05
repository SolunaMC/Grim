package ac.grim.grimac.utils.data;

// Counts events in fixed time windows, e.g. packets per second
public final class RateWindow {
    public static final long SECOND_NANOS = 1_000_000_000L;

    private long windowStart;
    private int count;

    // Returns how many events fell into the current window, this one included
    public int record(long now, long windowLength) {
        if (count == 0 || now - windowStart >= windowLength || now - windowStart < 0) {
            windowStart = now;
            count = 0;
        }
        if (count != Integer.MAX_VALUE) count++;
        return count;
    }
}
