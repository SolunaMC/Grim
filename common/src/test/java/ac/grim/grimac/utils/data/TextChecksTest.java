package ac.grim.grimac.utils.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextChecksTest {
    @Test
    void acceptsNormalText() {
        assertEquals(-1, TextChecks.firstIllegalChar("Hello, World! äöü 漢字 ‌", true, true));
    }

    @Test
    void findsControlCharacters() {
        assertEquals(2, TextChecks.firstIllegalChar("ab\ncd", true, false));
        assertEquals(0, TextChecks.firstIllegalChar("\u0000", true, false));
        assertEquals(1, TextChecks.firstIllegalChar("a\u007f", true, false));
        assertEquals(-1, TextChecks.firstIllegalChar("ab\ncd", false, true));
    }

    @Test
    void findsFormatCodes() {
        assertEquals(1, TextChecks.firstIllegalChar("a§cRed", false, true));
        assertEquals(-1, TextChecks.firstIllegalChar("a§cRed", true, false));
    }

    @Test
    void nothingToCheck() {
        assertEquals(-1, TextChecks.firstIllegalChar("\n§", false, false));
    }
}
