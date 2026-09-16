package MengySmod.joyevents.gameplay.util;

/**
 * 【物品赌博】的结果抽取规则（纯函数，便于离线验证）。
 *
 * <p><b>模型</b>：获得与丢失是<b>两次互相独立的掷骰</b>，各自按配置概率决定这一次是否发生。
 * 因此一共有四种组合：</p>
 *
 * <table border="1">
 *   <caption>四种组合</caption>
 *   <tr><th>获得</th><th>丢失</th><th>结果</th></tr>
 *   <tr><td>命中</td><td>命中</td><td>{@link Outcome#REPLACE 替换}——丢了一格、又进了一格，净效果就是"替换"</td></tr>
 *   <tr><td>命中</td><td>未命中</td><td>{@link Outcome#GAIN 获得}</td></tr>
 *   <tr><td>未命中</td><td>命中</td><td>{@link Outcome#LOSS 丢失}</td></tr>
 *   <tr><td>未命中</td><td>未命中</td><td>{@link Outcome#NOTHING 什么都不发生}</td></tr>
 * </table>
 *
 * <p>由此自然得到两条结论：</p>
 * <ul>
 *   <li>两个概率都非 0 时，才会出现“替换”，其概率 = 获得概率 × 丢失概率（默认 50% × 50% = 25%）。</li>
 *   <li>任意一方为 0 时，替换永远不可能发生，结果必然是纯粹的获得或丢失。</li>
 * </ul>
 */
public final class OutcomeTable {
    private OutcomeTable() {}

    public enum Outcome {
        NOTHING,
        GAIN,
        LOSS,
        /** 获得与丢失同时命中：一格物品被拿走，同时又有新物品进来。 */
        REPLACE
    }

    /**
     * 抽取结果。
     *
     * @param gainPercent 获得的概率（0~100）
     * @param lossPercent 丢失的概率（0~100）
     * @param gainRoll    获得掷骰，取值 [0, 100)
     * @param lossRoll    丢失掷骰，取值 [0, 100)
     */
    public static Outcome roll(double gainPercent, double lossPercent, double gainRoll, double lossRoll) {
        boolean gained = gainRoll < clampPercent(gainPercent);
        boolean lost = lossRoll < clampPercent(lossPercent);
        if (gained && lost) {
            return Outcome.REPLACE;
        }
        if (gained) {
            return Outcome.GAIN;
        }
        if (lost) {
            return Outcome.LOSS;
        }
        return Outcome.NOTHING;
    }

    /** 由两个独立概率推导出“替换”的概率（百分比），用于配置说明与状态输出。 */
    public static double replacePercent(double gainPercent, double lossPercent) {
        return clampPercent(gainPercent) * clampPercent(lossPercent) / 100.0D;
    }

    private static double clampPercent(double value) {
        return value < 0.0D ? 0.0D : (value > 100.0D ? 100.0D : value);
    }
}
