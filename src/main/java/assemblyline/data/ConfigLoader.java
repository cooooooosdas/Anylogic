package assemblyline.data;

import assemblyline.model.*;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.utils.RandomGenerator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 从 data/*.json 加载仿真参数的配置加载器。
 * 所有路径相对于 classpath 的 data/ 目录。
 *
 * 使用 Gson 解析，解析失败时抛出带上下文的 RuntimeException。
 */
public class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    // ── stations.json ──────────────────────────────────────────

    public record StationSpec(
            String id, String name, String line,
            double[] processingTime, // [min, mode, max] 三角分布
            boolean changeoverPoint, String changeoverType,
            boolean hasTighteningMachine) {}

    public record LineSpec(String id, String name, List<StationSpec> stations) {}
    public record BuffersSpec(String description, Map<String, Integer> capacity) {}
    public record StationsDoc(
            String description, String unit,
            String note, List<LineSpec> lines,
            BuffersSpec buffers,
            List<String> lineSequence,
            List<String> sources) {}

    public static AssemblyLine loadAssemblyLine() {
        StationsDoc doc = loadJson("data/stations.json", StationsDoc.class);
        AssemblyLine line = new AssemblyLine("一汽解放混流装配线");

        for (LineSpec ls : doc.lines) {
            AssemblyLine.Line aline = new AssemblyLine.Line(ls.id, ls.name);
            for (StationSpec ss : ls.stations) {
                Station s = new Station(
                        ss.id, ss.name, ss.line,
                        ss.processingTime[0], ss.processingTime[1], ss.processingTime[2],
                        ss.changeoverPoint, ss.changeoverType,
                        ss.hasTighteningMachine);
                aline.addStation(s);
            }
            line.addLine(aline);
        }

        // 添加缓存区
        if (doc.buffers() != null && doc.buffers().capacity() != null) {
            for (Map.Entry<String, Integer> e : doc.buffers().capacity().entrySet()) {
                String[] parts = e.getKey().split("_to_");
                line.addBuffer(new Buffer(e.getKey(), parts[0], parts[1], e.getValue()));
            }
        }
        return line;
    }

    // ── vehicle_models.json ────────────────────────────────────

    public record VehicleModelSpec(
            String id, String name, String category,
            String color, String driveType, String engineType,
            int annualVolume) {}

    public record VehiclesDoc(
            String description, String note,
            Map<String, String> categories, Map<String, String> colors,
            Map<String, String> driveTypes, Map<String, String> engineTypes,
            List<VehicleModelSpec> models,
            List<String> sources) {}

    public static List<VehicleModel> loadVehicleModels() {
        VehiclesDoc doc = loadJson("data/vehicle_models.json", VehiclesDoc.class);
        List<VehicleModel> result = new ArrayList<>();
        for (VehicleModelSpec s : doc.models) {
            result.add(new VehicleModel(
                    s.id, s.name,
                    VehicleCategory.valueOf(s.category),
                    Color.valueOf(s.color),
                    DriveType.valueOf(s.driveType),
                    EngineType.valueOf(s.engineType),
                    s.annualVolume));
        }
        return result;
    }

    // ── changeover_matrix.json ─────────────────────────────────

    public record ChangeoverDoc(
            String description, String unit, String distribution, String note,
            Map<String, ChangeoverDim> dimensions,
            Map<String, Map<String, Double>> pairwiseTimes,
            ChangeoverClustering changeoverClustering,
            List<String> sources) {}

    public record ChangeoverDim(
            String description, List<String> values,
            double mean, double sigma,
            Object affectsStation, Object affectsBuffer) {}

    public record ChangeoverClustering(
            String description, List<String> priorityOrder) {}

    public static ChangeoverMatrix loadChangeoverMatrix(RandomGenerator rng) {
        ChangeoverDoc doc = loadJson("data/changeover_matrix.json", ChangeoverDoc.class);
        ChangeoverDim d = doc.dimensions.get("COLOR");
        ChangeoverDim dr = doc.dimensions.get("DRIVE");
        ChangeoverDim e = doc.dimensions.get("ENGINE");
        ChangeoverDim p = doc.dimensions.get("PLATFORM");

        Map<String, Double> drivePairwise = doc.pairwiseTimes.getOrDefault("DRIVE", Map.of());
        Map<String, Double> enginePairwise = doc.pairwiseTimes.getOrDefault("ENGINE", Map.of());
        Map<String, Double> platformPairwise = doc.pairwiseTimes.getOrDefault("PLATFORM", Map.of());

        return new ChangeoverMatrix(
                d.mean, d.sigma,
                dr.mean, dr.sigma,
                e.mean, e.sigma,
                p.mean, p.sigma,
                drivePairwise, enginePairwise, platformPairwise);
    }

    // ── equipment_params.json ──────────────────────────────────

    public record EquipmentSpec(
            String id, String name, String location, String function,
            List<String> failureModes,
            WeibullSpec weibull,
            MttrSpec mttr,
            Map<String, Boolean> sensors,
            HealthSpec healthIndex,
            RulSpec rul,
            String stationId) {}

    public record WeibullSpec(double beta, double eta, String description) {}
    public record MttrSpec(double mean, double min, double max, String distribution) {}
    public record HealthSpec(double initial, double degradationRate,
                             double warningThreshold, double criticalThreshold, String unit) {}
    public record RulSpec(String predictionMethod, double updateInterval, double confidenceLevel) {}

    public record ThreeStateSpec(
            String description, List<String> states,
            Map<String, TransitionSpec> transitions,
            Map<String, int[]> healthIndexMapping) {}

    public record TransitionSpec(String method, String note) {}
    public record TransitionWithValuesSpec(String method, double mean, String unit, String note) {}

    public record EquipmentDoc(
            String description, Map<String, String> units,
            List<EquipmentSpec> equipment,
            ThreeStateSpec threeStateModel,
            List<String> sources) {}

    public static Map<String, EquipmentHealth> loadEquipmentHealths(RandomGenerator rng) {
        EquipmentDoc doc = loadJson("data/equipment_params.json", EquipmentDoc.class);
        Map<String, EquipmentHealth> map = new LinkedHashMap<>();
        for (EquipmentSpec s : doc.equipment) {
            map.put(s.id, new EquipmentHealth(
                    s.id, s.name,
                    s.weibull.beta, s.weibull.eta,
                    s.mttr.mean, s.mttr.min, s.mttr.max,
                    s.stationId));
        }
        return map;
    }

    public static Map<String, ThreeStateModel> loadThreeStateModels(
            Map<String, EquipmentHealth> healths, RandomGenerator rng) {
        EquipmentDoc doc = loadJson("data/equipment_params.json", EquipmentDoc.class);
        Map<String, ThreeStateModel> map = new LinkedHashMap<>();
        for (EquipmentSpec s : doc.equipment) {
            EquipmentHealth h = healths.get(s.id);
            if (h == null) continue;
            double degRate = s.healthIndex.degradationRate;
            map.put(s.id, new ThreeStateModel(
                    h, rng,
                    50.0, // DEGRADED→FAILED 指数均值 50h
                    degRate, degRate * 2.5));
        }
        return map;
    }

    // ── experiment_config.json ─────────────────────────────────

    public record ExperimentConfig(
            SimulationConfig simulation,
            MonteCarloConfig monteCarlo,
            Map<String, ScenarioConfig> scenarios,
            ArrivalProcessConfig arrivalProcess,
            OutputConfig output,
            StatisticalTestsConfig statisticalTests,
            List<String> sources) {}

    public record SimulationConfig(
            int durationDays, int durationMinutes, int warmUpDays,
            int warmUpMinutes, String timeUnit, int randomSeed, String warmUpNote) {}

    public record MonteCarloConfig(
            int numRuns, double confidenceLevel, String note) {}

    public record ScenarioConfig(
            String id, String name, String scheduling, String maintenance,
            String s1IntervalHours, String description) {}

    public record ArrivalProcessConfig(
            String type, double meanInterArrivalMinutes, String description) {}

    public record OutputConfig(
            String directory, String csvPrefix, List<String> metrics) {}

    public record StatisticalTestsConfig(
            PairedTTest pairedTTest, SensitivityAnalysis sensitivityAnalysis) {}

    public record PairedTTest(boolean enabled, double alpha, String description) {}
    public record SensitivityAnalysis(double failureRatePerturbation, String description) {}

    public static ExperimentConfig loadExperimentConfig() {
        return loadJson("data/experiment_config.json", ExperimentConfig.class);
    }

    // ── 通用工具 ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static <T> T loadJson(String path, Class<T> clazz) {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new FileLoadException("Resource not found: " + path);
            InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
            return GSON.fromJson(r, clazz);
        } catch (Exception e) {
            throw new FileLoadException("Failed to load " + path + ": " + e.getMessage(), e);
        }
    }

    public static class FileLoadException extends RuntimeException {
        public FileLoadException(String m, Throwable c) { super(m, c); }
        public FileLoadException(String m) { super(m); }
    }
}
