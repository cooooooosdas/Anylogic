package assemblyline.data;

import assemblyline.model.AssemblyLine;
import assemblyline.model.VehicleModel;
import assemblyline.phm.EquipmentHealth;
import assemblyline.phm.ThreeStateModel;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.utils.RandomGenerator;

import java.util.List;
import java.util.Map;

/**
 * 仿真数据源抽象。
 *
 * <p>定义从外部加载装配线全部参数的接口。
 * 默认实现 {@link JsonDataSource} 从 classpath 的 {@code data/*.json} 加载（模拟数据）。
 * 真实数据对接阶段替换为 {@link EnterpriseDataSource}（JDBC / REST API / CSV），
 * 仿真核心代码无需修改。</p>
 *
 * <p>参考 {@code docs/ALGORITHM.md §8.3 数据层抽象设计}。</p>
 *
 * // AnyLogic API:  teammate A 可将 EnterpriseDataSource 对接 AnyLogic 内置数据库
 */
public interface DataSource {

    /**
     * 加载装配线拓扑（工位、缓存区）。
     */
    AssemblyLine loadAssemblyLine();

    /**
     * 加载车型库（含年产量比例）。
     */
    List<VehicleModel> loadVehicleModels();

    /**
     * 加载换型时间矩阵。
     *
     * @param rng 随机生成器（用于换型时间采样时的随机抖动）
     */
    ChangeoverMatrix loadChangeoverMatrix(RandomGenerator rng);

    /**
     * 加载设备健康快照（参数化构造）。
     *
     * @param rng 随机生成器
     * @return 设备 ID → 健康快照的映射
     */
    Map<String, EquipmentHealth> loadEquipmentHealths(RandomGenerator rng);

    /**
     * 基于健康快照构造三状态 PHM 模型。
     *
     * @param healths {@link #loadEquipmentHealths} 的返回值
     * @param rng     随机生成器
     */
    Map<String, ThreeStateModel> loadThreeStateModels(
            Map<String, EquipmentHealth> healths, RandomGenerator rng);

    /**
     * 加载实验配置（蒙特卡洛参数、场景定义、输出路径等）。
     */
    ConfigLoader.ExperimentConfig loadExperimentConfig();
}
