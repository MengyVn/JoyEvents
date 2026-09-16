package MengySmod.joyevents.client;

import MengySmod.joyevents.Joyevents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

import java.util.List;

import javax.annotation.Nullable;

/**
 * 自定义配置入口：<b>只在“单人世界已经开放到局域网”时使用</b>。
 *
 * <p>NeoForge 内置配置界面在这种情形下会把 “Server Config” 那个入口按钮直接禁用
 * （{@code ConfigurationScreen#addOptions} 里 {@code btn.active = false}，理由是局域网里其他玩家
 * 的客户端不该改规则）。但此时你仍然是这台世界的服主、文件也归你所有，改配置是完全合理的需求。</p>
 *
 * <p>而内置的<b>分节界面</b>（{@code ConfigurationScreen.ConfigurationSectionScreen}）内部并不做这个检查，
 * 它的保存走的是 {@code context.modSpec.save()}，也不依赖顶层界面的重启确认流程。因此这里只做一件事：
 * 换成我们自己的入口，直接打开分节界面，从而让局域网下改配置成为可能。参数控件仍然由 NeoForge 提供，
 * 不存在重复实现一套 UI 的问题。</p>
 *
 * <p><b>远端服务器不在本界面范围内</b>：那里客户端改服务端配置既不会生效（改的是自己本地的副本），
 * 也不应该被允许，所以仍交给内置界面按只读处理。远端请用 {@code /joyevents} 命令（需要 OP）或改服务端文件。</p>
 */
public final class LocalServerConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLOR_HINT = 0xFFA0A0A0;

    private final ModContainer container;
    private final Screen parent;

    public LocalServerConfigScreen(ModContainer container, Screen parent) {
        super(Component.translatable("joyevents.config_screen.title"));
        this.container = container;
        this.parent = parent;
    }

    /** 纯判定逻辑，便于离线断言：是否需要用本界面绕开 NeoForge 的局域网只读限制。 */
    public static boolean needsBypass(boolean hasSingleplayerServer, boolean lanPublished) {
        return hasSingleplayerServer && lanPublished;
    }

    /** 当前客户端是否处于“单人世界 + 已开放局域网”的状态。 */
    public static boolean shouldBypassLanRestriction() {
        return needsBypass(
                Minecraft.getInstance().hasSingleplayerServer(),
                LocalServerConfigScreen.lanPublished());
    }

    private static boolean lanPublished() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPublished();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 3;

        addRenderableWidget(Button.builder(Component.translatable("joyevents.config_screen.server"),
                        button -> openSection(ModConfig.Type.SERVER))
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("joyevents.config_screen.server.tooltip")))
                .build());
        y += BUTTON_HEIGHT + 6;

        addRenderableWidget(Button.builder(Component.translatable("joyevents.config_screen.client"),
                        button -> openSection(ModConfig.Type.CLIENT))
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("joyevents.config_screen.client.tooltip")))
                .build());
        y += BUTTON_HEIGHT + 14;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /** 直接打开 NeoForge 的分节界面（绕开被禁用的类型入口按钮）。 */
    private void openSection(ModConfig.Type type) {
        ModConfig config = findConfig(type);
        if (config == null || !isLoaded(config)) {
            return;
        }
        this.minecraft.setScreen(new ConfigurationScreen.ConfigurationSectionScreen(
                this, type, config, Component.translatable("joyevents.config_screen.section." + type.name().toLowerCase(java.util.Locale.ROOT))));
    }

    @Nullable
    private ModConfig findConfig(ModConfig.Type type) {
        List<ModConfig> configs = ModConfigs.getModConfigs(Joyevents.MODID);
        for (ModConfig config : configs) {
            if (config.getType() == type) {
                return config;
            }
        }
        return null;
    }

    private static boolean isLoaded(ModConfig config) {
        return config.getSpec() instanceof net.neoforged.neoforge.common.ModConfigSpec spec && spec.isLoaded();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, this.height / 3 - 34, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("joyevents.config_screen.hint"),
                centerX, this.height / 3 - 20, COLOR_HINT);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
