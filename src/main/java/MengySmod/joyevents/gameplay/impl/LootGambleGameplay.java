package MengySmod.joyevents.gameplay.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import MengySmod.joyevents.Joyevents;
import MengySmod.joyevents.config.JoyConfig;
import MengySmod.joyevents.gameplay.Gameplay;
import MengySmod.joyevents.gameplay.util.ItemIdList;
import MengySmod.joyevents.gameplay.util.ItemWeightTable;
import MengySmod.joyevents.gameplay.util.OutcomeTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 玩法四：物品赌博。
 *
 * <p>每次触发（挖掘方块、吃东西、跳跃、受伤、击杀、合成、拾取、右键使用、倒计时，可任意叠加）时，
 * 获得与丢失<b>各自独立掷骰</b>，两者同时命中即为“替换”：</p>
 * <ul>
 *   <li><b>获得</b>：随机获得某个物品的若干数量（物品与数量都随机）。</li>
 *   <li><b>丢失</b>：随机丢掉背包里的一整格——那一格里有多少就全没多少。</li>
 *   <li><b>替换</b>：把随机一格的内容整体换成另一个完全随机的物品与随机数量。</li>
 * </ul>
 *
 * <p>物品黑名单中的物品既不会被发出来，也不会被“丢失/替换”选中（放在背包里等于安全）。<br>
 * 物品池默认是“全物品均匀随机（黑名单除外）”，可用权重表对特定物品加/减概率（把默认权重设为 0 就变成只从表里抽）。
 * 结果抽取规则见 {@link OutcomeTable}，加权抽取见 {@link ItemWeightTable}。</p>
 */
public final class LootGambleGameplay extends Gameplay {
    /** 每个玩家各自的下次可触发 tick（按玩家计算冷却，避免一个人的高频动作影响别人）。 */
    private final Map<UUID, Integer> nextAllowedTick = new HashMap<>();

    /** 全部候选物品（排除空气），懒加载一次即可。 */
    @Nullable
    private List<Item> candidates;
    private ItemWeightTable gainTable = ItemWeightTable.parse(List.of(), 1.0D);
    private ItemWeightTable replaceTable = ItemWeightTable.parse(List.of(), 1.0D);
    private ItemWeightTable targetTable = ItemWeightTable.parse(List.of(), 1.0D);
    private String poolFingerprint = "";
    /** 物品黑名单：既不会被发出来，也不会被选中拿走或替换。 */
    private Set<ResourceLocation> blacklist = Set.of();

