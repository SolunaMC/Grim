package ac.grim.grimac.utils.clientdetection;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@UtilityClass
public class ClientDetectionMessages {

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
        for (Map.Entry<String, String> entry : values.entrySet()) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(entry.getKey())
                    .replacement(Component.text(entry.getValue()))
                    .build());
        }
        return component;
    }

    /** @return the detected mods joined for display, or {@code "none"} */
    public @NotNull String joinMods(@NotNull Iterable<String> mods) {
        String joined = String.join(", ", mods);
        return joined.isEmpty() ? "none" : joined;
    }
}
