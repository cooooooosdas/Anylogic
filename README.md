# 基于数字孪生的混流装配线效率优化仿真框架

工程实践与创新能力大赛参赛项目。以**一汽解放卡车厂混流装配线**为对象，基于 **AnyLogic 数字孪生** 思想，用 Java 构建了一套离散事件仿真（DES）+ 蒙特卡洛实验框架，用于评估装配序列优化与 PHM（故障预测与健康管理）维修策略的耦合效果。

## 核心功能

- **装配序列优化**：支持启发式贪心、迭代改进两种排产策略，以换型损失最小化为目标。
- **PHM 三状态健康模型**：Normal → Degraded → Failed，基于 Weibull 寿命采样 + 指数劣化计时器。
- **三种维修策略**：S1 计划性维修（固定周期）、S2 状态维修（健康指数阈值）、S3 预测性维修（RUL 预警窗口）。
- **蒙特卡洛批量仿真**：事件驱动引擎（优先级队列），200+ 次独立运行，相同种子序列保证配对比较可复现。
- **统计汇总 + CSV 导出**：均值 / 标准差 / 95% CI，每次运行的原始结果写出到 `results/experiment_{scenario}.csv`，供竞赛报告用 Excel / matplotlib 画图。

## 项目结构

```
├── CLAUDE.md                  # 项目协作规范（编码规范、竞赛约束）
├── pom.xml                    # Maven 构建（Java 17 + Gson）
├── README.md                  # 本文件
├── LICENSE                     # MIT License
├── .gitignore
├── data/                       # 仿真参数（JSON，改参数改这里，不硬编码）
│   ├── experiment_config.json # 蒙特卡洛参数、场景定义
│   ├── stations.json          # 工位定义（21 工位 / 4 条主线）
│   ├── vehicle_models.json    # 车型库（15 款 / 4 大类）
│   ├── changeover_matrix.json # 换型时间矩阵（颜色/驱动/动力/平台）
│   └── equipment_params.json  # 设备 PHM 参数（Weibull / MTTR）
├── src/main/java/assemblyline/
│   ├── data/ConfigLoader.java         # JSON 配置加载（Gson + Java 17 record）
│   ├── model/                         # 领域模型
│   │   ├── AssemblyLine.java          # 产线拓扑（4 线 + 3 缓存区）
│   │   ├── Station.java               # 工位（三角分布加工时间、换型点标记）
│   │   ├── VehicleModel.java          # 车型（颜色/驱动/动力/年产量）
│   │   ├── WorkPiece.java             # 在制品（车身/车架实体）
│   │   └── ...                        # Buffer / Color / DriveType / EngineType / ...
│   ├── utils/
│   │   ├── RandomGenerator.java       # 可重置种子的 RNG（蒙特卡洛复现）
│   │   └── Distribution.java          # 三角 / Weibull / 指数 / 正态 / 泊松
│   ├── phm/                           # 故障预测与健康管理
│   │   ├── EquipmentHealth.java       # 设备健康快照（HI / 累计工时 / MTTR）
│   │   ├── ThreeStateModel.java       # 三状态转移模型
│   │   ├── MaintenanceStrategy.java   # 维修策略接口
│   │   ├── ScheduledMaintenance.java  # S1：固定周期保养
│   │   ├── ConditionBasedMaintenance.java # S2：健康指数阈值
│   │   └── PredictiveMaintenance.java # S3：RUL 预警窗口
│   ├── scheduling/
│   │   ├── ChangeoverMatrix.java      # 换型时间查询（多维度取最大）
│   │   └── SequenceGenerator.java     # 排产序列生成（启发式 / 迭代改进）
│   ├── optimization/
│   │   ├── ObjectiveFunction.java     # 换型代价 + 瓶颈利用率复合目标
│   │   └── SequenceOptimizer.java     # 爬山 + 模拟退火早接受
│   └── simulation/
│       ├── SimulationEngine.java      # 事件驱动仿真引擎（ARRIVAL / PROC / CHANGEOVER / FAILURE / MAINT_END）
│       ├── MonteCarloRunner.java      # 批量运行 + CSV 导出
│       └── StatisticsCollector.java   # 汇总统计（均值 / 标准差 / 95% CI）
└── experiments/                       # 竞赛演示入口
    ├── BaselineExperiment.java        # S1 计划性维修基准组
    ├── OptimizationExperiment.java    # S3 预测性维修优化组
    └── ComparisonExperiment.java      # 两组对比 + 改进率表格（主要输出）
```

