package experiments;

import assemblyline.data.ConfigLoader;
import assemblyline.model.AssemblyLine;
import assemblyline.model.VehicleModel;
import assemblyline.optimization.*;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.scheduling.SequenceGenerator;
import assemblyline.simulation.*;
import assemblyline.utils.RandomGenerator;

import java.nio.file.Path;
import java.util.*;

/**
 * 对比实验：基准组 vs 优化组。
 *
 * 运行方式：
 *  mvn exec:java -Dexec.mainClass=experiments.ComparisonExperiment
 */
public class ComparisonExperiment {

    public static void main(String[] args) {
        System.out.println("══════════════════════════════════════════════");
        System.out.println(" 对比实验：基准组 vs 优化组");
        System.out.println("══════════════════════════════════════════════");

        long t0 = System.currentTimeMillis();

        // 1. 加载配置
        ConfigLoader.ExperimentConfig cfg = ConfigLoader.loadExperimentConfig();
        ConfigLoader.SimulationConfig simCfg = cfg.simulation();
        ConfigLoader.MonteCarloConfig mcCfg = cfg.monteCarlo();

        AssemblyLine line = ConfigLoader.loadAssemblyLine();
        List<VehicleModel> models = ConfigLoader.loadVehicleModels();
        System.out.println("\n" + line);
        System.out.println("车型数：" + models.size() + "  蒙特卡洛次数：" + mcCfg.numRuns());

        // 2. 公共组件
        RandomGenerator rng = new RandomGenerator(simCfg.randomSeed());
        ChangeoverMatrix cm = ConfigLoader.loadChangeoverMatrix(rng);

        // 3. ── 基准组 ────────────────────────────────────────
        System.out.println("\n── 运行基准组（经验排产 + S1 计划性维修）──");
        ConfigLoader.ScenarioConfig baselineCfg = cfg.scenarios().get("baseline");
        MaintenanceStrategy baselineStrategy = new ScheduledMaintenance(
                Double.parseDouble(baselineCfg.s1IntervalHours()));

        MonteCarloRunner baselineRunner = new MonteCarloRunner(
                line, models, cm, baselineStrategy,
                mcCfg.numRuns(), simCfg.durationMinutes(), simCfg.warmUpMinutes(),
                cfg.arrivalProcess().meanInterArrivalMinutes(), simCfg.randomSeed());

        Path outputDir = Path.of(cfg.output().directory());
        StatisticsCollector.Summary baseline = baselineRunner.run("baseline", outputDir);

        // 4. ── 优化组 ────────────────────────────────────────
        System.out.println("── 运行优化组（优化排产 + S3 预测性维修）──");
        ConfigLoader.ScenarioConfig optCfg = cfg.scenarios().get("optimized");
        MaintenanceStrategy optStrategy = new PredictiveMaintenance(72.0);

        MonteCarloRunner optRunner = new MonteCarloRunner(
                line, models, cm, optStrategy,
                mcCfg.numRuns(), simCfg.durationMinutes(), simCfg.warmUpMinutes(),
                cfg.arrivalProcess().meanInterArrivalMinutes(), simCfg.randomSeed());
        StatisticsCollector.Summary optimized = optRunner.run("optimized", outputDir);

        // 5. ── 对比 ──────────────────────────────────────────
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println(" 对比结果（优化组相对基准组的改进率）");
        System.out.println("══════════════════════════════════════════════");

        Map<String, Double> improvement = StatisticsCollector.compare(baseline, optimized);
        System.out.printf("%-30s %10s %12s %12s %10s%n",
                "指标", "基准均值", "优化均值", "改进率", "目标");
        System.out.println("──────────────────────────────────────────────────────────────");

        printRow("产线可用度", baseline.meanAvailability, optimized.meanAvailability,
                improvement.get("lineAvailability"), "≥95%", true);
        printRow("非计划停机次数", baseline.meanUnplannedDowntimeCount,
                optimized.meanUnplannedDowntimeCount,
                improvement.get("unplannedDowntimeCount"), "降低≥40%", true);
        printRow("平均装配周期(min)", baseline.meanAvgCycleMinutes,
                optimized.meanAvgCycleMinutes,
                improvement.get("avgCycleMinutes"), "缩短10-15%", true);
        printRow("日均换型次数", (double) baseline.meanChangeoverEvents / simCfg.durationDays(),
                (double) optimized.meanChangeoverEvents / simCfg.durationDays(),
                improvement.get("changeoverEventCount"), "降低≥30%", true);
        printRow("WIP峰值", (double) baseline.meanWipPeak, (double) optimized.meanWipPeak,
                improvement.get("wipPeak"), "降低20%", true);
        printRow("故障涟漪(min)", baseline.meanRippleMinutes, optimized.meanRippleMinutes,
                improvement.get("rippleEffectMinutes"), "缩短30%", true);
        printRow("故障预警提前量(h)", baseline.meanPredictionLeadTime,
                optimized.meanPredictionLeadTime,
                improvement.getOrDefault("predictionLeadTimeHours", 0.0), "≥24h", true);

        System.out.println("──────────────────────────────────────────────────────────────");
        System.out.println("\n维护策略对比：");
        System.out.println("  基准：" + baselineStrategy.getName());
        System.out.println("  优化：" + optStrategy.getName());
        System.out.println("  排产：基准=启发式贪心，优化=启发式+迭代改进");

        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("\n总耗时：" + (elapsed / 1000.0) + "s");
        System.out.println("CSV 输出：" + outputDir.toAbsolutePath()
                + "/experiment_baseline.csv  /  experiment_optimized.csv"
                + "（每行一次蒙特卡洛运行，供报告绘图）");
        System.out.println("（注：S3 预测性维修仅在劣化状态触发，计划性维修 S1 固定周期保养；扩展全设备 PHM 后非计划停机与 RUL 指标将更显著。）");
    }

    private static void printRow(String name, double base, double opt,
                                 double improvementRate, String target, boolean higherIsBetter) {
        String direction = higherIsBetter ? "↑" : "↓";
        String achieved = higherIsBetter
                ? String.format("%.1f%%", improvementRate * 100)
                : String.format("%.1f%%", -improvementRate * 100);
        System.out.printf("%-30s %12.2f %12.2f %10s %10s %s%n",
                name, base, opt, achieved, target, direction);
    }
}
