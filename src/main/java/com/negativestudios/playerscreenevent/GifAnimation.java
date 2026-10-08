package com.negativestudios.playerscreenevent;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GifAnimation {
    private final List<BufferedImage> frames;
    private final List<Integer> delays;
    private final int width;
    private final int height;

    private long startTime;
    private int currentFrame = -1;
    private ResourceLocation textureLocation;
    private DynamicTexture dynamicTexture;

    private GifAnimation(List<BufferedImage> frames, List<Integer> delays, int width, int height) {
        this.frames = frames;
        this.delays = delays;
        this.width = width;
        this.height = height;
    }

    public static GifAnimation fromBytes(byte[] bytes) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("No se pudo abrir el GIF.");
            }

            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input, false, false);

                int count = reader.getNumImages(true);
                int canvasWidth = reader.getWidth(0);
                int canvasHeight = reader.getHeight(0);

                BufferedImage canvas = new BufferedImage(
                        canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB
                );

                for (int i = 0; i < count; i++) {
                    BufferedImage decoded = reader.read(i);
                    if (decoded == null) {
                        continue;
                    }

                    BufferedImage frame = new BufferedImage(
                            canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB
                    );

                    Graphics2D graphics = frame.createGraphics();
                    try {
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.drawImage(canvas, 0, 0, null);
                        graphics.setComposite(AlphaComposite.SrcOver);
                        graphics.drawImage(decoded, 0, 0, null);
                    } finally {
                        graphics.dispose();
                    }

                    frames.add(frame);
                    delays.add(readDelay(reader.getImageMetadata(i)));

                    // Para los GIFs que usan disposal=2, la zona del frame debe
                    // quedar transparente antes del siguiente frame. ImageIO ya
                    // nos da el frame actual; guardamos el resultado visible y
                    // limpiamos el canvas cuando corresponde.
                    String disposal = readDisposalMethod(reader.getImageMetadata(i));
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        Graphics2D clear = canvas.createGraphics();
                        try {
                            clear.setComposite(AlphaComposite.Clear);
                            clear.fillRect(0, 0, canvasWidth, canvasHeight);
                        } finally {
                            clear.dispose();
                        }
                    } else if ("restoreToPrevious".equals(disposal)) {
                        canvas = new BufferedImage(
                                canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB
                        );
                    } else {
                        canvas = frame;
                    }
                }

                if (frames.isEmpty()) {
                    throw new IOException("El GIF no contiene frames.");
                }

                return new GifAnimation(frames, delays, canvasWidth, canvasHeight);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int readDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;
        }

        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        Node extension = findNode(root, "GraphicControlExtension");
        if (extension == null) {
            return 100;
        }

        NamedNodeMap attributes = extension.getAttributes();
        Node delay = attributes == null ? null : attributes.getNamedItem("delayTime");
        if (delay == null) {
            return 100;
        }

        try {
            return Math.max(20, Integer.parseInt(delay.getNodeValue()) * 10);
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    private static String readDisposalMethod(IIOMetadata metadata) {
        if (metadata == null) {
            return "none";
        }

        Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
        Node extension = findNode(root, "GraphicControlExtension");
        if (extension == null) {
            return "none";
        }

        NamedNodeMap attributes = extension.getAttributes();
        Node disposal = attributes == null ? null : attributes.getNamedItem("disposalMethod");
        return disposal == null ? "none" : disposal.getNodeValue();
    }

    private static Node findNode(Node node, String name) {
        if (node == null) {
            return null;
        }

        if (name.equals(node.getNodeName())) {
            return node;
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public void start() {
        startTime = System.currentTimeMillis();
        currentFrame = -1;
    }

    public void renderFrame() {
        if (frames.isEmpty()) {
            return;
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - startTime);
        long total = 0L;

        for (int delay : delays) {
            total += delay;
        }

        long position = total > 0L ? elapsed % total : 0L;
        long accumulated = 0L;
        int frame = frames.size() - 1;

        for (int i = 0; i < frames.size(); i++) {
            accumulated += delays.get(i);
            if (position < accumulated) {
                frame = i;
                break;
            }
        }

        if (frame == currentFrame && textureLocation != null) {
            return;
        }

        currentFrame = frame;
        uploadCurrentFrame();
    }

    private void uploadCurrentFrame() {
        BufferedImage image = frames.get(currentFrame);

        NativeImage nativeImage = new NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);

                int a = (argb >>> 24) & 255;
                int r = (argb >>> 16) & 255;
                int g = (argb >>> 8) & 255;
                int b = argb & 255;

                nativeImage.setPixelRGBA(
                        x, y,
                        (a << 24) | (b << 16) | (g << 8) | r
                );
            }
        }

        releaseTexture();

        dynamicTexture = new DynamicTexture(nativeImage);
        dynamicTexture.upload();

        textureLocation = Minecraft.getInstance().getTextureManager().register(
                "playerscreenevent/gif_" + System.nanoTime(),
                dynamicTexture
        );
    }

    private void releaseTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
        }

        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void close() {
        releaseTexture();
    }
}
