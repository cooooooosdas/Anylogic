package assemblyline.optimization;

import assemblyline.model.VehicleModel;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.scheduling.SequenceGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配序列优化器。
 * 用简单迭代改进（Hill Climbing）搜索近似最优序列。
 *
 * 替代策略：
 *  - 独立运行时：用本类实现，可工作但非全局最优。
 *  - 嵌入 AnyLogic 时：替换为 AnyLogic.Optimization.optQuest()，
 *    以本类接口为模板定义 OptQuest 变量、约束和目标。
 *
 * // AnyLogic API: Optimization.optQuest()
 * //   .addVariable("sequence", 0, N) ...
 * //   .addObjective("minimize", "totalChangeoverCost")
 */
public class SequenceOptimizer {

    private final ChangeoverMatrix changeoverMatrix;
    private final ObjectiveFunction objective;
    private final SequenceGenerator generator;
    private final int maxIterations;
    private final double defaultBottleneckUtilization;
    private final java.util.Random neighborRng;

    public SequenceOptimizer(ChangeoverMatrix changeoverMatrix,
                             ObjectiveFunction objective,
                             SequenceGenerator generator,
                             int maxIterations) {
        this(changeoverMatrix, objective, generator, maxIterations, 0.85, 42);
    }

    /**
     * @param defaultBottleneckUtilization 瓶颈利用率启发式估计（0-1），
     *                                    用于 {@link ObjectiveFunction#score} 的利用率惩罚项。
     *                                    替代硬编码 0.85，允许根据实际仿真结果校准。
     * @param neighborRngSeed             邻域搜索随机数种子，保证可复现。
     *                                    传 {@code -1} 使用系统时钟熵。
     */
    public SequenceOptimizer(ChangeoverMatrix changeoverMatrix,
                             ObjectiveFunction objective,
                             SequenceGenerator generator,
                             int maxIterations,
                             double defaultBottleneckUtilization,
                             long neighborRngSeed) {
        this.changeoverMatrix = changeoverMatrix;
        this.objective = objective;
        this.generator = generator;
        this.maxIterations = maxIterations;
        this.defaultBottleneckUtilization = defaultBottleneckUtilization;
        this.neighborRng = neighborRngSeed >= 0
                ? new java.util.Random(neighborRngSeed)
                : new java.util.Random();
    }

    /**
     * 优化装配序列。
     * @param candidatePool 可用车型池（多份，按年产量比例扩充）
     * @param targetLength 目标序列长度（辆）
     * @return 优化后的序列
     */
    public List<VehicleModel> optimize(List<VehicleModel> candidatePool, int targetLength) {
        // 初始解：启发式贪心
        List<VehicleModel> current = generator.generate(
                expandPool(candidatePool, targetLength),
                SequenceGenerator.Strategy.HEURISTIC);

        double currentScore = objective.score(current, defaultBottleneckUtilization);

        List<VehicleModel> best = new ArrayList<>(current);
        double bestScore = currentScore;

        int noImprove = 0;
        for (int iter = 0; iter < maxIterations; iter++) {
            // 邻域操作：随机交换两个位置
            List<VehicleModel> neighbor = swapMove(current);
            double neighborScore = objective.score(neighbor, defaultBottleneckUtilization);

            if (neighborScore < currentScore) {
                current = neighbor;
                currentScore = neighborScore;
                noImprove = 0;

                if (currentScore < bestScore) {
                    best = new ArrayList<>(current);
                    bestScore = currentScore;
                }
            } else {
                noImprove++;
                // 早期接受（模拟退火风格）：以概率接受劣化解
                if (noImprove > 50 && neighborRng.nextDouble() < 0.05) {
                    current = neighbor;
                    currentScore = neighborScore;
                    noImprove = 0;
                }
            }

            if (noImprove > 200) {
                current = generator.generate(
                        expandPool(candidatePool, targetLength),
                        SequenceGenerator.Strategy.HEURISTIC);
                currentScore = objective.score(current, defaultBottleneckUtilization);
                noImprove = 0;
            }
        }

        return best;
    }

    // ── 辅助 ──────────────────────────────────────────────

    private List<VehicleModel> swapMove(List<VehicleModel> seq) {
        List<VehicleModel> copy = new ArrayList<>(seq);
        int i = neighborRng.nextInt(seq.size());
        int j = neighborRng.nextInt(seq.size());
        if (i == j) return copy;
        VehicleModel tmp = copy.get(i);
        copy.set(i, copy.get(j));
        copy.set(j, tmp);
        return copy;
    }

    /**
     * 按年产量比例扩充车型池到目标长度。
     */
    private List<VehicleModel> expandPool(List<VehicleModel> uniqueModels, int targetLength) {
        List<VehicleModel> expanded = new ArrayList<>(targetLength);
        int totalVolume = uniqueModels.stream().mapToInt(VehicleModel::getAnnualVolume).sum();
        for (VehicleModel m : uniqueModels) {
            int count = Math.max(1,
                    (int) Math.round((double) m.getAnnualVolume() / totalVolume * targetLength));
            for (int i = 0; i < count && expanded.size() < targetLength; i++) {
                expanded.add(m);
            }
        }
        // 补齐或截断
        while (expanded.size() < targetLength) expanded.add(uniqueModels.get(0));
        while (expanded.size() > targetLength) expanded.remove(expanded.size() - 1);
        return expanded;
    }
}
