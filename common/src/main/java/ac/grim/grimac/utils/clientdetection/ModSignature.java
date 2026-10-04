package ac.grim.grimac.utils.clientdetection;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Maps registered plugin channel names to a human-readable mod or client name.
 *
 * @param pattern matched with {@link java.util.regex.Matcher#find()} against each channel name
 * @param name    display name shown to staff
 */
public record ModSignature(@NotNull Pattern pattern, @NotNull String name) {

    /** Separator of the {@code "regex -> name"} config format. */
    public static final String SEPARATOR = "->";

    public ModSignature {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(name, "name");
    }

    public static @NotNull ModSignature of(@NotNull String regex, @NotNull String name) {
        return new ModSignature(Pattern.compile(regex), name);
    }

    /**
     * Parses a {@code "regex -> name"} entry. The last {@code "->"} separates the
     * two parts, so the regex itself may contain {@code "->"}.
     *
     * @throws IllegalArgumentException if the entry is malformed or the regex is invalid
     */
    public static @NotNull ModSignature parse(@NotNull String entry) {
        int separator = entry.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("expected \"regex -> name\"");
        }
        String regex = entry.substring(0, separator).trim();
        String name = entry.substring(separator + SEPARATOR.length()).trim();
        if (regex.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("expected \"regex -> name\"");
        }
        try {
            return new ModSignature(Pattern.compile(regex), name);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex: " + e.getDescription(), e);
        }
    }

    public boolean matches(@NotNull String channel) {
        return pattern.matcher(channel).find();
    }
}
