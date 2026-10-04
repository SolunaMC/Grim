package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.RateWindow;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "CrashH", stableKey = "grim.crash.invalid_tab_complete", description = "Sent a tab complete request with invalid or excessive length, or too many tab complete requests")
public class CrashH extends Check implements PacketReceiveListener {
    private static final int INVALID_LENGTH = 0;
    private static final int TOO_MANY = 1;
    private static final Verbose V = Verbose
            .of("[(length)|(invalid)] length={sint}") // INVALID_LENGTH
            .or("more than {uint} per second");       // TOO_MANY

    private final RateWindow rate = new RateWindow();
    private boolean cancelRate;
    private int maxPerSecond;

    public CrashH(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.TAB_COMPLETE) {
            // Modern clients ask on every key press while typing some commands, so the default limit is high
            if (maxPerSecond > 0) {
                int count = rate.record(System.nanoTime(), RateWindow.SECOND_NANOS);
                if (count > maxPerSecond) {
                    // flag once per second, but drop every request above the limit
                    if (count == maxPerSecond + 1) cancelRate = flag(V.write(verbose(), TOO_MANY).uint(maxPerSecond));
                    if (cancelRate && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    return;
                }
            }

            WrapperPlayClientTabComplete wrapper = new WrapperPlayClientTabComplete(event);
            String text = wrapper.getText();
            final int length = text.length();
            // general length limit
            if (length > (!player.canUseGameMasterBlocks() ? 256 : 32500)) {
                if (shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                flag(V.write(verbose(), INVALID_LENGTH).bool(true).sint(length));
                return;
            }
            // paper's patch
            final int index;
            if (length > 64 && ((index = text.indexOf(' ')) == -1 || index >= 64)) {
                if (shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                flag(V.write(verbose(), INVALID_LENGTH).bool(false).sint(length));
            }
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxPerSecond = config.getIntElse("exploit.tab-completes-per-second", 100);
    }
}
