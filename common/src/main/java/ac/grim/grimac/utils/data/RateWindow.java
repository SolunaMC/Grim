package ac.grim.grimac.utils.data;

/**
 * Counts events in fixed time windows, e.g. packets per second. Not thread safe; use one
 * instance per player and packet kind. Does not allocate.
 */
public final class RateWindow {
    public static final long SECOND_NANOS = 1_000_000_000L;

    private long windowStart;
    private int count;

    /**
     * Counts one event and returns how many events fell into the current window, this one included.
     *
     * @param now          current time, e.g. {@link System#nanoTime()}
     * @param windowLength length of a window in the same unit as {@code now}
     */
    public int record(long now, long windowLength) {
        if (count == 0 || now - windowStart >= windowLength || now - windowStart < 0) {
            windowStart = now;
            count = 0;
        }
        if (count != Integer.MAX_VALUE) count++;
        return count;
    }

    public void reset() {
        count = 0;
    }
}
