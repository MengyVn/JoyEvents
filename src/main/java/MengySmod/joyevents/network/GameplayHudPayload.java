package MengySmod.joyevents.network;

import javax.annotation.Nullable;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.gameplay.Gameplay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端的 HUD 同步包。
 *
 * <p>玩法规则完全在服务端，客户端只负责显示，所以这里只传“显示所需的最小信息”：
 * 剩余 tick、是否有附加信息（位置互换的目的地）、以及生命池数值。
 * 文案不传字符串，而是让客户端用翻译键自行组装，保证语言文件仍然有效。</p>
 */
public record GameplayHudPayload(
        String gameplayId,
        boolean active,
        int remainingTicks,
        boolean hasDetail,
        String detailLabel,
        double detailX,
        double detailY,
        double detailZ,
        boolean hasPool,
        double poolCurrent,
        double poolMax) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameplayHudPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Joyevents.MODID, "gameplay_hud"));

    public static final StreamCodec<FriendlyByteBuf, GameplayHudPayload> STREAM_CODEC = StreamCodec.of(
            GameplayHudPayload::encode,
            GameplayHudPayload::decode);

    public GameplayHudPayload(String gameplayId, boolean active, int remainingTicks,
                              @Nullable Gameplay.HudDetail detail, @Nullable Gameplay.PoolStatus pool) {
        this(gameplayId,
                active,
                remainingTicks,
                detail != null,
                detail != null ? detail.label() : "",
                detail != null ? detail.x() : 0.0D,
                detail != null ? detail.y() : 0.0D,
                detail != null ? detail.z() : 0.0D,
                pool != null,
                pool != null ? pool.current() : 0.0D,
                pool != null ? pool.max() : 0.0D);
    }

    private static void encode(FriendlyByteBuf buf, GameplayHudPayload payload) {
        buf.writeUtf(payload.gameplayId, 64);
        buf.writeBoolean(payload.active);
        buf.writeVarInt(payload.remainingTicks);
        buf.writeBoolean(payload.hasDetail);
        if (payload.hasDetail) {
            buf.writeUtf(payload.detailLabel, 64);
            buf.writeDouble(payload.detailX);
            buf.writeDouble(payload.detailY);
            buf.writeDouble(payload.detailZ);
        }
        buf.writeBoolean(payload.hasPool);
        if (payload.hasPool) {
            buf.writeDouble(payload.poolCurrent);
            buf.writeDouble(payload.poolMax);
        }
    }

    private static GameplayHudPayload decode(FriendlyByteBuf buf) {
        String gameplayId = buf.readUtf(64);
        boolean active = buf.readBoolean();
        int remainingTicks = buf.readVarInt();
        boolean hasDetail = buf.readBoolean();
        String label = "";
        double x = 0;
        double y = 0;
        double z = 0;
        if (hasDetail) {
            label = buf.readUtf(64);
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
        }
        boolean hasPool = buf.readBoolean();
        double poolCurrent = 0;
        double poolMax = 0;
        if (hasPool) {
            poolCurrent = buf.readDouble();
            poolMax = buf.readDouble();
        }
        return new GameplayHudPayload(gameplayId, active, remainingTicks, hasDetail, label, x, y, z,
                hasPool, poolCurrent, poolMax);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public double remainingSeconds() {
        return this.remainingTicks / 20.0D;
    }
}
