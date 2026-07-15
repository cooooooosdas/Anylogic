package assemblyline.phm;

import java.util.ArrayList;
import java.util.List;

/**
 * S1 计划性维修 — 固定周期保养（方案文档 3.3 节）。
 * 每 T 小时强制保养一次，不考虑设备实际健康状态。
 */
public class ScheduledMaintenance implements MaintenanceStrategy {
    private final double intervalHours; // 保养周期（小时）
    private final String name;

    public ScheduledMaintenance(double intervalHours) {
        this.intervalHours = intervalHours;
        this.name = "S1-计划性维修(周期=" + intervalHours + "h)";
    }

    @Override
    public List<String> shouldMaintain(double currentTime, List<EquipmentHealth> equipments) {
        List<String> toMaintain = new ArrayList<>();
        for (EquipmentHealth eq : equipments) {
            if (eq.getTimeSinceMaintenance() >= intervalHours && eq.isOperational()) {
                toMaintain.add(eq.getEquipmentId());
            }
        }
        return toMaintain;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() {
        return "固定周期 " + intervalHours + "h 保养，易造成过度维护或维护不足";
    }
}
