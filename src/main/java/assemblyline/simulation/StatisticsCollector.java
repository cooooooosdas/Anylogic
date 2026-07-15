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
        List<Double> cycle = new ArrayList<>();
        List<Double> changeovers = new ArrayList<>();
        List<Double> wip = new ArrayList<>();
        List<Double> ripple = new ArrayList<>();
        List<Double> leadTime = new ArrayList<>();

        for (SimulationEngine.RunResult r : results) {
            avail.add(r.lineAvailability);
            unplanned.add((double) r.unplannedDowntimeCount);
            cycle.add(r.avgCycleMinutes);
            changeovers.add((double) r.changeoverEventCount);
            wip.add((double) r.wipPeak);
            ripple.add(r.rippleEffectMinutes);
            leadTime.add(r.failurePredictionLeadTime);
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
        s.mean.put("avgCycleMinutes", s.meanAvgCycleMinutes);
        s.mean.put("changeoverEventCount", s.meanChangeoverEvents);
        s.mean.put("wipPeak", s.meanWipPeak);
        s.mean.put("rippleEffectMinutes", s.meanRippleMinutes);
        s.mean.put("predictionLeadTimeHours", s.meanPredictionLeadTime);

        for (String k : new ArrayList<>(s.mean.keySet())) {
            List<Double> vals = switch (k) {
                case "lineAvailability" -> avail;
                case "unplannedDowntimeCount" -> unplanned;
                case "avgCycleMinutes" -> cycle;
                case "changeoverEventCount" -> changeovers;
                case "wipPeak" -> wip;
                case "rippleEffectMinutes" -> ripple;
                case "predictionLeadTimeHours" -> leadTime;
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
