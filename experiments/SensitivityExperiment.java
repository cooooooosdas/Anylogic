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
import java.util.stream.Collectors;

/**
 * 敏感性分析：变化 Weibull η（设备故障率关键参数），观察对产线可用度的影响。
 *
 * 测试矩阵：
 *  4 个 η 值 × 50 次蒙特卡洛 = 200 次仿真
 *
 * 运行方式：
 *  mvn exec:java -Dexec.mainClass=experiments.SensitivityExperiment
 *
 * 输出目录结构：
 *  results/sensitivity/
 *    eta_100h/run_summary.csv
 *    eta_150h/run_summary.csv
 *    eta_200h/run_summary.csv
 *    eta_280h/run_summary.csv
 *    sensitivity_summary.csv   ← 汇总
 */
public class SensitivityExperiment {

    /** 测试的 Weibull η 值（小时），覆盖设备当前值 ±30%。 */
    private static final double[] ETA_VALUES = {100, 150, 200, 280};
    /** 每个 η 值对应的蒙特卡洛次数。 */
    private static final int RUNS_PER_ETA = 50;
    /** 共享的维修策略（S3 预测性维修）。 */
    private static final double RUL_WINDOW_HOURS = 72.0;

    public static void main(String[] args) throws Exception {
        System.out.println("══════════════════════════════════════════════");
        System.out.println(" 敏感性分析：Weibull η 对产线可用度的影响");
        System.out.println(" 矩阵：" + ETA_VALUES.length + " η 值 × "
                + RUNS_PER_ETA + " 次 = " + (ETA_VALUES.length * RUNS_PER_ETA) + " 次仿真");
        System.out.println("══════════════════════════════════════════════");

        long t0 = System.currentTimeMillis();

        // 1. 加载公共组件
        ConfigLoader.ExperimentConfig cfg = ConfigLoader.loadExperimentConfig();
        ConfigLoader.SimulationConfig simCfg = cfg.simulation();
        double arrivalMean = cfg.arrivalProcess().meanInterArrivalMinutes();
        long seed = simCfg.randomSeed();

        AssemblyLine line = ConfigLoader.loadAssemblyLine();
        List<VehicleModel> models = ConfigLoader.loadVehicleModels();
        RandomGenerator rng = new RandomGenerator(seed);
        ChangeoverMatrix cm = ConfigLoader.loadChangeoverMatrix(rng);

        MaintenanceStrategy strategy = new PredictiveMaintenance(RUL_WINDOW_HOURS);
        double simDuration = simCfg.durationMinutes();
        double warmUp = simCfg.warmUpMinutes();

        // 2. 对每个 η 值运行蒙特卡洛
        List<SensitivityResult> results = new ArrayList<>();
        Path baseOut = Path.of("results/sensitivity");

        for (double eta : ETA_VALUES) {
            String label = "eta_" + (int) eta + "h";
            Path etaOut = baseOut.resolve(label);
            java.nio.file.Files.createDirectories(etaOut);

            System.out.println("\n── η = " + (int) eta + "h（" + RUNS_PER_ETA + " 次）──");

            // 构造 η 覆盖映射：每个设备统一使用测试 η 值
            Map<String, Double> etaOverrides = new HashMap<>();
            // 先加载一次以获取设备 ID 列表
            Map<String, EquipmentHealth> probeEqs = ConfigLoader.loadEquipmentHealths(rng);
            for (EquipmentHealth eq : probeEqs.values()) {
                etaOverrides.put(eq.getEquipmentId(), eta);
            }

            // 运行蒙特卡洛（每次重载设备并应用 η 覆盖）
            List<SimulationEngine.RunResult> allRuns = new ArrayList<>(RUNS_PER_ETA);
            long runSeed = seed;
            for (int i = 0; i < RUNS_PER_ETA; i++) {
                RandomGenerator runRng = new RandomGenerator(runSeed);
                runSeed = runRng.nextSeed();

                Map<String, EquipmentHealth> eqs = ConfigLoader.loadEquipmentHealths(runRng);
                for (Map.Entry<String, Double> e : etaOverrides.entrySet()) {
                    EquipmentHealth eq = eqs.get(e.getKey());
                    if (eq != null) eq.setEtaOverride(e.getValue());
                }
                Map<String, ThreeStateModel> models_map = ConfigLoader.loadThreeStateModels(eqs, runRng);

                SimulationEngine engine = new SimulationEngine(
                        line, models, cm, strategy,
                        runRng, simDuration, warmUp, arrivalMean);
                engine.setEquipments(eqs, models_map);
                allRuns.add(engine.run());
            }

            // 写出每次运行的原始结果
            StatisticsCollector.exportRunCsv(allRuns, label, etaOut);

            // 汇总
            StatisticsCollector.Summary summary = StatisticsCollector.summarize(allRuns);
            results.add(new SensitivityResult(eta, summary));

            System.out.printf("  η=%3dh → 可用度=%.4f (±%.4f) | 非计划停机=%.1f 次 | 维护成本=%.0f 元%n",
                    (int) eta,
                    summary.meanAvailability,
                    summary.stdDev.getOrDefault("lineAvailability", 0.0),
                    summary.meanUnplannedDowntimeCount,
                    summary.mean.getOrDefault("maintenanceCost", 0.0));
        }

        // 3. 写出汇总表
        writeSensitivitySummary(results, baseOut);

        long elapsedSec = (System.currentTimeMillis() - t0) / 1000;
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println(" 敏感性分析完成（耗时 " + elapsedSec + " 秒）");
        System.out.println("══════════════════════════════════════════════");
        System.out.println("输出文件：");
        for (double eta : ETA_VALUES) {
            System.out.println("  results/sensitivity/eta_" + (int) eta + "h/run_summary.csv");
        }
        System.out.println("  results/sensitivity/sensitivity_summary.csv");
    }

