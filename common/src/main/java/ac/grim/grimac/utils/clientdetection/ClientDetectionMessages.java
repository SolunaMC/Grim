package ac.grim.grimac.utils.clientdetection;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

@UtilityClass
public class ClientDetectionMessages {
    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-zA-Z0-9_]+%");

    /**
     * Renders a configured message. {@code values} are inserted as plain text after
     * the regular placeholders, so client controlled strings (brands, channel names)
     * can't inject formatting.
     *
     * @param values placeholder (including the percent signs) to plain text value
     */
    public @NotNull Component render(@Nullable GrimPlayer player, @NotNull String template, @NotNull Map<String, String> values) {
        Component component = MessageUtil.miniMessage(template);
        if (player != null) {
            component = MessageUtil.replacePlaceholders(player, component);
        }
        if (values.isEmpty()) return component;
        // One pass, so a value containing another placeholder isn't expanded again
        return component.replaceText(TextReplacementConfig.builder()
                .match(PLACEHOLDER)
                .replacement((match, builder) -> {
                    String value = values.get(match.group());
                    return value == null ? builder : Component.text(value);
                })
                .build());
    }

    /** @return the detected mods joined for display, or {@code "none"} */
    public @NotNull String joinMods(@NotNull Iterable<String> mods) {
        String joined = String.join(", ", mods);
        return joined.isEmpty() ? "none" : joined;
    }
}
