package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.ItemDataDepth;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "CrashB", stableKey = "grim.crash.creative_while_not_creative", description = "Sent creative mode inventory click packets while not in creative mode, or with oversized item data")
public class CrashB extends Check implements PacketReceiveListener {
    private static final int ITEM_TOO_LARGE = 0;
    private static final int ITEM_TOO_DEEP = 1;
    private static final Verbose V = Verbose
            .of("size={uint} bytes, max={uint}") // ITEM_TOO_LARGE
            .or("depth>{uint}");                 // ITEM_TOO_DEEP

    private int maxBytes;
    private int maxDepth;

    public CrashB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            if (player.gamemode != GameMode.CREATIVE) {
                event.setCancelled(true);
                player.onPacketCancel();
                flag(); // Could be transaction split, no need to setback though
                return;
            }

            // The buffer holds exactly this packet, so its size is known without decoding anything
            int size = ByteBufHelper.writerIndex(event.getByteBuf());
            if (maxBytes > 0 && size > maxBytes) {
                flagItemData(event, V.write(verbose(), ITEM_TOO_LARGE).uint(size).uint(maxBytes));
                return;
            }

            if (maxDepth > 0 && ItemDataDepth.of(new WrapperPlayClientCreativeInventoryAction(event).getItemStack(), maxDepth) > maxDepth) {
                flagItemData(event, V.write(verbose(), ITEM_TOO_DEEP).uint(maxDepth));
            }
        }
    }

    private void flagItemData(PacketReceiveEvent event, Verbose.Writer writer) {
        if (flag(writer) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBytes = config.getIntElse("exploit.max-item-packet-bytes", 2097152);
        maxDepth = config.getIntElse("exploit.max-item-nbt-depth", 128);
    }
}
