package MengySmod.joyevents.gameplay.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.Gameplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 玩法一：位置互换。
 *
 * <p>规则（按 need.md 的约定精确实现）：</p>
 * <ol>
 *   <li>先对全体参与玩家做一次<b>位置快照</b>；</li>
 *   <li>每名玩家计算“离自己最近的那名玩家”（仅在同一维度内比较，跨维度比坐标没有意义）；</li>
 *   <li>把该玩家的落点记为目标玩家<b>快照时的坐标</b>；</li>
 *   <li>全部计算完成后统一执行传送。</li>
 * </ol>
 *
 * <p>因为目标是快照坐标而不是“另一名玩家本人”，所以这是一个基于位置的映射而非两两配对：
 * 三人时 A→原B点、B→原C点、C→原B点，可能出现两人落在同一坐标，这是约定内允许的结果。</p>
 */
public final class PositionSwapGameplay extends Gameplay {
    /** 一次落点信息：目标坐标 + 目标玩家名字（用于 HUD 显示）。 */
    private record Destination(ServerLevel level, double x, double y, double z, String name) {}

    public PositionSwapGameplay() {
        super("position_swap");
    }

    @Override
    public boolean isEnabled() {
        return JoyConfig.positionSwapEnabled;
    }

    @Override
    public int intervalTicks() {
        return Math.max(1, JoyConfig.positionSwapIntervalSeconds) * 20;
    }

    @Override
    public boolean announce() {
        return JoyConfig.positionSwapAnnounce;
    }

    @Override
    public int minimumPlayers() {
        return 2;
    }

    @Override
    public boolean canRun(MinecraftServer server) {
        // 需要“同一维度里至少有两名玩家”才有互换对象；单人时倒计时停在满周期等待。
        return groupByDimension(eligiblePlayers(server)).values().stream().anyMatch(group -> group.size() >= 2);
    }

    @Override
    public void execute(MinecraftServer server) {
        List<ServerPlayer> players = eligiblePlayers(server);
        if (players.size() < 2) {
            return;
        }

        // 第一步：快照 + 计算落点（此时还没有任何人被移动）
        Map<UUID, Destination> destinations = new HashMap<>();
        for (List<ServerPlayer> group : groupByDimension(players).values()) {
            if (group.size() < 2) {
                continue;
            }
            for (ServerPlayer player : group) {
                ServerPlayer nearest = nearestIn(group, player);
                if (nearest == null) {
                    continue;
                }
                destinations.put(player.getUUID(), new Destination(
                        nearest.serverLevel(),
                        nearest.getX(),
                        nearest.getY(),
                        nearest.getZ(),
                        nearest.getGameProfile().getName()));
            }
        }

        if (destinations.isEmpty()) {
            return;
        }

        // 第二步：统一传送
        int moved = 0;
        for (ServerPlayer player : players) {
            Destination destination = destinations.get(player.getUUID());
            if (destination == null) {
                continue;
            }
            teleportEffect(player.serverLevel(), player.getX(), player.getY(), player.getZ());
            player.teleportTo(destination.level(),
                    destination.x(), destination.y(), destination.z(),
                    player.getYRot(), player.getXRot());
            // 传送后清除坠落距离，避免刚落地就摔伤
            player.resetFallDistance();
            teleportEffect(player.serverLevel(), player.getX(), player.getY(), player.getZ());
            moved++;
        }

        if (moved > 0 && announce()) {
            broadcast(server, Component.translatable("joyevents.message.position_swap.triggered", moved));
        }
    }

    @Override
    @Nullable
    public HudDetail hudDetail(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || player.isSpectator() || !player.isAlive()) {
            return null;
        }
        List<ServerPlayer> group = groupByDimension(eligiblePlayers(server)).get(player.serverLevel().dimension());
        if (group == null || group.size() < 2) {
            return null;
        }
        ServerPlayer nearest = nearestIn(group, player);
        if (nearest == null) {
            return null;
        }
        // 提前告诉玩家“你将被送到此人此刻所在的位置”，方便做取舍
        return new HudDetail(nearest.getGameProfile().getName(), nearest.getX(), nearest.getY(), nearest.getZ());
    }

    @Override
    public Component statusDetail(MinecraftServer server) {
        int groups = 0;
        for (List<ServerPlayer> group : groupByDimension(eligiblePlayers(server)).values()) {
            if (group.size() >= 2) {
                groups++;
            }
        }
        if (groups == 0) {
            return Component.translatable("joyevents.status.position_swap.none");
        }
        return Component.translatable("joyevents.status.position_swap.pairs", groups);
    }

    @Nullable
    private static ServerPlayer nearestIn(List<ServerPlayer> group, ServerPlayer self) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : group) {
            if (other == self) {
                continue;
            }
            double distance = self.position().distanceToSqr(other.position());
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    private static Map<ResourceKey<Level>, List<ServerPlayer>> groupByDimension(List<ServerPlayer> players) {
        Map<ResourceKey<Level>, List<ServerPlayer>> groups = new LinkedHashMap<>();
        for (ServerPlayer player : players) {
            groups.computeIfAbsent(player.serverLevel().dimension(), key -> new ArrayList<>()).add(player);
        }
        return groups;
    }
}
