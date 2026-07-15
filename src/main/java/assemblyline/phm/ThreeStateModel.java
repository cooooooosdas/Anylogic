package assemblyline.phm;

import assemblyline.utils.Distribution;
import assemblyline.utils.RandomGenerator;

/**
 * 设备三状态转移模型（方案文档 3.3 节、图5）。
 * 状态：NORMAL → DEGRADED → FAILED。
 * NORMAL→DEGRADED：基于 Weibull 随机寿命。
 * DEGRADED→FAILED：指数分布（剩余寿命）。
 * FAILED→NORMAL：维修后恢复（由 MaintenanceStrategy 触发）。
 *
 * 在仿真中，每次调用 advance() 推进设备运行时间并更新健康状态。
 */
public class ThreeStateModel {

    private final EquipmentHealth health;
    private final RandomGenerator rng;

    // 劣化→故障的指数均值（小时），从配置加载
    private final double degradedToFailedMean;

    // 健康指数每小时劣化速率（NORMAL 和 DEGRADED 各不同）
    private final double normalDegradationRate;
    private final double degradedDegradationRate;

    // 生成的下次失效时间（小时），-1 表示未生成
    private double nextFailureTime;

    // 劣化状态剩余寿命（小时），-1 表示当前未劣化
    private double degradedRemaining;

    public ThreeStateModel(EquipmentHealth health, RandomGenerator rng,
                           double degradedToFailedMean,
                           double normalDegradationRate,
                           double degradedDegradationRate) {
        this.health = health;
        this.rng = rng;
        this.degradedToFailedMean = degradedToFailedMean;
        this.normalDegradationRate = normalDegradationRate;
        this.degradedDegradationRate = degradedDegradationRate;
        this.nextFailureTime = -1;
    }

    /**
     * 推进设备运行指定小时数，更新健康状态。
     * @param operatingHours 本次运行时长（设备实际加工时间，不含故障等待）
     * @return 本次推进后是否发生故障（true=刚进入 FAILED）
     */
    public boolean advance(double operatingHours) {
        if (health.getState() == HealthState.FAILED) return true;

        // 维修后恢复：重置追踪变量
        if (health.getState() == HealthState.NORMAL && degradedRemaining > 0) {
            nextFailureTime = -1;
            degradedRemaining = -1;
        }

        health.incrementHours(operatingHours);

        if (health.getState() == HealthState.NORMAL) {
            if (nextFailureTime < 0) {
                nextFailureTime = Distribution.weibull(rng,
                        health.getWeibullBeta(), health.getWeibullEta());
            }
            if (health.getCumulativeHours() >= nextFailureTime) {
                // 进入劣化：HI 一次性降至预警区间，并采样劣化→故障剩余寿命
                health.degrade(100 - 60); // HI: 100→60（预警阈值）
                nextFailureTime = -1;
                degradedRemaining = Distribution.exponential(rng, degradedToFailedMean);
            } else {
                health.degrade(normalDegradationRate * operatingHours);
            }
        } else if (health.getState() == HealthState.DEGRADED) {
            // 劣化计时器递减，耗尽即故障
            degradedRemaining -= operatingHours;
            if (degradedRemaining <= 0) {
                health.degrade(100); // 强制进入故障
            } else {
                health.degrade(degradedDegradationRate * operatingHours);
            }
        }

        return health.getState() == HealthState.FAILED;
    }

    /**
     * 基于当前健康指数预测剩余使用寿命（RUL），简化版。
     * 劣化状态优先返回指数分布的剩余计时器，否则线性外推。
     */
    public double predictRUL() {
        double hi = health.getHealthIndex();
        if (health.getState() == HealthState.DEGRADED && degradedRemaining > 0) {
            double rul = degradedRemaining;
            health.setRemainingLife(rul);
            return rul;
        }
        double rate = (health.getState() == HealthState.DEGRADED)
                ? degradedDegradationRate : normalDegradationRate;
        if (rate <= 0) return 720; // 保守估计
        double rul = hi / rate;
        health.setRemainingLife(rul);
        return rul;
    }

    /**
     * 重置模型（新一次蒙特卡洛运行时调用）。
     */
    public void reset(RandomGenerator newRng) {
        // 通过新 rng 重新采样 Weibull 寿命
        this.nextFailureTime = Distribution.weibull(newRng,
                health.getWeibullBeta(), health.getWeibullEta());
    }
}
