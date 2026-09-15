package MengySmod.joyevents.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.Gameplay;
import MengySmod.joyevents.gameplay.GameplayManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 游戏内管理命令。
 *
 * <p>存在的意义有两个：一是让服主在没有图形界面的专用服务器上也能开关玩法；
 * 二是不必等 3 分钟就能立刻验证玩法，{@code /joyevents trigger <玩法>} 是开发与排查的主要入口。</p>
 */
@EventBusSubscriber(modid = Joyevents.MODID)
public final class JoyEventsCommand {
    private JoyEventsCommand() {}

    @SubscribeEvent
    static void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("joyevents")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(JoyEventsCommand::status))
                .then(Commands.literal("enable")
                        .then(gameplayArgument().executes(context -> setEnabled(context, true))))
                .then(Commands.literal("disable")
                        .then(gameplayArgument().executes(context -> setEnabled(context, false))))
                .then(Commands.literal("trigger")
                        .then(gameplayArgument().executes(JoyEventsCommand::trigger))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> gameplayArgument() {
        return Commands.argument("gameplay", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (Gameplay gameplay : GameplayManager.all()) {
                        builder.suggest(gameplay.id());
                    }
                    return builder.buildFuture();
                });
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable("joyevents.command.status.header"), false);
        for (Gameplay gameplay : GameplayManager.all()) {
            Component name = Component.translatable(gameplay.nameKey());
            boolean enabled = gameplay.isEnabled();
            Component state = Component.translatable(enabled
                    ? "joyevents.command.state.enabled"
                    : "joyevents.command.state.disabled");
            Component detail;
            if (!enabled) {
                detail = Component.translatable(gameplay.descriptionKey());
            } else {
                Component timing = gameplay.periodic()
                        ? Component.translatable("joyevents.command.detail.next",
                                GameplayManager.remainingSeconds(gameplay.id()))
                        : Component.translatable("joyevents.command.detail.always_on");
                detail = timing.copy()
                        .append(Component.literal(" · "))
                        .append(gameplay.statusDetail(server));
            }
            source.sendSuccess(() -> Component.translatable("joyevents.command.status.line", name, state, detail), false);
        }
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String id = StringArgumentType.getString(context, "gameplay");
        Gameplay gameplay = GameplayManager.byId(id);
        if (gameplay == null) {
            context.getSource().sendFailure(Component.translatable("joyevents.command.unknown", id));
            return 0;
        }
        if (!JoyConfig.writeEnabled(id, enabled)) {
            context.getSource().sendFailure(Component.translatable("joyevents.command.write_failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("joyevents.command.set",
                Component.translatable(gameplay.nameKey()),
                Component.translatable(enabled
                        ? "joyevents.command.state.enabled"
                        : "joyevents.command.state.disabled")), true);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "gameplay");
        Gameplay gameplay = GameplayManager.byId(id);
        if (gameplay == null) {
            context.getSource().sendFailure(Component.translatable("joyevents.command.unknown", id));
            return 0;
        }
        if (!gameplay.isEnabled()) {
            context.getSource().sendFailure(Component.translatable("joyevents.command.trigger_disabled",
                    Component.translatable(gameplay.nameKey())));
            return 0;
        }
        GameplayManager.triggerNow(context.getSource().getServer(), id);
        context.getSource().sendSuccess(() -> Component.translatable("joyevents.command.triggered",
                Component.translatable(gameplay.nameKey())), true);
        return 1;
    }
}
