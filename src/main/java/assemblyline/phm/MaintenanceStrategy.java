package assemblyline.phm;

import java.util.List;

/**
 * 维护策略接口。
 * 三种策略（方案文档 3.3 节）：
 *   S1：计划性维修（固定周期）
 *   S2：视情维修（HI 低于阈值触发）
 *   S3：预测性维修（基于 RUL 预测窗口期）
 */
public interface MaintenanceStrategy {

    /**
     * 给定当前时间和所有设备健康状态，判断是否需要进行预防性维护。
     * @param currentTime 仿真当前时刻（小时）
     * @param equipments 该产线关联的所有设备
     * @return 需要维护的设备 ID 列表，空列表=无需维护
     */
    List<String> shouldMaintain(double currentTime, List<EquipmentHealth> equipments);

    /**
     * 策略名称（用于实验报告标注）。
     */
    String getName();

    /**
     * 获取策略描述（用于报告）。
     */
    String getDescription();
}
