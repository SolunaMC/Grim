package ac.grim.grimac.manager.config;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.data.ClientRule;
import ac.grim.grimac.utils.data.ModSignature;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
 * This is to hold whatever config manager was set via the reload method in the API
 * and any global variables that are the same between players.
 */
public class BaseConfigManager {

    // Replaced on reload, netty threads read these while handling plugin messages
    private volatile List<Pattern> ignoredClientPatterns = List.of();
    @Getter
    private volatile List<ModSignature> modSignatures = ModSignature.BUILT_IN;
    @Getter
    private volatile List<ClientRule> clientRules = List.of();
    @Getter
    private ConfigManager config = null;
    @Getter
    private boolean printAlertsToConsole = false;
    @Getter
    private volatile boolean bedrockEnabled = false;
    @Getter
    private String prefix = "&bGrim &8»";
    @Getter
    private String webhookNotEnabled;
    @Getter
    private String webhookTestMessage;
    @Getter
    private String webhookTestSucceeded;
    @Getter
    private String webhookTestFailed;
    @Getter
    private String disconnectTimeout;
    @Getter
    private String disconnectClosed;
    @Getter
    private String disconnectPacketError;
    @Getter
    private String disconnectBlacklistedForge;
    @Getter
    private boolean blockBlacklistedForgeClients;
    @Getter
    private boolean disablePongCancelling;
    @Getter
    private int updatePermissionTicks = -1;

    // initialize the config
    public void load(ConfigManager config) {
        this.config = config;

        int configuredMaxTransactionTime = config.getIntElse("max-transaction-time", 60);
        if (configuredMaxTransactionTime > 180 || configuredMaxTransactionTime < 1) {
            LogUtil.warn("Detected invalid max-transaction-time! This setting is clamped between 1 and 180 to prevent issues. Attempting to disable or set this too high can result in memory usage issues.");
        }

        List<Pattern> patterns = new ArrayList<>();
        List<String> ignoredClients = config.getStringList("client-brand.ignored-clients");
        if (ignoredClients != null) {
            for (String string : ignoredClients) {
                try {
                    patterns.add(Pattern.compile(string));
                } catch (PatternSyntaxException e) {
                    LogUtil.error("Skipping invalid client-brand.ignored-clients pattern: " + string, e);
                }
            }
        }
        ignoredClientPatterns = List.copyOf(patterns);

        List<ModSignature> signatures = new ArrayList<>(ModSignature.BUILT_IN);
        for (Object entry : getList(config, "client-brand.mod-signatures")) {
            try {
                if (!(entry instanceof String string)) throw new IllegalArgumentException("expected \"regex -> name\"");
                signatures.add(ModSignature.parse(string));
            } catch (IllegalArgumentException e) {
                LogUtil.warn("Skipping invalid client-brand.mod-signatures entry " + entry + ": " + e.getMessage());
            }
        }
        modSignatures = List.copyOf(signatures);

        List<ClientRule> rules = new ArrayList<>();
        for (Object entry : getList(config, "client-brand.rules")) {
            try {
                rules.add(ClientRule.parse(entry));
            } catch (IllegalArgumentException e) {
                LogUtil.warn("Skipping invalid client-brand.rules entry " + entry + ": " + e.getMessage());
            }
        }
        clientRules = List.copyOf(rules);

        printAlertsToConsole = config.getBooleanElse("alerts.print-to-console", true);
        bedrockEnabled = config.getBooleanElse("bedrock.enabled", false);
        prefix = config.getStringElse("prefix", "&bGrim &8»");

        webhookNotEnabled = config.getStringElse("webhook-not-enabled", "Discord webhooks are not enabled!");
        webhookTestMessage = config.getStringElse("webhook-test-message", "test message");
        webhookTestSucceeded = config.getStringElse("webhook-test-succeeded", "Discord webhook test succeeded!");
        webhookTestFailed = config.getStringElse("webhook-test-failed", "Discord webhook test failed!");
        disconnectTimeout = config.getStringElse("disconnect.timeout", "<lang:disconnect.timeout>");
        disconnectClosed = config.getStringElse("disconnect.closed", "<lang:disconnect.timeout>");
        disconnectPacketError = config.getStringElse("disconnect.error", "<red>An error occurred whilst processing packets. Please contact the administrators.");
        blockBlacklistedForgeClients = config.getBooleanElse("client-brand.disconnect-blacklisted-forge-versions", true);
        disconnectBlacklistedForge = config.getStringElse("disconnect.blacklisted-forge",
                "<red>Your forge version is blacklisted due to inbuilt reach hacks.<newline><gold>Versions affected: 1.18.2-1.19.3<newline><newline><red>Please see https://github.com/MinecraftForge/MinecraftForge/issues/9309.");
        disablePongCancelling = config.getBooleanElse("disable-pong-cancelling", false);
        int configuredUpdatePermissionTicks = config.getIntElse("update-permission-ticks", -1);
        updatePermissionTicks = configuredUpdatePermissionTicks <= 0 ? -1 : configuredUpdatePermissionTicks;
    }

    // ran on start, can be used to handle things that can't be done while loading
    public void start() {}

    public boolean isIgnoredClient(String brand) {
        for (Pattern pattern : ignoredClientPatterns) {
            if (pattern.matcher(brand).find()) return true;
        }
        return false;
    }

    private static Collection<?> getList(ConfigManager config, String key) {
        // get() throws for missing keys
        return config.getElse(key, null) instanceof Collection<?> collection ? collection : List.of();
    }
}
