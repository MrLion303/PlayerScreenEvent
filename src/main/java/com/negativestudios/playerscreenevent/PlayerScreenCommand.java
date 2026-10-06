package com.negativestudios.playerscreenevent;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class PlayerScreenCommand {
    private PlayerScreenCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("playerscreen")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("show").executes(context -> show(context.getSource())))
                        .then(literal("hide").executes(context -> hide(context.getSource())))
        );
    }

    private static int show(CommandSourceStack source) {
        if (source.getServer() == null) {
            return 0;
        }

        if (!ServerScreenManager.show(source.getServer())) {
            source.sendFailure(Component.literal("No se pudo mostrar la pantalla. Revisa la consola y la imagen configurada."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("PlayerScreenEvent: pantalla activada."), true);
        return 1;
    }

    private static int hide(CommandSourceStack source) {
        if (source.getServer() == null) {
            return 0;
        }

        ServerScreenManager.hide(source.getServer());
        source.sendSuccess(() -> Component.literal("PlayerScreenEvent: pantalla desactivada."), true);
        return 1;
    }
}
