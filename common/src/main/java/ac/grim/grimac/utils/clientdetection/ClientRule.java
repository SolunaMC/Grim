package ac.grim.grimac.utils.clientdetection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * An operator defined rule from {@code client-brand.rules}.
 *
 * @param pattern matched with {@link java.util.regex.Matcher#find()}
 * @param type    what the pattern is matched against
 * @param action  what happens on a match
 * @param message optional alert/kick message overriding the default one
 */
public record ClientRule(@NotNull Pattern pattern, @NotNull Type type, @NotNull Action action, @Nullable String message) {

    public enum Type {
        /** Matches the client brand. */
        BRAND,
        /** Matches detected mod names and the raw registered channel names. */
        MOD
    }

    public enum Action {
        ALERT,
        KICK
    }

    public ClientRule {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(action, "action");
    }

    /**
     * Parses one entry of the form {@code {regex: "...", type: brand|mod, action: alert|kick, message: "..."}}.
     *
     * @throws IllegalArgumentException if the entry is malformed
     */
    public static @NotNull ClientRule parse(@Nullable Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a section with regex, type and action");
        }
        String regex = string(map, "regex");
        if (regex == null || regex.isEmpty()) throw new IllegalArgumentException("missing regex");
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex: " + e.getDescription(), e);
        }
        Type type = enumValue(Type.class, string(map, "type"), "type", "brand|mod");
        Action action = enumValue(Action.class, string(map, "action"), "action", "alert|kick");
        String message = string(map, "message");
        return new ClientRule(pattern, type, action, message == null || message.isEmpty() ? null : message);
    }

    /**
     * Parses all entries, skipping invalid ones.
     *
     * @param onError receives the offending entry and the reason
     */
    public static @NotNull List<ClientRule> parseAll(@NotNull Collection<?> entries, @NotNull BiConsumer<Object, String> onError) {
        List<ClientRule> rules = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            try {
                rules.add(parse(entry));
            } catch (IllegalArgumentException e) {
                onError.accept(entry, e.getMessage());
            }
        }
        return List.copyOf(rules);
    }

    /**
     * @param brand    the client brand, or null while it is unknown
     * @param mods     detected mod names
     * @param channels registered channel names
     * @return the value that matched, or null if the rule doesn't match (yet)
     */
    public @Nullable String match(@Nullable String brand, @NotNull Collection<String> mods, @NotNull Collection<String> channels) {
        if (type == Type.BRAND) {
            return brand != null && pattern.matcher(brand).find() ? brand : null;
        }
        for (String mod : mods) {
            if (pattern.matcher(mod).find()) return mod;
        }
        for (String channel : channels) {
            if (pattern.matcher(channel).find()) return channel;
        }
        return null;
    }

    private static @Nullable String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, @Nullable String value, String key, String expected) {
        if (value == null) throw new IllegalArgumentException("missing " + key + " (" + expected + ")");
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + key + " \"" + value + "\" (" + expected + ")");
        }
    }
}