## 快速开始

### 编译

```bash
# 方式一：Maven（需安装 Maven 3.8+）
mvn compile

# 方式二：javac 直接编译（需 Gson 在 classpath 中）
# 1. 复制 data/*.json 到 build/classes/data/
# 2. 运行下方编译命令
javac -encoding UTF-8 -cp build/gson -d build/classes \
    $(find src/main/java experiments -name "*.java")
```

### 运行对比实验

```bash
# 确保 data/*.json 在 classpath 下（如 build/classes/data/）
java -cp "build/classes:build/gson" experiments.ComparisonExperiment
# Windows 用分号：java -cp "build/classes;build/gson" experiments.ComparisonExperiment
```

输出示例：

```
══════════════════════════════════════════════
 对比结果（优化组相对基准组的改进率）
══════════════════════════════════════════════
指标                                  基准均值      优化均值     改进率      目标
──────────────────────────────────────────────────────────────
产线可用度                             1.00         1.00      -0.0%    ≥95% ↑
非计划停机次数                         0.33         0.00     100.0%  降低≥40% ↓
...
CSV 输出：results/experiment_baseline.csv / experiment_optimized.csv
```

## 参数说明

所有仿真参数均位于 `data/*.json`，**不要硬编码在 Java 里**。常用调整：

| 参数 | 位置 | 说明 |
|------|------|------|
| 仿真周期 / 预热期 | `experiment_config.json: simulation` | 默认 30 天 / 1 天预热 |
| 蒙特卡洛次数 | `experiment_config.json: monteCarlo.numRuns` | 默认 200 |
| 车型到达间隔 | `experiment_config.json: arrivalProcess.meanInterArrivalMinutes` | 默认 40 min |
| S1 保养间隔 | `experiment_config.json: scenarios.baseline.s1IntervalHours` | 默认 48h |
| S3 RUL 预警窗口 | `ComparisonExperiment.java` 第 59 行 | 默认 72h |
| Weibull η（寿命尺度） | `equipment_params.json: equipment[].weibull.eta` | 拧紧机默认 150h |
| 换型时间矩阵 | `changeover_matrix.json: pairwiseTimes` | 可扩展 CONFIGURATION 维 |

## 仿真假设与限制

- **当前 PHM 仅对配置了 stationId 的设备生效**（拧紧机 CHS-06、抽真空机 EXT-01、取件机 INT-01），扩展其余设备只需在 `equipment_params.json` 增加条目并配置 `stationId`。
- 故障涟漪（rippleEffect）按故障开始到维修结束的时间差计算，未区分瓶颈工位 / 非瓶颈工位的影响权重。
- 换型时间取 COLOR / DRIVE / ENGINE 三个维度的最大值（保守估计），PLATFORM 平台切换覆盖全部子维度。
- 蒙特卡洛复现性保证：两组场景使用相同随机种子序列，相同 seed 下两次运行结果完全一致（已通过 TestRepro 验证，ALL MATCH: true）。

## 竞赛报告配套建议

- `results/experiment_baseline.csv` 和 `experiment_optimized.csv` 可直接导入 Python(matplotlib) / Excel 生成箱线图、直方图。
- 报告 4.5 节"验证逻辑"可引用配对 t 检验（配置在 `experiment_config.json: statisticalTests.pairedTTest`，代码层可扩展）。
- 灵敏度分析建议扰动 Weibull η ±10%（对应 `failureRatePerturbation: 0.10`），验证 S3 策略鲁棒性。

## 技术栈

- Java 17（record / switch 表达式）
- Gson 2.x（JSON 解析）
- 无第三方依赖（无 Spring / 无 Hibernate，保证 AnyLogic 兼容性）
- 代码风格遵循 CLAUDE.md 中的项目协作规范

## License

MIT
