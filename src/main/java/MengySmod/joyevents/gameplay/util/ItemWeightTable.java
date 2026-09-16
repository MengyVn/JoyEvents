package MengySmod.joyevents.gameplay.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * 加权物品表：解析配置里的 {@code "物品ID=权重"} 条目，并提供加权抽取所需的权重查询。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>表为空 = 完全随机（调用方对所有候选物品使用同一个默认权重）。</li>
 *   <li>表非空 = 表内物品用表里的权重，表外物品用默认权重；默认权重设为 0 就变成“只从表里抽”。</li>
 *   <li>解析与抽取都是纯函数/纯数据，不接触游戏世界，因此可以离线断言。</li>
 * </ul>
 */
public final class ItemWeightTable {
    /** 已解析的权重表；键是物品 ID，值是权重。 */
    private final Map<ResourceLocation, Double> weights;
    private final double defaultWeight;
    /** 解析时被忽略的条目（物品不存在、格式错误等），用于日志提示。 */
    private final List<String> invalidEntries;

    private ItemWeightTable(Map<ResourceLocation, Double> weights, double defaultWeight, List<String> invalidEntries) {
        this.weights = weights;
        this.defaultWeight = defaultWeight;
        this.invalidEntries = invalidEntries;
    }

    /**
     * 解析条目列表。无法解析的条目不会抛异常，而是收集到 {@link #invalidEntries()} 里。
     *
     * @param entries       {@code "minecraft:diamond=0.5"} 形式的条目
     * @param defaultWeight 未列出物品的权重
     */
    public static ItemWeightTable parse(@Nullable List<? extends String> entries, double defaultWeight) {
        Map<ResourceLocation, Double> parsed = new LinkedHashMap<>();
        List<String> invalid = new java.util.ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                int split = raw.lastIndexOf('=');
                ResourceLocation id = split > 0 ? ResourceLocation.tryParse(raw.substring(0, split).trim()) : null;
                Double weight = null;
                if (split > 0 && split < raw.length() - 1) {
                    try {
                        weight = Double.parseDouble(raw.substring(split + 1).trim());
                    } catch (NumberFormatException ignored) {
                        // 落到下面的 invalid 分支
                    }
                }
                if (id == null || weight == null || weight < 0.0D) {
                    invalid.add(raw);
                    continue;
                }
                parsed.put(id, weight);
            }
        }
        return new ItemWeightTable(parsed, Math.max(0.0D, defaultWeight), List.copyOf(invalid));
    }

    public Map<ResourceLocation, Double> weights() {
        return this.weights;
    }

    public double defaultWeight() {
        return this.defaultWeight;
    }

    public List<String> invalidEntries() {
        return this.invalidEntries;
    }

    public boolean isEmpty() {
        return this.weights.isEmpty();
    }

    /** 某个物品 ID 的权重（未列出则返回默认权重）。 */
    public double weightOf(ResourceLocation id) {
        Double configured = this.weights.get(id);
        return configured != null ? configured : this.defaultWeight;
    }

    /**
     * 加权抽取下标（纯函数）。
     *
     * @param weights 各候选的权重（与候选列表一一对应）
     * @param roll    在 [0, total) 上的随机数
     * @return 选中的下标；权重合计为 0 或列表为空时返回 -1（表示“抽不出来”）
     */
    public static int pickIndex(double[] weights, double roll) {
        if (weights == null || weights.length == 0) {
            return -1;
        }
        double total = 0.0D;
        for (double weight : weights) {
            if (weight > 0.0D) {
                total += weight;
            }
        }
        if (total <= 0.0D) {
            return -1;
        }
        double position = roll;
        if (position < 0.0D) {
            position = 0.0D;
        }
        if (position >= total) {
            position = total * 0.999999D;
        }
        double accumulated = 0.0D;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0.0D) {
                continue;
            }
            accumulated += weights[i];
            if (position < accumulated) {
                return i;
            }
        }
        return -1;
    }
}
