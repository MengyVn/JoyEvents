package MengySmod.joyevents.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import MengySmod.joyevents.network.GameplayHudPayload;

/**
 * 客户端 HUD 状态缓存：服务端每秒同步一次，这里只保存“最近一次收到的状态”。
 *
 * <p>所有写入都通过 {@code IPayloadContext#enqueueWork} 调度到客户端主线程，
 * 渲染也发生在主线程，因此这里不存在竞态。</p>
 */
public final class ClientHudState {
    private ClientHudState() {}

    /** 单个玩法的一条显示状态。 */
    public record Entry(boolean active,
                        int remainingTicks,
                        boolean hasDetail,
                        String detailLabel,
                        double detailX,
                        double detailY,
                        double detailZ,
                        boolean hasPool,
                        double poolCurrent,
                        double poolMax) {
        public double remainingSeconds() {
            return this.remainingTicks / 20.0D;
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    public static void update(GameplayHudPayload payload) {
        if (!payload.active()) {
            ENTRIES.remove(payload.gameplayId());
            return;
        }
        ENTRIES.put(payload.gameplayId(), new Entry(
                true,
                payload.remainingTicks(),
                payload.hasDetail(),
                payload.detailLabel(),
                payload.detailX(),
                payload.detailY(),
                payload.detailZ(),
                payload.hasPool(),
                payload.poolCurrent(),
                payload.poolMax()));
    }

    @Nullable
    public static Entry get(String gameplayId) {
        return ENTRIES.get(gameplayId);
    }

    /** 断开连接时清空，避免退出服务器后 HUD 残留。 */
    public static void clear() {
        ENTRIES.clear();
    }
}
