package assemblyline.simulation;

import assemblyline.data.ConfigLoader;
import assemblyline.model.AssemblyLine;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.utils.RandomGenerator;

import java.nio.file.Path;
import java.util.*;

/**
 * 蒙特卡洛批量运行器。
 * 对指定场景独立运行 N 次，每次使用不同的随机种子，汇总统计结果。
 *
 * 方案文档 4.3 节：1000 次独立运行，30 天周期，相同随机种子序列实现配对比较。
 */
public class MonteCarloRunner {

    private final AssemblyLine assemblyLine;
    private final java.util.List<assemblyline.model.VehicleModel> modelPool;
    private final ChangeoverMatrix changeoverMatrix;
    private final MaintenanceStrategy maintenanceStrategy;
    private final int numRuns;
    private final double simDurationMinutes;
    private final double warmUpMinutes;
    private final double arrivalMeanMinutes;
    private final long baseSeed;

    public MonteCarloRunner(AssemblyLine assemblyLine,
                            java.util.List<assemblyline.model.VehicleModel> modelPool,
                            ChangeoverMatrix changeoverMatrix,
                            MaintenanceStrategy maintenanceStrategy,
                            int numRuns,
                            double simDurationMinutes,
                            double warmUpMinutes,
                            double arrivalMeanMinutes,
                            long baseSeed) {
        this.assemblyLine = assemblyLine;
        this.modelPool = modelPool;
        this.changeoverMatrix = changeoverMatrix;
        this.maintenanceStrategy = maintenanceStrategy;
        this.numRuns = numRuns;
        this.simDurationMinutes = simDurationMinutes;
        this.warmUpMinutes = warmUpMinutes;
        this.arrivalMeanMinutes = arrivalMeanMinutes;
        this.baseSeed = baseSeed;
    }

    /**
     * 执行批量运行，返回统计汇总，同时将每次运行的原始结果写出到 CSV。
     *
     * @param scenarioLabel  CSV 文件名后缀，如 "baseline" / "optimized"
     * @param outputDir      输出目录（为 null 时不写 CSV）
     */
    public StatisticsCollector.Summary run(String scenarioLabel, Path outputDir) {
        List<SimulationEngine.RunResult> all = runRaw();
        if (outputDir != null) {
            try {
                StatisticsCollector.exportRunCsv(all, scenarioLabel, outputDir);
            } catch (java.io.IOException e) {
                System.err.println("[MonteCarloRunner] CSV 导出失败(" + scenarioLabel + "): " + e.getMessage());
            }
        }
        return StatisticsCollector.summarize(all);
    }

    /**
     * 执行批量运行，返回统计汇总（不写 CSV）。
     */
    public StatisticsCollector.Summary run() {
        return run(null, null);
    }

    /**
     * 执行批量运行并返回每次运行的原始结果。
     */
    public List<SimulationEngine.RunResult> runRaw() {
        List<SimulationEngine.RunResult> all = new ArrayList<>(numRuns);
        long seed = baseSeed;
        for (int i = 0; i < numRuns; i++) {
            RandomGenerator rng = new RandomGenerator(seed);
            seed = rng.nextSeed();
            Map<String, EquipmentHealth> eqs = ConfigLoader.loadEquipmentHealths(rng);
            Map<String, ThreeStateModel> models = ConfigLoader.loadThreeStateModels(eqs, rng);
            SimulationEngine engine = new SimulationEngine(
                    assemblyLine, modelPool, changeoverMatrix,
                    maintenanceStrategy, rng,
                    simDurationMinutes, warmUpMinutes, arrivalMeanMinutes);
            engine.setEquipments(eqs, models);
            all.add(engine.run());
        }
        return all;
    }
}
