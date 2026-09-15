package MengySmod.joyevents.gameplay.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 共享生命 B 模式（共用一条命）的持久化状态。
 *
 * <p>共享条是唯一真实数值：所有玩家显示同一条血条与同一条饥饿条，
 * 每人血条上的数值 = 各自最大生命 × (共享值 / 上限)，默认上限 20 时即为共享值本身。</p>
 *
 * <p>数值口径在第 2 版发生过变化（旧版本是“总量 100 的抽象血池”，
 * 新版是“上限 20 = 一条原版血条”）。老存档里的数值无法直接沿用，
 * 因此这里带 {@link #DATA_VERSION}：读到旧存档时把共享条重置为满值，避免出现 82/100 突然变成 82/20 的错乱。</p>
 */
public class SharedHealthPoolData extends SavedData {
    public static final String DATA_NAME = "joyevents_shared_health_pool";
    /** 当前数值口径版本。1 = 旧抽象血池；2 = 共用一条原版血条（当前）。 */
    public static final int DATA_VERSION = 2;

    private int dataVersion = DATA_VERSION;
    private double health;
    private double food;
    private double maxHealth;
    private double maxFood;

    public SharedHealthPoolData() {
        this(0.0D, 0.0D, 20.0D, 20.0D);
    }

    public SharedHealthPoolData(double health, double food, double maxHealth, double maxFood) {
        this.maxHealth = Math.max(1.0D, maxHealth);
        this.maxFood = Math.max(1.0D, maxFood);
        this.health = clamp(health, 0.0D, this.maxHealth);
        this.food = clamp(food, 0.0D, this.maxFood);
    }

    public static SavedData.Factory<SharedHealthPoolData> factory() {
        return new SavedData.Factory<>(SharedHealthPoolData::new, SharedHealthPoolData::load);
    }

    public static SharedHealthPoolData load(CompoundTag tag, HolderLookup.Provider registries) {
        SharedHealthPoolData data = new SharedHealthPoolData();
        data.dataVersion = tag.contains("data_version") ? tag.getInt("data_version") : 1;
        data.maxHealth = Math.max(1.0D, tag.contains("max_health") ? tag.getDouble("max_health") : 20.0D);
        data.maxFood = Math.max(1.0D, tag.contains("max_food") ? tag.getDouble("max_food") : 20.0D);
        data.health = clamp(tag.contains("health") ? tag.getDouble("health") : data.maxHealth, 0.0D, data.maxHealth);
        data.food = clamp(tag.contains("food") ? tag.getDouble("food") : data.maxFood, 0.0D, data.maxFood);

        if (data.dataVersion < DATA_VERSION) {
            // 旧口径的数值没有可比性，直接按新口径重置为满值（上限交给配置同步时再校准）
            data.health = data.maxHealth;
            data.food = data.maxFood;
            data.dataVersion = DATA_VERSION;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("data_version", this.dataVersion);
        tag.putDouble("health", this.health);
        tag.putDouble("food", this.food);
        tag.putDouble("max_health", this.maxHealth);
        tag.putDouble("max_food", this.maxFood);
        return tag;
    }

    public double getHealth() {
        return this.health;
    }

    public double getFood() {
        return this.food;
    }

    public double getMaxHealthPool() {
        return this.maxHealth;
    }

    public double getMaxFoodPool() {
        return this.maxFood;
    }

    public double healthRatio() {
        return this.maxHealth <= 0 ? 0.0D : clamp(this.health / this.maxHealth, 0.0D, 1.0D);
    }

    public double foodRatio() {
        return this.maxFood <= 0 ? 0.0D : clamp(this.food / this.maxFood, 0.0D, 1.0D);
    }

    /** 扣血，返回扣完之后是否已经耗尽。 */
    public boolean damage(double amount) {
        if (amount > 0) {
            this.health = clamp(this.health - amount, 0.0D, this.maxHealth);
            this.setDirty();
        }
        return this.health <= 0.0D;
    }

    public void addHealth(double amount) {
        if (amount != 0) {
            this.health = clamp(this.health + amount, 0.0D, this.maxHealth);
            this.setDirty();
        }
    }

    public void addFood(double amount) {
        if (amount != 0) {
            this.food = clamp(this.food + amount, 0.0D, this.maxFood);
            this.setDirty();
        }
    }

    /** 全员死亡后按比例重置。 */
    public void resetTo(double ratio) {
        double safeRatio = clamp(ratio, 0.01D, 1.0D);
        this.health = this.maxHealth * safeRatio;
        this.food = this.maxFood * safeRatio;
        this.setDirty();
    }

    public void resetFull() {
        this.health = this.maxHealth;
        this.food = this.maxFood;
        this.setDirty();
    }

    /**
     * 同步配置里的上限。上限变化时按新旧比例缩放当前值，
     * 避免“把共享血条上限从 20 改成 10 的瞬间全员暴毙”。
     */
    public void syncMaxima(double newMaxHealth, double newMaxFood) {
        double healthRatio = this.healthRatio();
        double foodRatio = this.foodRatio();
        if (Math.abs(newMaxHealth - this.maxHealth) > 1.0E-4D) {
            this.maxHealth = Math.max(1.0D, newMaxHealth);
            this.health = this.maxHealth * healthRatio;
            this.setDirty();
        }
        if (Math.abs(newMaxFood - this.maxFood) > 1.0E-4D) {
            this.maxFood = Math.max(1.0D, newMaxFood);
            this.food = this.maxFood * foodRatio;
            this.setDirty();
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
