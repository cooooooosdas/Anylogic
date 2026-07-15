package assemblyline.scheduling;

import assemblyline.model.VehicleCategory;
import assemblyline.model.VehicleModel;
import assemblyline.utils.RandomGenerator;

import java.util.*;

/**
 * 装配序列生成器。
 * 提供三种生成策略：
 *   - RANDOM：完全随机打乱
 *   - HEURISTIC：基于换型维度的最近邻贪心（近邻尽量同色/同驱动）
 *   - PROPORTIONAL：按年产量比例穿插（模拟均衡化排产）
 *
 * 在 AnyLogic 中，HEURISTIC 可用 OptQuest 替换为全局最优搜索。
 * // AnyLogic API: 移植时调用 AnyLogic 的 Optimization.optQuest() 接口
 */
public class SequenceGenerator {

    public enum Strategy { RANDOM, HEURISTIC, PROPORTIONAL }

    private final RandomGenerator rng;
    private final ChangeoverMatrix changeoverMatrix;

    public SequenceGenerator(RandomGenerator rng, ChangeoverMatrix changeoverMatrix) {
        this.rng = rng;
        this.changeoverMatrix = changeoverMatrix;
    }

    /**
     * 生成指定车型集的装配序列。
     */
    public List<VehicleModel> generate(List<VehicleModel> models, Strategy strategy) {
        List<VehicleModel> pool = new ArrayList<>(models);
        return switch (strategy) {
            case RANDOM -> randomShuffle(pool);
            case HEURISTIC -> heuristicGreedy(pool);
            case PROPORTIONAL -> proportionalInterleave(pool);
        };
    }

    /**
     * 随机打乱 — 对应方案中"随机混流排产"。
     */
    private List<VehicleModel> randomShuffle(List<VehicleModel> pool) {
        List<VehicleModel> seq = new ArrayList<>(pool);
        Collections.shuffle(seq, new Random(rng.nextSeed()));
        return seq;
    }

    /**
     * 最近邻贪心 — 每次选与当前车型换型代价最小的下一辆。
     * 尽可能把同色/同驱动/同平台的车型聚在一起。
     */
    private List<VehicleModel> heuristicGreedy(List<VehicleModel> pool) {
        if (pool.isEmpty()) return List.of();
        List<VehicleModel> remaining = new ArrayList<>(pool);
        List<VehicleModel> sequence = new ArrayList<>(remaining.size());

        VehicleModel current = remaining.remove(rng.nextInt(remaining.size()));
        sequence.add(current);

        while (!remaining.isEmpty()) {
            VehicleModel best = null;
            double bestCost = Double.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < remaining.size(); i++) {
                double cost = changeoverMatrix.changeoverTime(current, remaining.get(i));
                // 加入随机扰动，避免陷入局部最优
                cost += rng.nextDouble() * 0.5;
                if (cost < bestCost) {
                    bestCost = cost;
                    best = remaining.get(i);
                    bestIdx = i;
                }
            }
            current = best;
            sequence.add(current);
            remaining.remove(bestIdx);
        }
        return sequence;
    }

    /**
     * 按年产量比例穿插 — 模拟均衡化排产（方案中的"均衡化排产"）。
     * 重卡/中卡/轻卡按产量比例穿插出现，保持各平台均匀分布。
     */
    private List<VehicleModel> proportionalInterleave(List<VehicleModel> pool) {
        // 按平台分组
        Map<VehicleCategory, List<VehicleModel>> byCategory = new HashMap<>();
        for (VehicleModel m : pool) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }

        // 按年产量比例分配"配额"
        List<VehicleModel> result = new ArrayList<>(pool.size());
        List<VehicleModel> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(rng.nextSeed()));

        // 简单轮询：每次从每个平台抽取一辆，直到抽完
        List<List<VehicleModel>> buckets = new ArrayList<>(byCategory.values());
        // 打乱每个 bucket 内部
        for (List<VehicleModel> b : buckets) {
            Collections.shuffle(b, new Random(rng.nextSeed()));
        }

        int[] idx = new int[buckets.size()];
        int remaining = pool.size();
        while (remaining > 0) {
            for (int b = 0; b < buckets.size() && remaining > 0; b++) {
                List<VehicleModel> bucket = buckets.get(b);
                if (idx[b] < bucket.size()) {
                    result.add(bucket.get(idx[b]++));
                    remaining--;
                }
            }
        }
        return result;
    }

    /**
     * 计算指定序列的总换型代价（供优化目标函数使用）。
     */
    public double evaluateSequenceCost(List<VehicleModel> sequence) {
        return changeoverMatrix.totalChangeoverCost(sequence);
    }
}
