package ac.grim.grimac.utils.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// An entry of client-brand.rules
public record ClientRule(@NotNull Pattern pattern, @NotNull Type type, @NotNull Action action, @Nullable String message) {

    public enum Type {
        BRAND,
        MOD // detected mod names and the raw channel names
    }

    public enum Action {
        ALERT,
        KICK
    }

    // {regex: "...", type: brand|mod, action: alert|kick, message: "..."}
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

    // Returns the matching value, or null if the rule doesn't match (yet)
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
