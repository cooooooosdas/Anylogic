package experiments;

import assemblyline.data.ConfigLoader;
import assemblyline.model.AssemblyLine;
import assemblyline.model.VehicleModel;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.simulation.*;
import assemblyline.utils.RandomGenerator;

import java.nio.file.Path;
import java.util.*;

/**
 * 仿真稳定性测试：对基准组与优化组各运行 1000 次蒙特卡洛仿真，
 * 计算关键指标的变异系数（CV），验证大数定律收敛。
 *
 * 方案文档 4.3 节：1000 次独立运行验证统计稳定性。
 *
 * 运行方式：
 *  mvn exec:java -Dexec.mainClass=experiments.StabilityExperiment
 *
 * 输出：
 *  results/sensitivity/stability_summary.csv
 *  results/sensitivity/stability_baseline.csv（1000 行原始数据）
 *  results/sensitivity/stability_optimized.csv（1000 行原始数据）
 */
public class StabilityExperiment {

    /** 蒙特卡洛次数，方案文档要求 1000 次。 */
    private static final int NUM_RUNS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("══════════════════════════════════════════════");
        System.out.println(" 仿真稳定性测试（" + NUM_RUNS + " 次蒙特卡洛）");
        System.out.println("══════════════════════════════════════════════");

        long t0 = System.currentTimeMillis();

        // 1. 加载配置
        ConfigLoader.ExperimentConfig cfg = ConfigLoader.loadExperimentConfig();
        ConfigLoader.SimulationConfig simCfg = cfg.simulation();
        double arrivalMean = cfg.arrivalProcess().meanInterArrivalMinutes();

        AssemblyLine line = ConfigLoader.loadAssemblyLine();
        List<VehicleModel> models = ConfigLoader.loadVehicleModels();
        RandomGenerator rng = new RandomGenerator(simCfg.randomSeed());
        ChangeoverMatrix cm = ConfigLoader.loadChangeoverMatrix(rng);

        Path outDir = Path.of("results/sensitivity");
        double simDuration = simCfg.durationMinutes();
        double warmUp = simCfg.warmUpMinutes();
        long seed = simCfg.randomSeed();

        // 2. 基准组（S1 计划性维修，48h 间隔）
        System.out.println("\n── 基准组（" + NUM_RUNS + " 次）──");
        MaintenanceStrategy baselineStrategy = new ScheduledMaintenance(48);
        MonteCarloRunner baselineRunner = new MonteCarloRunner(
                line, models, cm, baselineStrategy,
                NUM_RUNS, simDuration, warmUp, arrivalMean, seed);
        StatisticsCollector.Summary baseline = baselineRunner.run("stability_baseline", outDir);

        // 3. 优化组（S3 预测性维修，RUL<72h）
        System.out.println("\n── 优化组（" + NUM_RUNS + " 次）──");
        MaintenanceStrategy optStrategy = new PredictiveMaintenance(72.0);
        MonteCarloRunner optRunner = new MonteCarloRunner(
                line, models, cm, optStrategy,
                NUM_RUNS, simDuration, warmUp, arrivalMean, seed);
        StatisticsCollector.Summary optimized = optRunner.run("stability_optimized", outDir);

        // 4. 计算变异系数并打印
        long elapsedSec = (System.currentTimeMillis() - t0) / 1000;
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println(" 稳定性报告（耗时 " + elapsedSec + " 秒）");
        System.out.println("══════════════════════════════════════════════");
        System.out.printf("%-30s %15s %15s %15s %10s%n",
                "指标", "基准均值", "基准 CV", "优化均值", "优化 CV");
        System.out.println("──────────────────────────────────────────────────────────────────────");

        String[] keys = {
                "lineAvailability", "unplannedDowntimeCount",
                "avgCycleMinutes", "changeoverEventCount",
                "wipPeak", "rippleEffectMinutes", "maintenanceCost"
        };
        String[] labels = {
                "产线可用度", "非计划停机次数", "平均装配周期(min)",
                "换型事件次数", "WIP 峰值", "故障涟漪时长(min)", "维护总成本(元)"
        };

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            double bMean = baseline.mean.getOrDefault(key, 0.0);
            double bStd = baseline.stdDev.getOrDefault(key, 0.0);
            double bCV = bMean > 0 ? bStd / bMean : 0;

            double oMean = optimized.mean.getOrDefault(key, 0.0);
            double oStd = optimized.stdDev.getOrDefault(key, 0.0);
            double oCV = oMean > 0 ? oStd / oMean : 0;

            System.out.printf("%-30s %15.4f %15.4f %15.4f %10.4f%n",
                    labels[i], bMean, bCV, oMean, oCV);
        }

        // 5. 写出汇总 CSV
        writeStabilityCsv(baseline, optimized, outDir);

        System.out.println("\n稳定性阈值：CV < 0.05 视为收敛");
        System.out.println("（此阈值由大数定律保证：n=1000 时均值分布的 SE ≈ σ/√1000）");
        System.out.println("\n输出文件：");
        System.out.println("  results/sensitivity/stability_summary.csv");
        System.out.println("  results/sensitivity/stability_baseline.csv");
        System.out.println("  results/sensitivity/stability_optimized.csv");
    }

    private static void writeStabilityCsv(StatisticsCollector.Summary baseline,
                                          StatisticsCollector.Summary optimized,
                                          Path outDir) {
        try {
            java.nio.file.Files.createDirectories(outDir);
            java.nio.file.Path file = outDir.resolve("stability_summary.csv");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    java.nio.file.Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
                java.util.Locale locale = java.util.Locale.ROOT;
                pw.println("metric,baseline_mean,baseline_stddev,baseline_cv,"
                        + "optimized_mean,optimized_stddev,optimized_cv");
                for (String k : baseline.mean.keySet()) {
                    double bm = baseline.mean.get(k);
                    double bs = baseline.stdDev.getOrDefault(k, 0.0);
                    double bcv = bm > 0 ? bs / bm : 0;
                    double om = optimized.mean.get(k);
                    double os = optimized.stdDev.getOrDefault(k, 0.0);
                    double ocv = om > 0 ? os / om : 0;
                    pw.printf(locale, "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                            k, bm, bs, bcv, om, os, ocv);
                }
            }
            System.out.println("\n[稳定度汇总] 已写入 stability_summary.csv");
        } catch (java.io.IOException e) {
            System.err.println("[StabilityExperiment] CSV 导出失败: " + e.getMessage());
        }
    }
}
