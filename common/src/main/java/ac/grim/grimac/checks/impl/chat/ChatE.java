package ac.grim.grimac.checks.impl.chat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PreViaPacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.RateWindow;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "ChatE", stableKey = "grim.chat.command_spam", description = "Sent too many or too long commands")
public class ChatE extends Check implements PreViaPacketReceiveListener {
    private static final int TOO_MANY = 0;
    private static final int TOO_LONG = 1;
    private static final Verbose V = Verbose
            .of("more than {uint} per second") // TOO_MANY
            .or("length={uint}, max={uint}");  // TOO_LONG

    private final RateWindow rate = new RateWindow();
    private boolean cancelRate;

    private int maxPerSecond;
    private int maxLength;

    public ChatE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        final PacketTypeCommon type = event.getPacketType();
        final String legacyCommand;
        if (type == PacketType.Play.Client.CHAT_COMMAND || type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            legacyCommand = null;
        } else if (type == PacketType.Play.Client.CHAT_MESSAGE && player.getClientVersion().isOlderThan(ClientVersion.V_1_19)) {
            // before 1.19, commands are chat messages starting with a slash
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            if (!message.startsWith("/")) return;
            legacyCommand = message;
        } else {
            return;
        }

        if (maxPerSecond > 0) {
            int count = rate.record(System.nanoTime(), RateWindow.SECOND_NANOS);
            if (count > maxPerSecond) {
                // flag once per second, but drop every command above the limit
                if (count == maxPerSecond + 1) cancelRate = flag(V.write(verbose(), TOO_MANY).uint(maxPerSecond));
                if (cancelRate && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                return;
            }
        }

        if (maxLength <= 0) return;
        final int length;
        if (legacyCommand != null) {
            length = legacyCommand.length() - 1;
        } else if (ByteBufHelper.writerIndex(event.getByteBuf()) <= maxLength) {
            return; // the whole packet is smaller than the limit, no need to decode it
        } else if (type == PacketType.Play.Client.CHAT_COMMAND) {
            length = new WrapperPlayClientChatCommand(event).getCommand().length();
        } else {
            length = new WrapperPlayClientChatCommandUnsigned(event).getCommand().length();
        }

        if (length > maxLength && flag(V.write(verbose(), TOO_LONG).uint(length).uint(maxLength)) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxPerSecond = config.getIntElse("exploit.commands-per-second", 20);
        maxLength = config.getIntElse("exploit.max-command-length", 2048);
    }
}
