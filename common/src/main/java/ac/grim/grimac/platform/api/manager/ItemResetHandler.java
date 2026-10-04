package ac.grim.grimac.platform.api.manager;

import ac.grim.grimac.platform.api.player.PlatformPlayer;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ItemResetHandler {
    /**
     * clears any item usage the player may have, without triggering side effects (ie bow firing)
     * <p>
     * Can be called from any thread (ie netty), the reset is performed on the thread owning the player,
     * before the server handles any packet the player sends after this call
     */
    void resetItemUsage(@Nullable PlatformPlayer player);
    /**
     * Same as {@link #resetItemUsage(PlatformPlayer)}, but only if the player is using an item in the given hand
     * at the time the reset is performed
     */
    void resetItemUsage(@Nullable PlatformPlayer player, @NotNull InteractionHand hand);
    /**
     * Returns the hand in which the player is using an item, or null if the player isn't using an item
     * <p>
     * Must be called on the thread owning the player
     */
    @Contract("null -> null")
    @Nullable InteractionHand getItemUsageHand(@Nullable PlatformPlayer player);
    /**
     * Must be called on the thread owning the player
     */
    @Contract("null -> false")
    boolean isUsingItem(@Nullable PlatformPlayer player);
}
