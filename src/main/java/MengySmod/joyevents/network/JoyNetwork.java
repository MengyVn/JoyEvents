package MengySmod.joyevents.network;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.client.ClientPayloadHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册与发送。
 *
 * <p>本模组只有一个 playToClient 包（HUD 同步）。客户端处理器写在一个客户端专用类里，
 * 服务端永远不会加载那个类，因此专用服务器上不会因为缺少客户端类而崩溃。</p>
 */
@EventBusSubscriber(modid = Joyevents.MODID)
public final class JoyNetwork {
    private JoyNetwork() {}

    /** 网络协议版本：客户端与服务端不一致时会被 NeoForge 拒绝连接，便于排查版本错配。 */
    public static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                GameplayHudPayload.TYPE,
                GameplayHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleHud(payload)));
    }

    /** 向单个玩家同步 HUD 状态。 */
    public static void sendHud(ServerPlayer player, GameplayHudPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
