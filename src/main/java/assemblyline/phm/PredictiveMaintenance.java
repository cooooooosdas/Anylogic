package assemblyline.phm;

import java.util.ArrayList;
import java.util.List;

/**
 * S3 预测性维修 — 基于 RUL 预测，在故障前最优窗口期维修（方案文档 3.3 节）。
 * 当预测 RUL 小于预定窗口期（如 72 小时）时触发维护。
 */
public class PredictiveMaintenance implements MaintenanceStrategy {
    private final double rulWindowHours; // RUL 预警窗口（小时）
    private final String name;

    public PredictiveMaintenance(double rulWindowHours) {
        this.rulWindowHours = rulWindowHours;
        this.name = "S3-预测性维修(RUL窗口=" + rulWindowHours + "h)";
    }

    @Override
    public List<String> shouldMaintain(double currentTime, List<EquipmentHealth> equipments) {
        List<String> toMaintain = new ArrayList<>();
        for (EquipmentHealth eq : equipments) {
            if (!eq.isOperational()) continue;
            double rul = eq.getRemainingLife();
            if (rul < 0) continue; // 尚未计算 RUL
            if (rul < rulWindowHours) {
                toMaintain.add(eq.getEquipmentId());
            }
        }
        return toMaintain;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() {
        return "RUL 预测低于 " + rulWindowHours + "h 时触发，故障预警提前量最大";
    }
}
