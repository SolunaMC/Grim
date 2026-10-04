package ac.grim.grimac.platform.fabric.manager;

import ac.grim.grimac.platform.api.manager.ItemResetHandler;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.fabric.AbstractGrimACFabricEntryPoint;
import ac.grim.grimac.platform.fabric.inject.FabricMinecraftServerHandle;
import ac.grim.grimac.platform.fabric.inject.FabricServerPlayerHandle;
import ac.grim.grimac.platform.fabric.utils.convert.IFabricConversionUtil;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

@RequiredArgsConstructor
public class FabricItemResetHandler implements ItemResetHandler {
    private final IFabricConversionUtil conversionUtil;

    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player) {
        if (player != null) {
            runOnServerThread(() -> handle(player).stopUsingItem());
        }
    }

    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player, @NotNull InteractionHand hand) {
        if (player != null) {
            runOnServerThread(() -> {
                if (getItemUsageHand(player) == hand) {
                    handle(player).stopUsingItem();
                }
            });
        }
    }

    @Override
    public @Nullable InteractionHand getItemUsageHand(@Nullable PlatformPlayer platformPlayer) {
        if (platformPlayer == null) {
            return null;
        }

        FabricServerPlayerHandle player = handle(platformPlayer);
        return player.isUsingItem() ? conversionUtil.fromFabricInteractionHand(player.usedItemHand()) : null;
    }

    @Override
    public boolean isUsingItem(@Nullable PlatformPlayer player) {
        return player != null && handle(player).isUsingItem();
    }

    // The server is an Executor which inbound packets are queued onto in the order they arrive, so the task runs
    // before the server handles any later packet (and runs immediately if we are already on the server thread)
    private static void runOnServerThread(Runnable task) {
        FabricMinecraftServerHandle server = AbstractGrimACFabricEntryPoint.serverOrNull();
        if (server instanceof Executor executor) {
            executor.execute(task);
        } else {
            task.run();
        }
    }

    private FabricServerPlayerHandle handle(PlatformPlayer player) {
        return (FabricServerPlayerHandle) player.getNative();
    }
}
