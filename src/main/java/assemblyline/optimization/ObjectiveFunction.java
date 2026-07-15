package assemblyline.optimization;

import assemblyline.model.VehicleModel;
import assemblyline.scheduling.ChangeoverMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * 多目标函数：最小化换型代价 + 最大化瓶颈利用率。
 *
 * 复合得分 = w1 * (归一化换型代价) + w2 * (1 - 瓶颈利用率)
 * 得分越低越好。
 *
 * 在 AnyLogic 中，此逻辑可直接替换为 OptQuest 的目标函数定义。
 * // AnyLogic API: Optimization.optQuest().addObjective(...)
 */
public class ObjectiveFunction {

    private final ChangeoverMatrix changeoverMatrix;
    private final double weightChangeover;
    private final double weightUtilization;

    // 基准值（用于归一化，第一次评估时计算）
    private double baselineChangeoverCost = -1;

    public ObjectiveFunction(ChangeoverMatrix changeoverMatrix,
                              double weightChangeover, double weightUtilization) {
        this.changeoverMatrix = changeoverMatrix;
        this.weightChangeover = weightChangeover;
        this.weightUtilization = weightUtilization;
    }

    /**
     * 计算序列的综合得分（越低越好）。
     */
    public double score(List<VehicleModel> sequence, double bottleneckUtilization) {
        double changeoverCost = changeoverMatrix.totalChangeoverCost(sequence);

        // 第一次评估时记录基准
        if (baselineChangeoverCost < 0 || changeoverCost < baselineChangeoverCost * 0.5) {
            baselineChangeoverCost = changeoverCost;
        }

        // 归一化
        double normChangeover = baselineChangeoverCost > 0
                ? changeoverCost / baselineChangeoverCost : 1;
        double normUtilGap = 1.0 - bottleneckUtilization; // 利用率越高越好

        return weightChangeover * normChangeover + weightUtilization * normUtilGap;
    }

    /**
     * 仅计算换型代价（用于快速预评估）。
     */
    public double changeoverCostOnly(List<VehicleModel> sequence) {
        return changeoverMatrix.totalChangeoverCost(sequence);
    }

    public void resetBaseline() { baselineChangeoverCost = -1; }

    @Override
    public String toString() {
        return "Objective(w_changeover=" + weightChangeover
                + ", w_utilization=" + weightUtilization + ")";
    }
}
