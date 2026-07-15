package assemblyline.data;

import assemblyline.model.AssemblyLine;
import assemblyline.model.VehicleModel;
import assemblyline.phm.EquipmentHealth;
import assemblyline.phm.ThreeStateModel;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.utils.RandomGenerator;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 企业真实数据源桩（Phase 1 对接）。
 *
 * <p>当前为桩实现：所有 {@code loadXxx()} 方法抛出
 * {@link UnsupportedOperationException}，附带填写说明。
 * 开学后对接一汽数据时，按下方注释指示逐一实现。</p>
 *
 * <p>参考 {@code docs/ALGORITHM.md §8.2 真实数据对接路径}。</p>
 *
 * <h3>Phase 1 实现清单</h3>
 * <table>
 *   <tr><th>方法</th><th>数据来源</th><th>一汽系统</th></tr>
 *   <tr>
 *     <td>{@link #loadAssemblyLine}</td>
 *     <td>MES 工位清单 + 产线拓扑</td>
 *     <td>制造执行系统 / ERP</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #loadVehicleModels}</td>
 *     <td>真实 SOR（销售订单记录）年产量</td>
 *     <td>CRM / DMS</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #loadChangeoverMatrix}</td>
 *     <td>历史换型记录（前后车型、耗时）</td>
 *     <td>MES / 精益生产看板</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #loadEquipmentHealths}</td>
 *     <td>设备故障历史 → 拟合 Weibull 参数</td>
 *     <td>CMMS（计算机化维护管理系统）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #loadThreeStateModels}</td>
 *     <td>同上（DEGRADED 计时器均值需标定）</td>
 *     <td>CMMS</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #loadExperimentConfig}</td>
 *     <td>仿真窗口、成本参数可保留 JSON 或写入数据库</td>
 *     <td>项目配置表</td>
 *   </tr>
 * </table>
 *
 * <h3>接入方式</h3>
 * <ul>
 *   <li><b>JDBC</b>：直接连一汽 MySQL / Oracle，查询视图</li>
 *   <li><b>REST API</b>：若一汽提供中间件服务，用 {@link java.net.http.HttpClient}</li>
 *   <li><b>CSV 导出</b>：作为过渡，由企业提供定期导出，本地解析</li>
 * </ul>
 *
 * <h3>切换方式</h3>
 * <pre>
 *   // 在 MonteCarloRunner 或实验入口处替换
 *   DataSource ds = new EnterpriseDataSource(
 *           "jdbc:mysql://faw-prod-db:3306/mes", "user", "pwd");
 *   AssemblyLine line = ds.loadAssemblyLine();
 * </pre>
 *
 * // AnyLogic API: teammate A 可将 JDBC 连接写在 AnyLogic 的 Database 元素中，
 * //  然后把结果以 Java bean 形式传给仿真引擎。
 */
public class EnterpriseDataSource implements DataSource {

    /** 数据源连接串（JDBC URL / REST base URL / CSV 目录） */
    private final String connectionString;
    /** 认证信息（生产环境应走密钥管理服务，不要硬编码） */
    private final Properties credentials;

    /**
     * @param connectionString JDBC URL 或 REST 根路径或 CSV 目录
     * @param credentials      可空；生产环境应从环境变量 / 密钥管理服务读取
     */
    public EnterpriseDataSource(String connectionString, Properties credentials) {
        this.connectionString = Objects.requireNonNull(connectionString);
        this.credentials = credentials != null ? credentials : new Properties();
    }

    // ── DataSource 接口 ────────────────────────────────────────

    @Override
    public AssemblyLine loadAssemblyLine() {
        // TODO(Phase 1): 查询 MES 工位表
        //   SELECT station_id, station_name, line_id, processing_time_min,
        //          processing_time_mode, processing_time_max, is_changeover_point
        //   FROM mes_station WHERE line_id = ?
        // 映射为 AssemblyLine → Line → Station
        throw unsupported("loadAssemblyLine",
                "从 MES 查询产线拓扑，映射为 AssemblyLine/Loader/Station 对象");
    }

    @Override
    public List<VehicleModel> loadVehicleModels() {
        // TODO(Phase 1): 查询 CRM 车型年产量
        //   SELECT model_id, model_name, category, color, drive_type,
        //          engine_type, annual_volume
        //   FROM crm_vehicle_model WHERE year = ?
        throw unsupported("loadVehicleModels",
                "从 CRM/DMS 获取真实 SOR 车型年产量");
    }

    @Override
    public ChangeoverMatrix loadChangeoverMatrix(RandomGenerator rng) {
        // TODO(Phase 1): 从 MES 历史换型记录拟合
        //   SELECT prev_model_id, next_model_id, AVG(changeover_minutes) AS mean,
        //          STDDEV(changeover_minutes) AS sigma
        //   FROM mes_changeover_log
        //   GROUP BY prev_model_id, next_model_id
        //   HAVING COUNT(*) >= 5   // 至少 5 次样本才纳入
        throw unsupported("loadChangeoverMatrix",
                "从 MES 换型记录拟合各维度均值/标准差 + pairwise 表");
    }

    @Override
    public Map<String, EquipmentHealth> loadEquipmentHealths(RandomGenerator rng) {
        // TODO(Phase 1): 从 CMMS 故障历史拟合 Weibull 参数
        //   1. 查询每条设备故障记录：equipment_id, failure_time, repair_time
        //   2. 计算运行时间 = 上次维修后到故障的累计小时
        //   3. 最大似然估计拟合 Weibull(β, η)
        //   4. 更新 equipment_params.json 或直接传给 EquipmentHealth 构造器
        throw unsupported("loadEquipmentHealths",
                "从 CMMS 故障历史拟合 Weibull(β, η)，构造 EquipmentHealth");
    }

    @Override
    public Map<String, ThreeStateModel> loadThreeStateModels(
            Map<String, EquipmentHealth> healths, RandomGenerator rng) {
        // TODO(Phase 1): 与 loadEquipmentHealths 同步，DEGRADED→FAILED 均值
        //   从历史劣化间隔采样估计（当前假设 50h，需标定）
        throw unsupported("loadThreeStateModels",
                "从 CMMS 劣化记录估计 DEGRADED→FAILED 指数均值");
    }

    @Override
    public ConfigLoader.ExperimentConfig loadExperimentConfig() {
        // 仿真窗口、成本参数可先保留 JSON 作为过渡
        // TODO(Phase 3): 写入企业项目配置表
        throw unsupported("loadExperimentConfig",
                "从项目配置表读取仿真参数（或保留 JSON 过渡）");
    }

    // ── 辅助 ────────────────────────────────────────────────────

    private static UnsupportedOperationException unsupported(String method, String todo) {
        return new UnsupportedOperationException(
                "[EnterpriseDataSource." + method + "] 尚未对接真实数据。TODO: " + todo);
    }

    // ── Phase 1 预留：JDBC 连接模板 ──────────────────────────

    /**
     * 打开 JDBC 连接的模板方法（Phase 1 实现时参考）。
     *
     * @return 已连接的 JDBC Connection
     * @throws SQLException 连接失败
     */
    protected Connection openJdbcConnection() throws SQLException {
        String url = connectionString;
        Properties props = new Properties(credentials);
        // 生产环境：从环境变量 / 密钥管理服务读取凭证
        // String dbUser = System.getenv("FAW_DB_USER");
        // String dbPass = System.getenv("FAW_DB_PASS");
        // props.setProperty("user", dbUser);
        // props.setProperty("password", dbPass);
        return DriverManager.getConnection(url, props);
    }

    /**
     * 查询单列的模板方法（Phase 1 实现时参考）。
     */
    protected List<Map<String, Object>> query(String sql, Object... args) throws SQLException {
        try (Connection c = openJdbcConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= cols; col++) {
                        row.put(md.getColumnLabel(col), rs.getObject(col));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
