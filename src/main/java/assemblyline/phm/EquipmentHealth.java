package assemblyline.phm;

import java.util.Objects;

/**
 * 单台设备的健康状态快照。
 * 记录了 HI（健康指数）、状态、累计运行时间、故障/维修次数。
 */
public class EquipmentHealth {
    private final String equipmentId;
    private final String equipmentName;

    private double healthIndex;       // 0~100
    private HealthState state;
    private double cumulativeHours;   // 累计运行时间
    private int failureCount;
    private int maintenanceCount;
    private double lastMaintenanceTime;
    private double timeSinceMaintenance;

    // Weibull 参数（从 equipment_params.json 加载）
    private final double weibullBeta;
    private final double weibullEta;
    /** η 覆盖值，-1 表示未设置（使用 weibullEta）。用于敏感性分析。 */
    private double etaOverride = -1;
    private double remainingLife; // 预测的剩余寿命（小时），-1 表示未计算

    // MTTR 参数（从 equipment_params.json 加载）
    private final double mttrMin;
    private final double mttrMean;
    private final double mttrMax;

    // 归属工位（用于 SimulationEngine 将加工推进到对应设备）
    private final String stationId;

    public EquipmentHealth(String equipmentId, String equipmentName,
                           double weibullBeta, double weibullEta,
                           double mttrMin, double mttrMean, double mttrMax,
                           String stationId) {
        this.equipmentId = Objects.requireNonNull(equipmentId);
        this.equipmentName = Objects.requireNonNull(equipmentName);
        this.weibullBeta = weibullBeta;
        this.weibullEta = weibullEta;
        this.mttrMin = mttrMin;
        this.mttrMean = mttrMean;
        this.mttrMax = mttrMax;
        this.stationId = stationId;
        this.healthIndex = 100.0;
        this.state = HealthState.NORMAL;
        this.cumulativeHours = 0;
        this.failureCount = 0;
        this.maintenanceCount = 0;
        this.lastMaintenanceTime = 0;
        this.timeSinceMaintenance = 0;
        this.remainingLife = -1;
    }

    // 兼容旧构造（用于初始化测试，stationId 为空）
    public EquipmentHealth(String equipmentId, String equipmentName,
                           double weibullBeta, double weibullEta,
                           double mttrMin, double mttrMean, double mttrMax) {
        this(equipmentId, equipmentName, weibullBeta, weibullEta,
                mttrMin, mttrMean, mttrMax, null);
    }

    // 更旧兼容（4 参数，MTTR 使用默认值，stationId 为空）
    public EquipmentHealth(String equipmentId, String equipmentName,
                           double weibullBeta, double weibullEta) {
        this(equipmentId, equipmentName, weibullBeta, weibullEta, 1.5, 3.0, 6.0, null);
    }

    // Getters
    public String getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName; }
    public double getHealthIndex() { return healthIndex; }
    public HealthState getState() { return state; }
    public double getCumulativeHours() { return cumulativeHours; }
    public int getFailureCount() { return failureCount; }
    public int getMaintenanceCount() { return maintenanceCount; }
    public double getLastMaintenanceTime() { return lastMaintenanceTime; }
    public double getTimeSinceMaintenance() { return timeSinceMaintenance; }
    public double getRemainingLife() { return remainingLife; }
    public double getWeibullBeta() { return weibullBeta; }
    /** 返回 η 值（优先使用覆盖值，用于敏感性分析）。 */
    public double getWeibullEta() { return etaOverride > 0 ? etaOverride : weibullEta; }
    public double getOriginalWeibullEta() { return weibullEta; }
    public void setEtaOverride(double eta) { this.etaOverride = eta; }
    public void clearEtaOverride() { this.etaOverride = -1; }
    public double getMttrMin() { return mttrMin; }
    public double getMttrMean() { return mttrMean; }
    public double getMttrMax() { return mttrMax; }
    public String getStationId() { return stationId; }

    // 状态更新
    public void incrementHours(double hours) {
        cumulativeHours += hours;
        timeSinceMaintenance += hours;
    }

    public void degrade(double amount) {
        healthIndex = Math.max(0, healthIndex - amount);
        if (healthIndex <= 20 && state != HealthState.FAILED) {
            state = HealthState.FAILED;
            failureCount++;
        } else if (healthIndex <= 70 && state == HealthState.NORMAL) {
            state = HealthState.DEGRADED;
        }
    }

    public void performMaintenance(double currentTime) {
        healthIndex = 100.0;
        state = HealthState.NORMAL;
        maintenanceCount++;
        lastMaintenanceTime = currentTime;
        timeSinceMaintenance = 0;
        remainingLife = -1;
    }

    public void setRemainingLife(double rul) {
        this.remainingLife = rul;
    }

    public boolean isOperational() { return state != HealthState.FAILED; }
}
