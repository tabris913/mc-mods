package com.tosshie.rbd.command;

import com.tosshie.rbd.dimension.DimensionManager;
import com.tosshie.rbd.event.SpawnDimensionHandler;
import com.tosshie.rbd.util.DimensionHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;

public class GenerateCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rbd_generate")
                .requires(source -> source.hasPermission(2))
                .executes(GenerateCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        source.sendSuccess(() -> Component.literal("[RBD] Removing existing dimensions..."), true);
        SpawnDimensionHandler.ejectPlayersFromRBDDimensions(server);
        DimensionManager.removeDimensions(server);

        source.sendSuccess(() -> Component.literal("[RBD] Generating new dimensions..."), true);
        List<ResourceKey<Level>> created = DimensionManager.generateDimensions(server);

        if (created.isEmpty()) {
            source.sendFailure(Component.literal("[RBD] No valid biomes found in pool. Check config."));
            return 0;
        }

        // Setup world borders for created dimensions
        for (ResourceKey<Level> dimKey : created) {
            ServerLevel level = server.getLevel(dimKey);
            if (level != null) {
                DimensionManager.setupWorldBorder(level);
            }
        }

        source.sendSuccess(() -> Component.literal("[RBD] Generated " + created.size() + " dimensions. Ready to use!"), true);
        return 1;
    }
}
