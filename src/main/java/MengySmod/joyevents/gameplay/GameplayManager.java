package MengySmod.joyevents.gameplay;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.gameplay.impl.PositionSwapGameplay;
import MengySmod.joyevents.gameplay.impl.RandomTeleportGameplay;
import MengySmod.joyevents.gameplay.impl.SharedHealthGameplay;
import MengySmod.joyevents.network.GameplayHudPayload;
import MengySmod.joyevents.network.JoyNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩法注册表 + 周期调度器。
 *
 * <p>这里刻意把“启用/停用”的判定放在 tick 循环里做（而不是放在配置重载事件里），
 * 这样无论玩家是改配置文件、在配置界面点开关，还是用命令切换，
 * 启用状态的变化都会在下一 tick 被统一检测到并触发对应的 onEnabled/onDisabled 回调，
 * 不会因为事件触发顺序不同而漏掉清理逻辑。</p>
 */
public final class GameplayManager {
    private static final Map<String, Gameplay> GAMEPLAYS = new LinkedHashMap<>();
    private static final Map<String, Runtime> STATES = new LinkedHashMap<>();

    private static final class Runtime {
        /** 上一次观察到的启用状态（同时也是当前运行状态）。 */
        boolean enabled;
        int ticksUntilNext;
        int lastInterval = -1;
        boolean forceSync = true;
    }

    private GameplayManager() {}

    /** 注册全部玩法。默认全部关闭，是否启用只由配置决定。 */
    public static void bootstrap() {
        if (!GAMEPLAYS.isEmpty()) {
            return;
        }
        register(new PositionSwapGameplay());
        register(new RandomTeleportGameplay());
        register(new SharedHealthGameplay());
    }

    private static void register(Gameplay gameplay) {
        GAMEPLAYS.put(gameplay.id(), gameplay);
        STATES.put(gameplay.id(), new Runtime());
    }

    public static Collection<Gameplay> all() {
        return Collections.unmodifiableCollection(GAMEPLAYS.values());
    }

    public static Gameplay byId(String id) {
        return GAMEPLAYS.get(id);
    }

    /** 服务端 tick 入口。 */
    public static void serverTick(MinecraftServer server) {
        for (Gameplay gameplay : GAMEPLAYS.values()) {
            tickOne(server, gameplay);
        }
        syncHud(server);
    }

    private static void tickOne(MinecraftServer server, Gameplay gameplay) {
        Runtime runtime = STATES.get(gameplay.id());
        if (runtime == null) {
            return;
        }

        boolean enabled = gameplay.isEnabled();
        if (enabled != runtime.enabled) {
            runtime.enabled = enabled;
            runtime.lastInterval = -1;
            runtime.ticksUntilNext = Math.max(1, gameplay.intervalTicks());
            runtime.forceSync = true;
            try {
                if (enabled) {
                    gameplay.onEnabled(server);
                } else {
                    gameplay.onDisabled(server);
                }
            } catch (Exception e) {
                Joyevents.LOGGER.error("玩法 {} 在启用/停用回调中出错", gameplay.id(), e);
            }
        }

        if (!runtime.enabled) {
            return;
        }

        try {
            gameplay.onServerTick(server);
        } catch (Exception e) {
            Joyevents.LOGGER.error("玩法 {} 在 tick 回调中出错", gameplay.id(), e);
        }

        if (!gameplay.periodic()) {
            // 常驻效果型玩法（例如共享生命）：不参与倒计时与定时触发
            runtime.ticksUntilNext = 0;
            return;
        }

        int interval = Math.max(1, gameplay.intervalTicks());
        if (runtime.lastInterval != interval) {
            // 周期（或其它参数）被改动：按新周期重置倒计时，避免出现“改完还要等旧的剩余时间”。
            runtime.lastInterval = interval;
            runtime.ticksUntilNext = interval;
            runtime.forceSync = true;
        }

        if (!gameplay.canRun(server)) {
            // 人数不足：停在满周期，等条件满足再开始倒计时。
            runtime.ticksUntilNext = interval;
            return;
        }

        if (--runtime.ticksUntilNext <= 0) {
            runtime.ticksUntilNext = interval;
            runtime.forceSync = true;
            try {
                gameplay.execute(server);
            } catch (Exception e) {
                Joyevents.LOGGER.error("玩法 {} 执行时出错", gameplay.id(), e);
            }
        }
    }

    /** 每秒同步一次 HUD 状态；启用状态刚发生变化时立即补发一次。 */
    private static void syncHud(MinecraftServer server) {
        boolean secondBoundary = server.getTickCount() % 20 == 0;
        for (Gameplay gameplay : GAMEPLAYS.values()) {
            Runtime runtime = STATES.get(gameplay.id());
            if (runtime == null) {
                continue;
            }
            if (!runtime.forceSync && (!runtime.enabled || !secondBoundary)) {
                continue;
            }
            runtime.forceSync = false;

            Gameplay.PoolStatus pool = runtime.enabled ? gameplay.poolStatus() : null;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Gameplay.HudDetail detail = runtime.enabled ? gameplay.hudDetail(player) : null;
                JoyNetwork.sendHud(player, new GameplayHudPayload(
                        gameplay.id(),
                        runtime.enabled,
                        runtime.enabled ? runtime.ticksUntilNext : 0,
                        detail,
                        pool));
            }
        }
    }

    /** 服务端停止时回滚所有玩法状态（恢复血量等）。 */
    public static void onServerStopped(MinecraftServer server) {
        for (Gameplay gameplay : GAMEPLAYS.values()) {
            Runtime runtime = STATES.get(gameplay.id());
            if (runtime == null || !runtime.enabled) {
                continue;
            }
            runtime.enabled = false;
            runtime.forceSync = true;
            try {
                gameplay.onDisabled(server);
            } catch (Exception e) {
                Joyevents.LOGGER.error("玩法 {} 在服务端停止回滚时出错", gameplay.id(), e);
            }
        }
    }

    /** 命令 /joyevents trigger 用：立刻执行一次（不重置倒计时）。 */
    public static boolean triggerNow(MinecraftServer server, String id) {
        Gameplay gameplay = GAMEPLAYS.get(id);
        if (gameplay == null) {
            return false;
        }
        gameplay.execute(server);
        Runtime runtime = STATES.get(id);
        if (runtime != null) {
            runtime.forceSync = true;
        }
        return true;
    }

    /**
     * 命令 status 用：距离下次触发的剩余秒数。
     *
     * <p>刚改完配置的 1 tick 内，运行状态还没被 tick 循环观察到，此时回退成整周期，
     * 避免命令输出里出现 "-1 秒" 这种噪音。</p>
     */
    public static int remainingSeconds(String id) {
        Runtime runtime = STATES.get(id);
        Gameplay gameplay = GAMEPLAYS.get(id);
        if (runtime == null || gameplay == null) {
            return -1;
        }
        if (!runtime.enabled) {
            return (Math.max(1, gameplay.intervalTicks()) + 19) / 20;
        }
        return (runtime.ticksUntilNext + 19) / 20;
    }
}
