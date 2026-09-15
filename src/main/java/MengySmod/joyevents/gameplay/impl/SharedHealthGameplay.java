package MengySmod.joyevents.gameplay.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.Gameplay;
import MengySmod.joyevents.gameplay.data.SharedHealthPoolData;
import MengySmod.joyevents.gameplay.util.PoolAccounting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩法二：共享生命。
 *
 * <p><b>A 模式（DAMAGE_SHARE）</b>：只共享伤害。监听 {@link LivingDamageEvent.Post} 读到挨打者
 * <b>经过自身护甲/抗性结算之后</b>实际承受的伤害，按配置百分比传递给其他参与者；
 * 其他玩家收到后再经过各自的护甲结算，所以护甲在这个模式下依然有用。A 模式不共享回血。</p>
 *
 * <p><b>B 模式（POOL）</b>：所有人共用一条命。共享条（血条 + 饥饿条）是唯一真实数值，
 * 每个人血条上显示的就是共享条本身（默认上限 20 点 = 一条原版血条）。
 * 伤害在护甲结算之后被截流进共享条（{@link LivingDamageEvent.Pre}），个人血量不再单独扣除。</p>
 *
 * <p><b>为什么需要归一化</b>：共享条只有一条，但原版自然回血是“每名玩家各自触发”的。
 * 若把每个人的回血都直接加进来，人越多共享条自愈越快（旧版本就这样被玩家发现是 bug）。
 * 因此所有“每个身体各自触发一次”的收支都按人数均摊，详见 {@link PoolAccounting}。</p>
 *
 * <p><b>重入保护</b>：给他人施加伤害会再次触发同样的事件。这里用 {@link #applying} 标志
 * 在整体操作期间短路所有自己的处理器，避免无限递归与伤害翻倍。</p>
 */
public final class SharedHealthGameplay extends Gameplay {
    private static final ResourceKey<DamageType> SHARED_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Joyevents.MODID, "shared_damage"));
    private static final ResourceKey<DamageType> POOL_DEATH = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Joyevents.MODID, "shared_pool_death"));

    /** 每人“上次被我们写入的饥饿值”，用来把玩家自己的增减与我们的投影区分开。 */
    private Map<UUID, Double> lastProjectedFood = new HashMap<>();
    /** 每人“上次被我们钉住的饱和度”，用来捕捉原版每 4 点疲劳一次的结算。 */
    private Map<UUID, Double> lastPinnedSaturation = new HashMap<>();

    @Nullable
    private SharedHealthPoolData pool;
    private JoyConfig.ShareMode lastMode = JoyConfig.ShareMode.DAMAGE_SHARE;
    private boolean applying;

    public SharedHealthGameplay() {
        super("shared_health");
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, this::onDamagePost);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class, this::onDamagePre);
        NeoForge.EVENT_BUS.addListener(LivingHealEvent.class, this::onHeal);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, this::onPlayerRespawn);
    }

    // ==================================================================
    // Gameplay 基本属性
    // ==================================================================

    @Override
    public boolean isEnabled() {
        return JoyConfig.sharedHealthEnabled;
    }

    @Override
    public int intervalTicks() {
        return 20;
    }

    @Override
    public boolean periodic() {
        // 常驻效果：没有倒计时，也不该被定时执行
        return false;
    }

    @Override
    public boolean announce() {
        return JoyConfig.sharedHealthAnnounce;
    }

    /** 命令触发时的语义：把共享条重置为满值（B 模式）。 */
    @Override
    public void execute(MinecraftServer server) {
        if (JoyConfig.sharedHealthMode == JoyConfig.ShareMode.POOL) {
            SharedHealthPoolData data = poolFor(server);
            data.resetFull();
            resetMirrors();
            projectAll(server);
            if (announce()) {
                broadcast(server, Component.translatable("joyevents.message.shared_health.pool_reset"));
            }
        }
    }

    @Override
    public void onEnabled(MinecraftServer server) {
        this.lastMode = JoyConfig.sharedHealthMode;
        this.applying = false;
        resetMirrors();
        if (this.lastMode == JoyConfig.ShareMode.POOL) {
            SharedHealthPoolData data = poolFor(server);
            data.syncMaxima(configuredMaxHealth(), configuredMaxFood());
            if (data.getHealth() <= 0.0D) {
                data.resetFull();
            }
            projectAll(server);
            Joyevents.LOGGER.info("[共享生命] 已启用 B 模式（共用一条命）：血条 {}/{}，饥饿条 {}/{}",
                    format(data.getHealth()), format(data.getMaxHealthPool()),
                    format(data.getFood()), format(data.getMaxFoodPool()));
        } else {
            this.pool = null;
            Joyevents.LOGGER.info("[共享生命] 已启用 A 模式（伤害共享）：其他玩家承受 {}% 伤害",
                    format(JoyConfig.sharedHealthDamageRatioPercent));
        }
    }

    /**
     * 停用（或服务端停止）时：把所有被投影过的玩家恢复成各自满血满饱食，
     * 并清空共享条，避免“关掉玩法后全员永久残血”。
     */
    @Override
    public void onDisabled(MinecraftServer server) {
        restoreAllToFull(server);
        resetMirrors();
        this.applying = false;
        if (this.pool != null) {
            this.pool.resetFull();
            this.pool = null;
        }
    }

    // ==================================================================
    // 每 tick：模式切换处理 + B 模式投影
    // ==================================================================

    @Override
    public void onServerTick(MinecraftServer server) {
        JoyConfig.ShareMode mode = JoyConfig.sharedHealthMode;

        if (mode != this.lastMode) {
            // 运行中切换模式：先彻底回滚旧模式的残留状态，再初始化新模式
            Joyevents.LOGGER.info("[共享生命] 模式由 {} 切换为 {}", this.lastMode, mode);
            restoreAllToFull(server);
            resetMirrors();
            this.lastMode = mode;
            if (mode == JoyConfig.ShareMode.POOL) {
                SharedHealthPoolData data = poolFor(server);
                data.syncMaxima(configuredMaxHealth(), configuredMaxFood());
                if (data.getHealth() <= 0.0D) {
                    data.resetFull();
                }
            } else {
                this.pool = null;
            }
        }

        if (mode == JoyConfig.ShareMode.POOL) {
            projectAll(server);
        }
    }

    /** 把共享条投影到每名参与者的血条与饥饿条上（同时完成饥饿条的收支记账）。 */
    private void projectAll(MinecraftServer server) {
        SharedHealthPoolData data = poolFor(server);
        data.syncMaxima(configuredMaxHealth(), configuredMaxFood());

        List<ServerPlayer> participants = participants(server);
        int count = participants.size();

        // 饥饿条：先按“进食原值进账、生理消耗按人数均摊”记完账，再写回投影
        if (count > 0) {
            Map<UUID, Double> updatedFood = new HashMap<>();
            Map<UUID, Double> updatedSaturation = new HashMap<>();
            for (ServerPlayer player : participants) {
                if (!player.isAlive()) {
                    continue;
                }
                double delta = accountFood(player, count);
                if (delta != 0.0D) {
                    data.addFood(delta);
                }
                int projected = PoolAccounting.projectedFoodLevel(data.foodRatio());
                float pinned = PoolAccounting.pinnedSaturation(data.foodRatio());
                FoodData foodData = player.getFoodData();
                if (foodData.getFoodLevel() != projected) {
                    foodData.setFoodLevel(projected);
                }
                foodData.setSaturation(pinned);
                updatedFood.put(player.getUUID(), (double) projected);
                updatedSaturation.put(player.getUUID(), (double) pinned);
            }
            this.lastProjectedFood = updatedFood;
            this.lastPinnedSaturation = updatedSaturation;
        }

        // 血条：共享条比例 × 各自最大生命（默认全员 20 点血时，显示的就是共享条数值本身）
        double healthRatio = data.healthRatio();
        if (healthRatio <= 0.0D) {
            // 共享条见底的那一刻由 killEveryone 收尾，这里不投影，避免出现 0 血却不死的状态
            return;
        }
        for (ServerPlayer player : participants) {
            if (!player.isAlive()) {
                continue;
            }
            float target = (float) Math.max(0.5D, player.getMaxHealth() * healthRatio);
            if (Math.abs(player.getHealth() - target) > 0.01F) {
                player.setHealth(target);
            }
        }
    }

    /**
     * 单个玩家的饥饿条记账。
     *
     * <p>关键点：拿“观察值”与“上次我们写入的值”比较，而不是与上一 tick 的观察值比较，
     * 否则投影自身造成的舍入抖动会被误判成“吃东西”，形成正反馈。</p>
     */
    private double accountFood(ServerPlayer player, int participants) {
        FoodData foodData = player.getFoodData();
        double observedFood = foodData.getFoodLevel();
        double observedSaturation = foodData.getSaturationLevel();
        double lastFood = this.lastProjectedFood.getOrDefault(player.getUUID(), observedFood);
        double lastSaturation = this.lastPinnedSaturation.getOrDefault(player.getUUID(), observedSaturation);

        return PoolAccounting.foodPoolDelta(observedFood, observedSaturation, lastFood, lastSaturation, participants);
    }

    // ==================================================================
    // A 模式：只共享伤害，按比例传递
    // ==================================================================

    private void onDamagePost(LivingDamageEvent.Post event) {
        if (!JoyConfig.sharedHealthEnabled || JoyConfig.sharedHealthMode != JoyConfig.ShareMode.DAMAGE_SHARE) {
            return;
        }
        if (this.applying || event.getSource().is(SHARED_DAMAGE)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim) || !isParticipant(victim)) {
            return;
        }
        // 注意：这里拿到的是挨打者“护甲/抗性结算之后”实际承受的伤害
        float appliedDamage = event.getNewDamage();
        if (appliedDamage <= 0.0F) {
            return;
        }
        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }
        double ratio = JoyConfig.sharedHealthDamageRatioPercent / 100.0D;
        float propagate = (float) (appliedDamage * ratio);
        if (propagate <= 0.0F) {
            return;
        }

        this.applying = true;
        try {
            for (ServerPlayer target : participants(server)) {
                if (target == victim) {
                    continue;
                }
                // 清掉无敌帧，保证共享伤害一定生效；伤害会再经过目标自己的护甲结算
                target.invulnerableTime = 0;
                target.hurt(sharedDamage(target), propagate);
            }
        } finally {
            this.applying = false;
        }
    }

    // ==================================================================
    // B 模式：伤害截流进共享条
    // ==================================================================

    private void onDamagePre(LivingDamageEvent.Pre event) {
        if (!JoyConfig.sharedHealthEnabled || JoyConfig.sharedHealthMode != JoyConfig.ShareMode.POOL) {
            return;
        }
        if (this.applying || event.getSource().is(POOL_DEATH) || event.getSource().is(SHARED_DAMAGE)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim) || !isParticipant(victim)) {
            return;
        }
        // 此时护甲/抗性已经结算完毕，取到的就是要扣在共享条上的真实伤害
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }

        // 个人血量不再单独扣除：受伤一律计入共享条，共享条才是唯一权威
        event.setNewDamage(0.0F);

        SharedHealthPoolData data = poolFor(server);
        if (data.damage(damage)) {
            this.applying = true;
            try {
                killEveryone(server, data);
            } finally {
                this.applying = false;
            }
        }
    }

    /** 共享条耗尽：全员死亡，然后按配置比例重置（比例最小 1%，不会形成死亡循环）。 */
    private void killEveryone(MinecraftServer server, SharedHealthPoolData data) {
        List<ServerPlayer> targets = participants(server);
        if (announce()) {
            broadcast(server, Component.translatable("joyevents.message.shared_health.pool_emptied"));
        }
        for (ServerPlayer target : targets) {
            if (!target.isAlive()) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(poolDeath(target), Float.MAX_VALUE);
        }
        data.resetTo(JoyConfig.sharedHealthBarDeathResetPercent / 100.0D);
        resetMirrors();
    }

    // ==================================================================
    // 回血：只作用于 B 模式（A 模式不共享回血）
    // ==================================================================

    private void onHeal(LivingHealEvent event) {
        if (!JoyConfig.sharedHealthEnabled || JoyConfig.sharedHealthMode != JoyConfig.ShareMode.POOL) {
            return;
        }
        if (this.applying || !(event.getEntity() instanceof ServerPlayer healer) || !isParticipant(healer)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        MinecraftServer server = healer.getServer();
        if (server == null) {
            return;
        }

        int count = Math.max(1, participants(server).size());
        // 无论是否允许回血，都要拦掉个人回血：共享条才是唯一权威
        event.setCanceled(true);

        if (!JoyConfig.sharedHealthBarAllowHealing) {
            // 硬核模式：共享条只减不增。但原版回血消耗的疲劳会被记到饥饿条上，
            // 所以这里把“这次被我们丢掉的治疗”对应的饱食度代价退回去，避免白掉饥饿值。
            poolFor(server).addFood(PoolAccounting.healCredit(amount, count) * PoolAccounting.FOOD_PER_HEALTH);
            return;
        }

        // 被动回血（自然回血 / 生命恢复效果，每人各自触发）按人数均摊；
        // 瞬时治疗（药水、金苹果）作用于这一条命，按原值进账。
        poolFor(server).addHealth(PoolAccounting.healCredit(amount, count));
    }

    // ==================================================================
    // 玩家上下线 / 复活
    // ==================================================================

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!JoyConfig.sharedHealthEnabled || JoyConfig.sharedHealthMode != JoyConfig.ShareMode.POOL) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            // 新加入的玩家立刻按当前共享条比例投影，避免他满血站在濒死的队伍里
            projectAll(player.getServer());
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!JoyConfig.sharedHealthEnabled) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && JoyConfig.sharedHealthMode == JoyConfig.ShareMode.POOL) {
            forget(player.getUUID());
            projectAll(player.getServer());
        }
    }

    // ==================================================================
    // HUD / 状态
    // ==================================================================

    @Override
    @Nullable
    public PoolStatus poolStatus() {
        if (JoyConfig.sharedHealthMode != JoyConfig.ShareMode.POOL || this.pool == null) {
            return null;
        }
        return new PoolStatus(this.pool.getHealth(), this.pool.getMaxHealthPool());
    }

    @Override
    public Component statusDetail(MinecraftServer server) {
        if (JoyConfig.sharedHealthMode == JoyConfig.ShareMode.DAMAGE_SHARE) {
            return Component.translatable("joyevents.status.shared_health.damage_share",
                    format(JoyConfig.sharedHealthDamageRatioPercent));
        }
        SharedHealthPoolData data = poolFor(server);
        return Component.translatable("joyevents.status.shared_health.pool",
                format(data.getHealth()),
                format(data.getMaxHealthPool()),
                Long.toString(Math.round(data.healthRatio() * 100.0D)),
                format(data.getFood()),
                format(data.getMaxFoodPool()));
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 参与者 = 非旁观者 + （可选）创造模式玩家。 */
    private List<ServerPlayer> participants(MinecraftServer server) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : eligiblePlayers(server)) {
            if (player.isCreative() && !JoyConfig.sharedHealthIncludeCreative) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    private boolean isParticipant(Player player) {
        if (player.isSpectator()) {
            return false;
        }
        return !player.isCreative() || JoyConfig.sharedHealthIncludeCreative;
    }

    private SharedHealthPoolData poolFor(MinecraftServer server) {
        if (this.pool == null) {
            this.pool = server.overworld().getDataStorage()
                    .computeIfAbsent(SharedHealthPoolData.factory(), SharedHealthPoolData.DATA_NAME);
        }
        return this.pool;
    }

    private static double configuredMaxHealth() {
        return Math.max(1.0D, JoyConfig.sharedHealthBarMaxHealth);
    }

    private static double configuredMaxFood() {
        return Math.max(1.0D, JoyConfig.sharedHealthBarMaxFood);
    }

    private void resetMirrors() {
        this.lastProjectedFood = new HashMap<>();
        this.lastPinnedSaturation = new HashMap<>();
    }

    private void forget(UUID playerId) {
        this.lastProjectedFood.remove(playerId);
        this.lastPinnedSaturation.remove(playerId);
    }

    private DamageSource sharedDamage(ServerPlayer player) {
        return damageSource(player, SHARED_DAMAGE);
    }

    private DamageSource poolDeath(ServerPlayer player) {
        return damageSource(player, POOL_DEATH);
    }

    private static DamageSource damageSource(ServerPlayer player, ResourceKey<DamageType> key) {
        Holder<DamageType> holder = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
        return new DamageSource(holder);
    }

    /** 停用/模式切换时把玩家恢复成各自满血满饱食。 */
    private void restoreAllToFull(MinecraftServer server) {
        for (ServerPlayer player : eligiblePlayers(server)) {
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0F);
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
