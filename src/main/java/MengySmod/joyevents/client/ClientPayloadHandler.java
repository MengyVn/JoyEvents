package MengySmod.joyevents.client;

import MengySmod.joyevents.network.GameplayHudPayload;

/**
 * 客户端包处理入口。
 *
 * <p>刻意做成独立类：服务端上的 {@code playToClient} 处理器永远不会被调用，
 * 于是这个引用了客户端状态的类在专用服务器上也不会被加载。</p>
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleHud(GameplayHudPayload payload) {
        ClientHudState.update(payload);
    }
}