    public LootGambleGameplay() {
        super("loot_gamble");
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Finish.class, this::onUseItemFinished);
        NeoForge.EVENT_BUS.addListener(LivingEvent.LivingJumpEvent.class, this::onJump);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, this::onHurt);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, this::onKill);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.ItemCraftedEvent.class, this::onCraft);
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Post.class, this::onPickup);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, this::onRightClickItem);
    }

    // ==================================================================
    // Gameplay 基本属性
    // ==================================================================

    @Override
    public boolean isEnabled() {
        return JoyConfig.lootGambleEnabled;
    }

    @Override
    public int intervalTicks() {
        return Math.max(1, JoyConfig.lootTimerIntervalSeconds) * 20;
    }

    @Override
    public boolean periodic() {
        // 只有当“倒计时触发”打开时才是周期型玩法；此时调度器会驱动倒计时并按时调用 execute()
        return JoyConfig.lootTriggerTimer;
    }

    @Override
    public boolean announce() {
        return false;
    }

    /**
     * 两条路径都会走到这里：倒计时触发到点，或者命令 {@code /joyevents trigger loot_gamble} 手动触发。
     * 两者都<b>绕过且不占用</b>动作冷却——倒计时本身的间隔已经限定了频率，
     * 也不该因为刚刚挖过方块就把这次倒计时“吃掉”。
     */
    @Override
    public void execute(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            handleTrigger(player, true);
        }
    }

    @Override
    public void onEnabled(MinecraftServer server) {
        this.nextAllowedTick.clear();
        refreshPools(true);
        Joyevents.LOGGER.info("[物品赌博] 已启用：获得 {}% / 丢失 {}%（替换 {}%），冷却 {} tick，倒计时触发 {}（{} 秒），触发方式 {}",
                format(JoyConfig.lootGainChance), format(JoyConfig.lootLossChance),
                format(OutcomeTable.replacePercent(JoyConfig.lootGainChance, JoyConfig.lootLossChance)),
                JoyConfig.lootGambleCooldownTicks,
                JoyConfig.lootTriggerTimer ? "开" : "关", JoyConfig.lootTimerIntervalSeconds,
                enabledTriggers().size());
    }

    @Override
    public void onDisabled(MinecraftServer server) {
        this.nextAllowedTick.clear();
    }

    // ==================================================================
    // 八种触发方式
    // ==================================================================

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!JoyConfig.lootTriggerBlockBreak || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    /** 吃东西：只有带食物属性的物品才算。 */
    private void onUseItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!JoyConfig.lootTriggerEat || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getItem().get(DataComponents.FOOD) == null) {
            return;
        }
        handleTrigger(player);
    }

    private void onJump(LivingEvent.LivingJumpEvent event) {
        if (!JoyConfig.lootTriggerJump) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    private void onHurt(LivingDamageEvent.Post event) {
        if (!JoyConfig.lootTriggerHurt || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    /** 击杀：只算玩家造成的击杀。 */
    private void onKill(LivingDeathEvent event) {
        if (!JoyConfig.lootTriggerKill) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    private void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!JoyConfig.lootTriggerCraft) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    private void onPickup(ItemEntityPickupEvent.Post event) {
        if (!JoyConfig.lootTriggerPickup || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        handleTrigger(player);
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!JoyConfig.lootTriggerUseItem || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            handleTrigger(player);
        }
    }

    // ==================================================================
    // 触发与结算
    // ==================================================================

    private void handleTrigger(ServerPlayer player) {
        handleTrigger(player, false);
    }

    private void handleTrigger(ServerPlayer player, boolean ignoreCooldown) {
        if (!JoyConfig.lootGambleEnabled || player.isSpectator() || !player.isAlive()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // 冷却：即使是“什么都不发生”也消耗冷却，避免同一个动作被反复重掷
        int now = server.getTickCount();
        int cooldown = Math.max(0, JoyConfig.lootGambleCooldownTicks);
        if (!ignoreCooldown) {
            if (cooldown > 0) {
                Integer next = this.nextAllowedTick.get(player.getUUID());
                if (next != null && now < next) {
                    return;
                }
            }
            this.nextAllowedTick.put(player.getUUID(), now + cooldown);
        }

        refreshPools(false);

        RandomSource random = player.getRandom();
        // 获得与丢失各自独立掷骰：两者同时命中即为“替换”
        OutcomeTable.Outcome outcome = OutcomeTable.roll(
                JoyConfig.lootGainChance, JoyConfig.lootLossChance,
                random.nextDouble() * 100.0D, random.nextDouble() * 100.0D);

        switch (outcome) {
            case GAIN -> applyGain(player, random);
            case LOSS -> applyLoss(player, random);
            case REPLACE -> applyReplace(player, random);
            case NOTHING -> { }
        }
    }

    /** 随机获得某个物品的若干数量；背包放不下就掉在脚下。 */
    private void applyGain(ServerPlayer player, RandomSource random) {
        ItemStack reward = randomStack(random, this.gainTable, JoyConfig.lootGainMin, JoyConfig.lootGainMax);
        if (reward.isEmpty()) {
            return;
        }
        ItemStack toGive = reward.copy();
        if (!player.getInventory().add(toGive)) {
            player.drop(toGive, false);
        }
        syncInventory(player);
        notifyPlayer(player, Component.translatable("joyevents.message.loot_gamble.gain",
                reward.getHoverName(), reward.getCount()));
    }

    /** 随机丢掉背包里一整格（那一格里有多少全没多少）。 */
    private void applyLoss(ServerPlayer player, RandomSource random) {
        Inventory inventory = player.getInventory();
        int slot = pickTargetSlot(inventory, random);
        if (slot < 0) {
            return;
        }
        ItemStack lost = inventory.getItem(slot).copy();
        inventory.setItem(slot, ItemStack.EMPTY);
        syncInventory(player);
        notifyPlayer(player, Component.translatable("joyevents.message.loot_gamble.loss",
                lost.getHoverName(), lost.getCount()));
    }

    /**
     * 替换：获得与丢失同时命中时的结果——抽一格，把它的内容整体换成另一个完全随机的物品与随机数量。
     * 之所以把“两者同时命中”实现成原地替换，是因为这一格的旧物品被拿走、新物品进来，
     * 净效果正是玩家理解的“替换”，而且提示里能明确说出“什么被换成了什么”。
     */
    private void applyReplace(ServerPlayer player, RandomSource random) {
        Inventory inventory = player.getInventory();
        int slot = pickTargetSlot(inventory, random);
        if (slot < 0) {
            return;
        }
        ItemStack before = inventory.getItem(slot).copy();
        ItemStack after = randomStack(random, this.replaceTable, JoyConfig.lootReplaceMin, JoyConfig.lootReplaceMax);
        if (after.isEmpty()) {
            return;
        }
        inventory.setItem(slot, after.copy());
        syncInventory(player);
        notifyPlayer(player, Component.translatable("joyevents.message.loot_gamble.replace",
                before.getHoverName(), after.getHoverName(), after.getCount()));
    }

    // ==================================================================
    // 抽取与背包操作
    // ==================================================================

    /** 按权重表抽一个物品，再抽一个数量（受该物品自身堆叠上限约束）。 */
    private ItemStack randomStack(RandomSource random, ItemWeightTable table, int minCount, int maxCount) {
        List<Item> pool = this.candidates;
        if (pool == null || pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        double[] weights = new double[pool.size()];
        double total = 0.0D;
        for (int i = 0; i < pool.size(); i++) {
            double weight = Math.max(0.0D, table.weightOf(BuiltInRegistries.ITEM.getKey(pool.get(i))));
            weights[i] = weight;
            total += weight;
        }
        int index = ItemWeightTable.pickIndex(weights, random.nextDouble() * total);
        if (index < 0) {
            // 权重全为 0：抽不出任何东西（例如默认权重设为 0 但表是空的）
            return ItemStack.EMPTY;
        }
        Item item = pool.get(index);
        ItemStack probe = new ItemStack(item);
        int count = rollCount(random, minCount, maxCount);
        // 不能超过该物品自身的堆叠上限（工具类最多 1 个）
        count = Math.max(1, Math.min(count, Math.max(1, probe.getMaxStackSize())));
        return new ItemStack(item, count);
    }

    /** 在背包 36 个主格里按权重挑一个非空格子；没有可选的格子返回 -1。 */
    private int pickTargetSlot(Inventory inventory, RandomSource random) {
        int size = Inventory.INVENTORY_SIZE;
        double[] weights = new double[size];
        double total = 0.0D;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            // 黑名单物品放在背包里是安全的：不会被选中拿走、也不会被替换
            if (this.blacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                continue;
            }
            double weight = Math.max(0.0D, this.targetTable.weightOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
            weights[slot] = weight;
            total += weight;
        }
        if (total <= 0.0D) {
            return -1;
        }
        return ItemWeightTable.pickIndex(weights, random.nextDouble() * total);
    }

    private static int rollCount(RandomSource random, int minCount, int maxCount) {
        int low = Math.max(1, Math.min(minCount, maxCount));
        int high = Math.max(low, Math.max(minCount, maxCount));
        return high > low ? low + random.nextInt(high - low + 1) : low;
    }

    /** 直接改写背包后主动同步一次容器，避免客户端显示与服务器不一致。 */
    private static void syncInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void notifyPlayer(ServerPlayer player, Component message) {
        if (JoyConfig.lootGambleNotify) {
            player.displayClientMessage(message, true);
        }
    }

    // ==================================================================
    // 物品池缓存
    // ==================================================================

    /** 配置里的权重表变化时重建缓存（正常情况下只在配置改动时执行一次）。 */
    private void refreshPools(boolean force) {
        String fingerprint = String.join("|", JoyConfig.lootItemBlacklist)
                + '#' + String.join("|", JoyConfig.lootGainPool) + '#' + JoyConfig.lootGainDefaultWeight
                + '#' + String.join("|", JoyConfig.lootReplacePool) + '#' + JoyConfig.lootReplaceDefaultWeight
                + '#' + String.join("|", JoyConfig.lootTargetWeights) + '#' + JoyConfig.lootTargetDefaultWeight;
        if (!force && fingerprint.equals(this.poolFingerprint)) {
            return;
        }
        this.poolFingerprint = fingerprint;

        this.gainTable = filterMissing(ItemWeightTable.parse(JoyConfig.lootGainPool, JoyConfig.lootGainDefaultWeight), "gain_pool");
        this.replaceTable = filterMissing(ItemWeightTable.parse(JoyConfig.lootReplacePool, JoyConfig.lootReplaceDefaultWeight), "replace_pool");
        this.targetTable = filterMissing(ItemWeightTable.parse(JoyConfig.lootTargetWeights, JoyConfig.lootTargetDefaultWeight), "target_weights");

        this.blacklist = resolveBlacklist();
        List<Item> all = new ArrayList<>();
        int excluded = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (this.blacklist.contains(BuiltInRegistries.ITEM.getKey(item))) {
                excluded++;
                continue;
            }
            all.add(item);
        }
        this.candidates = List.copyOf(all);
        Joyevents.LOGGER.info("[物品赌博] 物品池已建立：可选 {} 个物品，黑名单排除 {} 个",
                this.candidates.size(), excluded);
    }

    /**
     * 解析只保证语法正确（不懂注册表，因此可以离线测试），
     * 这里再按物品注册表过滤掉“格式没问题但物品不存在”的条目并记录日志。
     */
    /** 解析黑名单：语法交给 ItemIdList，物品是否存在在这里过滤并记日志。 */
    private static Set<ResourceLocation> resolveBlacklist() {
        ItemIdList.Parsed parsed = ItemIdList.parse(JoyConfig.lootItemBlacklist);
        for (String invalid : parsed.invalidEntries()) {
            Joyevents.LOGGER.warn("[物品赌博] 忽略格式错误的黑名单条目：{}", invalid);
        }
        Set<ResourceLocation> resolved = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : parsed.ids()) {
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                resolved.add(id);
            } else {
                Joyevents.LOGGER.warn("[物品赌博] 忽略不存在的黑名单物品：{}", id);
            }
        }
        return Set.copyOf(resolved);
    }

    private static ItemWeightTable filterMissing(ItemWeightTable parsed, String name) {
        for (String invalid : parsed.invalidEntries()) {
            Joyevents.LOGGER.warn("[物品赌博] 忽略格式错误的权重条目（{}）：{}", name, invalid);
        }
        List<String> kept = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Double> entry : parsed.weights().entrySet()) {
            if (BuiltInRegistries.ITEM.containsKey(entry.getKey())) {
                kept.add(entry.getKey() + "=" + entry.getValue());
            } else {
                Joyevents.LOGGER.warn("[物品赌博] 忽略不存在的物品（{}）：{}", name, entry.getKey());
            }
        }
        return ItemWeightTable.parse(kept, parsed.defaultWeight());
    }

    // ==================================================================
    // 状态输出
    // ==================================================================

    @Override
    public Component statusDetail(MinecraftServer server) {
        List<Component> triggers = enabledTriggers();
        Component triggerList = triggers.isEmpty()
                ? Component.translatable("joyevents.status.loot_gamble.no_triggers")
                : Component.literal(String.join(", ", triggers.stream().map(Component::getString).toList()));
        return Component.translatable("joyevents.status.loot_gamble",
                format(JoyConfig.lootGainChance), format(JoyConfig.lootLossChance),
                format(OutcomeTable.replacePercent(JoyConfig.lootGainChance, JoyConfig.lootLossChance)),
                Integer.toString(JoyConfig.lootGambleCooldownTicks),
                triggerList,
                Integer.toString(this.blacklist.size()));
    }

    /** 已启用的触发方式的名称（直接复用配置项的中文标签）。 */
    private static List<Component> enabledTriggers() {
        List<Component> list = new ArrayList<>();
        addIf(list, JoyConfig.lootTriggerBlockBreak, "block_break");
        addIf(list, JoyConfig.lootTriggerEat, "eat");
        addIf(list, JoyConfig.lootTriggerJump, "jump");
        addIf(list, JoyConfig.lootTriggerHurt, "hurt");
        addIf(list, JoyConfig.lootTriggerKill, "kill_entity");
        addIf(list, JoyConfig.lootTriggerCraft, "craft");
        addIf(list, JoyConfig.lootTriggerPickup, "pickup");
        addIf(list, JoyConfig.lootTriggerUseItem, "use_item");
        addIf(list, JoyConfig.lootTriggerTimer, "timer");
        return list;
    }

    private static void addIf(List<Component> list, boolean enabled, String key) {
        if (enabled) {
            list.add(Component.translatable("joyevents.configuration.loot_gamble.triggers." + key));
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

}
