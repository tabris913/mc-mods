package com.tosshie.rbd.event;

import com.tosshie.rbd.RandomBiomeDimensions;
import com.tosshie.rbd.dimension.DimensionManager;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * バニラバイオームのみのスポーンディメンションを生成し、
 * プレイヤーの初回ログイン時にそこへテレポートする。
 */
public class SpawnDimensionHandler {

    /** ディメンションごとのスポーン位置を保持 */
    private static final Map<ResourceKey<Level>, BlockPos> dimensionSpawnPositions = new HashMap<>();

    /**
     * スポーン用ディメンションのキー。
     */
    public static final ResourceKey<Level> SPAWN_DIM = DimensionHelper.spawnDimensionKey();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DimensionManager.getOrCreateSpawnDimension(server);
        RandomBiomeDimensions.LOGGER.info("スポーンディメンションを準備しました");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // オーバーワールドにいる場合はスポーンディメンションに移動
        if (player.level().dimension() == Level.OVERWORLD) {
            ServerLevel spawnLevel = player.getServer().getLevel(SPAWN_DIM);
            if (spawnLevel != null) {
                BlockPos safePos = dimensionSpawnPositions.computeIfAbsent(SPAWN_DIM,
                        k -> findSafeSpawn(spawnLevel, BlockPos.ZERO));
                player.teleportTo(spawnLevel, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // リスポーン先がオーバーワールドの場合、スポーンディメンションに移動
        if (player.level().dimension() == Level.OVERWORLD && player.getRespawnPosition() == null) {
            ServerLevel spawnLevel = player.getServer().getLevel(SPAWN_DIM);
            if (spawnLevel != null) {
                BlockPos safePos = dimensionSpawnPositions.computeIfAbsent(SPAWN_DIM,
                        k -> findSafeSpawn(spawnLevel, BlockPos.ZERO));
                player.teleportTo(spawnLevel, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
        }
    }

    /**
     * ディメンションのスポーン位置を設定する。
     */
    public static void setDimensionSpawn(ResourceKey<Level> dim, BlockPos pos) {
        dimensionSpawnPositions.put(dim, pos);
    }

    /**
     * ディメンションのスポーン位置を取得する。
     */
    public static BlockPos getDimensionSpawn(ResourceKey<Level> dim) {
        return dimensionSpawnPositions.get(dim);
    }

    /**
     * RBDバイオームディメンションにいるプレイヤーをスポーンディメンションに退避させる。
     * リスポーンポイントが削除対象のディメンションにある場合はリセットする。
     */
    public static void ejectPlayersFromRBDDimensions(MinecraftServer server) {
        ServerLevel spawnLevel = server.getLevel(SPAWN_DIM);
        if (spawnLevel == null) {
            return;
        }
        BlockPos spawnPos = dimensionSpawnPositions.getOrDefault(SPAWN_DIM,
                findSafeSpawn(spawnLevel, BlockPos.ZERO));
        final int sx = spawnPos.getX();
        final int sy = spawnPos.getY();
        final int sz = spawnPos.getZ();

        for (ServerPlayer player : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            // リスポーンポイントがRBDディメンションにある場合リセット
            ResourceKey<Level> respawnDim = player.getRespawnDimension();
            if (DimensionHelper.isRBDDimension(respawnDim)) {
                player.setRespawnPosition(SPAWN_DIM, spawnPos, 0, false, false);
            }

            // RBDディメンションにいるプレイヤーを退避
            if (DimensionHelper.isRBDDimension(player.level().dimension())) {
                com.tosshie.rbd.portal.PortalTeleportHandler.rbdTeleporting.add(player.getUUID());
                try {
                    player.changeDimension(spawnLevel, new net.minecraftforge.common.util.ITeleporter() {
                        @Override
                        public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity entity,
                                net.minecraft.server.level.ServerLevel currentWorld, net.minecraft.server.level.ServerLevel destWorld,
                                float yaw, java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> repositionEntity) {
                            net.minecraft.world.entity.Entity e = repositionEntity.apply(false);
                            e.moveTo(sx + 0.5, sy, sz + 0.5, yaw, e.getXRot());
                            return e;
                        }
                    });
                } finally {
                    com.tosshie.rbd.portal.PortalTeleportHandler.rbdTeleporting.remove(player.getUUID());
                }
                player.teleportTo(spawnLevel, sx + 0.5, sy, sz + 0.5, player.getYRot(), player.getXRot());
                removeModItems(player);
            }
        }
    }

    /**
     * プレイヤーのインベントリからバニラ（minecraft）以外のアイテムを削除する。
     */
    private static void removeModItems(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                net.minecraft.resources.ResourceLocation itemId =
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null && !"minecraft".equals(itemId.getNamespace())) {
                    player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * 指定座標付近で安全なスポーン位置を探す。
     */
    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos target) {
        int x = target.getX();
        int z = target.getZ();
        // チャンクの完全生成を強制
        level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        level.getChunk(x >> 4, z >> 4);
        // 上から下に探索して最初の空気2ブロックを見つける
        for (int y = level.getMaxBuildHeight() - 2; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = new BlockPos(x, y - 1, z);
            if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(below).isSolid()) {
                return pos;
            }
        }
        // 見つからなければ高い位置に（落下前に着地する）
        return new BlockPos(x, level.getSeaLevel() + 10, z);
    }
}