    /**
     * 写出汇总表：每行对应一个 η 值，各列是均值 + 95% CI。
     */
    private static void writeSensitivitySummary(List<SensitivityResult> results, Path outDir) {
        try {
            java.nio.file.Files.createDirectories(outDir);
            java.nio.file.Path file = outDir.resolve("sensitivity_summary.csv");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    java.nio.file.Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
                java.util.Locale locale = java.util.Locale.ROOT;
                pw.println("eta_h,nRuns,lineAvailability_mean,lineAvailability_stddev,"
                        + "lineAvailability_ci95_low,lineAvailability_ci95_high,"
                        + "unplannedDowntimeCount_mean,rippleEffectMinutes_mean,"
                        + "maintenanceCost_mean,predictionLeadTimeHours_mean");
                for (SensitivityResult r : results) {
                    StatisticsCollector.Summary s = r.summary();
                    pw.printf(locale,
                            "%.0f,%d,%.6f,%.6f,%.6f,%.6f,%.2f,%.2f,%.2f,%.4f%n",
                            r.etaHours(), s.nRuns,
                            s.meanAvailability,
                            s.stdDev.getOrDefault("lineAvailability", 0.0),
                            s.ci95Low.getOrDefault("lineAvailability", 0.0),
                            s.ci95High.getOrDefault("lineAvailability", 0.0),
                            s.meanUnplannedDowntimeCount,
                            s.meanRippleMinutes,
                            s.mean.getOrDefault("maintenanceCost", 0.0),
                            s.meanPredictionLeadTime);
                }
            }
            System.out.println("\n[敏感性汇总] 已写入 sensitivity_summary.csv");
        } catch (java.io.IOException e) {
            System.err.println("[SensitivityExperiment] CSV 导出失败: " + e.getMessage());
        }
    }

    /** 一个 η 值对应的实验结果。 */
    private record SensitivityResult(double etaHours,
                                     StatisticsCollector.Summary summary) {}
}
