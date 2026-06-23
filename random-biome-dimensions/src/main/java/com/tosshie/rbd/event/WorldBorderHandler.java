package com.tosshie.rbd.event;

import com.tosshie.rbd.dimension.DimensionManager;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * RBDディメンションのワールドボーダーをロード後に確実に適用し、
 * プレイヤーがディメンションに入った際にボーダー情報を送信する。
 */
public class WorldBorderHandler {

    private final Set<String> pending = new HashSet<>();
    private boolean scheduled = false;

    @SubscribeEvent
    public void onLevelLoad(net.minecraftforge.event.level.LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DimensionHelper.isRBDDimension(level.dimension())) {
            return;
        }
        pending.add(level.dimension().location().toString());
        scheduled = true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!scheduled || event.phase != TickEvent.Phase.END) {
            return;
        }
        scheduled = false;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (pending.remove(level.dimension().location().toString())) {
                DimensionManager.setupWorldBorder(level);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!DimensionHelper.isRBDDimension(event.getTo())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        player.connection.send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
    }
}
