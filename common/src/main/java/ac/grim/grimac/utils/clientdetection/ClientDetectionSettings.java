package ac.grim.grimac.utils.clientdetection;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.utils.anticheat.LogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parsed {@code client-brand.*} settings for mod detection, rules and the brand
 * spoof check. Immutable; replaced as a whole on reload.
 *
 * @param signatures          built-in followed by operator signatures
 * @param rules               operator rules, empty by default
 * @param notificationDelayMs how long the staff brand notification waits for channel registrations
 * @param spoofCheckEnabled   master switch of the brand spoof check, off by default
 * @param spoofVanillaWithModLoader flag "vanilla" brands that register mod loader channels
 * @param spoofBrandChange    flag a different brand later in the same session
 */
public record ClientDetectionSettings(
        @NotNull List<ModSignature> signatures,
        @NotNull List<ClientRule> rules,
        long notificationDelayMs,
        boolean spoofCheckEnabled,
        boolean spoofVanillaWithModLoader,
        boolean spoofBrandChange
) {

    public static final long DEFAULT_NOTIFICATION_DELAY_MS = 1000;

    public static final ClientDetectionSettings DEFAULT = new ClientDetectionSettings(
            ModSignatures.BUILT_IN, List.of(), DEFAULT_NOTIFICATION_DELAY_MS, false, true, false);

    public static @NotNull ClientDetectionSettings load(@NotNull ConfigManager config) {
        List<ModSignature> signatures = new ArrayList<>(ModSignatures.BUILT_IN);
        signatures.addAll(ModSignatures.parse(list(config, "client-brand.mod-signatures"), (entry, reason) ->
                LogUtil.warn("Skipping invalid client-brand.mod-signatures entry " + entry + ": " + reason)));

        List<ClientRule> rules = ClientRule.parseAll(list(config, "client-brand.rules"), (entry, reason) ->
                LogUtil.warn("Skipping invalid client-brand.rules entry " + entry + ": " + reason));

        long delay = Math.max(0, config.getLongElse("client-brand.notification-delay-ms", DEFAULT_NOTIFICATION_DELAY_MS));

        return new ClientDetectionSettings(
                List.copyOf(signatures),
                rules,
                delay,
                config.getBooleanElse("client-brand.spoof-check.enabled", false),
                config.getBooleanElse("client-brand.spoof-check.vanilla-with-mod-loader", true),
                config.getBooleanElse("client-brand.spoof-check.brand-change", false));
    }

    private static Collection<?> list(ConfigManager config, String key) {
        Object value = config.<Object>getElse(key, null); // get() throws for missing keys
        return value instanceof Collection<?> collection ? collection : List.of();
    }
}
