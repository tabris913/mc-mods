package com.tosshie.rbd.util;

import com.tosshie.rbd.RandomBiomeDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

public class DimensionHelper {

    public static ResourceKey<Level> dimensionKey(int index) {
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(RandomBiomeDimensions.MOD_ID, "rbd_dim_" + index));
    }

    public static ResourceKey<Level> spawnDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(RandomBiomeDimensions.MOD_ID, "rbd_spawn"));
    }

    public static boolean isRBDDimension(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(RandomBiomeDimensions.MOD_ID)
                && dimension.location().getPath().startsWith("rbd_dim_");
    }

    /**
     * スポーンディメンションかどうかを判定する。
     */
    public static boolean isSpawnDimension(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(RandomBiomeDimensions.MOD_ID)
                && dimension.location().getPath().equals("rbd_spawn");
    }
}
