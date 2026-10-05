package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.impl.misc.ClientBrand;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.manager.AlertManagerImpl;
import ac.grim.grimac.manager.datastore.PlayerToggleStore;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GrimBrands implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("brands", Description.of("Toggle brands for the sender"))
                        .permission("grim.brand")
                        .handler(this::handleBrands)
        );
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("brands")
                        .literal("stats", Description.of("Show brand, mod and version statistics of online players"))
                        .permission("grim.brand.stats")
                        .handler(this::handleStats)
        );
    }

    private void handleBrands(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (sender.isPlayer()) {
            PlatformPlayer player = Objects.requireNonNull(context.sender().getPlatformPlayer(), "player");
            AlertManagerImpl am = GrimAPI.INSTANCE.getAlertManager();
            boolean newState = !am.hasBrandsEnabled(player);
            am.setBrandsEnabled(player, newState, false);
            GrimAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore()
                    .applyUserToggle(player.getUniqueId(), PlayerToggleStore.KEY_BRANDS, newState);
        } else if (sender.isConsole()) {
            GrimAPI.INSTANCE.getAlertManager().toggleConsoleBrands();
        }
    }

    // Online players only, the datastore can't aggregate the brands of past sessions
    private void handleStats(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        Map<String, Integer> brands = new HashMap<>();
        Map<String, Integer> mods = new HashMap<>();
        Map<String, Integer> versions = new HashMap<>();
        int players = 0;

        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (player.platformPlayer == null || player.platformPlayer.isExternalPlayer()) continue;
            players++;
            ClientBrand clientBrand = player.checkManager.get(ClientBrand.class);
            brands.merge(clientBrand.isHasBrand() ? clientBrand.getBrand() : "unknown", 1, Integer::sum);
            for (String mod : clientBrand.getMods()) {
                mods.merge(mod, 1, Integer::sum);
            }
            versions.merge(player.getClientVersion().getReleaseName(), 1, Integer::sum);
        }

        ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        int maxEntries = Math.max(1, config.getIntElse("client-brand.stats.max-entries", 10));
        send(sender, config.getStringElse("client-brand.stats.header", "%prefix% &bClients of &f%players% &bonline players &7(online players only)"),
                Map.of("%players%", Integer.toString(players)));
        sendSection(sender, config, config.getStringElse("client-brand.stats.brands", "&bBrands:"), brands, players, maxEntries);
        sendSection(sender, config, config.getStringElse("client-brand.stats.mods", "&bDetected mods:"), mods, players, maxEntries);
        sendSection(sender, config, config.getStringElse("client-brand.stats.versions", "&bClient versions:"), versions, players, maxEntries);
    }

    private void sendSection(Sender sender, ConfigManager config, String title, Map<String, Integer> counts, int players, int maxEntries) {
        send(sender, title, Map.of());
        if (counts.isEmpty()) {
            send(sender, config.getStringElse("client-brand.stats.none", " &7- none"), Map.of());
            return;
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
        String entryFormat = config.getStringElse("client-brand.stats.entry", " &7- &f%name%&7: &f%count% &7(%percent%%)");
        for (Map.Entry<String, Integer> entry : sorted.subList(0, Math.min(maxEntries, sorted.size()))) {
            int percent = Math.round(entry.getValue() * 100f / players);
            send(sender, entryFormat, Map.of(
                    "%name%", entry.getKey(),
                    "%count%", Integer.toString(entry.getValue()),
                    "%percent%", Integer.toString(percent)));
        }
        if (sorted.size() > maxEntries) {
            send(sender, config.getStringElse("client-brand.stats.more", " &7... and &f%count% &7more"),
                    Map.of("%count%", Integer.toString(sorted.size() - maxEntries)));
        }
    }

    private void send(Sender sender, String template, Map<String, String> values) {
        sender.sendMessage(MessageUtil.replacePlaceholders(null, MessageUtil.miniMessage(template), values));
    }
}
