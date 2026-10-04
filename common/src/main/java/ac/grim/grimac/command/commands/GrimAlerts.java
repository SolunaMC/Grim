package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.manager.AlertManagerImpl;
import ac.grim.grimac.manager.datastore.PlayerToggleStore;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GrimAlerts implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("alerts", Description.of("Toggle alerts for the sender"))
                        .permission("grim.alerts")
                        .handler(this::handleAlerts)
        );
    }

    private void handleAlerts(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (sender.isPlayer()) {
            PlatformPlayer player = Objects.requireNonNull(context.sender().getPlatformPlayer(), "player");
            AlertManagerImpl am = GrimAPI.INSTANCE.getAlertManager();
            boolean newState = !am.hasAlertsEnabled(player);
            am.setAlertsEnabled(player, newState, false);
            PlayerToggleStore toggles = GrimAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore();
            toggles.applyUserToggle(player.getUniqueId(), PlayerToggleStore.KEY_ALERTS, newState);
            // Disabling alerts also disables verbose — persist that too, otherwise a stored
            // verbose=true turns alerts back on with the next join.
            if (!newState) toggles.applyUserToggle(player.getUniqueId(), PlayerToggleStore.KEY_VERBOSE, false);
        } else if (sender.isConsole()) {
            GrimAPI.INSTANCE.getAlertManager().toggleConsoleAlerts();
        }
    }
}
