package MengySmod.joyevents.config;

import java.util.List;
import java.util.Locale;

import MengySmod.joyevents.Joyevents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * 本模组的全部配置。
 *
 * <p>分为两份配置文件：</p>
 * <ul>
 *   <li>{@code joyevents-server.toml}（SERVER 类型）：玩法规则与参数。由服务端权威决定并自动同步给客户端，
 *       因此它才是“玩法开关”的唯一真实来源。文件位置：默认在 {@code <实例>/config/joyevents-server.toml}
 *       （单人游戏所有存档共用同一份）；若把同名文件放进某存档的 {@code <存档>/serverconfig/} 下，
 *       该存档会用那份覆盖实例级的配置（NeoForge 的服务器配置覆盖机制，见 run/saves/&lt;世界&gt;/serverconfig/readme.txt）。</li>
 *   <li>{@code joyevents-client.toml}（CLIENT 类型）：纯客户端显示偏好（HUD 是否显示倒计时等）。</li>
 * </ul>
 *
 * <p>界面呈现：NeoForge 内置配置界面会读取下面的注释作为鼠标悬浮说明，
 * 因此每个条目的注释都写成了“这个开关/参数到底改变什么”的完整解释。</p>
 */
@EventBusSubscriber(modid = Joyevents.MODID)
public final class JoyConfig {
    private JoyConfig() {}

