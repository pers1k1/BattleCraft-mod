package com.persiki84.battlecraft.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.ui.UiGlassProbe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class GlassProbeCommand {

    private GlassProbeCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bcglass")
                .then(Commands.literal("probe")
                        .then(Commands.argument("mode", IntegerArgumentType.integer(0, UiGlassProbe.MODES))
                                .executes(GlassProbeCommand::apply))));
    }

    private static int apply(CommandContext<CommandSourceStack> context) {
        int mode = IntegerArgumentType.getInteger(context, "mode");
        UiGlassProbe.mode(mode);
        context.getSource().sendSuccess(() -> Component.literal("glass probe " + mode), false);
        return 1;
    }
}
