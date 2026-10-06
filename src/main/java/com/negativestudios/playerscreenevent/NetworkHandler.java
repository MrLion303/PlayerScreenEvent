package com.negativestudios.playerscreenevent;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PlayerScreenEvent.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int packetId = 0;

    private NetworkHandler() {}

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                ScreenUpdatePacket.class,
                ScreenUpdatePacket::encode,
                ScreenUpdatePacket::decode,
                ScreenUpdatePacket::handle
        );
    }
}
