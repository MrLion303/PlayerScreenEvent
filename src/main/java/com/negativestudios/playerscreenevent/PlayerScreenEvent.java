package com.negativestudios.playerscreenevent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PlayerScreenEvent.MOD_ID)
public final class PlayerScreenEvent {
    public static final String MOD_ID = "playerscreenevent";

    public PlayerScreenEvent() {
        PlayerScreenConfig.register();
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.register(ServerScreenManager.class);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        PlayerScreenCommand.register(event.getDispatcher());
    }
}
