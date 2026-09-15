package MengySmod.joyevents.gameplay.util;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;

/**
 * 安全落点搜索。
 *
 * <p>随机传送最容易翻车的地方就是“把人传进岩浆/虚空/下界基岩顶”。这里的策略是：
 * 采样 -> 强制加载该点所在区块（传送本身也会加载它，所以不额外浪费）-> 从上向下寻找
 * “双脚有空气、脚下是实心、周围没有危险方块”的第一个位置；找不到就换个点重试，
 * 全部失败就放弃该玩家（宁可不传送，也不埋人）。</p>
 */
public final class SafeLocationFinder {
    private SafeLocationFinder() {}

    private static final Set<Block> HAZARD_BLOCKS = Set.of(
            Blocks.LAVA,
            Blocks.FIRE,
            Blocks.SOUL_FIRE,
            Blocks.MAGMA_BLOCK,
            Blocks.CACTUS,
            Blocks.SWEET_BERRY_BUSH,
            Blocks.POWDER_SNOW,
            Blocks.CAMPFIRE,
            Blocks.SOUL_CAMPFIRE,
            Blocks.WITHER_ROSE,
            Blocks.POINTED_DRIPSTONE,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL,
            Blocks.SCULK_SHRIEKER);

    /** 下界基岩顶的高度上限：超过这个高度就是“屋顶上面”，绝不允许落点在那里。 */
    private static final int NETHER_ROOF_LIMIT = 120;
    /** 单列向下扫描的最大步数，避免出现病态开销。 */
    private static final int MAX_DOWNWARD_SCAN = 48;

    /**
     * 在 (centerX, centerZ) 周围半径 radius 的圆面内寻找一个安全落点。
     *
     * @param requireSafe 为 false 时不做安全检查，直接返回采样点的地表高度
     * @return 找到的位置；全部尝试失败时返回 null
     */
    @Nullable
    public static BlockPos find(ServerLevel level,
                               RandomSource random,
                               double centerX,
                               double centerZ,
                               int radius,
                               boolean requireSafe,
                               int maxAttempts) {
        BlockPos waterFallback = null;
        int attempts = Math.max(1, maxAttempts);

        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(centerX + Math.cos(angle) * distance);
            int z = Mth.floor(centerZ + Math.sin(angle) * distance);

            // 强制加载目标区块，否则高度图查不到真实数值（未加载区块会返回世界最低点）
            level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));

            if (!requireSafe) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                return new BlockPos(x, Math.max(y, level.getMinBuildHeight() + 1), z);
            }

            BlockPos candidate = scanColumn(level, x, z);
            if (candidate == null) {
                continue;
            }
            FluidState feetFluid = level.getFluidState(candidate);
            if (!feetFluid.isEmpty() && feetFluid.is(FluidTags.WATER)) {
                // 水面可以站（会游泳），但优先找干燥地面，把它留作兜底
                if (waterFallback == null) {
                    waterFallback = candidate;
                }
                continue;
            }
            return candidate;
        }

        return waterFallback;
    }

    /** 在单列 (x, z) 上从上向下找到第一个可站立的位置。 */
    @Nullable
    private static BlockPos scanColumn(ServerLevel level, int x, int z) {
        int minY = level.getMinBuildHeight();
        int startY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int ceiling = ceilingLimit(level);
        if (startY > ceiling) {
            // 典型情况：下界，高度图给的是基岩顶，必须从封顶高度往下找真正的落脚点
            startY = ceiling;
        }
        startY = Math.min(startY, level.getMaxBuildHeight() - 2);

        int lowest = minY + 1;
        int scanned = 0;
        for (int y = startY; y >= lowest && scanned < MAX_DOWNWARD_SCAN; y--, scanned++) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos ground = feet.below();

            BlockState groundState = level.getBlockState(ground);
            FluidState groundFluid = level.getFluidState(ground);
            FluidState feetFluid = level.getFluidState(feet);

            if (isHazardous(groundState) || isHazardousFluid(groundFluid) || isHazardousFluid(feetFluid)) {
                continue;
            }
            if (groundState.getCollisionShape(level, ground).isEmpty()) {
                // 脚下不是实心（空气、草丛、告示牌等），继续往下找
                continue;
            }
            if (!hasRoom(level, feet)) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /** 双脚与头部都要有空间，避免落进 1 格缝隙里窒息。 */
    private static boolean hasRoom(ServerLevel level, BlockPos feet) {
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.above();
        return level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    private static boolean isHazardous(BlockState state) {
        return HAZARD_BLOCKS.contains(state.getBlock()) || state.is(BlockTags.FIRE);
    }

    private static boolean isHazardousFluid(FluidState fluid) {
        return !fluid.isEmpty() && fluid.is(FluidTags.LAVA);
    }

    /** 该维度允许的最高落点：下界必须压在基岩顶之下，其它维度取建造高度上限。 */
    private static int ceilingLimit(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            return NETHER_ROOF_LIMIT;
        }
        return level.getMaxBuildHeight() - 1;
    }
}
