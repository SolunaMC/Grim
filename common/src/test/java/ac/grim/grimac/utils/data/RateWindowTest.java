package ac.grim.grimac.utils.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateWindowTest {
    private static final long SECOND = RateWindow.SECOND_NANOS;

    @Test
    void countsEventsInsideOneWindow() {
        RateWindow window = new RateWindow();
        long start = 5_000_000_000L;
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, window.record(start + i * 1_000_000L, SECOND));
        }
    }

    @Test
    void startsOverAfterTheWindow() {
        RateWindow window = new RateWindow();
        window.record(0, SECOND);
        window.record(SECOND - 1, SECOND);
        assertEquals(1, window.record(SECOND, SECOND));
        assertEquals(2, window.record(SECOND + 1, SECOND));
    }

    @Test
    void handlesNegativeAndBackwardsTime() {
        RateWindow window = new RateWindow();
        assertEquals(1, window.record(-10L * SECOND, SECOND));
        assertEquals(2, window.record(-10L * SECOND + 5, SECOND));
        // a clock that jumps back starts a new window instead of counting forever
        assertEquals(1, window.record(-20L * SECOND, SECOND));
    }

    @Test
    void keepsCountingWithinALongWindow() {
        RateWindow window = new RateWindow();
        for (long i = 0; i < 100; i++) window.record(i, Long.MAX_VALUE);
        assertEquals(101, window.record(100, Long.MAX_VALUE));
    }
}
