package com.negativestudios.playerscreenevent;

import net.minecraft.network.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ScreenMediaChunkPacket {
    // Keep each custom payload comfortably below Minecraft's 1 MiB payload ceiling.
    public static final int CHUNK_SIZE = 768 * 1024;

    private final long transferId;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] data;

    public ScreenMediaChunkPacket(long transferId, int chunkIndex, int totalChunks, byte[] data) {
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data == null ? new byte[0] : data;
    }

    public static void encode(ScreenMediaChunkPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.transferId);
        buf.writeVarInt(packet.chunkIndex);
        buf.writeVarInt(packet.totalChunks);
        buf.writeByteArray(packet.data);
    }

    public static ScreenMediaChunkPacket decode(FriendlyByteBuf buf) {
        long transferId = buf.readLong();
        int chunkIndex = buf.readVarInt();
        int totalChunks = buf.readVarInt();
        byte[] data = buf.readByteArray(CHUNK_SIZE);

        if (transferId <= 0L || totalChunks <= 0 || chunkIndex < 0
                || chunkIndex >= totalChunks || data.length == 0) {
            throw new DecoderException("Fragmento multimedia de PlayerScreenEvent no valido.");
        }

        return new ScreenMediaChunkPacket(transferId, chunkIndex, totalChunks, data);
    }

    public static void handle(ScreenMediaChunkPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                PlayerScreenOverlay.receiveMediaChunk(
                        packet.transferId,
                        packet.chunkIndex,
                        packet.totalChunks,
                        packet.data
                )
        ));

        context.setPacketHandled(true);
    }
}
