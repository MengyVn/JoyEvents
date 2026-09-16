package MengySmod.joyevents.gameplay.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 物品 ID 列表的解析（纯函数，便于离线验证）。
 *
 * <p>与 {@link ItemWeightTable} 一样，这里只做<b>语法</b>校验，不接触物品注册表：
 * “物品是否存在”交给配置校验器和上层过滤负责。这样这一层可以在没有游戏环境的 JVM 里直接断言。</p>
 */
public final class ItemIdList {
    private ItemIdList() {}

    /** 解析结果：合法的 ID 集合 + 被忽略的非法条目。 */
    public record Parsed(Set<ResourceLocation> ids, List<String> invalidEntries) {}

    public static Parsed parse(List<? extends String> entries) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(raw.trim());
                if (id == null) {
                    invalid.add(raw);
                } else {
                    ids.add(id);
                }
            }
        }
        return new Parsed(Set.copyOf(ids), List.copyOf(invalid));
    }
}
