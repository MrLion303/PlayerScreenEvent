package com.negativestudios.playerscreenevent;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class PlayerScreenConfig {
    private static ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.ConfigValue<String> TITLE;
    public static ForgeConfigSpec.IntValue MAX_PLAYERS;
    public static ForgeConfigSpec.ConfigValue<String> IMAGE_PATH;

    private PlayerScreenConfig() {}

    public static void register() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("pantalla");

        TITLE = builder
                .comment("Texto grande que aparece en el centro de la pantalla.")
                .define("texto", "Esperando jugadores...");

        MAX_PLAYERS = builder
                .comment("Cantidad maxima mostrada. Usa -1 para usar el maximo real del servidor.")
                .defineInRange("jugadores_maximos", -1, -1, 1000000);

        IMAGE_PATH = builder
                .comment("Ruta de la imagen respecto a la carpeta del servidor.")
                .define("imagen", "config/playerscreenevent/screen.png");

        builder.pop();

        SPEC = builder.build();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "playerscreenevent-common.toml");
    }
}
