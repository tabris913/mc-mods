package com.tosshie.rbd.event;

import com.tosshie.rbd.advancement.BiomeAdvancementManager;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーがRBDディメンション内で新しいバイオームに入ったことを検出し、
 * BiomeAdvancementManagerに通知するハンドラ。
 */
public class BiomeDiscoveryHandler {

    private static final int CHECK_INTERVAL = 20; // 1秒ごとにチェック

    private final BiomeAdvancementManager manager = new BiomeAdvancementManager();
    private final Map<UUID, String> lastBiome = new HashMap<>();
    private final Map<UUID, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        manager.init(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // RBDディメンション（rbd_dim_* または rbd_spawn）のみ対象
        if (!DimensionHelper.isRBDDimension(player.level().dimension())
                && !DimensionHelper.isSpawnDimension(player.level().dimension())) return;

        UUID uuid = player.getUUID();
        int count = tickCounters.merge(uuid, 1, Integer::sum);
        if (count < CHECK_INTERVAL) return;
        tickCounters.put(uuid, 0);

        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        ResourceLocation biomeId = biomeHolder.unwrapKey()
                .map(key -> key.location())
                .orElse(null);
        if (biomeId == null) return;

        String biomeStr = biomeId.toString();

        // 前回と同じバイオームならスキップ
        if (biomeStr.equals(lastBiome.get(uuid))) return;
        lastBiome.put(uuid, biomeStr);

        manager.onBiomeDiscovered(player, biomeStr);
    }

    /**
     * BiomeAdvancementManagerを取得する。
     */
    public BiomeAdvancementManager getManager() {
        return manager;
    }
}
