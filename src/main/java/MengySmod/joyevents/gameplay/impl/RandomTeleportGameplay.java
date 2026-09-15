package MengySmod.joyevents.gameplay.impl;

import javax.annotation.Nullable;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.Gameplay;
import MengySmod.joyevents.gameplay.util.SafeLocationFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 玩法三：随机传送。
 *
 * <p>规则：每隔一段时间，把每名玩家传送到<b>以他当前位置为圆心、配置半径内</b>的一个随机安全落点。
 * 维度策略可配置为“保持当前维度”或“固定到某个维度”；随机传送的落点在触发前无法预知，
 * 因此 HUD 只显示倒计时，不显示目的地。</p>
 */
public final class RandomTeleportGameplay extends Gameplay {
    public RandomTeleportGameplay() {
        super("random_teleport");
    }

    @Override
    public boolean isEnabled() {
        return JoyConfig.randomTeleportEnabled;
    }

    @Override
    public int intervalTicks() {
        return Math.max(1, JoyConfig.randomTeleportIntervalSeconds) * 20;
    }

    @Override
    public boolean announce() {
        return JoyConfig.randomTeleportAnnounce;
    }

    @Override
    public void execute(MinecraftServer server) {
        int radius = JoyConfig.randomTeleportRadius;
        boolean requireSafe = JoyConfig.randomTeleportSafeLanding;
        int maxAttempts = JoyConfig.randomTeleportMaxAttempts;
        boolean fixedMode = JoyConfig.randomTeleportDimensionMode == JoyConfig.DimensionMode.FIXED;

        ServerLevel fixedLevel = null;
        if (fixedMode) {
            fixedLevel = resolveFixedLevel(server);
            if (fixedLevel == null) {
                Joyevents.LOGGER.warn("随机传送被跳过：配置的目标维度 {} 不存在", JoyConfig.randomTeleportFixedDimension);
                return;
            }
        }

        int moved = 0;
        int skipped = 0;
        for (ServerPlayer player : eligiblePlayers(server)) {
            ServerLevel level = fixedMode ? fixedLevel : player.serverLevel();
            BlockPos target = SafeLocationFinder.find(level, level.random,
                    player.getX(), player.getZ(), radius, requireSafe, maxAttempts);
            if (target == null) {
                // 找不到安全落点：宁可不传送，也不要把玩家丢进岩浆或虚空
                skipped++;
                continue;
            }

            teleportEffect(player.serverLevel(), player.getX(), player.getY(), player.getZ());
            player.teleportTo(level,
                    target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
            teleportEffect(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
            moved++;
        }

        if (announce() && moved > 0) {
            if (skipped > 0) {
                broadcast(server, Component.translatable("joyevents.message.random_teleport.triggered_skipped", moved, skipped));
            } else {
                broadcast(server, Component.translatable("joyevents.message.random_teleport.triggered", moved));
            }
        }
    }

    @Nullable
    private static ServerLevel resolveFixedLevel(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(JoyConfig.randomTeleportFixedDimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Override
    public Component statusDetail(MinecraftServer server) {
        JoyConfig.DimensionMode mode = JoyConfig.randomTeleportDimensionMode;
        if (mode == JoyConfig.DimensionMode.FIXED) {
            return Component.translatable("joyevents.status.random_teleport.fixed",
                    JoyConfig.randomTeleportRadius, mode.getTranslatedName(), JoyConfig.randomTeleportFixedDimension);
        }
        return Component.translatable("joyevents.status.random_teleport.keep",
                JoyConfig.randomTeleportRadius, mode.getTranslatedName());
    }

    /** 随机传送的维度用于状态展示，避免“传送到了哪里”无从判断。 */
    public static String describeDimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** 供命令/调试使用：当前维度策略下某名玩家的目标维度。 */
    @Nullable
    public static ResourceKey<Level> targetDimension(MinecraftServer server, ServerPlayer player) {
        if (JoyConfig.randomTeleportDimensionMode == JoyConfig.DimensionMode.KEEP) {
            return player.serverLevel().dimension();
        }
        ServerLevel level = resolveFixedLevel(server);
        return level == null ? null : level.dimension();
    }
}
