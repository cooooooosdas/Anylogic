package experiments;

import assemblyline.data.ConfigLoader;
import assemblyline.model.AssemblyLine;
import assemblyline.model.VehicleModel;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.simulation.*;
import assemblyline.utils.RandomGenerator;

import java.util.*;

/**
 * 基准实验：经验排产（启发式贪心）+ 计划性维修（S1）。
 *
 * 对应方案文档"基准组"：
 *  - 排产：按计划员经验，简化模拟为贪心最近邻（非全局优化）
 *  - 维护：固定周期 720h（30 天）保养
 *
 * 运行方式：
 *  mvn exec:java -Dexec.mainClass=experiments.BaselineExperiment
 */
public class BaselineExperiment {

    public static void main(String[] args) {
        System.out.println("══════════════════════════════════════════");
        System.out.println(" 基准实验：经验排产 + S1 计划性维修");
        System.out.println("══════════════════════════════════════════");

        // 1. 加载配置
        ConfigLoader.ExperimentConfig cfg = ConfigLoader.loadExperimentConfig();
        ConfigLoader.SimulationConfig simCfg = cfg.simulation();
        ConfigLoader.MonteCarloConfig mcCfg = cfg.monteCarlo();
        ConfigLoader.ScenarioConfig scenario = cfg.scenarios().get("baseline");

        AssemblyLine line = ConfigLoader.loadAssemblyLine();
        List<VehicleModel> models = ConfigLoader.loadVehicleModels();
        System.out.println(line);

        // 2. 维护策略 S1
        double s1Interval = Double.parseDouble(scenario.s1IntervalHours());
        MaintenanceStrategy strategy = new ScheduledMaintenance(s1Interval);

        // 3. 蒙特卡洛运行
        RandomGenerator rng = new RandomGenerator(simCfg.randomSeed());
        ChangeoverMatrix cm = ConfigLoader.loadChangeoverMatrix(rng);

        MonteCarloRunner runner = new MonteCarloRunner(
                line, models, cm, strategy,
                mcCfg.numRuns(),
                simCfg.durationMinutes(),
                simCfg.warmUpMinutes(),
                cfg.arrivalProcess().meanInterArrivalMinutes(),
                simCfg.randomSeed());

        long start = System.currentTimeMillis();
        StatisticsCollector.Summary summary = runner.run();
        long elapsed = System.currentTimeMillis() - start;

        // 4. 输出结果
        System.out.println("\n" + summary);
        System.out.println("耗时：" + (elapsed / 1000.0) + "s"
                + "  场景：" + scenario.name());
        System.out.println("\n维护策略：" + strategy.getName());
        System.out.println("策略说明：" + strategy.getDescription());
    }
}