    /** 共享生命的两种模式。 */
    public enum ShareMode implements TranslatableEnum {
        /** A 模式：伤害共享（一人挨打，全员受伤）。 */
        DAMAGE_SHARE,
        /** B 模式：生命池（所有人共用一个总血量与总饱食度）。 */
        POOL;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("joyevents.config.share_mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    /** 随机传送的维度策略。 */
    public enum DimensionMode implements TranslatableEnum {
        /** 保持玩家当前所在维度，只在当前位置附近的半径内随机。 */
        KEEP,
        /** 把玩家传送到指定维度（需要落点安全检查）。 */
        FIXED;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("joyevents.config.dimension_mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    /**
     * 物品黑名单默认值：正常情况下看不到、也不该出现在背包里的管理/调试类物品。
     * 末地传送门框架、龙蛋、基岩等“稀有但见得到”的物品刻意不列入。
     */
    private static final List<String> DEFAULT_LOOT_BLACKLIST = List.of(
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:command_block_minecart",
            "minecraft:barrier",
            "minecraft:light",
            "minecraft:structure_void",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:debug_stick",
            "minecraft:knowledge_book");

    /** 给条目挂上中文注释（同时作为界面悬浮说明）与翻译键。 */
    private static ModConfigSpec.Builder entry(ModConfigSpec.Builder builder, String path, String comment) {
        return builder.comment(comment).translation("joyevents.configuration." + path);
    }

    // ------------------------------------------------------------------
    // 玩法一：位置互换
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue POSITION_SWAP_ENABLED;
    private static final ModConfigSpec.IntValue POSITION_SWAP_INTERVAL;
    private static final ModConfigSpec.BooleanValue POSITION_SWAP_ANNOUNCE;

    // ------------------------------------------------------------------
    // 玩法二：随机传送
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue RANDOM_TELEPORT_ENABLED;
    private static final ModConfigSpec.IntValue RANDOM_TELEPORT_INTERVAL;
    private static final ModConfigSpec.IntValue RANDOM_TELEPORT_RADIUS;
    private static final ModConfigSpec.EnumValue<DimensionMode> RANDOM_TELEPORT_DIMENSION_MODE;
    private static final ModConfigSpec.ConfigValue<String> RANDOM_TELEPORT_FIXED_DIMENSION;
    private static final ModConfigSpec.BooleanValue RANDOM_TELEPORT_SAFE_LANDING;
    private static final ModConfigSpec.IntValue RANDOM_TELEPORT_MAX_ATTEMPTS;
    private static final ModConfigSpec.BooleanValue RANDOM_TELEPORT_ANNOUNCE;

    // ------------------------------------------------------------------
    // 玩法三：共享生命
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue SHARED_HEALTH_ENABLED;
    private static final ModConfigSpec.EnumValue<ShareMode> SHARED_HEALTH_MODE;
    private static final ModConfigSpec.BooleanValue SHARED_HEALTH_INCLUDE_CREATIVE;
    private static final ModConfigSpec.BooleanValue SHARED_HEALTH_ANNOUNCE;
    private static final ModConfigSpec.DoubleValue SHARED_HEALTH_DAMAGE_RATIO;
    private static final ModConfigSpec.DoubleValue POOL_MAX_HEALTH;
    private static final ModConfigSpec.DoubleValue POOL_MAX_FOOD;
    private static final ModConfigSpec.BooleanValue POOL_ALLOW_HEALING;
    private static final ModConfigSpec.IntValue POOL_DEATH_RESET_PERCENT;

    // ------------------------------------------------------------------
    // 物品赌博
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue LOOT_GAMBLE_ENABLED;
    private static final ModConfigSpec.BooleanValue LOOT_GAMBLE_NOTIFY;
    private static final ModConfigSpec.IntValue LOOT_GAMBLE_COOLDOWN;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_BLOCK_BREAK;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_EAT;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_JUMP;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_HURT;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_KILL;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_CRAFT;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_PICKUP;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_USE_ITEM;
    private static final ModConfigSpec.BooleanValue LOOT_TRIGGER_TIMER;
    private static final ModConfigSpec.IntValue LOOT_TIMER_INTERVAL;
    private static final ModConfigSpec.DoubleValue LOOT_GAIN_CHANCE;
    private static final ModConfigSpec.DoubleValue LOOT_LOSS_CHANCE;
    private static final ModConfigSpec.IntValue LOOT_GAIN_MIN;
    private static final ModConfigSpec.IntValue LOOT_GAIN_MAX;
    private static final ModConfigSpec.IntValue LOOT_REPLACE_MIN;
    private static final ModConfigSpec.IntValue LOOT_REPLACE_MAX;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_ITEM_BLACKLIST;
    private static final ModConfigSpec.DoubleValue LOOT_GAIN_DEFAULT_WEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_GAIN_POOL;
    private static final ModConfigSpec.DoubleValue LOOT_REPLACE_DEFAULT_WEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_REPLACE_POOL;
    private static final ModConfigSpec.DoubleValue LOOT_TARGET_DEFAULT_WEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_TARGET_WEIGHTS;

    // ------------------------------------------------------------------
    // 客户端 HUD
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue HUD_SWAP_COUNTDOWN;
    private static final ModConfigSpec.BooleanValue HUD_SWAP_DESTINATION;
    private static final ModConfigSpec.BooleanValue HUD_TELEPORT_COUNTDOWN;
    private static final ModConfigSpec.BooleanValue HUD_POOL_STATUS;
    private static final ModConfigSpec.BooleanValue HUD_LOOT_COUNTDOWN;

    static {
        // ================= 位置互换 =================
        entry(SERVER, "position_swap",
                "【位置互换】每隔一段时间，把每名玩家送到“离他最近的那名玩家当前所在的位置”，全体同时生效。\n"
                        + "按 need.md 约定：只计算落点快照，不强行两两绑定；因此三人同维度时，可能出现两人被送到同一坐标。\n"
                        + "默认关闭，开启后以 interval_seconds 为周期自动执行。")
                .push("position_swap");

        POSITION_SWAP_ENABLED = entry(SERVER, "position_swap.enabled",
                "是否启用【位置互换】。默认 false（所有玩法默认关闭）。\n"
                        + "启用后每隔 interval_seconds 秒触发一次，可用 /joyevents trigger position_swap 立刻手动触发。")
                .define("enabled", false);

        POSITION_SWAP_INTERVAL = entry(SERVER, "position_swap.interval_seconds",
                "【位置互换】的触发周期，单位：秒，默认 180（3 分钟）。取值范围 5 ~ 86400。\n"
                        + "修改后倒计时会立即按新周期重置。")
                .defineInRange("interval_seconds", 180, 5, 86400);

        POSITION_SWAP_ANNOUNCE = entry(SERVER, "position_swap.announce",
                "触发时是否在聊天栏向全体玩家提示。默认 true。")
                .define("announce", true);

        SERVER.pop();

        // ================= 随机传送 =================
        entry(SERVER, "random_teleport",
                "【随机传送】每隔一段时间，把每名玩家随机传送到他当前位置附近的某个安全落点。\n"
                        + "默认关闭。传送原点固定为玩家自身当前位置，半径、维度策略均可配置。")
                .push("random_teleport");

        RANDOM_TELEPORT_ENABLED = entry(SERVER, "random_teleport.enabled",
                "是否启用【随机传送】。默认 false。\n"
                        + "启用后每隔 interval_seconds 秒触发一次，可用 /joyevents trigger random_teleport 立刻手动触发。")
                .define("enabled", false);

        RANDOM_TELEPORT_INTERVAL = entry(SERVER, "random_teleport.interval_seconds",
                "【随机传送】的触发周期，单位：秒，默认 180（3 分钟）。取值范围 5 ~ 86400。")
                .defineInRange("interval_seconds", 180, 5, 86400);

        RANDOM_TELEPORT_RADIUS = entry(SERVER, "random_teleport.radius",
                "【随机传送】的随机半径，单位：方块，默认 500。取值范围 16 ~ 30000。\n"
                        + "以玩家当前位置为圆心，在半径内均匀取点（越靠近圆心概率密度越高，是圆面均匀分布而非圆环）。")
                .defineInRange("radius", 500, 16, 30000);

        RANDOM_TELEPORT_DIMENSION_MODE = entry(SERVER, "random_teleport.dimension_mode",
                "【随机传送】的维度策略，默认 KEEP（保持当前维度）。\n"
                        + "KEEP = 玩家留在自己当前所在维度，只随机水平坐标；\n"
                        + "FIXED = 把玩家送到 fixed_dimension 指定的维度（跨维度传送，落点仍为安全搜索）。")
                .defineEnum("dimension_mode", DimensionMode.KEEP);

        RANDOM_TELEPORT_FIXED_DIMENSION = entry(SERVER, "random_teleport.fixed_dimension",
                "当 dimension_mode = FIXED 时使用的目标维度，默认 minecraft:overworld。\n"
                        + "填写维度 ID，例如 minecraft:overworld / minecraft:the_nether / minecraft:the_end；\n"
                        + "若填写的维度不存在（例如模组维度未安装），该次传送会被安全跳过并记录日志。")
                .define("fixed_dimension", "minecraft:overworld", JoyConfig::isValidDimensionId);

        RANDOM_TELEPORT_SAFE_LANDING = entry(SERVER, "random_teleport.safe_landing",
                "是否只寻找安全落点，默认 true。\n"
                        + "开启时会避开岩浆、火、仙人掌、细雪、虚空、下界基岩顶等危险位置，多次采样失败则跳过该玩家（宁可不传，也不埋人）。\n"
                        + "关闭时会直接把玩家放到该坐标的地表高度，可能落入岩浆或虚空，请谨慎使用。")
                .define("safe_landing", true);

        RANDOM_TELEPORT_MAX_ATTEMPTS = entry(SERVER, "random_teleport.max_attempts",
                "【随机传送】每个玩家最多尝试多少次采样来寻找安全落点，默认 24。取值范围 1 ~ 256。\n"
                        + "次数越多越容易找到安全点，但会加载更多区块。")
                .defineInRange("max_attempts", 24, 1, 256);

        RANDOM_TELEPORT_ANNOUNCE = entry(SERVER, "random_teleport.announce",
                "触发时是否在聊天栏向全体玩家提示。默认 true。")
                .define("announce", true);

        SERVER.pop();

        // ================= 共享生命 =================
        entry(SERVER, "shared_health",
                "【共享生命】两种模式二选一：\n"
                        + "A 模式 DAMAGE_SHARE：只共享伤害。任意玩家受伤，其他玩家按配置比例一起受伤。\n"
                        + "B 模式 POOL：所有人共用同一条生命条与同一条饥饿条，一人掉血全员掉血，一人死亡全员死亡。\n"
                        + "默认关闭。旁观者永远不参与；创造模式玩家默认不参与（可在 include_creative 调整）。")
                .push("shared_health");

        SHARED_HEALTH_ENABLED = entry(SERVER, "shared_health.enabled",
                "是否启用【共享生命】。默认 false。\n"
                        + "关闭时会立即把全体参与者的血量与饱食度恢复为各自满值，避免关掉玩法后全员永久残血。")
                .define("enabled", false);

        SHARED_HEALTH_MODE = entry(SERVER, "shared_health.mode",
                "【共享生命】的模式，默认 DAMAGE_SHARE（A 模式）。\n"
                        + "DAMAGE_SHARE = A 模式，只共享伤害（不共享回血）；POOL = B 模式，共用一条命。\n"
                        + "切换模式时会自动清理上一模式的残留状态（残血恢复、共享条重置等）。")
                .defineEnum("mode", ShareMode.DAMAGE_SHARE);

        SHARED_HEALTH_INCLUDE_CREATIVE = entry(SERVER, "shared_health.include_creative",
                "创造模式玩家是否参与共享生命，默认 false。\n"
                        + "创造玩家通常免疫伤害，若让其参与会导致“挨打不掉血却要分担伤害”的矛盾，因此默认排除。")
                .define("include_creative", false);

        SHARED_HEALTH_ANNOUNCE = entry(SERVER, "shared_health.announce",
                "B 模式共享条被耗尽等关键事件是否在聊天栏提示（例如“生命池已耗尽，全员倒下”）。默认 true。")
                .define("announce", true);

        // -------- A 模式参数 --------
        entry(SERVER, "shared_health.damage_share",
                "【共享生命·A 模式】伤害共享参数。仅在 mode = DAMAGE_SHARE 时生效。\n"
                        + "机制：挨打者先按原版结算（含自身护甲、抗性、附魔），然后把这个“实际承受的伤害”按下面的比例\n"
                        + "传递给其他玩家；其他玩家收到后仍会再经过自己的护甲结算，所以护甲在这个模式下依然有用。")
                .push("damage_share");

        SHARED_HEALTH_DAMAGE_RATIO = entry(SERVER, "shared_health.damage_share.damage_ratio_percent",
                "【A 模式】其他玩家承受的伤害比例，单位：百分比，默认 100。取值范围 0 ~ 500。\n"
                        + "100 = 其他玩家同步受到等量伤害（例：挨打者实际掉 6 点，其他人各掉 6 点）；\n"
                        + "50  = 其他玩家各掉一半（例：挨打者实际掉 6 点，其他人各掉 3 点）；\n"
                        + "0   = 等于关闭伤害共享。\n"
                        + "注意：这里用的是“挨打者护甲结算之后的实际伤害”，所以护甲高的人挨打，全队也跟着少掉血。")
                .defineInRange("damage_ratio_percent", 100.0D, 0.0D, 500.0D);

        SERVER.pop();

        // -------- B 模式参数 --------
        entry(SERVER, "shared_health.bar",
                "【共享生命·B 模式】共用一条命。仅在 mode = POOL 时生效。\n"
                        + "实现方式：共享条是唯一真实数值，所有人都显示同一条血条与同一条饥饿条。\n"
                        + "默认上限 20 点 = 一条原版血条（10 颗心），因此血条上显示的数字就是共享条的数值本身。\n"
                        + "例：3 人共用一条 20 点的血条，共享条剩 14 时，三个人看到的都是 7 颗心。\n"
                        + "注意：总量不随人数变化，所以人越多每个人分摊到的越少、整体越脆。")
                .push("bar");

        POOL_MAX_HEALTH = entry(SERVER, "shared_health.bar.max_health",
                "【B 模式】共享血条的上限，默认 20.0（= 一条原版血条 = 10 颗心），不随人数变化。取值范围 1 ~ 1024。\n"
                        + "每人血条显示 = 自己的最大生命 × (共享条当前值 / 该上限)，因此默认配置下（所有人 20 点血）\n"
                        + "血条上显示的就是共享条数值本身。\n"
                        + "改大可以调低难度（例如 40 = 两条血条的量），但此时血条显示的是比例而非绝对值。")
                .defineInRange("max_health", 20.0D, 1.0D, 1024.0D);

        POOL_MAX_FOOD = entry(SERVER, "shared_health.bar.max_food",
                "【B 模式】共享饥饿条的上限，默认 20.0（= 一条原版饥饿条 = 10 个鸡腿），不随人数变化。\n"
                        + "取值范围 1 ~ 20：原版饥饿值上限就是 20，写更大的值饥饿条会显示不出来。")
                .defineInRange("max_food", 20.0D, 1.0D, 20.0D);

        POOL_ALLOW_HEALING = entry(SERVER, "shared_health.bar.allow_healing",
                "【B 模式】是否允许回血注入共享条，默认 true。\n"
                        + "开启时：自然回血、生命恢复效果、治疗药水、金苹果等都会抬高共享条。\n"
                        + "回血速率按“一个身体”计算：因为每个人都会各自触发一次自然回血，若不做归一化，\n"
                        + "人越多共享条自愈得越快（这正是旧版本的 bug）。归一化规则见下方说明。\n"
                        + "关闭后共享条只减不增，属于硬核玩法。")
                .define("allow_healing", true);

        POOL_DEATH_RESET_PERCENT = entry(SERVER, "shared_health.bar.death_reset_percent",
                "【B 模式】全员死亡后共享条重置的百分比，默认 100（即满条复活）。取值范围 1 ~ 100。\n"
                        + "该值不允许为 0，否则复活瞬间全员会立刻再次死亡，形成死亡循环。")
                .defineInRange("death_reset_percent", 100, 1, 100);

        SERVER.pop();
        SERVER.pop();

        // ================= 物品赌博 =================
        entry(SERVER, "loot_gamble",
                "【物品赌博】每次触发（挖掘方块、吃东西、跳跃、受伤……）时，按概率发生下面其中一件事：\n"
                        + "获得：随机获得某个物品的若干数量（砍一块橡木可能得到 5 个钻石）；\n"
                        + "丢失：随机丢掉背包里的一整格物品（那一格里有多少就全没多少，例如几十个铁锭一并消失）；\n"
                        + "替换：把背包里随机一格的内容整体换成另一个完全随机的物品与随机数量\n"
                        + "（可能是 8 个工作台，可能是 42 个下界合金块，也可能只是一个木制按钮）。\n"
                        + "默认关闭。旁观者永远不触发。")
                .push("loot_gamble");

        LOOT_GAMBLE_ENABLED = entry(SERVER, "loot_gamble.enabled",
                "是否启用【物品赌博】。默认 false（所有玩法默认关闭）。")
                .define("enabled", false);

        LOOT_GAMBLE_NOTIFY = entry(SERVER, "loot_gamble.notify_player",
                "发生获得/丢失/替换时，是否在玩家动作栏（快捷栏上方）提示具体内容。默认 true。\n"
                        + "建议保持开启：物品凭空消失时如果不提示，玩家会以为是 bug。")
                .define("notify_player", true);

        LOOT_GAMBLE_COOLDOWN = entry(SERVER, "loot_gamble.cooldown_ticks",
                "触发冷却，单位：tick（20 tick = 1 秒），默认 20（即 1 秒），按玩家各自独立计算。取值范围 0 ~ 1200。\n"
                        + "设为 0 表示完全没有冷却——跳跃、右键这类高频动作会瞬间刷屏，请谨慎。\n"
                        + "注意：倒计时触发不受这个冷却影响（它的间隔本身就限定了频率），也不会占用冷却时间。")
                .defineInRange("cooldown_ticks", 20, 0, 1200);

        LOOT_TIMER_INTERVAL = entry(SERVER, "loot_gamble.timer_interval_seconds",
                "倒计时触发的间隔，单位：秒，默认 180（3 分钟）。取值范围 5 ~ 86400。\n"
                        + "仅在 triggers.timer 开启时生效；修改后倒计时会立即按新间隔重置。")
                .defineInRange("timer_interval_seconds", 180, 5, 86400);

        // -------- 触发方式 --------
        entry(SERVER, "loot_gamble.triggers",
                "【触发方式】每种都可以单独开关，并且可以同时打开多种（叠加生效）。\n"
                        + "默认全部关闭：开启玩法后请至少打开一种，否则永远不会触发\n"
                        + "（用 /joyevents status 可以看到“未开启任何触发方式”的提示）。")
                .push("triggers");

        LOOT_TRIGGER_BLOCK_BREAK = entry(SERVER, "loot_gamble.triggers.block_break",
                "挖掘 / 破坏方块时触发。默认 false（所有触发方式默认关闭，需要哪一个就打开哪一个）。")
                .define("block_break", false);

        LOOT_TRIGGER_EAT = entry(SERVER, "loot_gamble.triggers.eat",
                "吃东西时触发（吃完一份食物结算一次）。默认 false。")
                .define("eat", false);

        LOOT_TRIGGER_JUMP = entry(SERVER, "loot_gamble.triggers.jump",
                "跳跃时触发。默认 false：跳跃频率极高，开启后请确认冷却设置合适。")
                .define("jump", false);

        LOOT_TRIGGER_HURT = entry(SERVER, "loot_gamble.triggers.hurt",
                "受伤时触发（实际掉血才触发，被完全挡下或伤害为 0 不算）。默认 false。")
                .define("hurt", false);

        LOOT_TRIGGER_KILL = entry(SERVER, "loot_gamble.triggers.kill_entity",
                "击杀生物时触发（由玩家造成击杀才算）。默认 false。")
                .define("kill_entity", false);

        LOOT_TRIGGER_CRAFT = entry(SERVER, "loot_gamble.triggers.craft",
                "合成物品时触发（每完成一次合成结算一次）。默认 false。")
                .define("craft", false);

        LOOT_TRIGGER_PICKUP = entry(SERVER, "loot_gamble.triggers.pickup",
                "捡起地上的物品时触发。默认 false。")
                .define("pickup", false);

        LOOT_TRIGGER_USE_ITEM = entry(SERVER, "loot_gamble.triggers.use_item",
                "使用物品（右键）时触发。默认 false：右键频率很高，开启后请确认冷却设置合适。")
                .define("use_item", false);

        LOOT_TRIGGER_TIMER = entry(SERVER, "loot_gamble.triggers.timer",
                "倒计时触发：每隔一段时间自动给所有在线玩家各结算一次，不需要玩家做任何动作。默认 false。\n"
                        + "这是唯一会影响到“什么都没做”的玩家的触发方式，因此默认关闭。\n"
                        + "开启后屏幕上会出现独立倒计时（见客户端配置 hud.loot_gamble.show_countdown），\n"
                        + "它与位置互换、随机传送的倒计时互不影响，显示时会依次向下排布，不会互相覆盖。")
                .define("timer", false);

        SERVER.pop();

        // -------- 三种结果的概率 --------
        entry(SERVER, "loot_gamble.outcome",
                "【结果概率】获得与丢失是两次互相独立的掷骰，各自按下面的概率决定这一次是否发生，共四种组合：\n"
                        + "  两者都命中 → 替换：随机一格的物品被拿走，同时换成另一个完全随机的物品与数量；\n"
                        + "  只命中获得 → 获得：随机物品的若干数量进入背包；\n"
                        + "  只命中丢失 → 丢失：背包里随机一整格被清空（那格里有多少就全没多少）；\n"
                        + "  都没命中   → 什么都不发生。\n"
                        + "所以“替换”不需要单独配置，它的概率 = 获得概率 × 丢失概率。\n"
                        + "默认 获得 50% + 丢失 50% → 获得 25%、丢失 25%、替换 25%、无事 25%。\n"
                        + "任意一项设为 0 时，替换就永远不可能发生，结果必然是纯粹的获得或丢失；\n"
                        + "两项都设为 100% 则每次都替换。想让替换更多，就把两项一起调高（例如各 70% → 替换 49%）。")
                .push("outcome");

        LOOT_GAIN_CHANCE = entry(SERVER, "loot_gamble.outcome.gain_chance_percent",
                "【获得】这一次触发的获得概率，单位：百分比，默认 50。取值范围 0 ~ 100。\n"
                        + "设为 0 时不会获得任何东西，“替换”也就不会出现。")
                .defineInRange("gain_chance_percent", 50.0D, 0.0D, 100.0D);

        LOOT_LOSS_CHANCE = entry(SERVER, "loot_gamble.outcome.loss_chance_percent",
                "【丢失】这一次触发的丢失概率，单位：百分比，默认 50。取值范围 0 ~ 100。\n"
                        + "设为 0 时不会丢失任何东西，“替换”也就不会出现。")
                .defineInRange("loss_chance_percent", 50.0D, 0.0D, 100.0D);

        SERVER.pop();

        // -------- 数量与物品池 --------
        entry(SERVER, "loot_gamble.amount",
                "【数量范围】获得与替换时，一次给多少个物品。实际数量还会受该物品自身的堆叠上限限制\n"
                        + "（例如工具、护甲最多 1 个，不会因为你设了 64 就变成 64 个）。")
                .push("amount");

        LOOT_GAIN_MIN = entry(SERVER, "loot_gamble.amount.gain_min",
                "【获得】一次最少给多少个，默认 1。取值范围 1 ~ 64。")
                .defineInRange("gain_min", 1, 1, 64);

        LOOT_GAIN_MAX = entry(SERVER, "loot_gamble.amount.gain_max",
                "【获得】一次最多给多少个，默认 5。取值范围 1 ~ 64，且会自动不小于最小值。")
                .defineInRange("gain_max", 5, 1, 64);

        LOOT_REPLACE_MIN = entry(SERVER, "loot_gamble.amount.replace_min",
                "【替换】替换后的物品最少多少个（替换 = 获得与丢失同时命中时），默认 1。取值范围 1 ~ 64。")
                .defineInRange("replace_min", 1, 1, 64);

        LOOT_REPLACE_MAX = entry(SERVER, "loot_gamble.amount.replace_max",
                "【替换】替换后的物品最多多少个，默认 64。取值范围 1 ~ 64，且会自动不小于最小值。")
                .defineInRange("replace_max", 64, 1, 64);

        SERVER.pop();

        entry(SERVER, "loot_gamble.pool",
                "【物品池与自定义概率】三张权重表，格式为 \"物品ID=权重\"，例如 \"minecraft:diamond=0.2\"。\n"
                        + "默认三张表都是空的，此时就是“完全随机”：从游戏里所有物品中均匀抽取（空气除外）。\n"
                        + "权重越高越容易被抽中；没有写进表里的物品使用各自的 default_weight。\n"
                        + "把 default_weight 设为 0，就等于“只从表里抽”，可以做成一个人工筛选过的物品池。\n"
                        + "写错的条目（物品不存在、权重不是数字）会被忽略并在日志里提示，不会导致崩溃。")
                .push("pool");

        LOOT_ITEM_BLACKLIST = entry(SERVER, "loot_gamble.item_blacklist",
                "【物品黑名单】列在这里的物品：既不会被“获得”发出来，也不会被“丢失/替换”选中\n"
                        + "（也就是说，放在背包里的黑名单物品是安全的，不会被拿走或被换掉）。\n"
                        + "默认只收录正常情况下看不到、也不该出现在背包里的管理/调试类物品：\n"
                        + "  命令方块 / 连锁命令方块 / 循环命令方块 / 命令方块矿车 / 屏障 / 光源 / 结构空位 / 结构方块 / 拼图方块 / 调试棒 / 知识之书。\n"
                        + "注意：末地传送门框架、龙蛋、基岩这类虽然稀有或无法正常获取，但在游戏里是“见得到”的物品，\n"
                        + "默认不列入黑名单；需要的话把它们的 ID 加进来即可（例如 minecraft:end_portal_frame）。\n"
                        + "留空 = 不启用黑名单。写错的条目会被自动剔除并在日志提示。")
                .defineListAllowEmpty("item_blacklist", DEFAULT_LOOT_BLACKLIST, () -> "minecraft:bedrock",
                        JoyConfig::isValidItemIdEntry);

        LOOT_GAIN_DEFAULT_WEIGHT = entry(SERVER, "loot_gamble.pool.gain_default_weight",
                "【获得】未列在 gain_pool 里的物品的权重，默认 1.0。设为 0 则只从 gain_pool 中抽取。\n"
                        + "例：想让钻石变稀罕，把 gain_pool 写成 [\"minecraft:diamond=0.02\"]，其余物品仍是 1.0。")
                .defineInRange("gain_default_weight", 1.0D, 0.0D, 10000.0D);

        LOOT_GAIN_POOL = entry(SERVER, "loot_gamble.pool.gain_pool",
                "【获得】的物品权重表，默认空（= 全部物品均匀随机）。\n"
                        + "格式：[\"minecraft:diamond=0.5\", \"minecraft:netherite_block=0.01\"]。")
                .defineListAllowEmpty("gain_pool", List.of(), () -> "minecraft:diamond=1.0", JoyConfig::isValidWeightEntry);

        LOOT_REPLACE_DEFAULT_WEIGHT = entry(SERVER, "loot_gamble.pool.replace_default_weight",
                "【替换】未列在 replace_pool 里的物品的权重，默认 1.0。设为 0 则只替换成 replace_pool 里的物品。")
                .defineInRange("replace_default_weight", 1.0D, 0.0D, 10000.0D);

        LOOT_REPLACE_POOL = entry(SERVER, "loot_gamble.pool.replace_pool",
                "【替换】替换成什么物品的权重表，默认空（= 全部物品均匀随机）。\n"
                        + "格式同 gain_pool，例如 [\"minecraft:crafting_table=1.0\", \"minecraft:netherite_block=0.05\"]。")
                .defineListAllowEmpty("replace_pool", List.of(), () -> "minecraft:stone=1.0", JoyConfig::isValidWeightEntry);

        LOOT_TARGET_DEFAULT_WEIGHT = entry(SERVER, "loot_gamble.pool.target_default_weight",
                "【丢失/替换的目标格】未列在 target_weights 里的物品的权重，默认 1.0（即背包每一格被选中的机会相等）。\n"
                        + "设为 0 则只有 target_weights 里列出的物品所在格子会被选中。")
                .defineInRange("target_default_weight", 1.0D, 0.0D, 10000.0D);

        LOOT_TARGET_WEIGHTS = entry(SERVER, "loot_gamble.pool.target_weights",
                "【丢失/替换的目标格】背包里“哪一格被选中”的权重表，默认空（= 每个非空格子等概率）。\n"
                        + "例：[\"minecraft:diamond=5.0\"] 会让装有钻石的格子被拿走或被替换的概率变成普通格子的 5 倍。\n"
                        + "格式同 gain_pool。")
                .defineListAllowEmpty("target_weights", List.of(), () -> "minecraft:iron_ingot=2.0", JoyConfig::isValidWeightEntry);

        SERVER.pop();
        SERVER.pop();

        // ================= 客户端 HUD =================
        entry(CLIENT, "hud",
                "【客户端显示】仅影响你自己屏幕上的 HUD 提示，不影响玩法本身，也不需要服务器同意。")
                .push("hud");

        entry(CLIENT, "hud.position_swap",
                "【位置互换】的 HUD 显示设置。")
                .push("position_swap");

        HUD_SWAP_COUNTDOWN = entry(CLIENT, "hud.position_swap.show_countdown",
                "是否在屏幕上显示【位置互换】的倒计时（还有多少秒触发）。默认 true。")
                .define("show_countdown", true);

        HUD_SWAP_DESTINATION = entry(CLIENT, "hud.position_swap.show_destination",
                "是否在屏幕上显示【位置互换】的目的地信息。默认 true。\n"
                        + "显示内容为“你当前最近的那名玩家”的名字与坐标，也就是倒计时结束时你将被送到的位置。\n"
                        + "该值每秒刷新一次；如果此人中途移动，显示会随之变化。")
                .define("show_destination", true);

        CLIENT.pop();

        entry(CLIENT, "hud.random_teleport",
                "【随机传送】的 HUD 显示设置。")
                .push("random_teleport");

        HUD_TELEPORT_COUNTDOWN = entry(CLIENT, "hud.random_teleport.show_countdown",
                "是否在屏幕上显示【随机传送】的倒计时。默认 true。\n"
                        + "随机的落点在触发前无法预知，因此没有“目的地”可显示。")
                .define("show_countdown", true);

        CLIENT.pop();

        entry(CLIENT, "hud.loot_gamble",
                "【物品赌博】的 HUD 显示设置。")
                .push("loot_gamble");

        HUD_LOOT_COUNTDOWN = entry(CLIENT, "hud.loot_gamble.show_countdown",
                "是否在屏幕上显示【物品赌博·倒计时触发】的倒计时。默认 true。\n"
                        + "只有在服务端开启了 triggers.timer 时才会出现这一行；\n"
                        + "它与位置互换、随机传送的倒计时各自独立，显示时依次向下排布，不会互相覆盖。")
                .define("show_countdown", true);

        CLIENT.pop();

        entry(CLIENT, "hud.shared_health",
                "【共享生命】的 HUD 显示设置。")
                .push("shared_health");

        HUD_POOL_STATUS = entry(CLIENT, "hud.shared_health.show_pool_status",
                "B 模式（生命池）下是否在屏幕上显示血池剩余量与百分比。默认 true。\n"
                        + "A 模式（伤害共享）下没有池数值，本项不生效。")
                .define("show_pool_status", true);

        CLIENT.pop();
        CLIENT.pop();
    }

    /** 服务端配置（玩法规则）。 */
    public static final ModConfigSpec SERVER_SPEC = SERVER.build();
    /** 客户端配置（HUD 显示）。 */
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    // ==================================================================
    // 缓存值：运行期逻辑只读这些字段，永远不直接读 ConfigValue，
    // 以避免“配置尚未加载”导致的异常，并保证客户端/服务端两侧字段总是有值。
    // ==================================================================

    public static boolean positionSwapEnabled = false;
    public static int positionSwapIntervalSeconds = 180;
    public static boolean positionSwapAnnounce = true;

    public static boolean randomTeleportEnabled = false;
    public static int randomTeleportIntervalSeconds = 180;
    public static int randomTeleportRadius = 500;
    public static DimensionMode randomTeleportDimensionMode = DimensionMode.KEEP;
    public static String randomTeleportFixedDimension = "minecraft:overworld";
    public static boolean randomTeleportSafeLanding = true;
    public static int randomTeleportMaxAttempts = 24;
    public static boolean randomTeleportAnnounce = true;

    public static boolean sharedHealthEnabled = false;
    public static ShareMode sharedHealthMode = ShareMode.DAMAGE_SHARE;
    public static boolean sharedHealthIncludeCreative = false;
    public static boolean sharedHealthAnnounce = true;
    public static double sharedHealthDamageRatioPercent = 100.0D;
    public static double sharedHealthBarMaxHealth = 20.0D;
    public static double sharedHealthBarMaxFood = 20.0D;
    public static boolean sharedHealthBarAllowHealing = true;
    public static int sharedHealthBarDeathResetPercent = 100;

    public static boolean lootGambleEnabled = false;
    public static boolean lootGambleNotify = true;
    public static int lootGambleCooldownTicks = 20;
    public static boolean lootTriggerBlockBreak = true;
    public static boolean lootTriggerEat = true;
    public static boolean lootTriggerJump = false;
    public static boolean lootTriggerHurt = true;
    public static boolean lootTriggerKill = false;
    public static boolean lootTriggerCraft = false;
    public static boolean lootTriggerPickup = false;
    public static boolean lootTriggerUseItem = false;
    public static boolean lootTriggerTimer = false;
    public static int lootTimerIntervalSeconds = 180;
    public static double lootGainChance = 50.0D;
    public static double lootLossChance = 50.0D;
    public static int lootGainMin = 1;
    public static int lootGainMax = 5;
    public static int lootReplaceMin = 1;
    public static int lootReplaceMax = 64;
    public static List<String> lootItemBlacklist = DEFAULT_LOOT_BLACKLIST;
    public static double lootGainDefaultWeight = 1.0D;
    public static List<String> lootGainPool = List.of();
    public static double lootReplaceDefaultWeight = 1.0D;
    public static List<String> lootReplacePool = List.of();
    public static double lootTargetDefaultWeight = 1.0D;
    public static List<String> lootTargetWeights = List.of();

    public static boolean hudSwapShowCountdown = true;
    public static boolean hudSwapShowDestination = true;
    public static boolean hudTeleportShowCountdown = true;
    public static boolean hudPoolShowStatus = true;
    public static boolean hudLootShowCountdown = true;

    /** 校验物品 ID 条目（用于黑名单：只写物品 ID，不带权重，且物品必须存在）。 */
    static boolean isValidItemIdEntry(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.trim());
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    /** 校验权重表条目："物品ID=权重"（物品必须存在、权重必须是合法数字）。 */
    static boolean isValidWeightEntry(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }
        int split = entry.lastIndexOf('=');
        if (split <= 0 || split == entry.length() - 1) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, split).trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }
        try {
            return Double.parseDouble(entry.substring(split + 1).trim()) >= 0.0D;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidDimensionId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    /** 读取服务端配置到缓存字段；任何单项失败都退回默认值，绝不让配置异常打断游戏逻辑。 */
    public static void refreshServer() {
        positionSwapEnabled = read(POSITION_SWAP_ENABLED, positionSwapEnabled);
        positionSwapIntervalSeconds = read(POSITION_SWAP_INTERVAL, positionSwapIntervalSeconds);
        positionSwapAnnounce = read(POSITION_SWAP_ANNOUNCE, positionSwapAnnounce);

        randomTeleportEnabled = read(RANDOM_TELEPORT_ENABLED, randomTeleportEnabled);
        randomTeleportIntervalSeconds = read(RANDOM_TELEPORT_INTERVAL, randomTeleportIntervalSeconds);
        randomTeleportRadius = read(RANDOM_TELEPORT_RADIUS, randomTeleportRadius);
        randomTeleportDimensionMode = read(RANDOM_TELEPORT_DIMENSION_MODE, randomTeleportDimensionMode);
        randomTeleportFixedDimension = read(RANDOM_TELEPORT_FIXED_DIMENSION, randomTeleportFixedDimension);
        randomTeleportSafeLanding = read(RANDOM_TELEPORT_SAFE_LANDING, randomTeleportSafeLanding);
        randomTeleportMaxAttempts = read(RANDOM_TELEPORT_MAX_ATTEMPTS, randomTeleportMaxAttempts);
        randomTeleportAnnounce = read(RANDOM_TELEPORT_ANNOUNCE, randomTeleportAnnounce);

        sharedHealthEnabled = read(SHARED_HEALTH_ENABLED, sharedHealthEnabled);
        sharedHealthMode = read(SHARED_HEALTH_MODE, sharedHealthMode);
        sharedHealthIncludeCreative = read(SHARED_HEALTH_INCLUDE_CREATIVE, sharedHealthIncludeCreative);
        sharedHealthAnnounce = read(SHARED_HEALTH_ANNOUNCE, sharedHealthAnnounce);
        sharedHealthDamageRatioPercent = read(SHARED_HEALTH_DAMAGE_RATIO, sharedHealthDamageRatioPercent);
        sharedHealthBarMaxHealth = read(POOL_MAX_HEALTH, sharedHealthBarMaxHealth);
        sharedHealthBarMaxFood = read(POOL_MAX_FOOD, sharedHealthBarMaxFood);
        sharedHealthBarAllowHealing = read(POOL_ALLOW_HEALING, sharedHealthBarAllowHealing);
        sharedHealthBarDeathResetPercent = read(POOL_DEATH_RESET_PERCENT, sharedHealthBarDeathResetPercent);

        lootGambleEnabled = read(LOOT_GAMBLE_ENABLED, lootGambleEnabled);
        lootGambleNotify = read(LOOT_GAMBLE_NOTIFY, lootGambleNotify);
        lootGambleCooldownTicks = read(LOOT_GAMBLE_COOLDOWN, lootGambleCooldownTicks);
        lootTriggerBlockBreak = read(LOOT_TRIGGER_BLOCK_BREAK, lootTriggerBlockBreak);
        lootTriggerEat = read(LOOT_TRIGGER_EAT, lootTriggerEat);
        lootTriggerJump = read(LOOT_TRIGGER_JUMP, lootTriggerJump);
        lootTriggerHurt = read(LOOT_TRIGGER_HURT, lootTriggerHurt);
        lootTriggerKill = read(LOOT_TRIGGER_KILL, lootTriggerKill);
        lootTriggerCraft = read(LOOT_TRIGGER_CRAFT, lootTriggerCraft);
        lootTriggerPickup = read(LOOT_TRIGGER_PICKUP, lootTriggerPickup);
        lootTriggerUseItem = read(LOOT_TRIGGER_USE_ITEM, lootTriggerUseItem);
        lootTriggerTimer = read(LOOT_TRIGGER_TIMER, lootTriggerTimer);
        lootTimerIntervalSeconds = read(LOOT_TIMER_INTERVAL, lootTimerIntervalSeconds);
        lootGainChance = read(LOOT_GAIN_CHANCE, lootGainChance);
        lootLossChance = read(LOOT_LOSS_CHANCE, lootLossChance);
        lootGainMin = read(LOOT_GAIN_MIN, lootGainMin);
        lootGainMax = Math.max(lootGainMin, read(LOOT_GAIN_MAX, lootGainMax));
        lootReplaceMin = read(LOOT_REPLACE_MIN, lootReplaceMin);
        lootReplaceMax = Math.max(lootReplaceMin, read(LOOT_REPLACE_MAX, lootReplaceMax));
        lootItemBlacklist = List.copyOf(read(LOOT_ITEM_BLACKLIST, lootItemBlacklist));
        lootGainDefaultWeight = read(LOOT_GAIN_DEFAULT_WEIGHT, lootGainDefaultWeight);
        lootGainPool = List.copyOf(read(LOOT_GAIN_POOL, lootGainPool));
        lootReplaceDefaultWeight = read(LOOT_REPLACE_DEFAULT_WEIGHT, lootReplaceDefaultWeight);
        lootReplacePool = List.copyOf(read(LOOT_REPLACE_POOL, lootReplacePool));
        lootTargetDefaultWeight = read(LOOT_TARGET_DEFAULT_WEIGHT, lootTargetDefaultWeight);
        lootTargetWeights = List.copyOf(read(LOOT_TARGET_WEIGHTS, lootTargetWeights));
    }

    /** 读取客户端显示配置到缓存字段。 */
    public static void refreshClient() {
        hudSwapShowCountdown = read(HUD_SWAP_COUNTDOWN, hudSwapShowCountdown);
        hudSwapShowDestination = read(HUD_SWAP_DESTINATION, hudSwapShowDestination);
        hudTeleportShowCountdown = read(HUD_TELEPORT_COUNTDOWN, hudTeleportShowCountdown);
        hudPoolShowStatus = read(HUD_POOL_STATUS, hudPoolShowStatus);
        hudLootShowCountdown = read(HUD_LOOT_COUNTDOWN, hudLootShowCountdown);
    }

    private static boolean read(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            Boolean v = value.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            Integer v = value.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double read(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            Double v = value.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static <T> T read(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T v = value.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T read(ModConfigSpec.EnumValue<T> value, T fallback) {
        try {
            T v = value.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * 供命令使用：直接改写“启用开关”的配置值并落盘。
     *
     * <p>写入后 {@link ModConfigSpec#save()} 会触发配置重载事件，从而让缓存字段与玩法计时器同步更新，
     * 因此命令与配置界面两条路径最终都收敛到同一份配置值上。</p>
     *
     * @return 是否成功写入（配置尚未加载时会失败）
     */
    public static boolean writeEnabled(String gameplayId, boolean enabled) {
        ModConfigSpec.BooleanValue target = switch (gameplayId) {
            case "position_swap" -> POSITION_SWAP_ENABLED;
            case "random_teleport" -> RANDOM_TELEPORT_ENABLED;
            case "shared_health" -> SHARED_HEALTH_ENABLED;
            case "loot_gamble" -> LOOT_GAMBLE_ENABLED;
            default -> null;
        };
        if (target == null) {
            return false;
        }
        try {
            target.set(enabled);
            SERVER_SPEC.save();
        } catch (RuntimeException e) {
            Joyevents.LOGGER.warn("通过命令写入配置失败（配置可能尚未加载）: {}", e.toString());
            return false;
        }
        refreshServer();
        return true;
    }

    /** 当前配置文件中该玩法的启用状态（未经缓存，读的是配置本体）。 */
    public static boolean readEnabledFromSpec(String gameplayId) {
        return switch (gameplayId) {
            case "position_swap" -> read(POSITION_SWAP_ENABLED, false);
            case "random_teleport" -> read(RANDOM_TELEPORT_ENABLED, false);
            case "shared_health" -> read(SHARED_HEALTH_ENABLED, false);
            case "loot_gamble" -> read(LOOT_GAMBLE_ENABLED, false);
            default -> false;
        };
    }

    @SubscribeEvent
    static void onConfigLoad(final ModConfigEvent event) {
        IConfigSpec spec = event.getConfig().getSpec();
        if (spec == SERVER_SPEC) {
            refreshServer();
        } else if (spec == CLIENT_SPEC) {
            refreshClient();
        }
    }
}
