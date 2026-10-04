package ac.grim.grimac.platform.bukkit.manager;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.platform.api.Platform;
import ac.grim.grimac.platform.api.manager.ItemResetHandler;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.bukkit.utils.reflection.PaperUtils;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.reflection.ReflectionUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class BukkitItemResetHandler implements ItemResetHandler {
    private static final Consumer<Player> resetItemUsage;
    private static final Predicate<Player> isUsingItem;
    private static final Function<Player, InteractionHand> getItemUsageHand;
    // the NMS server, an Executor on 1.14+ which inbound packets are queued onto in the order they arrive
    private static final @Nullable Executor serverExecutor;

    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player) {
        if (player != null) runOnOwningThread(player, () -> resetItemUsage.accept((Player) player.getNative()));
    }

    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player, @NotNull InteractionHand hand) {
        if (player != null) runOnOwningThread(player, () -> {
            Player bukkitPlayer = (Player) player.getNative();
            if (getItemUsageHand.apply(bukkitPlayer) == hand) resetItemUsage.accept(bukkitPlayer);
        });
    }

    @Override
    public @Nullable InteractionHand getItemUsageHand(@Nullable PlatformPlayer player) {
        return player == null ? null : getItemUsageHand.apply((Player) player.getNative());
    }

    @Override
    public boolean isUsingItem(@Nullable PlatformPlayer player) {
        return player != null && isUsingItem.test((Player) player.getNative());
    }

    // The entity must only be modified by the thread owning it, and the reset has to happen before the server handles
    // any packet received after this call, otherwise it could cancel an item use the player started afterwards
    private static void runOnOwningThread(PlatformPlayer player, Runnable task) {
        if (GrimAPI.INSTANCE.getPlatform() == Platform.FOLIA) {
            if (Bukkit.isOwnedByCurrentRegion((Player) player.getNative())) {
                task.run();
            } else {
                // Folia handles packets through the player's entity scheduler with a delay of 1 tick, so this runs before any later packet
                GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player, GrimAPI.INSTANCE.getGrimPlugin(), task, null, 1);
            }
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (serverExecutor != null) {
            serverExecutor.execute(task);
        } else {
            // 1.13 and below: scheduled tasks run at the start of the tick, before the packets queued for that tick
            GrimAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player, GrimAPI.INSTANCE.getGrimPlugin(), task, null, 0);
        }
    }

    static {
        final ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();

        final boolean legacy = version.isOlderThanOrEquals(ServerVersion.V_1_8_8);

        Executor executor = null;
        if (GrimAPI.INSTANCE.getPlatform() != Platform.FOLIA) {
            try {
                if (SpigotReflectionUtil.getMinecraftServerInstance(Bukkit.getServer()) instanceof Executor e) executor = e;
            } catch (Throwable ignored) {
            }
        }
        serverExecutor = executor;

        Predicate<Player> isUsingItemImpl = null;
        Function<Player, InteractionHand> getItemUsageHandImpl = null;
        Consumer<Player> resetItemUsageImpl = null;

        try {
            final Method getHandle;
            final String nmsPackage;

            Class<?> CraftLivingEntity = ReflectionUtils.getClass("org.bukkit.craftbukkit.entity.CraftLivingEntity");
            if (CraftLivingEntity != null) {
                getHandle = CraftLivingEntity.getMethod("getHandle");
                nmsPackage = null;
            } else {
                nmsPackage = Bukkit.getServer().getClass().getPackageName().split("\\.")[3];
                final String className = legacy ? "CraftHumanEntity" : "CraftLivingEntity";
                getHandle = Class.forName("org.bukkit.craftbukkit." + nmsPackage + ".entity." + className).getMethod("getHandle");
            }

            final boolean obfuscated = nmsPackage != null;
            final Class<?> clazz = getHandle.getReturnType();

            if (version.isNewerThanOrEquals(ServerVersion.V_1_10)) {
                isUsingItemImpl = Player::isHandRaised;
            } else {
                Method method = clazz.getMethod(switch (Objects.requireNonNull(nmsPackage, "nmsPackage")) {
                    case "v1_8_R3" -> "bS";
                    case "v1_9_R1" -> "cs";
                    case "v1_9_R2" -> "ct";
                    default -> throw new IllegalStateException("You are using an unsupported server version: " + nmsPackage + "/" + version.getReleaseName());
                });
                isUsingItemImpl = player -> {
                    try {
                        return (boolean) method.invoke(getHandle.invoke(player));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                };
            }

            if (legacy) {
                final Predicate<Player> usingItem = isUsingItemImpl;
                getItemUsageHandImpl = player -> usingItem.test(player) ? InteractionHand.MAIN_HAND : null;
            } else if (PaperUtils.PAPER && version.isNewerThanOrEquals(ServerVersion.V_1_16_5)) {
                getItemUsageHandImpl = player -> player.isHandRaised()
                        ? player.getHandRaised() == EquipmentSlot.OFF_HAND
                          ? InteractionHand.OFF_HAND
                          : InteractionHand.MAIN_HAND
                        : null;
            } else {
                Method method = clazz.getMethod(nmsPackage != null ? switch (Objects.requireNonNull(nmsPackage, "nmsPackage")) {
                    case "v1_9_R1" -> "ct";
                    case "v1_9_R2" -> "cu";
                    case "v1_10_R1" -> "cy";
                    case "v1_11_R1" -> "cz";
                    case "v1_12_R1" -> "cH";
                    case "v1_13_R1", "v1_13_R2", "v1_14_R1" -> "cU";
                    case "v1_15_R1", "v1_16_R1", "v1_16_R2", "v1_16_R3", "v1_17_R1" -> "getRaisedHand";
                    case "v1_18_R1" -> "eM";
                    case "v1_18_R2" -> "eN";
                    case "v1_19_R1" -> "eU";
                    case "v1_19_R2" -> "fa";
                    case "v1_19_R3" -> "ff";
                    case "v1_20_R1" -> "fj";
                    case "v1_20_R2" -> "fn";
                    case "v1_20_R3" -> "fo";
                    case "v1_20_R4" -> "fw";
                    case "v1_21_R1" -> "fs";
                    case "v1_21_R2", "v1_21_R3", "v1_21_R4" -> "fA";
                    case "v1_21_R5" -> "fH";
                    case "v1_21_R6" -> "fP";
                    case "v1_21_R7" -> "ga";
                    default -> throw new IllegalStateException("You are using an unsupported server version: " + nmsPackage + "/" + version.getReleaseName());
                } : "getUsedItemHand");

                final Predicate<Player> usingItem = isUsingItemImpl;
                getItemUsageHandImpl = player -> {
                    try {
                        return usingItem.test(player)
                                ? ((Enum<?>) method.invoke(getHandle.invoke(player))).ordinal() == 0
                                  ? InteractionHand.MAIN_HAND
                                  : InteractionHand.OFF_HAND
                                : null;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                };
            }

            Method setLivingEntityFlag;

            if (version.isNewerThanOrEquals(ServerVersion.V_1_19)) {
                String name = obfuscated ? "c" : "setLivingEntityFlag";
                setLivingEntityFlag = clazz.getDeclaredMethod(name, int.class, boolean.class);
                setLivingEntityFlag.setAccessible(true);
            } else {
                setLivingEntityFlag = null;
            }

            if (PaperUtils.PAPER && version.isNewerThan(ServerVersion.V_1_17)) {
                resetItemUsageImpl = setLivingEntityFlag == null ? LivingEntity::clearActiveItem : player -> {
                    try {
                        setLivingEntityFlag.invoke(getHandle.invoke(player), 1, false);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    player.clearActiveItem();
                };
            } else {
                Method method = clazz.getMethod(obfuscated ? switch (nmsPackage) {
                    case "v1_8_R3" -> "bV";
                    case "v1_9_R1" -> "cz";
                    case "v1_9_R2" -> "cA";
                    case "v1_10_R1" -> "cE";
                    case "v1_11_R1" -> "cF";
                    case "v1_12_R1" -> "cN";
                    case "v1_13_R1", "v1_13_R2" -> "da";
                    case "v1_14_R1" -> "dp";
                    case "v1_15_R1" -> "dH";
                    case "v1_16_R1", "v1_16_R2", "v1_16_R3", "v1_17_R1" -> "clearActiveItem";
                    case "v1_18_R1" -> "eR";
                    case "v1_18_R2" -> "eS";
                    case "v1_19_R1" -> "eZ";
                    case "v1_19_R2" -> "ff";
                    case "v1_19_R3" -> "fk";
                    case "v1_20_R1" -> "fo";
                    case "v1_20_R2" -> "fs";
                    case "v1_20_R3" -> "ft";
                    case "v1_20_R4" -> "fB";
                    case "v1_21_R1" -> "fx";
                    case "v1_21_R2", "v1_21_R3", "v1_21_R4" -> "fF";
                    case "v1_21_R5" -> "fM";
                    case "v1_21_R6" -> "fU";
                    case "v1_21_R7" -> "gf";
                    default -> throw new IllegalStateException("You are using an unsupported server version: " + nmsPackage + "/" + version.getReleaseName());
                } : "stopUsingItem");

                if (legacy) { // 1.8.8
                    final Predicate<Player> usingItem = isUsingItemImpl;
                    resetItemUsageImpl = player -> {
                        try {
                            method.invoke(getHandle.invoke(player));

                            // in 1.8 we need to resync item usage manually,
                            // only do so if the player is using an item
                            if (usingItem.test(player)) player.updateInventory();
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    };
                } else if (setLivingEntityFlag == null) { // 1.9-1.18.2
                    resetItemUsageImpl = player -> {
                        try {
                            method.invoke(getHandle.invoke(player));
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    };
                } else { // 1.19+
                    resetItemUsageImpl = player -> {
                        try {
                            Object handle = getHandle.invoke(player);
                            setLivingEntityFlag.invoke(handle, 1, false);
                            method.invoke(handle);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    };
                }
            }
        } catch (Throwable t) {
            // e.g. a new NMS revision we don't know the obfuscated names for yet, item usage resets are not essential
            LogUtil.warn("Failed to set up item usage resets for " + version.getReleaseName() + ", some item usage resets will be disabled", t);
        }

        if (isUsingItemImpl == null) {
            isUsingItemImpl = version.isNewerThanOrEquals(ServerVersion.V_1_10) ? Player::isHandRaised : player -> false;
        }
        if (getItemUsageHandImpl == null) {
            getItemUsageHandImpl = player -> null;
        }
        if (resetItemUsageImpl == null) {
            resetItemUsageImpl = ReflectionUtils.hasMethod(LivingEntity.class, "clearActiveItem") ? LivingEntity::clearActiveItem : player -> {};
        }

        isUsingItem = isUsingItemImpl;
        getItemUsageHand = getItemUsageHandImpl;
        resetItemUsage = resetItemUsageImpl;
    }
}
