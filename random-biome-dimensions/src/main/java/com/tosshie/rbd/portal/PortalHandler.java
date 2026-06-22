package com.tosshie.rbd.portal;

import com.tosshie.rbd.config.RBDConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * Handles portal frame detection and activation for RBD dimensions.
 * Each dimension has its own frame block type specified in the config.
 */
public class PortalHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!isFlintAndSteel(player.getMainHandItem().getItem())) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos clickedPos = event.getPos();

        com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: 右クリック検出 pos={}", clickedPos);

        // 着火対象はクリックしたブロックの上（空気ブロック）またはクリックしたブロック自体
        // ネザーポータルと同様、フレーム下部の上面を着火する想定
        BlockPos firePos = clickedPos.above();
        if (!level.getBlockState(firePos).isAir()) {
            // クリックしたのが内部空間の場合
            if (level.getBlockState(clickedPos).isAir()) {
                firePos = clickedPos;
            } else {
                return;
            }
        }

        // 着火位置の周囲からフレームブロックを探して、どのディメンションのポータルか判定する
        List<? extends String> portalBlocks = RBDConfig.PORTAL_BLOCKS.get();
        int dimIndex = -1;
        Block portalFrameBlock = null;
        BlockPos frameBlockPos = null;

        for (BlockPos neighbor : BlockPos.betweenClosed(firePos.offset(-1, -1, -1), firePos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(neighbor);
            for (int i = 0; i < portalBlocks.size(); i++) {
                Block frameBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(portalBlocks.get(i)));
                if (frameBlock != null && state.is(frameBlock)) {
                    dimIndex = i;
                    portalFrameBlock = frameBlock;
                    frameBlockPos = neighbor.immutable();
                    break;
                }
            }
            if (dimIndex != -1) {
                break;
            }
        }

        if (dimIndex == -1) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: フレームブロック未検出 firePos={}", firePos);
            return;
        }

        com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: フレームブロック検出 dimIndex={} block={}", dimIndex, portalBlocks.get(dimIndex));

        // ディメンションが生成されているか確認
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> targetDimKey =
                com.tosshie.rbd.util.DimensionHelper.dimensionKey(dimIndex);
        if (level.getServer().getLevel(targetDimKey) == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[RBD] ディメンションが生成されていません。/rbd_generate を実行してください。"));
            event.setCanceled(true);
            return;
        }

        // Try to detect a valid portal frame from the fire position
        // フレームブロックの位置から内部空間を探す
        Optional<PortalFrame> frame = detectPortalFrame(level, frameBlockPos, portalFrameBlock);
        if (frame.isEmpty()) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: フレーム構造検証失敗 firePos={}", firePos);
            return;
        }

        // Fill the portal interior with nether portal blocks
        com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("RBDポータル: 起動成功 dim={}", dimIndex);
        fillPortal(level, frame.get());
        event.setCanceled(true);
    }

    private boolean isFlintAndSteel(net.minecraft.world.item.Item item) {
        return item == net.minecraft.world.item.Items.FLINT_AND_STEEL;
    }

    private Optional<PortalFrame> detectPortalFrame(ServerLevel level, BlockPos framePos, Block frameBlock) {
        // フレームブロックの隣接ブロックと、さらにその上を候補とする
        // 底面のどの位置を着火しても内部空間に到達できるようにする
        java.util.Set<BlockPos> candidates = new java.util.LinkedHashSet<>();
        for (BlockPos adj : new BlockPos[]{
                framePos.above(), framePos.below(),
                framePos.north(), framePos.south(),
                framePos.east(), framePos.west(),
                framePos.above().north(), framePos.above().south(),
                framePos.above().east(), framePos.above().west()}) {
            if (isPassable(level, adj, frameBlock)) {
                candidates.add(adj);
            }
        }
        for (BlockPos candidate : candidates) {
            for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                Optional<PortalFrame> frame = tryDetectFrame(level, candidate, frameBlock, axis);
                if (frame.isPresent()) {
                    return frame;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<PortalFrame> tryDetectFrame(ServerLevel level, BlockPos pos, Block frameBlock, Direction.Axis axis) {
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction left = horizontal.getOpposite();

        // posから下に移動して底を見つける（フレームブロックか固体ブロックに当たるまで）
        BlockPos bottom = pos;
        while (bottom.getY() > level.getMinBuildHeight() && isPassable(level, bottom.below(), frameBlock)) {
            bottom = bottom.below();
        }

        // 底の下がフレームブロックでなければ無効
        if (!level.getBlockState(bottom.below()).is(frameBlock)) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("  axis={} 底検証失敗: bottom={} below={}", axis, bottom, level.getBlockState(bottom.below()).getBlock());
            return Optional.empty();
        }

        // 左に移動して左端を見つける（フレームブロックに当たるまで）
        BlockPos bottomLeft = bottom;
        int maxSearch = 22;
        while (maxSearch-- > 0 && isPassable(level, bottomLeft.relative(left), frameBlock)) {
            bottomLeft = bottomLeft.relative(left);
        }

        // 左がフレームブロックでなければ無効
        if (!level.getBlockState(bottomLeft.relative(left)).is(frameBlock)) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("  axis={} 左柱検証失敗: bottomLeft={} leftBlock={}", axis, bottomLeft, level.getBlockState(bottomLeft.relative(left)).getBlock());
            return Optional.empty();
        }

        // 右方向に幅を測る（フレームブロックに当たるまで）
        int width = 0;
        BlockPos check = bottomLeft;
        while (width < 22 && isPassable(level, check, frameBlock)) {
            width++;
            check = check.relative(horizontal);
        }
        if (width < 2 || width > 21 || !level.getBlockState(check).is(frameBlock)) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("  axis={} 幅検証失敗: width={} rightBlock={}", axis, width, level.getBlockState(check).getBlock());
            return Optional.empty();
        }

        // 上方向に高さを測る（フレームブロックに当たるまで）
        int height = 0;
        check = bottomLeft;
        while (height < 22 && isPassable(level, check, frameBlock)) {
            height++;
            check = check.above();
        }
        if (height < 3 || height > 21 || !level.getBlockState(check).is(frameBlock)) {
            com.tosshie.rbd.RandomBiomeDimensions.LOGGER.info("  axis={} 高さ検証失敗: height={} topBlock={}", axis, height, level.getBlockState(check).getBlock());
            return Optional.empty();
        }

        // フレーム全体を検証（四隅は不要）
        // 底辺（角を除く）
        for (int x = 0; x < width; x++) {
            if (!level.getBlockState(bottomLeft.below().relative(horizontal, x)).is(frameBlock)) {
                return Optional.empty();
            }
        }
        // 上辺（角を除く）
        for (int x = 0; x < width; x++) {
            if (!level.getBlockState(bottomLeft.above(height).relative(horizontal, x)).is(frameBlock)) {
                return Optional.empty();
            }
        }
        // 左右の柱
        for (int y = 0; y < height; y++) {
            if (!level.getBlockState(bottomLeft.above(y).relative(left)).is(frameBlock)) {
                return Optional.empty();
            }
            if (!level.getBlockState(bottomLeft.above(y).relative(horizontal, width)).is(frameBlock)) {
                return Optional.empty();
            }
        }
        // 内部がpassable
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!isPassable(level, bottomLeft.above(y).relative(horizontal, x), frameBlock)) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(new PortalFrame(bottomLeft, width, height, axis));
    }

    /**
     * ブロックがポータル内部として通過可能か判定する。
     * 空気、草、花などの非固体ブロックは通過可能。フレームブロックは不可。
     */
    private boolean isPassable(ServerLevel level, BlockPos pos, Block frameBlock) {
        BlockState state = level.getBlockState(pos);
        if (state.is(frameBlock)) {
            return false;
        }
        return !state.isSolid();
    }

    private void fillPortal(ServerLevel level, PortalFrame frame) {
        Direction horizontal = frame.axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockState portalState = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS, frame.axis);

        for (int x = 0; x < frame.width; x++) {
            for (int y = 0; y < frame.height; y++) {
                BlockPos portalPos = frame.bottomLeft.above(y).relative(horizontal, x);
                level.setBlockAndUpdate(portalPos, portalState);
            }
        }
    }

    private record PortalFrame(BlockPos bottomLeft, int width, int height, Direction.Axis axis) {
    }
}
