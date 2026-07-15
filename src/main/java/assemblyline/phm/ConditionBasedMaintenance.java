package assemblyline.phm;

import java.util.ArrayList;
import java.util.List;

/**
 * S2 视情维修 — HI 低于阈值时触发（方案文档 3.3 节）。
 * 依赖传感器实时读数，在劣化阶段早期干预。
 */
public class ConditionBasedMaintenance implements MaintenanceStrategy {
    private final double hiWarningThreshold; // HI 低于此值触发维护
    private final String name;

    public ConditionBasedMaintenance(double hiWarningThreshold) {
        this.hiWarningThreshold = hiWarningThreshold;
        this.name = "S2-视情维修(HI阈值=" + hiWarningThreshold + ")";
    }

    @Override
    public List<String> shouldMaintain(double currentTime, List<EquipmentHealth> equipments) {
        List<String> toMaintain = new ArrayList<>();
        for (EquipmentHealth eq : equipments) {
            if (eq.getHealthIndex() < hiWarningThreshold && eq.isOperational()) {
                toMaintain.add(eq.getEquipmentId());
            }
        }
        return toMaintain;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() {
        return "HI 低于 " + hiWarningThreshold + " 时触发，仍需人工设定阈值";
    }
}
