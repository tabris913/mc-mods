package com.tosshie.rbd.portal;

import com.tosshie.rbd.config.RBDConfig;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles teleportation when a player stands on a nether portal block
 * that belongs to an RBD portal frame.
 */
public class PortalTeleportHandler {

    private static final int PORTAL_COOLDOWN_TICKS = 100;
    private static final int PORTAL_TRIGGER_TICKS = 60;
    private static final Map<UUID, Integer> portalTimer = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    public static final java.util.Set<UUID> rbdTeleporting = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final Map<UUID, BlockPos> returnPositions = new HashMap<>();

    /**
     * RBDディメンションまたはRBDポータルにいる場合、バニラのポータルテレポートをキャンセルする。
     * RBD自身のテレポートはキャンセルしない。
     */
    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // RBD自身のテレポート中はキャンセルしない
        if (rbdTeleporting.contains(player.getUUID())) {
            return;
        }
        ResourceKey<Level> currentDim = player.level().dimension();
        // RBDディメンションにいる場合はバニラのテレポートをキャンセル
        if (DimensionHelper.isRBDDimension(currentDim) || currentDim.location().getNamespace().equals("randombimedimensions")) {
            event.setCanceled(true);
            return;
        }
        // RBDポータルに立っている場合はバニラのテレポートをキャンセル
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        if (level.getBlockState(playerPos).is(Blocks.NETHER_PORTAL) && isRBDPortal(level, playerPos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        // Check if player is standing in a nether portal block
        boolean inPortal = level.getBlockState(playerPos).is(Blocks.NETHER_PORTAL);

        if (!inPortal) {
            portalTimer.remove(player.getUUID());
            return;
        }

        // Check if the portal is an RBD portal (adjacent frame block check)
        if (!isRBDPortal(level, playerPos)) {
            return;
        }

        // バニラのポータル処理を無効化（ポータルタイマーをリセット）
        player.setPortalCooldown(player.getPortalCooldown());

        // Check cooldown
        Long lastTeleport = cooldowns.get(player.getUUID());
        if (lastTeleport != null && (level.getGameTime() - lastTeleport) < PORTAL_COOLDOWN_TICKS) {
            return;
        }

        // Increment timer
        int timer = portalTimer.getOrDefault(player.getUUID(), 0) + 1;
        portalTimer.put(player.getUUID(), timer);

        if (timer >= PORTAL_TRIGGER_TICKS) {
            portalTimer.remove(player.getUUID());
            cooldowns.put(player.getUUID(), level.getGameTime());
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: テレポート実行 player={}", player.getName().getString());
            teleportPlayer(player);
        }
    }

    private boolean isRBDPortal(ServerLevel level, BlockPos portalPos) {
        // Check if any adjacent block is one of the RBD frame blocks
        List<? extends String> portalBlocks = RBDConfig.PORTAL_BLOCKS.get();
        for (BlockPos neighbor : BlockPos.betweenClosed(portalPos.offset(-1, -1, -1), portalPos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(neighbor);
            for (String blockName : portalBlocks) {
                Block frameBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
                if (frameBlock != null && state.is(frameBlock)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void teleportPlayer(ServerPlayer player) {
        ResourceKey<Level> currentDim = player.level().dimension();
        ResourceKey<Level> targetDim;
        ResourceKey<Level> spawnDim = DimensionHelper.spawnDimensionKey();

        if (DimensionHelper.isRBDDimension(currentDim)) {
            targetDim = spawnDim;
        } else if (currentDim == spawnDim || currentDim == Level.OVERWORLD) {
            // Determine target by the frame block type
            targetDim = findTargetByPortalBlock(player);
        } else {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: テレポート不可 - 対象外ディメンション: {}", currentDim.location());
            return;
        }

        if (targetDim == null) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: テレポート不可 - targetDimがnull");
            return;
        }

        ServerLevel targetLevel = player.getServer().getLevel(targetDim);
        if (targetLevel == null) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: テレポート不可 - targetLevelがnull: {}", targetDim.location());
            return;
        }

        com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: テレポート {} -> {}", currentDim.location(), targetDim.location());

        // テレポート前にフレームブロックを取得
        final Block frameBlock = portalBlock(player);
        final boolean isGoingToRBD = !DimensionHelper.isRBDDimension(currentDim);

        // 行き：元の位置を保存し、RBDディメンションの(0,0)に移動
        // 帰り：保存した元の位置に戻る
        final BlockPos targetPos;
        if (isGoingToRBD) {
            returnPositions.put(player.getUUID(), player.blockPosition());
            targetPos = BlockPos.ZERO;
        } else {
            BlockPos saved = returnPositions.remove(player.getUUID());
            targetPos = saved != null ? saved : player.blockPosition();
        }

        // 帰還時はHeightmap不要（保存した位置をそのまま使う）
        final int finalX = targetPos.getX();
        final int finalY;
        final int finalZ = targetPos.getZ();
        if (isGoingToRBD) {
            // 行き: チャンク読み込みはbuildReturnPortal内で行う
            finalY = 0; // placeEntity内で上書きされる
        } else {
            // 帰り: 保存した位置をそのまま使う
            finalY = targetPos.getY();
        }

        rbdTeleporting.add(player.getUUID());
        final int[] portalY = {finalY};
        try {
            player.changeDimension(targetLevel, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity entity,
                        net.minecraft.server.level.ServerLevel currentWorld, net.minecraft.server.level.ServerLevel destWorld,
                        float yaw, java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> repositionEntity) {
                    net.minecraft.world.entity.Entity newEntity = repositionEntity.apply(false);
                    if (isGoingToRBD) {
                        portalY[0] = buildReturnPortal(destWorld, new BlockPos(0, 0, 0), frameBlock);
                    }
                    return newEntity;
                }
            });
        } finally {
            rbdTeleporting.remove(player.getUUID());
        }

        // changeDimension後に強制的に正しい位置へ移動
        if (isGoingToRBD) {
            player.teleportTo(targetLevel, 0.5, portalY[0] + 1, 0.0, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(targetLevel, finalX + 0.5, finalY, finalZ + 0.5, player.getYRot(), player.getXRot());
            // 帰還時：バニラ以外のアイテムを削除
            removeModItems(player);
        }
    }

    /**
     * プレイヤーのインベントリからバニラ（minecraft）以外のアイテムを削除する。
     */
    private void removeModItems(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null && !"minecraft".equals(itemId.getNamespace())) {
                    player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private ResourceKey<Level> findTargetByPortalBlock(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        List<? extends String> portalBlocks = RBDConfig.PORTAL_BLOCKS.get();

        // Find which frame block is adjacent to determine target dimension
        for (BlockPos neighbor : BlockPos.betweenClosed(playerPos.offset(-1, -1, -1), playerPos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(neighbor);
            for (int i = 0; i < portalBlocks.size(); i++) {
                Block frameBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(portalBlocks.get(i)));
                if (frameBlock != null && state.is(frameBlock)) {
                    ResourceKey<Level> key = DimensionHelper.dimensionKey(i);
                    ServerLevel target = player.getServer().getLevel(key);
                    if (target != null) {
                        return key;
                    }
                    com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: ディメンション未ロード dimIndex={} key={} allLevels={}",
                            i, key.location(), player.getServer().levelKeys());
                }
            }
        }
        return null;
    }

    /**
     * 使用したポータルのフレームブロックを取得する。
     */
    private Block portalBlock(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        List<? extends String> portalBlocks = RBDConfig.PORTAL_BLOCKS.get();
        for (BlockPos neighbor : BlockPos.betweenClosed(playerPos.offset(-1, -1, -1), playerPos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(neighbor);
            for (String blockName : portalBlocks) {
                Block frameBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
                if (frameBlock != null && state.is(frameBlock)) {
                    return frameBlock;
                }
            }
        }
        // フォールバック：最初のポータルブロック
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(portalBlocks.get(0)));
    }

    /**
     * 帰還用ポータルを生成する（4x5フレーム、内部2x3）。
     * baseのxz座標にポータルを生成。y座標は自動で地表に合わせる。
     * 生成したポータルのy座標を返す。
     */
    private int buildReturnPortal(ServerLevel level, BlockPos base, Block frameBlock) {
        int bx = base.getX();
        int bz = base.getZ();

        // チャンク強制読み込み
        level.getChunk(bx >> 4, bz >> 4);

        // 既存ポータルを探す（xz固定、y方向に探索）
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            if (level.getBlockState(new BlockPos(bx, y, bz)).is(Blocks.NETHER_PORTAL)) {
                return y - 1; // ポータル内部の1つ下（底辺）のyを返す
            }
        }

        // 新規生成：地表のyを取得
        int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx, bz);
        if (by <= level.getMinBuildHeight()) {
            by = level.getSeaLevel() + 1;
        }

        BlockState frame = frameBlock.defaultBlockState();
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS, net.minecraft.core.Direction.Axis.X);

        // 底辺 (2ブロック)
        for (int x = 0; x < 2; x++) {
            level.setBlockAndUpdate(new BlockPos(bx + x, by, bz), frame);
        }
        // 上辺
        for (int x = 0; x < 2; x++) {
            level.setBlockAndUpdate(new BlockPos(bx + x, by + 4, bz), frame);
        }
        // 左柱
        for (int y = 1; y <= 3; y++) {
            level.setBlockAndUpdate(new BlockPos(bx - 1, by + y, bz), frame);
        }
        // 右柱
        for (int y = 1; y <= 3; y++) {
            level.setBlockAndUpdate(new BlockPos(bx + 2, by + y, bz), frame);
        }
        // 内部ポータルブロック (2x3)
        for (int x = 0; x < 2; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlockAndUpdate(new BlockPos(bx + x, by + y, bz), portal);
            }
        }
        // ポータルの前後の空間を確保し、足場を生成
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 4; y++) {
                BlockPos front = new BlockPos(bx + x, by + y, bz - 1);
                if (!level.getBlockState(front).isAir()) {
                    level.setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
                }
                BlockPos back = new BlockPos(bx + x, by + y, bz + 1);
                if (!level.getBlockState(back).isAir()) {
                    level.setBlockAndUpdate(back, Blocks.AIR.defaultBlockState());
                }
            }
            // 足場（底辺の下にブロックを置く）
            BlockPos underFront = new BlockPos(bx + x, by - 1, bz - 1);
            if (!level.getBlockState(underFront).isSolid()) {
                level.setBlockAndUpdate(underFront, Blocks.STONE.defaultBlockState());
            }
            BlockPos underBack = new BlockPos(bx + x, by - 1, bz + 1);
            if (!level.getBlockState(underBack).isSolid()) {
                level.setBlockAndUpdate(underBack, Blocks.STONE.defaultBlockState());
            }
        }
        return by;
    }
}
