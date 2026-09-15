package MengySmod.joyevents;

import com.mojang.logging.LogUtils;

import MengySmod.joyevents.client.JoyEventsClient;
import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.GameplayManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * JoyEvents：把一堆趣味玩法打包在一起，并且每个玩法都能在配置界面里一键开关、调参、看说明。
 *
 * <p>本类只做三件事：注册配置、注册玩法、把服务端 tick 交给 {@link GameplayManager}。
 * 具体玩法逻辑都在 {@code gameplay.impl} 包内，新增玩法只需要写一个 {@code Gameplay} 子类
 * 并在 {@link GameplayManager#bootstrap()} 里注册。</p>
 */
@Mod(Joyevents.MODID)
public class Joyevents {
    public static final String MODID = "joyevents";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Joyevents(IEventBus modEventBus, ModContainer modContainer) {
        // 玩法规则（SERVER，服务端权威并自动同步给客户端；默认存放于 <实例>/config/）
        modContainer.registerConfig(ModConfig.Type.SERVER, JoyConfig.SERVER_SPEC);
        // 客户端显示偏好（CLIENT）
        modContainer.registerConfig(ModConfig.Type.CLIENT, JoyConfig.CLIENT_SPEC);

        // 注册内置配置界面。NeoForge 21.1 不会自动生成界面，缺了这一步模组列表里的“配置”按钮是灰的。
        // 只在客户端注册，且调用被放进仅客户端的类，避免专用服务器加载客户端类。
        if (FMLEnvironment.dist.isClient()) {
            JoyEventsClient.registerConfigScreen(modContainer);
        }

        // 注册玩法。全部玩法默认关闭，是否启用完全由配置决定。
        GameplayManager.bootstrap();

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onServerStarted(final ServerStartedEvent event) {
        // 配置可能在世界加载后才生效，这里再读一次，保证缓存字段一定是最新值
        JoyConfig.refreshServer();
        LOGGER.info("JoyEvents 已就绪：{} 个玩法已注册（默认全部关闭）", GameplayManager.all().size());
    }

    private void onServerTick(final ServerTickEvent.Post event) {
        GameplayManager.serverTick(event.getServer());
    }

    private void onServerStopped(final ServerStoppedEvent event) {
        // 服务端停止时回滚所有玩法的副作用（恢复血量、清空血池等）
        GameplayManager.onServerStopped(event.getServer());
    }
}
