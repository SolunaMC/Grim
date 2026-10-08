package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.impl.misc.ClientBrand;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.reflection.GeyserUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GrimProfile implements BuildableCommand {
    private static final List<String> DEFAULT_BEDROCK_PROFILE = List.of(
            "&7======================",
            "%prefix% &bProfile for &f%player%",
            "&bBedrock player &7(exempt from checks)",
            "&bDevice: &f%bedrock_device%",
            "&bInput: &f%bedrock_input%",
            "&7======================");
    private static final List<String> DEFAULT_CHECKED_BEDROCK_PROFILE = List.of(
            "&7======================",
            "%prefix% &bProfile for &f%player%",
            "&bBedrock player &7(checked by the Bedrock checks)",
            "&bDevice: &f%bedrock_device%",
            "&bInput: &f%bedrock_input%",
            "&bPing: &f%ping%",
            "&bVersion: &f%bedrock_version%",
            "&bClient Brand: &f%brand%",
            "&7======================");
    // Geyser UUID without the Floodgate or Geyser API to ask for the device
    private static final GeyserUtil.BedrockDevice UNKNOWN_DEVICE = new GeyserUtil.BedrockDevice("UNKNOWN", "UNKNOWN");

    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("profile")
                        .permission("grim.profile")
                        .required("target", arguments.singlePlayerSelectorParser())
                        .handler(this::handleProfile)
        );
    }

    private void handleProfile(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector target = context.get("target");

        PlatformPlayer targetPlatformPlayer = target.getSinglePlayer().getPlatformPlayer();
        if (Objects.requireNonNull(targetPlatformPlayer, "targetPlatformPlayer").isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender,"player-not-this-server", "%prefix% &cThis player isn't on this server!"));
            return;
        }

        GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(targetPlatformPlayer.getUniqueId());
        if (grimPlayer == null) {
            // Bedrock players are exempt, but their device is still useful to know
            GeyserUtil.BedrockDevice device = GeyserUtil.getBedrockDevice(targetPlatformPlayer.getUniqueId());
            if (device != null) {
                sendBedrockProfile(sender, null, targetPlatformPlayer, device, "client-brand.bedrock-profile", DEFAULT_BEDROCK_PROFILE);
                return;
            }
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cPlayer is exempt or offline!"));
            return;
        }

        // Sensitivity and FastMath mean nothing for Bedrock clients
        if (grimPlayer.bedrockPlayer) {
            GeyserUtil.BedrockDevice device = Objects.requireNonNullElse(GeyserUtil.getBedrockDevice(targetPlatformPlayer.getUniqueId()), UNKNOWN_DEVICE);
            // Turning bedrock.enabled off with a reload exempts players that are already tracked
            if (GrimAPI.INSTANCE.getConfigManager().isBedrockEnabled()) {
                sendBedrockProfile(sender, grimPlayer, targetPlatformPlayer, device, "client-brand.bedrock-profile-checked", DEFAULT_CHECKED_BEDROCK_PROFILE);
            } else {
                sendBedrockProfile(sender, grimPlayer, targetPlatformPlayer, device, "client-brand.bedrock-profile", DEFAULT_BEDROCK_PROFILE);
            }
            return;
        }

        ConfigManager config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        List<String> profile = new ArrayList<>(config.getStringList("profile"));
        // Added before the closing line, unless the profile already contains %mods%
        if (!grimPlayer.checkManager.get(ClientBrand.class).getMods().isEmpty()
                && profile.stream().noneMatch(line -> line.contains("%mods%"))) {
            profile.add(Math.max(0, profile.size() - 1), config.getStringElse("client-brand.profile-mods-line", "&bMods: &f%mods%"));
        }

        for (String message : profile) {
            final Component component = MessageUtil.miniMessage(message);
            sender.sendMessage(MessageUtil.replacePlaceholders(grimPlayer, component));
        }
    }

    private void sendBedrockProfile(Sender sender, @Nullable GrimPlayer player, PlatformPlayer target, GeyserUtil.BedrockDevice device, String key, List<String> defaults) {
        List<String> lines = GrimAPI.INSTANCE.getConfigManager().getConfig().getStringListElse(key, defaults);
        Map<String, String> values = Map.of(
                "%player%", target.getName(),
                "%bedrock_device%", device.os(),
                "%bedrock_input%", device.inputMode(),
                "%bedrock_version%", Objects.requireNonNullElse(GeyserUtil.getBedrockVersion(target.getUniqueId()), "UNKNOWN"));
        for (String line : lines) {
            sender.sendMessage(MessageUtil.replacePlaceholders(player, MessageUtil.miniMessage(line), values));
        }
    }
}
