package MengySmod.joyevents.client;

import java.util.Locale;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.config.JoyConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 左上角 HUD：显示各玩法的倒计时、位置互换目的地、生命池余量。
 *
 * <p>所有开关都在 {@code joyevents-client.toml} 里，属于纯客户端显示偏好。
 * 只有服务端把某个玩法打开并同步了状态，这里才会出现对应行。</p>
 */
public final class GameplayHudOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Joyevents.MODID, "gameplay_hud");

    private static final GameplayHudOverlay INSTANCE = new GameplayHudOverlay();

    private static final int COLOR_TIME = 0xFFFFD54F;
    private static final int COLOR_DETAIL = 0xFFA0A0A0;
    private static final int COLOR_POOL_GOOD = 0xFF66BB6A;
    private static final int COLOR_POOL_WARN = 0xFFFFB74D;
    private static final int COLOR_POOL_BAD = 0xFFEF5350;

    private static final int LEFT = 6;
    private static final int TOP = 6;
    private static final int LINE_HEIGHT = 10;

    private GameplayHudOverlay() {}

    public static GameplayHudOverlay instance() {
        return INSTANCE;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // F1 隐藏界面、F3 调试界面时不叠加，避免和调试文本糊在一起
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        int y = TOP;

        // ---- 位置互换 ----
        ClientHudState.Entry swap = ClientHudState.get("position_swap");
        if (swap != null) {
            if (JoyConfig.hudSwapShowCountdown) {
                graphics.drawString(minecraft.font,
                        Component.translatable("joyevents.hud.position_swap.countdown", formatTime(swap.remainingSeconds())),
                        LEFT, y, COLOR_TIME, true);
                y += LINE_HEIGHT;
            }
            if (JoyConfig.hudSwapShowDestination && swap.hasDetail()) {
                graphics.drawString(minecraft.font,
                        Component.translatable("joyevents.hud.position_swap.destination",
                                swap.detailLabel(),
                                formatCoord(swap.detailX()),
                                formatCoord(swap.detailY()),
                                formatCoord(swap.detailZ())),
                        LEFT, y, COLOR_DETAIL, true);
                y += LINE_HEIGHT;
            }
        }

        // ---- 随机传送 ----
        ClientHudState.Entry teleport = ClientHudState.get("random_teleport");
        if (teleport != null && JoyConfig.hudTeleportShowCountdown) {
            graphics.drawString(minecraft.font,
                    Component.translatable("joyevents.hud.random_teleport.countdown", formatTime(teleport.remainingSeconds())),
                    LEFT, y, COLOR_TIME, true);
            y += LINE_HEIGHT;
        }

        // ---- 生命池 ----
        ClientHudState.Entry shared = ClientHudState.get("shared_health");
        if (shared != null && shared.hasPool() && JoyConfig.hudPoolShowStatus) {
            double ratio = shared.poolMax() <= 0 ? 0 : Math.clamp(shared.poolCurrent() / shared.poolMax(), 0.0D, 1.0D);
            int color = ratio > 0.5D ? COLOR_POOL_GOOD : (ratio > 0.2D ? COLOR_POOL_WARN : COLOR_POOL_BAD);
            graphics.drawString(minecraft.font,
                    Component.translatable("joyevents.hud.shared_health.pool",
                            formatNumber(shared.poolCurrent()),
                            formatNumber(shared.poolMax()),
                            Integer.toString((int) Math.round(ratio * 100.0D))),
                    LEFT, y, color, true);
            y += LINE_HEIGHT;
        }
    }

    private static String formatTime(double seconds) {
        int total = Math.max(0, (int) Math.ceil(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
