package MengySmod.joyevents.gameplay.util;

/**
 * 共享生命 B 模式的记账规则（纯函数，便于离线验证）。
 *
 * <p><b>为什么需要归一化（÷N）</b>：共享条只有一条，但原版的“自然回血”是<b>每名玩家各自触发</b>的。
 * 如果直接把每个人的回血都加到共享条上，人越多共享条自愈得越快（3 人就是 3 倍速），
 * 这正是旧版本“生命池持续回复”bug 的根因。因此凡是“每个身体各自触发一次”的收支，
 * 都按参与人数均摊，让共享条始终按<b>一个身体</b>的速率代谢。</p>
 *
 * <p><b>收支两类的区别</b>：</p>
 * <ul>
 *   <li>主动进食：一个人吃掉的食物就是这一条命吃到的，按原值进账（3 人时一人吃 8 点，共享饥饿条 +8）。</li>
 *   <li>被动回血（自然回血、生命恢复效果）：每个人都会各触发一次，按 ÷N 归一化。</li>
 *   <li>生理消耗（疲劳结算导致饥饿值/饱和度下降）：同样是每人各自累积的，按 ÷N 归一化。</li>
 *   <li>瞬时治疗（治疗药水、金苹果、/heal 等一次性的）：作用于这一条命，按原值进账。</li>
 * </ul>
 */
public final class PoolAccounting {
    private PoolAccounting() {}

    /** 单次回血量不超过该值即视为“被动回血”（原版自然回血与生命恢复效果的单次回血都 ≤ 2，瞬时治疗药水 ≥ 4）。 */
    public static final float PASSIVE_HEAL_THRESHOLD = 2.0F;

    /**
     * 原版交换比：回 1 点血要消耗 6 点疲劳，而 4 点疲劳结算 1 点饥饿值/饱和度 —— 即 1 点血 ≈ 1.5 点饥饿值。
     * 仅用于“关闭回血”时把被丢弃的治疗所对应的饥饿代价退回去，避免白掉饥饿值。
     */
    public static final double FOOD_PER_HEALTH = 1.5D;

    /**
     * 回血进账。
     *
     * @param amount       本次回血量
     * @param participants 参与人数 N（至少按 1 计算）
     * @return 应当加进共享条的数值
     */
    public static double healCredit(float amount, int participants) {
        if (amount <= 0.0F) {
            return 0.0D;
        }
        double n = Math.max(1, participants);
        return amount <= PASSIVE_HEAL_THRESHOLD ? amount / n : amount;
    }

    /**
     * 共享饥饿条的每 tick 收支。
     *
     * @param observedFood           本 tick 观察到的饥饿值
     * @param observedSaturation     本 tick 观察到的饱和度
     * @param lastProjectedFood      上一 tick 我们写入的饥饿值
     * @param lastPinnedSaturation   上一 tick 我们写入的饱和度
     * @param participants           参与人数 N（至少按 1 计算）
     * @return 应当叠加到共享饥饿条上的数值（进食为正、消耗为负）
     */
    public static double foodPoolDelta(double observedFood,
                                       double observedSaturation,
                                       double lastProjectedFood,
                                       double lastPinnedSaturation,
                                       int participants) {
        // 玩家主动进食：饥饿值被抬高（原值进账）
        double eaten = Math.max(0.0D, observedFood - lastProjectedFood);

        // 原版每消耗 4 点疲劳就结算 1 点：优先扣饱和度，饱和度为 0 时扣饥饿值。
        // 两种情况都等价于“从共享饥饿条里花掉 1 点”，分别用两个差值捕捉。
        double saturationDrains = Math.max(0.0D, lastPinnedSaturation - observedSaturation);
        double foodLevelDrains = Math.max(0.0D, lastProjectedFood - observedFood);
        double consumed = saturationDrains + foodLevelDrains;

        return eaten - consumed / Math.max(1, participants);
    }

    /** 共享饥饿条百分比 -> 每个人显示的饥饿值（原版饥饿值上限就是 20）。 */
    public static int projectedFoodLevel(double foodRatio) {
        return (int) Math.round(20.0D * clamp01(foodRatio));
    }

    /**
     * 共享饥饿条百分比 -> 每个人被钉住的饱和度。
     *
     * <p>饱和度在原版里是“吃得越好、回血越快”的缓冲。共享条没有“每个人各自的余粮”这个概念，
     * 所以这里把它作为共享饥饿条的派生值：饥饿条越满，饱和度越高，回血越快（与原版手感一致）。</p>
     */
    public static float pinnedSaturation(double foodRatio) {
        return (float) Math.min(5.0D, 5.0D * clamp01(foodRatio));
    }

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : (value > 1.0D ? 1.0D : value);
    }
}
