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
 * 基于 classpath JSON 的默认数据源实现。
 *
 * <p>所有逻辑委托给 {@link ConfigLoader} 已有的静态加载方法，
 * 不改变现有行为。供 {@link DataSource} 接口的默认实现使用。</p>
 *
 * <p>开箱即用：</p>
 * <pre>
 *   DataSource ds = JsonDataSource.INSTANCE;
 *   AssemblyLine line = ds.loadAssemblyLine();
 * </pre>
 *
 * // AnyLogic API: teammate A 可直接将 {@code data/} 目录下 JSON 文件
 * //  打包进 AnyLogic 工程，运行时通过 classpath 加载。
 */
public enum JsonDataSource implements DataSource {

    INSTANCE;

    @Override
    public AssemblyLine loadAssemblyLine() {
        return ConfigLoader.loadAssemblyLine();
    }

    @Override
    public List<VehicleModel> loadVehicleModels() {
        return ConfigLoader.loadVehicleModels();
    }

    @Override
    public ChangeoverMatrix loadChangeoverMatrix(RandomGenerator rng) {
        return ConfigLoader.loadChangeoverMatrix(rng);
    }

    @Override
    public Map<String, EquipmentHealth> loadEquipmentHealths(RandomGenerator rng) {
        return ConfigLoader.loadEquipmentHealths(rng);
    }

    @Override
    public Map<String, ThreeStateModel> loadThreeStateModels(
            Map<String, EquipmentHealth> healths, RandomGenerator rng) {
        return ConfigLoader.loadThreeStateModels(healths, rng);
    }

    @Override
    public ConfigLoader.ExperimentConfig loadExperimentConfig() {
        return ConfigLoader.loadExperimentConfig();
    }
}
