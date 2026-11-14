package com.negativestudios.playerscreenevent;

import net.minecraft.network.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ScreenUpdatePacket {
    private static final int MAX_MEDIA_BYTES = 1_900_000;

    private final boolean active;
    private final String title;
    private final int playerCount;
    private final int maxPlayers;
    private final String joinedPlayer;
    private final boolean animated;
    private final boolean canOpenChat;
    private final long transferId;
    private final int mediaLength;
    private final int chunkCount;

    public ScreenUpdatePacket(boolean active, String title, int playerCount, int maxPlayers,
                              String joinedPlayer, boolean animated, boolean canOpenChat,
                              long transferId, int mediaLength, int chunkCount) {
        this.active = active;
        this.title = title == null ? "" : title;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.joinedPlayer = joinedPlayer == null ? "" : joinedPlayer;
        this.animated = animated;
        this.canOpenChat = canOpenChat;
        this.transferId = transferId;
        this.mediaLength = mediaLength;
        this.chunkCount = chunkCount;
    }

    public static void encode(ScreenUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeUtf(packet.title, 256);
        buf.writeVarInt(packet.playerCount);
        buf.writeVarInt(packet.maxPlayers);
        buf.writeUtf(packet.joinedPlayer, 64);
        buf.writeBoolean(packet.transferId > 0L);

        if (packet.transferId > 0L) {
            buf.writeLong(packet.transferId);
            buf.writeVarInt(packet.mediaLength);
            buf.writeVarInt(packet.chunkCount);
        }

        buf.writeBoolean(packet.animated);
        buf.writeBoolean(packet.canOpenChat);
    }

    public static ScreenUpdatePacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        String title = buf.readUtf(256);
        int playerCount = buf.readVarInt();
        int maxPlayers = buf.readVarInt();
        String joinedPlayer = buf.readUtf(64);

        long transferId = 0L;
        int mediaLength = 0;
        int chunkCount = 0;

        if (buf.readBoolean()) {
            transferId = buf.readLong();
            mediaLength = buf.readVarInt();
            chunkCount = buf.readVarInt();

            int expectedChunks = mediaLength <= 0 ? 0
                    : (mediaLength + ScreenMediaChunkPacket.CHUNK_SIZE - 1)
                    / ScreenMediaChunkPacket.CHUNK_SIZE;

            if (transferId <= 0L || mediaLength <= 0 || mediaLength > MAX_MEDIA_BYTES
                    || chunkCount != expectedChunks) {
                throw new DecoderException("Transferencia multimedia de PlayerScreenEvent no valida.");
            }
        }

        boolean animated = buf.readBoolean();
        boolean canOpenChat = buf.readBoolean();

        return new ScreenUpdatePacket(
                active, title, playerCount, maxPlayers, joinedPlayer,
                animated, canOpenChat, transferId, mediaLength, chunkCount
        );
    }

    public static void handle(ScreenUpdatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (packet.transferId > 0L) {
                PlayerScreenOverlay.beginMediaTransfer(
                        packet.active,
                        packet.title,
                        packet.playerCount,
                        packet.maxPlayers,
                        packet.joinedPlayer,
                        packet.animated,
                        packet.canOpenChat,
                        packet.transferId,
                        packet.mediaLength,
                        packet.chunkCount
                );
            } else {
                PlayerScreenOverlay.receive(
                        packet.active,
                        packet.title,
                        packet.playerCount,
                        packet.maxPlayers,
                        packet.joinedPlayer,
                        null,
                        packet.animated,
                        packet.canOpenChat
                );
            }
        }));

        context.setPacketHandled(true);
    }
}
