package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.GrimProcessor;
import ac.grim.grimac.checks.impl.badpackets.BadPacketsT;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.data.ClientRule;
import ac.grim.grimac.utils.data.ModSignature;
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

    // Written on the netty thread and read by commands, so it is replaced instead of modified
    private volatile Set<String> channels = Set.of();
    private volatile DetectedMods detectedMods;

    private @Nullable String firstValidBrand;
    private long notificationDelay;
    private boolean notificationPending;
    private long notificationAt; // 0 while waiting for the play phase
    // Keyed by the rule itself instead of its index so a reload doesn't fire the same rule again
    private final Set<String> firedRules = new HashSet<>();

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

    public @NotNull List<String> getMods() {
        Set<String> channels = this.channels;
        List<ModSignature> signatures = GrimAPI.INSTANCE.getConfigManager().getModSignatures();
        DetectedMods detected = detectedMods;
        // Recomputed after new registrations or a reload
        if (detected == null || detected.channels() != channels || detected.signatures() != signatures) {
            detected = new DetectedMods(channels, signatures, ModSignature.detect(channels, signatures));
            detectedMods = detected;
        }
        return detected.mods();
    }

    private void handle(String channel, byte[] data) {
        if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
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
                if (notificationDelay == 0) {
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
        boolean firstBrand = !hasBrand;
        hasBrand = true;

        if (hasReachHacks && GrimAPI.INSTANCE.getConfigManager().isBlockBlacklistedForgeClients()) {
            if (notificationPending) sendNotification(); // staff still see the brand of kicked players
            player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, GrimAPI.INSTANCE.getConfigManager().getDisconnectBlacklistedForge())));
            return;
        }

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

        List<String> registered = ModSignature.readChannels(data, MAX_CHANNELS - current.size());
        if (current.containsAll(registered)) return;

        Set<String> updated = new LinkedHashSet<>(current);
        updated.addAll(registered);
        channels = Collections.unmodifiableSet(updated);
        onClientInfoChanged();
    }

    // The brand and the registrations arrive in any order
    private void onClientInfoChanged() {
        String knownBrand = hasBrand ? brand : null;
        player.checkManager.get(BadPacketsT.class).onClientInfo(knownBrand, channels);

        List<ClientRule> rules = GrimAPI.INSTANCE.getConfigManager().getClientRules();
        if (rules.isEmpty()) return;

        List<String> mods = getMods();
        for (ClientRule rule : rules) {
            String key = rule.type() + ":" + rule.action() + ":" + rule.pattern().pattern();
            if (firedRules.contains(key)) continue;
            String match = rule.match(knownBrand, mods, channels);
            if (match == null) continue;

            firedRules.add(key);
            if (applyRule(rule, match)) return;
        }
    }

    // Returns whether the player was kicked
    private boolean applyRule(ClientRule rule, String match) {
        Map<String, String> values = Map.of("%match%", match, "%rule%", rule.pattern().pattern());
        ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        if (rule.action() == ClientRule.Action.KICK) {
            String message = rule.message() != null ? rule.message()
                    : config.getStringElse("client-brand.rule-kick-message", "<red>Your client or one of your mods is not allowed on this server.");
            if (notificationPending) sendNotification(); // staff still see the brand of kicked players
            LogUtil.info(player.getName() + " was kicked by client-brand rule \"" + rule.pattern().pattern() + "\" (matched \"" + match + "\")");
            player.disconnect(MessageUtil.replacePlaceholders(player, MessageUtil.miniMessage(message), values));
            return true;
        }

        String message = rule.message() != null ? rule.message()
                : config.getStringElse("client-brand.rule-alert-format", "%prefix% &f%player% &bmatched client rule &f%rule% &7(%match%)");
        GrimAPI.INSTANCE.getAlertManager().sendAlert(MessageUtil.replacePlaceholders(player, MessageUtil.miniMessage(message), values), null);
        return false;
    }

    // Waits a moment in the play phase so mods that register after the brand are listed too
    private void tickNotification(PacketReceiveEvent event) {
        if (notificationAt == 0) {
            if (!(event.getPacketType() instanceof PacketType.Play.Client)) return;
            notificationAt = System.currentTimeMillis() + notificationDelay;
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

    @Override
    public void onReload(@NotNull ConfigManager config) {
        notificationDelay = Math.max(0, config.getLongElse("client-brand.notification-delay-ms", 1000));
    }

    private record DetectedMods(Set<String> channels, List<ModSignature> signatures, List<String> mods) {}
}
