package assemblyline.simulation;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 蒙特卡洛批量结果的统计汇总。
 * 对多次运行的 RunResult 计算均值、标准差、95% 置信区间等。
 *
 * 竞赛报告中的表格和图表均由此类输出。
 */
public class StatisticsCollector {

    public static class Summary {
        public int nRuns;
        public Map<String, Double> mean = new LinkedHashMap<>();
        public Map<String, Double> stdDev = new LinkedHashMap<>();
        public Map<String, Double> ci95Low = new LinkedHashMap<>();
        public Map<String, Double> ci95High = new LinkedHashMap<>();
        public List<Double> samples = new ArrayList<>();

        // 专项汇总
        public double meanAvailability;
        public double meanUnplannedDowntimeCount;
        public double meanAvgCycleMinutes;
        public double meanChangeoverEvents;
        public double meanWipPeak;
        public double meanRippleMinutes;
        public double meanPredictionLeadTime;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Monte Carlo Summary (n=").append(nRuns).append(") ===\n");
            sb.append(String.format("%-30s %10s %10s %10s %10s%n",
                    "Metric", "Mean", "StdDev", "CI95-Low", "CI95-High"));
            for (String k : mean.keySet()) {
                sb.append(String.format("%-30s %10.2f %10.2f %10.2f %10.2f%n",
                        k, mean.get(k), stdDev.get(k), ci95Low.get(k), ci95High.get(k)));
            }
            return sb.toString();
        }

