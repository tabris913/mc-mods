package com.tosshie.rbd.event;

import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * RBDディメンションでは明るさに関係なく敵モブがスポーンするようにする。
 */
public class MobSpawnHandler {

    @SubscribeEvent
    public void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        LevelAccessor level = event.getLevel();
        if (level instanceof Level l && DimensionHelper.isRBDDimension(l.dimension())) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getLevel() instanceof Level l && DimensionHelper.isRBDDimension(l.dimension())) {
            event.setResult(Event.Result.ALLOW);
        }
    }
}
