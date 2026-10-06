package com.negativestudios.playerscreenevent;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;

public final class ServerScreenManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean active;
    private static String lastJoined = "";
    private static byte[] imageBytes = new byte[0];

    private ServerScreenManager() {}

    public static int getMaxPlayers(MinecraftServer server) {
        int configured = PlayerScreenConfig.MAX_PLAYERS.get();
        return configured < 0 ? server.getMaxPlayers() : configured;
    }

    public static boolean show(MinecraftServer server) {
        byte[] loaded;
        try {
            loaded = loadAndValidateImage(server);
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.error("No se pudo cargar la imagen de PlayerScreenEvent: {}", e.getMessage());
            return false;
        }

        active = true;
        lastJoined = "";
        imageBytes = loaded;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, true);
        }
        return true;
    }

    public static void hide(MinecraftServer server) {
        if (!active) {
            return;
        }

        active = false;
        lastJoined = "";
        imageBytes = new byte[0];

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        lastJoined = player.getGameProfile().getName();
        send(player, true, lastJoined);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player) send(other, false, lastJoined);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!active || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        server.execute(() -> broadcast(server, false, lastJoined));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        active = false;
        lastJoined = "";
        imageBytes = new byte[0];
    }

    private static void broadcast(MinecraftServer server, boolean includeImage, String joinedPlayer) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, includeImage, joinedPlayer);
        }
    }

    private static void send(ServerPlayer player, boolean includeImage) {
        send(player, includeImage, lastJoined);
    }

    private static void send(ServerPlayer player, boolean includeImage, String joinedPlayer) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ScreenUpdatePacket(
                        active,
                        PlayerScreenConfig.TITLE.get(),
                        server.getPlayerList().getPlayerCount(),
                        getMaxPlayers(server),
                        joinedPlayer,
                        includeImage ? imageBytes : null,
                        player.hasPermissions(2)
                )
        );
    }

    private static byte[] loadAndValidateImage(MinecraftServer server) throws IOException {
        Path path = PlayerScreenConfig.getImagePath();

        if (!Files.exists(path)) {
            throw new IOException("No existe " + path + ". Coloca una imagen 16:9 de al menos 1280x720.");
        }

        try (InputStream input = Files.newInputStream(path)) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                throw new IOException("El archivo no es una imagen compatible.");
            }

            BufferedImage output = source;

            byte[] png = encodePng(output);
            if (png.length <= 1_900_000) {
                return png;
            }

            byte[] jpg = encodeJpeg(output);
            if (jpg.length > 1_900_000) {
                throw new IOException("La imagen comprimida sigue siendo demasiado grande para enviarla a los jugadores.");
            }
            return jpg;
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.90f);

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }
}
