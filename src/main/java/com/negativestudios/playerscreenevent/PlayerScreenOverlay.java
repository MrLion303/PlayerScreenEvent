package com.negativestudios.playerscreenevent;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = PlayerScreenEvent.MOD_ID, value = Dist.CLIENT)
public final class PlayerScreenOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FADE_MS = 500L;
    private static final int MAX_MEDIA_BYTES = 1_900_000;
    private static final int MEDIA_CHUNK_SIZE = ScreenMediaChunkPacket.CHUNK_SIZE;

    private static boolean active;
    private static boolean fading;
    private static long fadeStart;
    private static String title = "Esperando jugadores...";
    private static String joinedPlayer = "";
    private static int playerCount;
    private static int maxPlayers;
    private static boolean canOpenChat;

    private static DynamicTexture dynamicTexture;
    private static GifAnimation gifAnimation;
    private static ResourceLocation textureLocation;
    private static int textureWidth;
    private static int textureHeight;
    private static PendingMediaTransfer pendingMediaTransfer;

    private static final class PendingMediaTransfer {
        private final boolean active;
        private final String title;
        private final int playerCount;
        private final int maxPlayers;
        private final String joinedPlayer;
        private final boolean animated;
        private final boolean canOpenChat;
        private final long transferId;
        private final byte[] media;
        private final boolean[] received;
        private int receivedCount;

        private PendingMediaTransfer(boolean active, String title, int playerCount, int maxPlayers,
                                     String joinedPlayer, boolean animated, boolean canOpenChat,
                                     long transferId, int mediaLength, int chunkCount) {
            this.active = active;
            this.title = title;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.joinedPlayer = joinedPlayer;
            this.animated = animated;
            this.canOpenChat = canOpenChat;
            this.transferId = transferId;
            this.media = new byte[mediaLength];
            this.received = new boolean[chunkCount];
        }
    }

    private PlayerScreenOverlay() {}

    public static void beginMediaTransfer(boolean newActive, String newTitle, int count, int max,
                                          String joined, boolean animated, boolean chatPermission,
                                          long transferId, int mediaLength, int chunkCount) {
        int expectedChunks = mediaLength <= 0 ? 0
                : (mediaLength + MEDIA_CHUNK_SIZE - 1) / MEDIA_CHUNK_SIZE;

        if (transferId <= 0L || mediaLength <= 0 || mediaLength > MAX_MEDIA_BYTES
                || chunkCount != expectedChunks || chunkCount <= 0) {
            pendingMediaTransfer = null;
            LOGGER.error("PlayerScreenEvent recibio una transferencia multimedia con metadatos invalidos.");
            return;
        }

        pendingMediaTransfer = new PendingMediaTransfer(
                newActive, newTitle, count, max, joined == null ? "" : joined,
                animated, chatPermission, transferId, mediaLength, chunkCount
        );
    }

    public static void receiveMediaChunk(long transferId, int chunkIndex, int totalChunks, byte[] data) {
        PendingMediaTransfer transfer = pendingMediaTransfer;
        if (transfer == null || transfer.transferId != transferId) {
            return;
        }

        if (totalChunks != transfer.received.length || chunkIndex < 0
                || chunkIndex >= transfer.received.length || data == null) {
            LOGGER.warn("PlayerScreenEvent descarto un fragmento multimedia inesperado.");
            pendingMediaTransfer = null;
            return;
        }

        int offset = chunkIndex * MEDIA_CHUNK_SIZE;
        int expectedLength = Math.min(MEDIA_CHUNK_SIZE, transfer.media.length - offset);
        if (expectedLength <= 0 || data.length != expectedLength) {
            LOGGER.error("PlayerScreenEvent recibio un fragmento multimedia incompleto o con tamano incorrecto.");
            pendingMediaTransfer = null;
            return;
        }

        if (!transfer.received[chunkIndex]) {
            System.arraycopy(data, 0, transfer.media, offset, data.length);
            transfer.received[chunkIndex] = true;
            transfer.receivedCount++;
        }

        if (transfer.receivedCount == transfer.received.length) {
            pendingMediaTransfer = null;
            receive(
                    transfer.active,
                    transfer.title,
                    transfer.playerCount,
                    transfer.maxPlayers,
                    transfer.joinedPlayer,
                    transfer.media,
                    transfer.animated,
                    transfer.canOpenChat
            );
        }
    }

    public static void receive(boolean newActive, String newTitle, int count, int max,
                               String joined, byte[] mediaBytes, boolean animated, boolean chatPermission) {
        Minecraft minecraft = Minecraft.getInstance();
        pendingMediaTransfer = null;

        title = newTitle;
        playerCount = count;
        maxPlayers = max;
        joinedPlayer = joined == null ? "" : joined;
        canOpenChat = chatPermission;

        if (mediaBytes != null && mediaBytes.length > 0) {
            if (animated) {
                updateGif(mediaBytes);
            } else {
                updateTexture(mediaBytes);
            }
        }

        if (newActive) {
            active = true;
            fading = false;
            fadeStart = 0L;

            if (!(minecraft.screen instanceof WaitingScreen) &&
                    !(minecraft.screen instanceof WaitingChatScreen) &&
                    !(minecraft.screen instanceof WaitingPauseScreen)) {
                minecraft.setScreen(new WaitingScreen());
            }
            return;
        }

        if (active) {
            fading = true;
            fadeStart = System.currentTimeMillis();

            if (minecraft.screen instanceof WaitingChatScreen) {
                minecraft.setScreen(new WaitingScreen());
            }
        }
    }

    private static void stopGif() {
        if (gifAnimation != null) {
            gifAnimation.close();
            gifAnimation = null;
        }
    }

    private static void releaseTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
            // TextureManager libera la DynamicTexture registrada.
            dynamicTexture = null;
        } else if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
    }

    private static void updateGif(byte[] bytes) {
        try {
            GifAnimation animation = GifAnimation.fromBytes(bytes);
            if (animation.getFrameCount() == 0) {
                return;
            }

            stopGif();
            releaseTexture();
            gifAnimation = animation;
            gifAnimation.start();
            textureWidth = animation.getWidth();
            textureHeight = animation.getHeight();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("PlayerScreenEvent no pudo decodificar el GIF recibido desde el servidor.", exception);
        }
    }

    private static void updateTexture(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                LOGGER.error("PlayerScreenEvent recibio datos que no corresponden a una imagen compatible.");
                return;
            }

            NativeImage nativeImage =
                    new NativeImage(image.getWidth(), image.getHeight(), false);

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int a = (argb >>> 24) & 255;
                    int r = (argb >>> 16) & 255;
                    int g = (argb >>> 8) & 255;
                    int b = argb & 255;
                    nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            stopGif();
            releaseTexture();

            dynamicTexture = new DynamicTexture(nativeImage);
            dynamicTexture.upload();
            textureLocation = Minecraft.getInstance().getTextureManager().register(
                    "playerscreenevent/screen_" + System.nanoTime(), dynamicTexture
            );
            textureWidth = image.getWidth();
            textureHeight = image.getHeight();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("PlayerScreenEvent no pudo decodificar o registrar la imagen recibida desde el servidor.", exception);
        }
    }

    private static void finishFade() {
        active = false;
        fading = false;
        fadeStart = 0L;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WaitingScreen ||
                minecraft.screen instanceof WaitingChatScreen ||
                minecraft.screen instanceof WaitingPauseScreen) {
            minecraft.setScreen(null);
        }
    }

    private static float getAlpha() {
        if (!fading) {
            return 1.0f;
        }

        float progress = (System.currentTimeMillis() - fadeStart) / (float) FADE_MS;
        if (progress >= 1.0f) {
            finishFade();
            return 0.0f;
        }
        return 1.0f - progress;
    }

    private static float getResponsiveScale() {
        Minecraft minecraft = Minecraft.getInstance();
        double windowWidth = minecraft.getWindow().getWidth();
        double windowHeight = minecraft.getWindow().getHeight();

        if (windowWidth <= 0 || windowHeight <= 0) {
            return 1.0f;
        }

        double scale = Math.min(windowWidth / 1600.0, windowHeight / 900.0);
        return (float) Math.max(0.5, Math.min(1.5, scale));
    }

    private static void renderWaitingContent(Screen screen, GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        float alpha = getAlpha();
        if (alpha <= 0.0f) {
            return;
        }

        int alphaByte = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ResourceLocation renderTexture = textureLocation;
        int renderWidth = textureWidth;
        int renderHeight = textureHeight;

        if (gifAnimation != null) {
            gifAnimation.renderFrame();
            renderTexture = gifAnimation.getTextureLocation();
            renderWidth = gifAnimation.getWidth();
            renderHeight = gifAnimation.getHeight();
        }

        if (renderTexture != null && renderWidth > 0 && renderHeight > 0) {
            RenderSystem.setShaderTexture(0, renderTexture);
            gui.blit(renderTexture, 0, 0, screen.width, screen.height,
                    0, 0, renderWidth, renderHeight, renderWidth, renderHeight);
        } else {
            gui.fill(0, 0, screen.width, screen.height, (alphaByte << 24) | 0x00FFFFFF);
        }

        // La pantalla GIF es exclusivamente visual: no muestra texto encima.
        if (gifAnimation == null) {
            int titleY = (int) (screen.height * 0.47f);
            int countY = (int) (screen.height * 0.565f);
            int joinedY = (int) (screen.height * 0.625f);

            float responsiveScale = getResponsiveScale();

            drawCentered(screen, gui, title, titleY, 4.0f * responsiveScale, alphaByte, 0xFFFFFFFF);
            drawCentered(screen, gui, playerCount + "/" + maxPlayers, countY,
                    1.75f * responsiveScale, alphaByte, 0xFFFFFFFF);

            if (!joinedPlayer.isEmpty()) {
                drawCentered(screen, gui, joinedPlayer + " se ha unido.", joinedY,
                        1.5f * responsiveScale, alphaByte, 0xFFFF5555);
            }
        }

        RenderSystem.disableBlend();
    }

    private static void drawCentered(Screen screen, GuiGraphics gui, String text, int y,
                                     float scale, int alpha, int color) {
        gui.pose().pushPose();
        gui.pose().translate(screen.width / 2.0f, y, 0.0f);
        gui.pose().scale(scale, scale, 1.0f);

        int textWidth = screen.getMinecraft().font.width(text);
        int finalColor = (alpha << 24) | (color & 0x00FFFFFF);
        gui.drawString(screen.getMinecraft().font, text, -textWidth / 2, 0, finalColor, false);

        gui.pose().popPose();
    }

    public static class WaitingScreen extends Screen {
        public WaitingScreen() {
            super(Component.literal("PlayerScreenEvent"));
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            renderWaitingContent(this, gui, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                minecraft.setScreen(new WaitingPauseScreen());
                return true;
            }

            if (canOpenChat && keyCode == GLFW.GLFW_KEY_T) {
                minecraft.setScreen(new WaitingChatScreen(""));
                return true;
            }

            if (canOpenChat && keyCode == GLFW.GLFW_KEY_SLASH) {
                minecraft.setScreen(new WaitingChatScreen("/"));
                return true;
            }

            return true;
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return true;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return true;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            return true;
        }
    }

    public static final class WaitingPauseScreen extends PauseScreen {
        public WaitingPauseScreen() {
            super(true);
        }

        @Override
        protected void init() {
            super.init();

            if (active && !fading) {
                disableWaitingOptions();
            }
        }

        @Override
        public void renderBackground(GuiGraphics gui) {
            // Durante la espera, sustituimos el mundo de fondo por la misma pantalla de espera.
            renderWaitingContent(this, gui, 0, 0, 0.0f);
        }

        private void disableWaitingOptions() {
            for (GuiEventListener child : children()) {
                if (!(child instanceof Button button)) {
                    continue;
                }

                if (isWaitingLockedButton(button)) {
                    button.active = false;
                }
            }
        }

        private boolean isWaitingLockedButton(Button button) {
            if (!(button.getMessage().getContents() instanceof TranslatableContents contents)) {
                return false;
            }

            return switch (contents.getKey()) {
                case "gui.advancements",
                     "menu.advancements",
                     "gui.stats",
                     "menu.stats",
                     "menu.sendFeedback",
                     "menu.reportBugs",
                     "menu.options",
                     "menu.shareToLan",
                     "menu.playerReporting",
                     "gui.socialInteractions",
                     "fml.menu.mods" -> true;
                default -> false;
            };
        }

        private boolean isReturnToGameButton(Button button) {
            if (!(button.getMessage().getContents() instanceof TranslatableContents contents)) {
                return false;
            }

            return "menu.returnToGame".equals(contents.getKey());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (active && !fading && button == 0) {
                for (GuiEventListener child : children()) {
                    if (child instanceof Button pauseButton &&
                            isReturnToGameButton(pauseButton) &&
                            pauseButton.isMouseOver(mouseX, mouseY)) {
                        minecraft.setScreen(new WaitingScreen());
                        return true;
                    }
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        public void removed() {
            super.removed();

            // Algunas rutas de PauseScreen cierran directamente con setScreen(null).
            // Si eso ocurre mientras la espera sigue activa, restauramos la pantalla.
            if (active && !fading && minecraft.screen == null) {
                minecraft.setScreen(new WaitingScreen());
            }
        }

        @Override
        public void onClose() {
            if (active && !fading) {
                minecraft.setScreen(new WaitingScreen());
            } else {
                minecraft.setScreen(null);
            }
        }
    }

    public static final class WaitingChatScreen extends ChatScreen {
        public WaitingChatScreen(String initial) {
            super(initial);
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            // ChatScreen puede preparar/renderizar su propio fondo. Dibujamos primero
            // la pantalla de espera y después dejamos que el chat se pinte encima.
            renderWaitingContent(this, gui, mouseX, mouseY, partialTick);
            super.render(gui, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderBackground(GuiGraphics gui) {
            // Evita que el fondo del mundo sustituya la pantalla de espera.
            renderWaitingContent(this, gui, 0, 0, 0.0f);
        }

        @Override
        public void onClose() {
            if (active && !fading) {
                minecraft.setScreen(new WaitingScreen());
            } else {
                minecraft.setScreen(null);
            }
        }
    }
}
