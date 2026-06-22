package com.tosshie.rbd.event;

import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Prevents sunlight damage to undead mobs in RBD dimensions.
 * Day/night cycle and weather cycle remain active.
 */
public class SunlightHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (!DimensionHelper.isRBDDimension(level.dimension())) {
            return;
        }

        // Extinguish undead mobs that are burning due to sunlight exposure
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Monster mob && mob.isOnFire() && isExposedToSunlight(level, mob)) {
                mob.clearFire();
            }
        }
    }

    private boolean isExposedToSunlight(ServerLevel level, Monster mob) {
        return level.isDay()
                && !level.isRaining()
                && level.canSeeSky(mob.blockPosition());
    }
}
