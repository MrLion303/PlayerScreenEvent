package com.negativestudios.playerscreenevent;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PlayerScreenConfig {
    private static ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.ConfigValue<String> TITLE;
    public static ForgeConfigSpec.IntValue MAX_PLAYERS;
    public static ForgeConfigSpec.ConfigValue<String> IMAGE_PATH;

    private PlayerScreenConfig() {}

    public static void register() {
        createConfigFolder();

        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("pantalla");

        TITLE = builder
                .comment("Texto grande que aparece en el centro de la pantalla.")
                .define("texto", "Esperando jugadores...");

        MAX_PLAYERS = builder
                .comment("Cantidad maxima mostrada. Usa -1 para usar el maximo real del servidor.")
                .defineInRange("jugadores_maximos", -1, -1, 1000000);

        IMAGE_PATH = builder
                .comment("Nombre del archivo de imagen dentro de config/playerscreenevent/. No escribas la ruta.")
                .define("imagen", "screen.png");

        builder.pop();

        SPEC = builder.build();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "playerscreenevent-common.toml");
    }

    public static Path getImageDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("playerscreenevent");
    }

    public static Path getImagePath() {
        String filename = IMAGE_PATH.get().trim();

        if (filename.isEmpty()) {
            filename = "screen.png";
        }

        // El campo de configuracion acepta solamente el nombre del archivo.
        // Evita que una configuracion accidental salga de la carpeta del mod.
        if (filename.contains("/") || filename.contains("\") || filename.equals(".") || filename.equals("..")) {
            filename = "screen.png";
        }

        return getImageDirectory().resolve(filename).normalize();
    }

    private static void createConfigFolder() {
        Path directory = getImageDirectory();

        try {
            Files.createDirectories(directory);
            createDefaultImage(directory.resolve("screen.png"));
        } catch (IOException exception) {
            PlayerScreenEvent.LOGGER.error("No se pudo crear la carpeta de configuracion de PlayerScreenEvent.", exception);
        }
    }

    private static void createDefaultImage(Path path) throws IOException {
        if (Files.exists(path)) {
            return;
        }

        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.fillRect(0, 0, 1280, 720);

            graphics.setFont(graphics.getFont().deriveFont(42.0f));
            String text = "Esperando jugadores...";
            int textWidth = graphics.getFontMetrics().stringWidth(text);

            graphics.drawString(text, (1280 - textWidth) / 2, 360);
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", path.toFile());
    }
}
