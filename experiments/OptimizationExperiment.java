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

import java.util.*;

/**
 * 优化实验：优化排产（贪心最近邻）+ 预测性维修（S3）。
 *
 * 对应方案文档"实验组"：
 *  - 排产：贪心最近邻 + 迭代改进搜索（移植 AnyLogic 后替换为 OptQuest）
 *  - 维护：基于 RUL 预测，预警窗口 72 小时
 *
 * 运行方式：
 *  mvn exec:java -Dexec.mainClass=experiments.OptimizationExperiment
 */
public class OptimizationExperiment {

    public static void main(String[] args) {
        System.out.println("══════════════════════════════════════════");
        System.out.println(" 优化实验：优化排产 + S3 预测性维修");
        System.out.println("══════════════════════════════════════════");

        // 1. 加载配置
        ConfigLoader.ExperimentConfig cfg = ConfigLoader.loadExperimentConfig();
        ConfigLoader.SimulationConfig simCfg = cfg.simulation();
        ConfigLoader.MonteCarloConfig mcCfg = cfg.monteCarlo();
        ConfigLoader.ScenarioConfig scenario = cfg.scenarios().get("optimized");

        AssemblyLine line = ConfigLoader.loadAssemblyLine();
        List<VehicleModel> models = ConfigLoader.loadVehicleModels();
        System.out.println(line);

        // 2. 维护策略 S3（RUL 窗口 72h）
        MaintenanceStrategy strategy = new PredictiveMaintenance(72.0);

        // 3. 优化排产序列
        RandomGenerator rng = new RandomGenerator(simCfg.randomSeed());
        ChangeoverMatrix cm = ConfigLoader.loadChangeoverMatrix(rng);
        SequenceGenerator generator = new SequenceGenerator(rng, cm);
        ObjectiveFunction objective = new ObjectiveFunction(cm, 0.6, 0.4);
        SequenceOptimizer optimizer = new SequenceOptimizer(cm, objective, generator, 500);

        System.out.println("\n── 排产序列优化 ──");
        List<VehicleModel> optimizedSeq = optimizer.optimize(models, 100);
        double cost = objective.changeoverCostOnly(optimizedSeq);
        System.out.println("优化后序列长度：" + optimizedSeq.size());
        System.out.println("总换型代价：" + String.format("%.1f 分钟", cost));

        // 4. 蒙特卡洛运行
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

        // 5. 输出
        System.out.println("\n" + summary);
        System.out.println("耗时：" + (elapsed / 1000.0) + "s"
                + "  场景：" + scenario.name());
        System.out.println("\n维护策略：" + strategy.getName());
        System.out.println("策略说明：" + strategy.getDescription());
        System.out.println("\n目标函数：" + objective);
    }
}
