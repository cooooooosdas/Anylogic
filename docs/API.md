# AnyLogic 联调接口文档

> 队友 A 专用。描述 Java 仿真核心逻辑如何被 AnyLogic 调用，以及 `data/*.json` 每个字段对应 AnyLogic 里的哪个参数。
>
> 前提：AnyLogic PLE 8.x（免费版功能足够）。

---

## 1. 整体架构

```
┌──────────────┐         Java API 调用        ┌──────────────────┐
│  AnyLogic    │ ────────────────────────────▶ │  SimulationEngine │
│  动画模型     │ ◀──────────────────────────── │  (纯 Java 逻辑)   │
└──────────────┘        RunResult 返回          └──────────────────┘
      ▲                                                    │
      │                                                    ▼
      │                                            data/*.json
      │                                            (仿真参数)
      ▼                                                    │
 用户点击按钮                                         MonteCarloRunner
 "Scenario S1"                                       (批量运行)
```

**两种集成方式**：
1. **推荐**：AnyLogic 做动画 + 教学演示，Java 独立跑实验（结论一致即可）。
2. **高级**：在 AnyLogic 的 Java 代码片段里 `import assemblyline.simulation.*;` 直接调用。

---

## 2. 核心 API

### 2.1 SimulationEngine

```java
// 构造
SimulationEngine engine = new SimulationEngine(
    assemblyLine,    // AssemblyLine — 产线拓扑
    modelPool,       // List<VehicleModel> — 车型库
    changeoverMatrix,// ChangeoverMatrix — 换型时间矩阵
    maintenanceStrategy, // MaintenanceStrategy — 维修策略
    rng,             // RandomGenerator — 可重置种子的 RNG
    simDurationMinutes, // double — 仿真时长（分钟），如 43200 = 30 天
    warmUpMinutes,   // double — 预热期（分钟），如 1440 = 1 天
    arrivalMeanMinutes // double — 车型到达平均间隔（分钟），如 40
);

// 注入设备健康模型（PHM 用）
engine.setEquipments(equipmentHealthMap, threeStateModelMap);

// 运行
SimulationEngine.RunResult result = engine.run();

// 获取结果
result.lineAvailability        // 产线可用度 0~1
result.unplannedDowntimeCount  // 非计划停机次数
result.unplannedDowntimeMinutes// 非计划停机总分钟
result.avgCycleMinutes         // 平均装配周期（分钟）
result.changeoverEventCount    // 换型事件次数
result.wipPeak                 // WIP 峰值
result.rippleEffectMinutes     // 故障涟漪总时长（分钟）
result.failurePredictionLeadTime // 故障预警提前量（小时）
result.maintenanceCost         // 维护总成本
result.plannedMaintMinutes     // 计划维护总分钟
result.completedCount          // 完成装配的车辆数
```

### 2.2 维修策略

```java
// S1: 计划性维修（固定周期保养）
MaintenanceStrategy s1 = new ScheduledMaintenance(720); // 720h 保养一次

// S2: 状态维修（健康指数低于阈值时保养）
MaintenanceStrategy s2 = new ConditionBasedMaintenance(60); // HI < 60

// S3: 预测性维修（RUL 低于窗口时保养）
MaintenanceStrategy s3 = new PredictiveMaintenance(72); // RUL < 72h
```

### 2.3 MonteCarloRunner（批量运行）

```java
MonteCarloRunner runner = new MonteCarloRunner(
    assemblyLine, modelPool, changeoverMatrix, maintenanceStrategy,
    200,           // 蒙特卡洛次数
    43200,         // 仿真时长（分钟）
    1440,          // 预热期（分钟）
    40,            // 到达间隔均值（分钟）
    42             // 随机种子
);

// 返回统计汇总
StatisticsCollector.Summary summary = runner.run();

// 同时写出 CSV
StatisticsCollector.Summary summary = runner.run("scenarioName", Path.of("results/"));
```

---

## 3. data/*.json → AnyLogic 参数映射

### 3.1 stations.json → AnyLogic 工位属性

| JSON 字段 | AnyLogic 对应 | 说明 |
|-----------|--------------|------|
| `lines[].stations[].processingTime` (3 元素数组) | `Delay` 块的 **三角分布** | `processingTime[0]` = min, `[1]` = mode, `[2]` = max |
| `lines[].stations[].changeoverPoint` | `Statechart` 的 **换型状态** | `true` = 该工位需要换型 |
| `lines[].stations[].changeoverType` | 换型 **维度类型** | COLOR / DRIVE / ENGINE / CONFIGURATION |
| `lines[].stations[].hasTighteningMachine` | PHM 仪表盘 **归属工位** | true = 拧紧机，有健康指数曲线 |
| `buffers.capacity` | `Queue` 块的 **容量** | 如 `EXTERIOR_to_INSPECTION: 10` |

