package MengySmod.joyevents.client;

import MengySmod.joyevents.Joyevents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 客户端（游戏总线）：退出服务器时清掉 HUD 状态，避免残留显示。 */
@EventBusSubscriber(modid = Joyevents.MODID, value = Dist.CLIENT)
public final class ClientConnectionHandler {
    private ClientConnectionHandler() {}

    @SubscribeEvent
    static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHudState.clear();
    }
}
