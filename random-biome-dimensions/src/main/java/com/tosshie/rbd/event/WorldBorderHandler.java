package com.tosshie.rbd.event;

import com.tosshie.rbd.dimension.DimensionManager;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Applies world border settings when RBD dimensions are loaded.
 */
public class WorldBorderHandler {

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!DimensionHelper.isRBDDimension(level.dimension())) {
            return;
        }

        DimensionManager.setupWorldBorder(level);
    }
}
