package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.GrimProcessor;
import ac.grim.grimac.checks.impl.badpackets.BadPacketsT;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.clientdetection.ChannelRegistration;
import ac.grim.grimac.utils.clientdetection.ClientDetectionMessages;
import ac.grim.grimac.utils.clientdetection.ClientDetectionSettings;
import ac.grim.grimac.utils.clientdetection.ClientRule;
import ac.grim.grimac.utils.clientdetection.ModSignature;
import ac.grim.grimac.utils.clientdetection.ModSignatures;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientBrand extends GrimProcessor implements PacketReceiveListener {

    private static final String CHANNEL = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13) ? "minecraft:brand" : "MC|Brand";
    // Bukkit itself only accepts 128 channels per player
    private static final int MAX_CHANNELS = 256;

    @Getter
    private String brand = "vanilla";
    @Getter
    private boolean hasBrand = false;

    // Written on the netty thread, read by commands: replaced as a whole, never mutated
    private volatile Set<String> channels = Set.of();
    private volatile DetectedMods detectedMods = DetectedMods.EMPTY;

    // Netty thread only
    private @Nullable String firstValidBrand;
    private boolean notificationPending;
    private long notificationAt; // 0 while waiting for the play phase
    private final Set<String> firedRules = new HashSet<>(); // keyed by ruleKey, survives reloads

    public ClientBrand(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            handle(packet.getChannelName(), packet.getData());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            handle(packet.getChannelName(), packet.getData());
        }

        if (notificationPending) {
            tickNotification(event);
        }
    }

    /** @return every channel the client registered this session (unregistrations are ignored) */
    public @NotNull Set<String> getChannels() {
        return channels;
    }

    /** @return names of the mods/clients detected from the registered channels */
    public @NotNull List<String> getMods() {
        Set<String> channels = this.channels;
        List<ModSignature> signatures = GrimAPI.INSTANCE.getConfigManager().getClientDetection().signatures();
        DetectedMods detected = detectedMods;
        // Recomputed lazily after new registrations or a reload, benign race when called concurrently
        if (detected.channels() != channels || detected.signatures() != signatures) {
            detected = new DetectedMods(channels, signatures, ModSignatures.detect(channels, signatures));
            detectedMods = detected;
        }
        return detected.mods();
    }

    private void handle(String channel, byte[] data) {
        if (ChannelRegistration.isRegisterChannel(channel)) {
            handleRegister(data);
            return;
        }

        if (!channel.equals(ClientBrand.CHANNEL)) return;

        if (data.length > 64 || data.length == 0) {
            brand = "sent " + data.length + " bytes as brand";
        } else if (!hasBrand) {
            brand = readBrand(data);
            firstValidBrand = brand;
            if (!GrimAPI.INSTANCE.getConfigManager().isIgnoredClient(brand)) {
                notificationPending = true;
                if (GrimAPI.INSTANCE.getConfigManager().getClientDetection().notificationDelayMs() == 0) {
                    sendNotification();
                }
            }
            // Push the now-known brand into the session row. The initial onJoin
            // upsert ran from PlayerJoinEvent, before the brand packet arrived,
            // so client_brand was null on disk. observeBrandFromCheck re-issues
            // the upsert with the same session_id (idempotent) but the brand
            // column now filled in. NOOP impl skips the work entirely.
            GrimAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks().observeBrandFromCheck(player);
        } else if (firstValidBrand != null) {
            player.checkManager.get(BadPacketsT.class).onLaterBrand(firstValidBrand, readBrand(data));
        }

        // https://github.com/MinecraftForge/MinecraftForge/issues/9309
        // "Forge 40.1.22 1.18.2+ has extended player reach"
        // quality development from forge devs
        // inbuilt reach hacks for over a year across 2 (3 if you include 1.19.3/1.20) major versions
        // Fixed in 1.19.4 possibly? Definitely fixed in 1.20+.
        final boolean hasReachHacks = brand.contains("forge")
                && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_18_2)
                && player.getClientVersion().isOlderThan(ClientVersion.V_1_19_4);
        if (hasReachHacks && GrimAPI.INSTANCE.getConfigManager().isBlockBlacklistedForgeClients()) {
            player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, GrimAPI.INSTANCE.getConfigManager().getDisconnectBlacklistedForge())));
        }

        boolean firstBrand = !hasBrand;
        hasBrand = true;
        if (firstBrand) {
            onClientInfoChanged();
        }
    }

    private static String readBrand(byte[] data) {
        byte[] minusLength = new byte[data.length - 1];
        System.arraycopy(data, 1, minusLength, 0, minusLength.length);

        String brand = new String(minusLength, StandardCharsets.UTF_8).replace(" (Velocity)", ""); // removes velocity's brand suffix
        return MessageUtil.stripColor(brand); // strip color codes from client brand
    }

    private void handleRegister(byte[] data) {
        Set<String> current = channels;
        if (current.size() >= MAX_CHANNELS) return;

        List<String> registered = ChannelRegistration.parse(data, MAX_CHANNELS - current.size());
        if (current.containsAll(registered)) return;

        Set<String> updated = new LinkedHashSet<>(current);
        updated.addAll(registered);
        channels = Collections.unmodifiableSet(updated);
        onClientInfoChanged();
    }

    // Brand and registrations arrive in any order; re-evaluated whenever either changes
    private void onClientInfoChanged() {
        String knownBrand = hasBrand ? brand : null;
        player.checkManager.get(BadPacketsT.class).onClientInfo(knownBrand, channels);
        evaluateRules(knownBrand);
    }

    private void evaluateRules(@Nullable String knownBrand) {
        List<ClientRule> rules = GrimAPI.INSTANCE.getConfigManager().getClientDetection().rules();
        if (rules.isEmpty()) return;

        List<String> mods = getMods();
        for (ClientRule rule : rules) {
            String key = ruleKey(rule);
            if (firedRules.contains(key)) continue;
            String match = rule.match(knownBrand, mods, channels);
            if (match == null) continue;

            firedRules.add(key);
            if (applyRule(rule, match)) return;
        }
    }

    private static String ruleKey(ClientRule rule) {
        return rule.type() + ":" + rule.action() + ":" + rule.pattern().pattern();
    }

    /** @return whether the player was kicked */
    private boolean applyRule(ClientRule rule, String match) {
        Map<String, String> values = Map.of("%match%", match, "%rule%", rule.pattern().pattern());
        ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        if (rule.action() == ClientRule.Action.KICK) {
            String message = rule.message() != null ? rule.message()
                    : config.getStringElse("client-brand.rule-kick-message", "<red>Your client or one of your mods is not allowed on this server.");
            LogUtil.info(player.getName() + " was kicked by client-brand rule \"" + rule.pattern().pattern() + "\" (matched \"" + match + "\")");
            player.disconnect(ClientDetectionMessages.render(player, message, values));
            return true;
        }

        String message = rule.message() != null ? rule.message()
                : config.getStringElse("client-brand.rule-alert-format", "%prefix% &f%player% &bmatched client rule &f%rule% &7(%match%)");
        GrimAPI.INSTANCE.getAlertManager().sendAlert(ClientDetectionMessages.render(player, message, values), null);
        return false;
    }

    // The notification waits a moment in the play phase so mods registered after the brand can be listed
    private void tickNotification(PacketReceiveEvent event) {
        if (notificationAt == 0) {
            if (!(event.getPacketType() instanceof PacketType.Play.Client)) return;
            notificationAt = System.currentTimeMillis() + GrimAPI.INSTANCE.getConfigManager().getClientDetection().notificationDelayMs();
        }
        if (System.currentTimeMillis() >= notificationAt) {
            sendNotification();
        }
    }

    private void sendNotification() {
        notificationPending = false;
        ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        String message = config.getStringElse("client-brand-format", "%prefix% &f%player% joined using %brand%");
        if (!getMods().isEmpty() && !message.contains("%mods%")) {
            message += config.getStringElse("client-brand.mods-suffix", " &7(mods: &f%mods%&7)");
        }
        Component component = MessageUtil.replacePlaceholders(player, MessageUtil.miniMessage(message));

        GrimAPI.INSTANCE.getAlertManager().sendBrand(component, null);
    }

    private record DetectedMods(Set<String> channels, List<ModSignature> signatures, List<String> mods) {
        static final DetectedMods EMPTY = new DetectedMods(Set.of(), ClientDetectionSettings.DEFAULT.signatures(), List.of());
    }
}
