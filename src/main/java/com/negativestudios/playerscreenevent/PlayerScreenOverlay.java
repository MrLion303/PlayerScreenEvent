package com.negativestudios.playerscreenevent;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = PlayerScreenEvent.MOD_ID, value = Dist.CLIENT)
public final class PlayerScreenOverlay {
    private static final long FADE_MS = 500L;

    private static boolean active;
    private static boolean fading;
    private static long fadeStart;
    private static String title = "Esperando jugadores...";
    private static String joinedPlayer = "";
    private static int playerCount;
    private static int maxPlayers;
    private static boolean canOpenChat;

    private static DynamicTexture dynamicTexture;
    private static ResourceLocation textureLocation;
    private static int textureWidth;
    private static int textureHeight;

    private PlayerScreenOverlay() {}

    public static void receive(boolean newActive, String newTitle, int count, int max,
                               String joined, byte[] imageBytes, boolean chatPermission) {
        Minecraft minecraft = Minecraft.getInstance();

        title = newTitle;
        playerCount = count;
        maxPlayers = max;
        joinedPlayer = joined == null ? "" : joined;
        canOpenChat = chatPermission;

        if (imageBytes != null && imageBytes.length > 0) {
            updateTexture(imageBytes);
        }

        if (newActive) {
            active = true;
            fading = false;
            fadeStart = 0L;

            if (!(minecraft.screen instanceof WaitingScreen) &&
                    !(minecraft.screen instanceof WaitingChatScreen)) {
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

    private static void updateTexture(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
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

            if (dynamicTexture != null) {
                dynamicTexture.close();
            }

            dynamicTexture = new DynamicTexture(nativeImage);
            textureLocation = Minecraft.getInstance().getTextureManager().register(
                    "playerscreenevent/screen", dynamicTexture
            );
            textureWidth = image.getWidth();
            textureHeight = image.getHeight();
        } catch (IOException ignored) {
            // Si una imagen no se puede decodificar, se conserva la anterior.
        }
    }

    private static void finishFade() {
        active = false;
        fading = false;
        fadeStart = 0L;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WaitingScreen || minecraft.screen instanceof WaitingChatScreen) {
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
            float alpha = getAlpha();
            if (alpha <= 0.0f) {
                return;
            }

            int alphaByte = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
                RenderSystem.setShaderTexture(0, textureLocation);

                double screenAspect = width / (double) height;
                double imageAspect = textureWidth / (double) textureHeight;

                if (screenAspect > imageAspect) {
                    double visibleHeight = textureWidth / screenAspect;
                    double v = (textureHeight - visibleHeight) / 2.0;
                    gui.blit(textureLocation, 0, 0, 0,
                            0.0f, (float) v, width, height, textureWidth, textureHeight);
                } else {
                    double visibleWidth = textureHeight * screenAspect;
                    double u = (textureWidth - visibleWidth) / 2.0;
                    gui.blit(textureLocation, 0, 0, 0,
                            (float) u, 0.0f, width, height, textureWidth, textureHeight);
                }
            } else {
                gui.fill(0, 0, width, height, (alphaByte << 24) | 0x00FFFFFF);
            }

            int titleY = (int) (height * 0.47f);
            int countY = (int) (height * 0.565f);
            int joinedY = (int) (height * 0.625f);

            drawCentered(gui, PlayerScreenOverlay.title, titleY, 1.0f, alphaByte, 0xFFFFFFFF);
            drawCentered(gui, playerCount + "/" + maxPlayers, countY, 1.0f, alphaByte, 0xFFFFFFFF);

            if (!joinedPlayer.isEmpty()) {
                drawCentered(gui, joinedPlayer + " se ha unido.", joinedY, 0.72f, alphaByte, 0xFFFF5555);
            }

            RenderSystem.disableBlend();
        }

        private void drawCentered(GuiGraphics gui, String text, int y, float scale, int alpha, int color) {
            gui.pose().pushPose();
            gui.pose().translate(width / 2.0f, y, 0.0f);
            gui.pose().scale(scale, scale, 1.0f);

            int textWidth = font.width(text);
            int finalColor = (alpha << 24) | (color & 0x00FFFFFF);
            gui.drawString(font, text, -textWidth / 2, 0, finalColor, false);

            gui.pose().popPose();
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
        public void onClose() {
            if (active && !fading) {
                minecraft.setScreen(new WaitingScreen());
            } else {
                minecraft.setScreen(null);
            }
        }
    }
}