**AnyLogic 中设置三角分布**：
```
Delay time: triangular( min, mode, max )
= triangular( 8, 10, 12 )  // 对应 stations.json 的 processingTime 数组
```

### 3.2 vehicle_models.json → AnyLogic Source 属性

| JSON 字段 | AnyLogic 对应 | 说明 |
|-----------|--------------|------|
| `models[].category` | `Source` 的 **Arrival rate 参数** | 按 category 加权 |
| `models[].color` | `MoveTo` 的 **动画外观** | WHITE=白色车, RED=红色车... |
| `models[].annualVolume` | Source 的 **生成频率权重** | 年产量越高，到达越频繁 |

**AnyLogic 中设置加权随机**：
```
Source → Arrival rate: custom
= uniform( 0, 1 ) < cumulative_weight / total_weight
```

### 3.3 changeover_matrix.json → AnyLogic 换型逻辑

| JSON 字段 | AnyLogic 对应 | 说明 |
|-----------|--------------|------|
| `dimensions.COLOR.mean / sigma` | `Delay` 块的 **颜色换型时间** | 正态分布 mean±sigma |
| `dimensions.DRIVE.mean / sigma` | `Delay` 块的 **驱动换型时间** | 同上 |
| `pairwiseTimes.DRIVE["FWD→RWD"]` | **精确换型时间** | 优先用 pairwise，否则用 mean |
| `changeoverClustering.priorityOrder` | `SelectOutput` 的 **换型判断逻辑** | PLATFORM > COLOR > DRIVE > ENGINE |

### 3.4 equipment_params.json → AnyLogic PHM 仪表盘

| JSON 字段 | AnyLogic 对应 | 说明 |
|-----------|--------------|------|
| `equipment[].weibull.eta` | PHM 模型的 **故障阈值时间** | 拧紧机 150h |
| `equipment[].mttr.mean` | `Delay` 块的 **维修时间** | 三角分布 |
| `equipment[].healthIndex.degradationRate` | **HI 下降斜率** | 每小时下降量 |
| `equipment[].healthIndex.warningThreshold` | PHM 仪表盘 **黄色阈值** | 默认 60 |
| `equipment[].stationId` | 仪表盘 **归属工位** | CHS-06 = 拧紧机工位 |

---

## 4. AnyLogic 建模快速参考

### 4.1 单工位加工（最小单元）

```
Source → Seize(resource: 工位) → Delay(triangular(min, mode, max)) → Release → Sink
```

### 4.2 换型状态机

```
<Statechart name="changeoverState">
  State: "正常加工" — 初始态
  Transition: "车型切换触发" → 目标态 "换型中"
    Condition: prevModel.id != nextModel.id && isChangeoverPoint
    Delay: changeoverMatrix.changeoverTime(prev, next) 分钟
  Transition: "换型完成" → 目标态 "正常加工"
    Condition: changeoverState.getTime() >= changeoverDuration
</Statechart>
```

### 4.3 PHM 健康指数曲线

```
<Chart name="healthChart">
  Y axis: healthIndex (0–100)
  X axis: time (小时)
  Series 1 (NORMAL): green line
  Series 2 (DEGRADED): yellow line
  Series 3 (FAILED): red line
  Threshold lines: 60 (warning), 30 (critical)
</Chart>
```

---

## 5. 常见问题

**Q: AnyLogic PLE 版有 Optimization.optQuest() 吗？**
A: PLE 版没有内置 OptQuest。可以用 OptQuest 插件（免费），或者用 AnyLogic 的内置优化（Parameter Variation + 自定义目标函数）。代码里 `SequenceOptimizer` 已实现爬山算法，PLE 版直接用 Java 代码即可。

**Q: 怎么读 JSON 文件？**
A: AnyLogic 支持 `java.io.File` 读取相对路径 JSON，然后用 Gson 解析。或者手动把 `data/stations.json` 的值填到 AnyLogic 的 Parameter 里，不用读文件。

**Q: 蒙特卡洛 200 次要跑多久？**
A: 200 次 ≈ 3 秒（纯 Java  bench）。AnyLogic 内置仿真会慢 10–100 倍，建议只用 AnyLogic 跑单次演示，批量实验用 Java 代码。
