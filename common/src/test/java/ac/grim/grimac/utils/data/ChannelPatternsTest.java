package ac.grim.grimac.utils.data;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelPatternsTest {
    @Test
    void emptyListMatchesNothing() {
        ChannelPatterns patterns = ChannelPatterns.of(Arrays.asList("", "  ", null));
        assertSame(ChannelPatterns.EMPTY, patterns);
        assertTrue(patterns.isEmpty());
        assertFalse(patterns.matches("minecraft:brand"));
    }

    @Test
    void matchesExactNamesIgnoringCase() {
        ChannelPatterns patterns = ChannelPatterns.of(List.of("BungeeCord", "examplemod:main"));
        assertTrue(patterns.matches("bungeecord"));
        assertTrue(patterns.matches("BungeeCord"));
        assertTrue(patterns.matches("examplemod:main"));
        assertFalse(patterns.matches("examplemod:other"));
        assertFalse(patterns.matches("bungeecord:main"));
    }

    @Test
    void matchesPrefixes() {
        ChannelPatterns patterns = ChannelPatterns.of(List.of("examplemod:*"));
        assertTrue(patterns.matches("examplemod:main"));
        assertTrue(patterns.matches("ExampleMod:Other"));
        assertFalse(patterns.matches("minecraft:brand"));
    }
}
