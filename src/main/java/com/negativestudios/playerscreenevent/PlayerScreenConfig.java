package com.negativestudios.playerscreenevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PlayerScreenConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ForgeConfigSpec SPEC;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ForgeConfigSpec.ConfigValue<String> TITLE;
    public static ForgeConfigSpec.IntValue MAX_PLAYERS;
    public static ForgeConfigSpec.ConfigValue<String> IMAGE_PATH;
    public static ForgeConfigSpec.ConfigValue<String> GIF_PATH;

    private PlayerScreenConfig() {}

    public static void register() {
        createConfigFolder();
        JsonObject json = loadJsonConfig();

        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("pantalla");

        TITLE = builder
                .comment("Texto grande que aparece en el centro de la pantalla.")
                .define("texto", json.has("texto") ? json.get("texto").getAsString() : "Esperando jugadores...");

        MAX_PLAYERS = builder
                .comment("Cantidad maxima mostrada. Usa -1 para usar el maximo real del servidor.")
                .defineInRange("jugadores_maximos",
                        json.has("jugadores_maximos") ? json.get("jugadores_maximos").getAsInt() : -1,
                        -1, 1000000);

        IMAGE_PATH = builder
                .comment("Nombre del archivo de la pantalla principal. Puede ser PNG, JPG o GIF.")
                .define("imagen", json.has("imagen") ? json.get("imagen").getAsString() : "screen.png");

        GIF_PATH = builder
                .comment("Nombre del GIF de la segunda pantalla.")
                .define("gif", json.has("gif") ? json.get("gif").getAsString() : "screen.gif");

        builder.pop();

        SPEC = builder.build();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "playerscreenevent-common.toml");
    }

    public static Path getImageDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("playerscreenevent");
    }

    public static Path getImagePath() {
        return resolveFilename(IMAGE_PATH.get(), "screen.png");
    }

    public static Path getGifPath() {
        return resolveFilename(GIF_PATH.get(), "screen.gif");
    }

    private static Path resolveFilename(String configured, String fallback) {
        String filename = configured == null ? "" : configured.trim();

        if (filename.isEmpty() || filename.contains("/") || filename.contains("\\") ||
                filename.equals(".") || filename.equals("..")) {
            filename = fallback;
        }

        return getImageDirectory().resolve(filename).normalize();
    }

    private static void createConfigFolder() {
        Path directory = getImageDirectory();

        try {
            Files.createDirectories(directory);
            createDefaultImage(directory.resolve("screen.png"));
            createDefaultGif(directory.resolve("screen.gif"));
            createDefaultJson(directory.resolve("config.json"));
        } catch (IOException exception) {
            LOGGER.error("No se pudo crear la carpeta de configuracion de PlayerScreenEvent.", exception);
        }
    }

    private static JsonObject loadJsonConfig() {
        Path path = getImageDirectory().resolve("config.json");

        if (!Files.exists(path)) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            return object == null ? new JsonObject() : object;
        } catch (Exception exception) {
            LOGGER.error("No se pudo leer config.json de PlayerScreenEvent.", exception);
            return new JsonObject();
        }
    }

    private static void createDefaultJson(Path path) throws IOException {
        if (Files.exists(path)) {
            return;
        }

        JsonObject object = new JsonObject();
        object.addProperty("texto", "Esperando jugadores...");
        object.addProperty("jugadores_maximos", -1);
        object.addProperty("imagen", "screen.png");
        object.addProperty("gif", "screen.gif");

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(object, writer);
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

    private static void createDefaultGif(Path path) throws IOException {
        if (Files.exists(path)) {
            return;
        }

        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try {
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);

            ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
            IIOMetadata metadata = writer.getDefaultImageMetadata(type, writer.getDefaultWriteParam());
            String format = "javax_imageio_gif_image_1.0";
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
            gce.setAttribute("disposalMethod", "none");
            gce.setAttribute("userInputFlag", "FALSE");
            gce.setAttribute("transparentColorFlag", "FALSE");
            gce.setAttribute("delayTime", "10");
            gce.setAttribute("transparentColorIndex", "0");
            root.appendChild(gce);
            metadata.setFromTree(format, root);

            writer.writeToSequence(new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
