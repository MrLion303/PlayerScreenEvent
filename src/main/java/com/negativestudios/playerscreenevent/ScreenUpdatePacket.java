package com.negativestudios.playerscreenevent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ScreenUpdatePacket {
    private final boolean active;
    private final String title;
    private final int playerCount;
    private final int maxPlayers;
    private final String joinedPlayer;
    private final boolean hasImage;
    private final byte[] imageBytes;
    private final boolean canOpenChat;

    public ScreenUpdatePacket(boolean active, String title, int playerCount, int maxPlayers,
                              String joinedPlayer, byte[] imageBytes, boolean canOpenChat) {
        this.active = active;
        this.title = title;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.joinedPlayer = joinedPlayer == null ? "" : joinedPlayer;
        this.hasImage = imageBytes != null && imageBytes.length > 0;
        this.imageBytes = this.hasImage ? imageBytes : new byte[0];
        this.canOpenChat = canOpenChat;
    }

    public static void encode(ScreenUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeUtf(packet.title, 256);
        buf.writeVarInt(packet.playerCount);
        buf.writeVarInt(packet.maxPlayers);
        buf.writeUtf(packet.joinedPlayer, 64);
        buf.writeBoolean(packet.hasImage);
        if (packet.hasImage) {
            buf.writeByteArray(packet.imageBytes);
        }
        buf.writeBoolean(packet.canOpenChat);
    }

    public static ScreenUpdatePacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        String title = buf.readUtf(256);
        int playerCount = buf.readVarInt();
        int maxPlayers = buf.readVarInt();
        String joinedPlayer = buf.readUtf(64);
        byte[] image = buf.readBoolean() ? buf.readByteArray(2_000_000) : new byte[0];
        boolean canOpenChat = buf.readBoolean();
        return new ScreenUpdatePacket(active, title, playerCount, maxPlayers, joinedPlayer, image, canOpenChat);
    }

    public static void handle(ScreenUpdatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                PlayerScreenOverlay.receive(
                        packet.active,
                        packet.title,
                        packet.playerCount,
                        packet.maxPlayers,
                        packet.joinedPlayer,
                        packet.imageBytes,
                        packet.canOpenChat
                )
        ));
        context.setPacketHandled(true);
    }
}
