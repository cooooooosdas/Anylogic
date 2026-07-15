package assemblyline.scheduling;

import assemblyline.model.VehicleModel;
import assemblyline.utils.Distribution;

import java.util.HashMap;
import java.util.Map;

/**
 * 换型时间查询矩阵。
 * 根据前后两种车型的换型维度差异，返回对应的换型时间。
 *
 * 设计意图：与 data/changeover_matrix.json 参数保持一致，
 * 移植到 AnyLogic 时可直接用 AnyLogic 的 SelectOutput 或 Lookup Table 替换。
 *
 * // AnyLogic API: 移植时可用 AnyLogic 内置的 Lookup Table 实现等价查询
 */
public class ChangeoverMatrix {

    // 各维度的均值（分钟）和标准差
    private final double colorMean, colorSigma;
    private final double driveMean, driveSigma;
    private final double engineMean, engineSigma;
    private final double platformMean, platformSigma;

    // 特殊对（从 data/changeover_matrix.json pairwiseTimes）
    private final Map<String, Double> drivePairwise;
    private final Map<String, Double> enginePairwise;
    private final Map<String, Double> platformPairwise;

    public ChangeoverMatrix(double colorMean, double colorSigma,
                            double driveMean, double driveSigma,
                            double engineMean, double engineSigma,
                            double platformMean, double platformSigma,
                            Map<String, Double> drivePairwise,
                            Map<String, Double> enginePairwise,
                            Map<String, Double> platformPairwise) {
        this.colorMean = colorMean; this.colorSigma = colorSigma;
        this.driveMean = driveMean; this.driveSigma = driveSigma;
        this.engineMean = engineMean; this.engineSigma = engineSigma;
        this.platformMean = platformMean; this.platformSigma = platformSigma;
        this.drivePairwise = drivePairwise;
        this.enginePairwise = enginePairwise;
        this.platformPairwise = platformPairwise;
    }

    /**
     * 计算从 prev 到 next 的换型时间（分钟）。
     * 多维换型叠加取最大值（保守估计）。
     */
    public double changeoverTime(VehicleModel prev, VehicleModel next) {
        if (prev == null || next == null) return 0;

        // 同车型无需换型
        if (prev.getId().equals(next.getId())) return 0;

        double platformTime = platformChangeover(prev.getCategory(), next.getCategory());
        double colorTime    = colorChangeover(prev.getColor(), next.getColor());
        double driveTime    = driveChangeover(prev.getDriveType(), next.getDriveType());
        double engineTime   = engineChangeover(prev.getEngineType(), next.getEngineType());

        // 平台切换已覆盖所有子维度，直接返回
        if (platformTime > 0) return platformTime;

        // 否则取各维度最大值
        return Math.max(0, Math.max(colorTime, Math.max(driveTime, engineTime)));
    }

    /**
     * 总换型代价（用于序列优化目标函数）。
     */
    public double totalChangeoverCost(java.util.List<VehicleModel> sequence) {
        double total = 0;
        for (int i = 1; i < sequence.size(); i++) {
            total += changeoverTime(sequence.get(i - 1), sequence.get(i));
        }
        return total;
    }

    // ── 私有实现 ──────────────────────────────────────────────

    private double platformChangeover(Object p1, Object p2) {
        if (p1.equals(p2)) return 0;
        String key = p1 + "→" + p2;
        Double v = platformPairwise.get(key);
        return v != null ? v : platformMean;
    }

    private double colorChangeover(Object c1, Object c2) {
        if (c1.equals(c2)) return 0;
        String key = c1 + "→" + c2;
        // 注：当前 ConfigLoader 未加载 color pairwise 表，预留扩展
        return colorMean;
    }

    private double driveChangeover(Object d1, Object d2) {
        if (d1.equals(d2)) return 0;
        String key = d1 + "→" + d2;
        Double v = drivePairwise.get(key);
        return v != null ? v : driveMean;
    }

    private double engineChangeover(Object e1, Object e2) {
        if (e1.equals(e2)) return 0;
        String key = e1 + "→" + e2;
        Double v = enginePairwise.get(key);
        return v != null ? v : engineMean;
    }
}
