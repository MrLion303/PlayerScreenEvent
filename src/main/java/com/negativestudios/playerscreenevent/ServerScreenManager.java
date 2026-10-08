package com.negativestudios.playerscreenevent;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ServerScreenManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MEDIA_BYTES = 1_900_000;

    private static boolean mainActive;
    private static boolean gifActive;
    private static String lastJoined = "";
    private static byte[] mainMedia = new byte[0];
    private static byte[] gifMedia = new byte[0];

    private static final Set<UUID> hiddenMainPlayers = new HashSet<>();
    private static final Set<UUID> hiddenGifPlayers = new HashSet<>();

    private ServerScreenManager() {}

    public static boolean isActive() {
        return mainActive;
    }

    public static boolean isGifActive() {
        return gifActive;
    }

    public static int getMaxPlayers(MinecraftServer server) {
        int configured = PlayerScreenConfig.MAX_PLAYERS.get();
        return configured < 0 ? server.getMaxPlayers() : configured;
    }

    public static boolean show(MinecraftServer server) {
        try {
            mainMedia = loadMedia(PlayerScreenConfig.getImagePath());
        } catch (IOException e) {
            LOGGER.error("No se pudo cargar la pantalla principal: {}", e.getMessage());
            return false;
        }

        mainActive = true;
        lastJoined = "";
        hiddenMainPlayers.clear();
        sendEffectiveToAll(server);
        return true;
    }

    public static void hide(MinecraftServer server) {
        if (!mainActive) {
            return;
        }

        mainActive = false;
        mainMedia = new byte[0];
        hiddenMainPlayers.clear();
        sendEffectiveToAll(server);
    }

    public static boolean showGif(MinecraftServer server) {
        try {
            gifMedia = loadGif(PlayerScreenConfig.getGifPath());
        } catch (IOException e) {
            LOGGER.error("No se pudo cargar el GIF de PlayerScreenEvent: {}", e.getMessage());
            return false;
        }

        gifActive = true;
        hiddenGifPlayers.clear();
        sendEffectiveToAll(server);
        return true;
    }

    public static void hideGif(MinecraftServer server) {
        if (!gifActive) {
            return;
        }

        gifActive = false;
        gifMedia = new byte[0];
        hiddenGifPlayers.clear();
        sendEffectiveToAll(server);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null || (!mainActive && !gifActive)) {
            return;
        }

        hiddenMainPlayers.remove(player.getUUID());
        hiddenGifPlayers.remove(player.getUUID());
        lastJoined = player.getGameProfile().getName();

        sendEffective(player, true, lastJoined);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player) {
                sendEffective(other, false, lastJoined);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null || (!mainActive && !gifActive)) {
            return;
        }

        server.execute(() -> broadcastJoined(server, lastJoined));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        mainActive = false;
        gifActive = false;
        lastJoined = "";
        mainMedia = new byte[0];
        gifMedia = new byte[0];
        hiddenMainPlayers.clear();
        hiddenGifPlayers.clear();
    }

    public static void hideForPlayer(ServerPlayer player) {
        if (!mainActive) {
            return;
        }

        hiddenMainPlayers.add(player.getUUID());
        sendEffective(player, false, "");
    }

    public static void hideGifForPlayer(ServerPlayer player) {
        if (!gifActive) {
            return;
        }

        hiddenGifPlayers.add(player.getUUID());
        sendEffective(player, false, "");
    }

    private static void broadcastJoined(MinecraftServer server, String joinedPlayer) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendEffective(player, false, joinedPlayer);
        }
    }

    private static void sendEffectiveToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendEffective(player, true, "");
        }
    }

    private static void sendEffective(ServerPlayer player, boolean includeMedia, String joinedPlayer) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        boolean useGif = gifActive && !hiddenGifPlayers.contains(player.getUUID());
        boolean useMain = mainActive && !hiddenMainPlayers.contains(player.getUUID());

        if (useGif) {
            send(player, true, isGifFile(PlayerScreenConfig.getGifPath()),
                    joinedPlayer, includeMedia ? gifMedia : null);
            return;
        }

        if (useMain) {
            send(player, true, isGifFile(PlayerScreenConfig.getImagePath()),
                    joinedPlayer, includeMedia ? mainMedia : null);
            return;
        }

        send(player, false, false, joinedPlayer, null);
    }

    private static void send(ServerPlayer player, boolean active, boolean animated,
                             String joinedPlayer, byte[] media) {
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
                        media,
                        animated,
                        player.hasPermissions(2)
                )
        );
    }

    private static byte[] loadGif(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("No existe " + path + ". Coloca un archivo GIF en config/playerscreenevent/.");
        }

        if (!isGifFile(path)) {
            throw new IOException("El archivo configurado para la segunda pantalla debe tener extension .gif.");
        }

        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) {
            throw new IOException("El GIF esta vacio.");
        }

        if (bytes.length > MAX_MEDIA_BYTES) {
            throw new IOException("El GIF supera el limite de " + MAX_MEDIA_BYTES + " bytes.");
        }

        try (InputStream input = Files.newInputStream(path)) {
            if (ImageIO.read(input) == null) {
                throw new IOException("El archivo no es un GIF valido.");
            }
        }

        return bytes;
    }

    private static byte[] loadMedia(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("No existe " + path + ".");
        }

        if (isGifFile(path)) {
            return loadGif(path);
        }

        try (InputStream input = Files.newInputStream(path)) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                throw new IOException("El archivo no es una imagen compatible.");
            }

            byte[] png = encodePng(source);
            if (png.length <= MAX_MEDIA_BYTES) {
                return png;
            }

            byte[] jpg = encodeJpeg(source);
            if (jpg.length > MAX_MEDIA_BYTES) {
                throw new IOException("La imagen comprimida sigue siendo demasiado grande para enviarla a los jugadores.");
            }

            return jpg;
        }
    }

    private static boolean isGifFile(Path path) {
        return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
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
