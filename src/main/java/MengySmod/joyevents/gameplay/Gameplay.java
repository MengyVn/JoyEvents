package MengySmod.joyevents.gameplay;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 一个“玩法”的抽象。
 *
 * <p>框架约定：</p>
 * <ul>
 *   <li>玩法默认关闭；是否启用完全由 {@link #isEnabled()} 决定，通常读配置。</li>
 *   <li>玩法只在服务端执行，客户端仅通过 HUD 同步包获得显示信息，因此联机时规则永远以服务端为准。</li>
 *   <li>计时、启用/停用回调、HUD 同步由 {@link GameplayManager} 统一驱动，玩法本身不自己数 tick。</li>
 * </ul>
 */
public abstract class Gameplay {
    /**
     * HUD 上的一条“附加信息”（例如位置互换的目的地）。
     *
     * @param label 目标名称，用于本地化占位（例如另一名玩家的名字）
     */
    public record HudDetail(String label, double x, double y, double z) {}

    /** 生命池状态，用于 HUD 显示。 */
    public record PoolStatus(double current, double max) {
        public double ratio() {
            return this.max <= 0 ? 0 : Math.clamp(this.current / this.max, 0.0, 1.0);
        }
    }

    private final String id;

    protected Gameplay(String id) {
        this.id = id;
    }

    /** 玩法唯一 ID，同时也是命令参数与翻译键的一部分。 */
    public final String id() {
        return this.id;
    }

    /** 玩家可读名称的翻译键。 */
    public final String nameKey() {
        return "joyevents.gameplay." + this.id;
    }

    /** 玩法说明的翻译键。 */
    public final String descriptionKey() {
        return "joyevents.gameplay." + this.id + ".desc";
    }

    /** 该玩法当前是否启用（读配置）。 */
    public abstract boolean isEnabled();

    /** 触发周期（tick）。 */
    public abstract int intervalTicks();

    /**
     * 该玩法是否是“周期触发型”。
     *
     * <p>位置互换、随机传送是周期触发型；共享生命是常驻效果，没有倒计时也不该被定时执行，
     * 因此返回 false，调度器只会给它发 tick 回调，不做倒计时与定时触发。</p>
     */
    public boolean periodic() {
        return true;
    }

    /** 触发时是否在聊天栏公告。 */
    public abstract boolean announce();

    /** 需要至少多少名玩家才会开始计时。 */
    public int minimumPlayers() {
        return 1;
    }

    /** 执行一次玩法。由 {@link GameplayManager} 在服务端主线程调用。 */
    public abstract void execute(MinecraftServer server);

    /** 是否满足运行条件；不满足时计时器会停在满周期上等待。 */
    public boolean canRun(MinecraftServer server) {
        return eligiblePlayers(server).size() >= this.minimumPlayers();
    }

    /** 由“关闭 -> 开启”时调用，用于初始化状态。 */
    public void onEnabled(MinecraftServer server) {}

    /** 由“开启 -> 关闭”时调用，必须在此彻底回滚自己造成的状态（例如恢复血量）。 */
    public void onDisabled(MinecraftServer server) {}

    /** 每个服务端 tick 调用一次（仅在启用时）。 */
    public void onServerTick(MinecraftServer server) {}

    /** 该玩家此刻应当显示的附加 HUD 信息，没有则返回 null。 */
    @Nullable
    public HudDetail hudDetail(ServerPlayer player) {
        return null;
    }

    /** 该玩法此刻的池状态，没有则返回 null。 */
    @Nullable
    public PoolStatus poolStatus() {
        return null;
    }

    /** 命令 status 输出里的一行补充说明。返回 Component 而不是 String，这样客户端会按自己的语言渲染。 */
    public net.minecraft.network.chat.Component statusDetail(MinecraftServer server) {
        return net.minecraft.network.chat.Component.empty();
    }

    /** 向全体玩家发送一条系统消息。 */
    protected static void broadcast(MinecraftServer server, net.minecraft.network.chat.Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** 传送的视觉/听觉反馈，让玩家立刻知道自己被挪动了。 */
    protected static void teleportEffect(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                x, y + 1.0D, z, 32, 0.4D, 0.6D, 0.4D, 0.05D);
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /** 参与玩法的玩家：排除旁观者与已死亡实体。 */
    protected static List<ServerPlayer> eligiblePlayers(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            players.add(player);
        }
        return players;
    }
}