        /** 输出为 CSV 字符串 */
        public String toCsv() {
            StringBuilder sb = new StringBuilder();
            sb.append("metric,mean,stddev,ci95_low,ci95_high\n");
            for (String k : mean.keySet()) {
                sb.append(String.format("%s,%.4f,%.4f,%.4f,%.4f\n",
                        k, mean.get(k), stdDev.get(k), ci95Low.get(k), ci95High.get(k)));
            }
            return sb.toString();
        }
    }

    /**
     * 汇总多次运行结果。
     */
    public static Summary summarize(List<SimulationEngine.RunResult> results) {
        Summary s = new Summary();
        s.nRuns = results.size();
        if (results.isEmpty()) return s;

        // 提取关键指标
        List<Double> avail = new ArrayList<>();
        List<Double> unplanned = new ArrayList<>();
        List<Double> unplannedMin = new ArrayList<>();
        List<Double> cycle = new ArrayList<>();
        List<Double> changeovers = new ArrayList<>();
        List<Double> wip = new ArrayList<>();
        List<Double> ripple = new ArrayList<>();
        List<Double> leadTime = new ArrayList<>();
        List<Double> maintCost = new ArrayList<>();
        List<Double> plannedMaint = new ArrayList<>();
        List<Double> completed = new ArrayList<>();

        for (SimulationEngine.RunResult r : results) {
            avail.add(r.lineAvailability);
            unplanned.add((double) r.unplannedDowntimeCount);
            unplannedMin.add(r.unplannedDowntimeMinutes);
            cycle.add(r.avgCycleMinutes);
            changeovers.add((double) r.changeoverEventCount);
            wip.add((double) r.wipPeak);
            ripple.add(r.rippleEffectMinutes);
            leadTime.add(r.failurePredictionLeadTime);
            maintCost.add(r.maintenanceCost);
            plannedMaint.add(r.plannedMaintMinutes);
            completed.add((double) r.completedCount);
            s.samples.add(r.lineAvailability);
        }

        s.meanAvailability = mean(avail);
        s.meanUnplannedDowntimeCount = mean(unplanned);
        s.meanAvgCycleMinutes = mean(cycle);
        s.meanChangeoverEvents = mean(changeovers);
        s.meanWipPeak = mean(wip);
        s.meanRippleMinutes = mean(ripple);
        s.meanPredictionLeadTime = mean(leadTime);

        s.mean.put("lineAvailability", s.meanAvailability);
        s.mean.put("unplannedDowntimeCount", s.meanUnplannedDowntimeCount);
        s.mean.put("unplannedDowntimeMinutes", mean(unplannedMin));
        s.mean.put("avgCycleMinutes", s.meanAvgCycleMinutes);
        s.mean.put("changeoverEventCount", s.meanChangeoverEvents);
        s.mean.put("wipPeak", s.meanWipPeak);
        s.mean.put("rippleEffectMinutes", s.meanRippleMinutes);
        s.mean.put("predictionLeadTimeHours", s.meanPredictionLeadTime);
        s.mean.put("maintenanceCost", mean(maintCost));
        s.mean.put("plannedMaintMinutes", mean(plannedMaint));
        s.mean.put("completedCount", mean(completed));

        for (String k : new ArrayList<>(s.mean.keySet())) {
            List<Double> vals = switch (k) {
                case "lineAvailability" -> avail;
                case "unplannedDowntimeCount" -> unplanned;
                case "unplannedDowntimeMinutes" -> unplannedMin;
                case "avgCycleMinutes" -> cycle;
                case "changeoverEventCount" -> changeovers;
                case "wipPeak" -> wip;
                case "rippleEffectMinutes" -> ripple;
                case "predictionLeadTimeHours" -> leadTime;
                case "maintenanceCost" -> maintCost;
                case "plannedMaintMinutes" -> plannedMaint;
                case "completedCount" -> completed;
                default -> List.of(0.0);
            };
            double m = s.mean.get(k);
            double sd = stdDev(vals, m);
            double halfWidth = 1.96 * sd / Math.sqrt(vals.size());
            s.stdDev.put(k, sd);
            s.ci95Low.put(k, m - halfWidth);
            s.ci95High.put(k, m + halfWidth);
        }
        return s;
    }

    /** 对比两组结果的百分比提升 */
    public static Map<String, Double> compare(Summary baseline, Summary optimized) {
        Map<String, Double> improvement = new LinkedHashMap<>();
        for (String k : baseline.mean.keySet()) {
            double b = baseline.mean.get(k);
            double o = optimized.mean.get(k);
            if (b == 0) {
                improvement.put(k, o > 0 ? 1.0 : 0.0);
            } else {
                improvement.put(k, (b - o) / b); // 正值=改进
            }
        }
        return improvement;
    }

    // ── 配对 t 检验 ────────────────────────────────────────────

    /**
     * 配对 t 检验结果。
     *
     * <p>对配对样本 d_i = baseline_i - optimized_i 计算：</p>
     * <pre>
     *   t = d̄ / (s_d / sqrt(n))
     *   p = 2 * (1 - Φ(|t|))    // 正态近似，n≥30 时足够
     * </pre>
     *
     * <p>Φ 采用 Abramowitz & Stegun 7.1.26 近似（误差 &lt; 7.5×10⁻⁸）。</p>
     */
    public static class TTestResult {
        public final double tStatistic;
        public final int degreesOfFreedom;
        public final double pValue;
        public final boolean significant;
        public final String conclusion;

        public TTestResult(double t, int df, double p, boolean significant, String conclusion) {
            this.tStatistic = t;
            this.degreesOfFreedom = df;
            this.pValue = p;
            this.significant = significant;
            this.conclusion = conclusion;
        }

        @Override
        public String toString() {
            return String.format(
                    "t(%d)=%.4f, p=%.6f, %s (α=0.05)",
                    degreesOfFreedom, tStatistic, pValue,
                    significant ? "显著" : "不显著");
        }
    }

    /**
     * 对两组蒙特卡洛运行结果的指定指标做配对 t 检验。
     *
     * @param baseline    基准组原始结果（须与 optimized 同长度）
     * @param optimized   优化组原始结果
     * @param extractor   从 RunResult 提取待检验指标的函数
     * @param alpha       显著性水平（默认 0.05）
     * @return TTestResult，含 t 统计量、自由度、p 值、是否显著
     * @throws IllegalArgumentException 两组长度不一致或为空
     */
    public static TTestResult compareWithTTest(List<SimulationEngine.RunResult> baseline,
                                               List<SimulationEngine.RunResult> optimized,
                                               java.util.function.ToDoubleFunction<SimulationEngine.RunResult> extractor,
                                               double alpha) {
        int n = baseline.size();
        if (n == 0 || n != optimized.size()) {
            throw new IllegalArgumentException(
                    "配对 t 检验要求两组非空且长度一致：baseline=" + n
                            + ", optimized=" + optimized.size());
        }

        // 计算配对差值 d_i = baseline_i - optimized_i
        double[] d = new double[n];
        double sumD = 0;
        for (int i = 0; i < n; i++) {
            d[i] = extractor.applyAsDouble(baseline.get(i))
                    - extractor.applyAsDouble(optimized.get(i));
            sumD += d[i];
        }
        double dBar = sumD / n;

        // 样本标准差 s_d
        double sumSq = 0;
        for (double v : d) sumSq += (v - dBar) * (v - dBar);
        double sd = n > 1 ? Math.sqrt(sumSq / (n - 1)) : 0;

        // t 统计量
        double se = sd / Math.sqrt(n);
        double t = se > 0 ? dBar / se : 0;
        int df = n - 1;

        // 双侧 p = 2 * (1 - Φ(|t|))  （正态近似，n≥30 时 t 分布≈正态）
        double p = 2 * (1 - normalCdf(Math.abs(t)));
        boolean sig = p < alpha;

        String conclusion = sig
                ? String.format("优化组显著优于基准组 (d̄=%.4f, t(%d)=%.4f, p=%.6f < %.2f)",
                dBar, df, t, p, alpha)
                : String.format("差异未达显著水平 (d̄=%.4f, t(%d)=%.4f, p=%.6f ≥ %.2f)",
                dBar, df, t, p, alpha);

        return new TTestResult(t, df, p, sig, conclusion);
    }

    /**
     * 标准正态 CDF，Abramowitz & Stegun 7.1.26 近似。
     *
     * <p>误差 &lt; 7.5×10⁻⁸，对竞赛报告完全够用。</p>
     */
    private static double normalCdf(double x) {
        if (x == 0) return 0.5;
        boolean neg = x < 0;
        if (neg) x = -x;

        // φ(x) = exp(-x²/2) / sqrt(2π)
        double phi = Math.exp(-x * x / 2.0) / Math.sqrt(2.0 * Math.PI);

        // Horner 形式，t = 1/(1 + p·x)
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double poly = t * (0.319381530
                + t * (-0.356563782
                + t * (1.781477937
                + t * (-1.821255978
                + t * (1.330274429)))));
        double cdf = 1.0 - phi * poly;

        return neg ? 1.0 - cdf : cdf;
    }

    // ── 统计辅助 ──────────────────────────────────────────────
    private static double mean(List<Double> vals) {
        return vals.stream().mapToDouble(d -> d).average().orElse(0);
    }

    private static double stdDev(List<Double> vals, double mean) {
        if (vals.size() < 2) return 0;
        double sumSq = vals.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum();
        return Math.sqrt(sumSq / (vals.size() - 1));
    }

    /**
     * 将每次蒙特卡洛运行的原始结果写出为 CSV，供竞赛报告画图。
     *
     * 输出文件：{outputDir}/experiment_{scenarioLabel}.csv
     * 表头包含：run_id, lineAvailability, unplannedDowntimeCount,
     *           unplannedDowntimeMinutes, avgCycleMinutes,
     *           changeoverEventCount, wipPeak, rippleEffectMinutes,
     *           failurePredictionLeadTime, maintenanceCost,
     *           plannedMaintMinutes, completedCount
     */
    public static void exportRunCsv(List<SimulationEngine.RunResult> results,
                                    String scenarioLabel,
                                    Path outputDir) throws java.io.IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("experiment_" + scenarioLabel + ".csv");
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            pw.println("run_id,lineAvailability,unplannedDowntimeCount,"
                    + "unplannedDowntimeMinutes,avgCycleMinutes,"
                    + "changeoverEventCount,wipPeak,rippleEffectMinutes,"
                    + "failurePredictionLeadTime,maintenanceCost,"
                    + "plannedMaintMinutes,completedCount");
            Locale locale = Locale.ROOT;
            for (int i = 0; i < results.size(); i++) {
                SimulationEngine.RunResult r = results.get(i);
                pw.printf(locale, "%d,%.6f,%d,%.2f,%.4f,%d,%d,%.2f,%.4f,%.2f,%.2f,%d%n",
                        i + 1,
                        r.lineAvailability,
                        r.unplannedDowntimeCount,
                        r.unplannedDowntimeMinutes,
                        r.avgCycleMinutes,
                        r.changeoverEventCount,
                        r.wipPeak,
                        r.rippleEffectMinutes,
                        r.failurePredictionLeadTime,
                        r.maintenanceCost,
                        r.plannedMaintMinutes,
                        r.completedCount);
            }
        }
    }
}
