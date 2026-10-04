package ac.grim.grimac.utils.data;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list of plugin channel names from the config. An entry is either an exact channel name or a
 * prefix ending in {@code *}, e.g. {@code "examplemod:*"}. Matching ignores case.
 */
public final class ChannelPatterns {
    public static final ChannelPatterns EMPTY = new ChannelPatterns(Set.of(), new String[0]);

    private final Set<String> exact;
    private final String[] prefixes;

    private ChannelPatterns(Set<String> exact, String[] prefixes) {
        this.exact = exact;
        this.prefixes = prefixes;
    }

    public static @NotNull ChannelPatterns of(@NotNull Collection<?> entries) {
        Set<String> exact = new HashSet<>();
        List<String> prefixes = new ArrayList<>();
        for (Object entry : entries) {
            if (entry == null) continue;
            String pattern = entry.toString().trim().toLowerCase(Locale.ROOT);
            if (pattern.isEmpty()) continue;
            if (pattern.endsWith("*")) {
                prefixes.add(pattern.substring(0, pattern.length() - 1));
            } else {
                exact.add(pattern);
            }
        }
        if (exact.isEmpty() && prefixes.isEmpty()) return EMPTY;
        return new ChannelPatterns(Set.copyOf(exact), prefixes.toArray(new String[0]));
    }

    public boolean isEmpty() {
        return exact.isEmpty() && prefixes.length == 0;
    }

    public boolean matches(@NotNull String channel) {
        if (isEmpty()) return false;
        String lower = channel.toLowerCase(Locale.ROOT);
        if (exact.contains(lower)) return true;
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }
}
