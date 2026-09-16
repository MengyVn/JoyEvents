package MengySmod.joyevents.client;

import MengySmod.joyevents.Joyevents;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** 客户端（MOD 总线）注册：配置界面入口 + HUD 图层。 */
@EventBusSubscriber(modid = Joyevents.MODID, value = Dist.CLIENT)
public final class JoyEventsClient {
    private JoyEventsClient() {}

    /**
     * 注册 NeoForge 内置配置界面。
     *
     * <p><b>这一步不能省。</b>NeoForge 21.1 不会自动为“有配置的模组”生成界面：
     * 模组列表里的“配置”按钮是否可点，取决于 {@code IConfigScreenFactory.getForMod(...)}
     * 能否取到扩展点，而它只查模组自己注册的扩展点
     * （{@code ModListScreen} 中 {@code configButton.active = IConfigScreenFactory.getForMod(mod).isPresent()}）。
     * 只注册 ModConfigSpec 而不注册这个扩展点，配置按钮就是灰的。</p>
     *
     * <p>本方法只在客户端被调用（调用方用 Dist 判断挡住服务端），
     * 因此这里引用的客户端类在专用服务器上不会被加载。</p>
     */
    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (ModContainer mod, Screen parent) -> createConfigScreen(container, parent));
        // 明确打一条日志：这一行是“配置按钮为什么是灰的”这类问题的唯一线索，
        // 判定方式与 ModListScreen 启用按钮时用的调用链一致。
        Joyevents.LOGGER.info("配置界面扩展点注册结果: getCustomExtension(IConfigScreenFactory) = {}",
                container.getCustomExtension(IConfigScreenFactory.class).isPresent());
    }

    /**
     * 选择使用哪个配置界面。
     *
     * <p>正常情况下直接用 NeoForge 内置界面。只有在“单人世界已开放局域网”时，内置界面会把
     * Server Config 的入口按钮禁用（{@code btn.active = false}），而分节界面内部并无此限制，
     * 因此这时改用 {@link LocalServerConfigScreen} 直接进入分节界面，让服主仍能改玩法配置。</p>
     *
     * <p>远端服务器不改动：那里客户端改服务端配置不生效也不该被允许，仍由内置界面按只读处理。</p>
     */
    private static Screen createConfigScreen(ModContainer container, Screen parent) {
        if (LocalServerConfigScreen.shouldBypassLanRestriction()) {
            Joyevents.LOGGER.info("检测到单人世界已开放局域网，使用 JoyEvents 自定义配置入口（服务端配置仍可编辑）");
            return new LocalServerConfigScreen(container, parent);
        }
        return new ConfigurationScreen(container, parent);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(GameplayHudOverlay.ID, GameplayHudOverlay.instance());
    }
}
